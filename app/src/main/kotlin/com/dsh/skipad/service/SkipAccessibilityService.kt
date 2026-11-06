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
 * 只在「前台包名切换」后的规则窗口内工作，其余时间完全不碰屏幕。
 */
class SkipAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())

    private var currentPkg: String? = null
    private var startAt = 0L
    private var clicks = 0
    private var rule: Rule? = null
    private var stopped = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        LogBus.init(this)
        Rules.load(this)
        LogBus.add("无障碍服务已连接，规则 ${Rules.size()} 条")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val e = event ?: return
        if (e.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            onWindowChanged(e)
        }
    }

    private fun onWindowChanged(e: AccessibilityEvent) {
        val pkg = e.packageName?.toString() ?: return
        if (pkg == currentPkg) return

        currentPkg = pkg
        clicks = 0
        stopped = false
        handler.removeCallbacks(poll)

        val r = Rules.forPackage(pkg)
        rule = r
        if (r == null) return

        startAt = SystemClock.uptimeMillis()
        LogBus.add("观察 $pkg")
        handler.postDelayed(poll, r.delayMs)
    }

    private val poll = object : Runnable {
        override fun run() {
            val r = rule ?: return
            if (stopped || clicks >= r.maxClicks) return

            val elapsed = SystemClock.uptimeMillis() - startAt
            if (elapsed > r.windowMs) {
                LogBus.add("${r.pkg} 窗口结束，未命中")
                return
            }

            val root = try {
                rootInActiveWindow
            } catch (t: Throwable) {
                null
            }

            if (root != null) {
                val hit = RuleEngine.find(this@SkipAccessibilityService, root, r, elapsed)
                if (hit != null && click(hit)) {
                    clicks++
                    LogBus.add("已跳过 ${r.pkg} · ${hit.via}")
                    return
                }
            }

            handler.postDelayed(this, 150L)
        }
    }

    private fun click(hit: Hit): Boolean {
        val n = hit.node
        if (n != null && n.isClickable) {
            try {
                if (n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            } catch (t: Throwable) {
                // 落到手势点击
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
        stopped = true
        handler.removeCallbacks(poll)
        LogBus.add("服务被中断")
    }

    override fun onDestroy() {
        stopped = true
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
