package com.dsh.skipad.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.dsh.skipad.data.LogBus
import com.dsh.skipad.data.NetRules
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * 本地 DNS 过滤（实验功能，默认关闭）。
 *
 * 只把 10.111.222.1 这一个地址路由进隧道，也就是只接管 DNS 查询：
 * 命中黑名单直接回 NXDOMAIN，其余转发给上游解析器。
 */
class AdFilterVpnService : VpnService() {

    companion object {
        const val ACTION_START = "com.dsh.skipad.vpn.START"
        const val ACTION_STOP = "com.dsh.skipad.vpn.STOP"

        private const val CHANNEL = "skipad_vpn"
        private const val NOTI_ID = 4201
        private const val UPSTREAM = "223.5.5.5"
        private const val TUN_ADDR = "10.111.222.1"

        @Volatile
        var running: Boolean = false
            private set

        @Volatile
        var blocked: Int = 0
            private set

        @Volatile
        var queried: Int = 0
            private set

        @Volatile
        var lastBlocked: String = ""
            private set

        fun start(ctx: Context) {
            val i = Intent(ctx, AdFilterVpnService::class.java).setAction(ACTION_START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i)
            else ctx.startService(i)
        }

        fun stop(ctx: Context) {
            val i = Intent(ctx, AdFilterVpnService::class.java).setAction(ACTION_STOP)
            try {
                ctx.startService(i)
            } catch (t: Throwable) {
                // 服务未运行时忽略
            }
        }

        /** 是否有别的 VPN 正在占用系统槽位（我们的没在跑时才算冲突） */
        fun otherVpnActive(ctx: Context): Boolean {
            if (running) return false
            return try {
                val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                val net = cm.activeNetwork ?: return false
                val caps = cm.getNetworkCapabilities(net) ?: return false
                caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
            } catch (t: Throwable) {
                false
            }
        }
    }

