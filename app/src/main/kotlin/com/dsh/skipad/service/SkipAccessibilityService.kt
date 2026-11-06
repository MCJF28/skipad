package com.dsh.skipad.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.dsh.skipad.data.LogBus
import com.dsh.skipad.engine.Hit
import com.dsh.skipad.engine.Rule
import com.dsh.skipad.engine.RuleEngine
import com.dsh.skipad.engine.Rules

/**
 * 开屏广告跳过服务。
 *
 * 安全边界：
 *  - 只在「前台包名发生切换」时开启观察窗口
 *  - 同包名有冷却时间，避免切出去回来就被点
 *  - 坐标兜底必须看到锚点文字（如「广告」角标）且在屏幕上部
 */
class SkipAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())

    private var currentPkg: String? = null
    private var startAt = 0L
    private var clicks = 0
    private var rule: Rule? = null
    private var running = false
    private var lastScanAt = 0L

    private val lastClickAt = HashMap<String, Long>()

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        LogBus.init(this)
        Rules.load(this)
        LogBus.add("无障碍服务已连接，规则 ${Rules.size()} 条")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val e = event ?: return
        when (e.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> onWindowChanged(e)
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                if (running) {
                    val now = SystemClock.uptimeMillis()
                    if (now - lastScanAt >= 60L) scan()
                }
            }
            else -> Unit
        }
    }

    private fun onWindowChanged(e: AccessibilityEvent) {
        val pkg = e.packageName?.toString() ?: return
        if (pkg == currentPkg) return

        currentPkg = pkg
        handler.removeCallbacks(poll)
        running = false
        clicks = 0
        rule = null

        val r = Rules.forPackage(pkg) ?: return

        val last = lastClickAt[pkg] ?: 0L
        if (last > 0L && SystemClock.uptimeMillis() - last < r.cooldownMs) {
            LogBus.add("$pkg 冷却中，跳过")
            return
        }

        rule = r
        startAt = SystemClock.uptimeMillis()
        running = true
        LogBus.add("观察 $pkg / ${e.className}")

        scan()                       // 立刻扫一次，抢在广告渲染前
        if (running) handler.postDelayed(poll, 80L)
    }

    private fun scan() {
        val r = rule ?: return
        if (!running || clicks >= r.maxClicks) return

        val elapsed = SystemClock.uptimeMillis() - startAt
        if (elapsed > r.windowMs) {
            running = false
            handler.removeCallbacks(poll)
            LogBus.add("${r.pkg} 窗口结束")
            return
        }
        lastScanAt = SystemClock.uptimeMillis()

        val root = try {
            rootInActiveWindow
        } catch (t: Throwable) {
            null
        } ?: return

        val hit = RuleEngine.find(this, root, r, elapsed) ?: return
        if (click(hit)) {
            clicks++
            lastClickAt[r.pkg] = SystemClock.uptimeMillis()
            running = false
            handler.removeCallbacks(poll)
            LogBus.add("已跳过 ${r.pkg} · ${hit.via} · ${elapsed}ms")
        }
    }

    private val poll = object : Runnable {
        override fun run() {
            if (!running) return
            scan()
            if (running) handler.postDelayed(this, 80L)
        }
    }

    private fun click(hit: Hit): Boolean {
        val n = hit.node
        if (n != null) {
            try {
                if (n.isClickable && n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            } catch (t: Throwable) {
                // 落到手势
            }
        }
        val path = Path()
        path.moveTo(hit.x.toFloat(), hit.y.toFloat())
        path.lineTo(hit.x.toFloat() + 2f, hit.y.toFloat() + 2f)
        val g = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, 60L))
            .build()
        return try {
            dispatchGesture(g, null, null)
        } catch (t: Throwable) {
            false
        }
    }

    override fun onInterrupt() {
        running = false
        handler.removeCallbacks(poll)
        LogBus.add("服务被中断")
    }

    override fun onDestroy() {
        running = false
        handler.removeCallbacks(poll)
        instance = null
        super.onDestroy()
    }

    companion object {
        @Volatile
        var instance: SkipAccessibilityService? = null
            private set
    }
}
