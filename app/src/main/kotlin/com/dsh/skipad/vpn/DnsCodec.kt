package com.dsh.skipad.vpn

/** 最小化的 DNS 报文解析 / 构造，只为本地过滤服务。 */
object DnsCodec {

    /** 从查询报文里取出域名（小写）。失败返回 null。 */
    fun queryName(msg: ByteArray, len: Int): String? {
        if (len < 13) return null
        var pos = 12
        val sb = StringBuilder()
        while (pos < len) {
            val l = msg[pos].toInt() and 0xFF
            if (l == 0) {
                return if (sb.isEmpty()) null else sb.toString().lowercase()
            }
            if (l and 0xC0 != 0) return null
            pos++
            if (pos + l > len) return null
            if (sb.isNotEmpty()) sb.append('.')
            var i = 0
            while (i < l) {
                val c = msg[pos + i].toInt() and 0xFF
                sb.append(if (c in 33..126) c.toChar() else '?')
                i++
            }
            pos += l
        }
        return null
    }

    /** 构造 NXDOMAIN 应答：保留问题段，清空资源记录。 */
    fun buildNxdomain(query: ByteArray, len: Int): ByteArray? {
        val qEnd = questionEnd(query, len) ?: return null
        val out = ByteArray(qEnd)
        System.arraycopy(query, 0, out, 0, qEnd)
        out[2] = 0x81.toByte()   // QR=1, RD=1
        out[3] = 0x83.toByte()   // RA=1, RCODE=3
        out[6] = 0; out[7] = 0   // ANCOUNT
        out[8] = 0; out[9] = 0   // NSCOUNT
        out[10] = 0; out[11] = 0 // ARCOUNT
        return out
    }

    private fun questionEnd(msg: ByteArray, len: Int): Int? {
        var pos = 12
        while (pos < len) {
            val l = msg[pos].toInt() and 0xFF
            if (l == 0) {
                val end = pos + 1 + 4
                return if (end <= len) end else null
            }
            if (l and 0xC0 != 0) return null
            pos += 1 + l
        }
        return null
    }
}