    private var tun: ParcelFileDescriptor? = null
    private var worker: Thread? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            shutdown()
            return START_NOT_STICKY
        }
        startForegroundSafely()
        startTunnel()
        return START_STICKY
    }

    private fun startForegroundSafely() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val nm = getSystemService(NotificationManager::class.java)
                val ch = NotificationChannel(CHANNEL, "网络层拦截", NotificationManager.IMPORTANCE_LOW)
                nm?.createNotificationChannel(ch)
            }
            val noti: Notification = NotificationCompat.Builder(this, CHANNEL)
                .setContentTitle("网络层拦截已开启")
                .setContentText("正在过滤广告域名，可随时关闭")
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setOngoing(true)
                .build()
            startForeground(NOTI_ID, noti)
        } catch (t: Throwable) {
            // 通知失败不影响隧道
        }
    }

    private fun startTunnel() {
        if (running || tun != null) return
        try {
            val b = Builder()
            b.setSession("开屏跳过 · 广告域名过滤")
            b.addAddress(TUN_ADDR, 32)
            b.addRoute(TUN_ADDR, 32)
            b.addDnsServer(TUN_ADDR)
            b.setBlocking(true)
            val pfd = b.establish()
            if (pfd == null) {
                LogBus.add("网络层拦截启动失败：未获得 VPN 授权")
                stopSelf()
                return
            }
            tun = pfd
            running = true
            worker = Thread({ loop(pfd) }, "skipad-dns").also { it.start() }
            LogBus.add("网络层拦截已启动（域名规则 ${NetRules.count} 条）")
        } catch (t: Throwable) {
            LogBus.add("网络层拦截启动异常：${t.message}")
            stopSelf()
        }
    }

    private fun loop(pfd: ParcelFileDescriptor) {
        val input = FileInputStream(pfd.fileDescriptor)
        val output = FileOutputStream(pfd.fileDescriptor)
        val buf = ByteArray(32767)
        var forwardFails = 0

        while (running) {
            val n = try {
                input.read(buf)
            } catch (t: Throwable) {
                break
            }
            if (n <= 0) continue
            val ok = try {
                handlePacket(buf, n, output)
            } catch (t: Throwable) {
                false
            }
            if (ok) {
                forwardFails = 0
            } else {
                forwardFails++
                if (forwardFails > 20) {
                    LogBus.add("上游解析连续失败，已自动关闭网络层过滤")
                    shutdown()
                    return
                }
            }
        }
    }

    /** 返回 true 表示这条查询已被处理（应答或有意忽略） */
    private fun handlePacket(pkt: ByteArray, n: Int, out: FileOutputStream): Boolean {
        if (n < 28) return true
        val version = (pkt[0].toInt() ushr 4) and 0xF
        if (version != 4) return true
        val ihl = (pkt[0].toInt() and 0xF) * 4
        if (ihl < 20 || n < ihl + 8) return true
        if ((pkt[9].toInt() and 0xFF) != 17) return true // 只处理 UDP

        val srcPort = ((pkt[ihl].toInt() and 0xFF) shl 8) or (pkt[ihl + 1].toInt() and 0xFF)
        val dstPort = ((pkt[ihl + 2].toInt() and 0xFF) shl 8) or (pkt[ihl + 3].toInt() and 0xFF)
        if (dstPort != 53) return true

        val srcIp = pkt.copyOfRange(12, 16)
        val dstIp = pkt.copyOfRange(16, 20)
        val dnsLen = n - ihl - 8
        if (dnsLen <= 12) return true

        val dns = ByteArray(dnsLen)
        System.arraycopy(pkt, ihl + 8, dns, 0, dnsLen)

        queried++
        val name = DnsCodec.queryName(dns, dnsLen)

        if (name != null && NetRules.isBlocked(name)) {
            val resp = DnsCodec.buildNxdomain(dns, dnsLen)
            if (resp != null) {
                writeResponse(out, dstIp, 53, srcIp, srcPort, resp)
                blocked++
                lastBlocked = name
                return true
            }
        }

        val upstream = forward(dns, dnsLen) ?: return false
        writeResponse(out, dstIp, 53, srcIp, srcPort, upstream)
        return true
    }

    private fun forward(query: ByteArray, len: Int): ByteArray? {
        var socket: DatagramSocket? = null
        return try {
            val s = DatagramSocket()
            socket = s
            protect(s)
            s.soTimeout = 4000
            val addr = InetAddress.getByName(UPSTREAM)
            s.send(DatagramPacket(query, len, addr, 53))
            val rb = ByteArray(4096)
            val rp = DatagramPacket(rb, rb.size)
            s.receive(rp)
            rb.copyOf(rp.length)
        } catch (t: Throwable) {
            null
        } finally {
            try {
                socket?.close()
            } catch (t: Throwable) {
                // 忽略
            }
        }
    }

    private fun writeResponse(
        out: FileOutputStream,
        fromIp: ByteArray,
        fromPort: Int,
        toIp: ByteArray,
        toPort: Int,
        payload: ByteArray
    ) {
        val total = 20 + 8 + payload.size
        val p = ByteArray(total)
        p[0] = 0x45
        p[1] = 0
        p[2] = ((total shr 8) and 0xFF).toByte()
        p[3] = (total and 0xFF).toByte()
        p[6] = 0x40
        p[8] = 64
        p[9] = 17
        System.arraycopy(fromIp, 0, p, 12, 4)
        System.arraycopy(toIp, 0, p, 16, 4)
        val sum = ipChecksum(p)
        p[10] = ((sum shr 8) and 0xFF).toByte()
        p[11] = (sum and 0xFF).toByte()

        val u = 20
        p[u] = ((fromPort shr 8) and 0xFF).toByte()
        p[u + 1] = (fromPort and 0xFF).toByte()
        p[u + 2] = ((toPort shr 8) and 0xFF).toByte()
        p[u + 3] = (toPort and 0xFF).toByte()
        val ulen = 8 + payload.size
        p[u + 4] = ((ulen shr 8) and 0xFF).toByte()
        p[u + 5] = (ulen and 0xFF).toByte()
        // UDP 校验和置 0（IPv4 下表示不校验）
        System.arraycopy(payload, 0, p, 28, payload.size)

        out.write(p)
        out.flush()
    }

    private fun ipChecksum(p: ByteArray): Int {
        var sum = 0
        var i = 0
        while (i < 20) {
            sum += ((p[i].toInt() and 0xFF) shl 8) or (p[i + 1].toInt() and 0xFF)
            i += 2
        }
        while ((sum shr 16) != 0) sum = (sum and 0xFFFF) + (sum shr 16)
        return sum.inv() and 0xFFFF
    }

    private fun shutdown() {
        running = false
        try {
            worker?.interrupt()
        } catch (t: Throwable) {
            // 忽略
        }
        worker = null
        try {
            tun?.close()
        } catch (t: Throwable) {
            // 忽略
        }
        tun = null
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (t: Throwable) {
            // 忽略
        }
        LogBus.add("网络层拦截已关闭")
        stopSelf()
    }

    override fun onRevoke() {
        LogBus.add("系统撤销了 VPN 授权")
        shutdown()
    }

    override fun onDestroy() {
        running = false
        try {
            worker?.interrupt()
        } catch (t: Throwable) {
            // 忽略
        }
        try {
            tun?.close()
        } catch (t: Throwable) {
            // 忽略
        }
        tun = null
        super.onDestroy()
    }
}
