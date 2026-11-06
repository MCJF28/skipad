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
 * 会话模型：
 *  - 目标包回到前台 = 新会话（点过一次也重新计数）
 *  - 目标包真正离开前台（被别的应用顶掉）= 会话结束
 *  - 系统 UI / 输入法 / 权限弹窗等中间窗口不会打断会话
 */
class SkipAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())

    private var sessionPkg: String? = null
    private var rule: Rule? = null
    private var startAt = 0L
    private var clicks = 0
    private var running = false
    private var leftForeground = true
    private var currentClass = ""
    private var lastTargetSeen = 0L
    private var lastScanAt = 0L
    private var fgPkg: String? = null
    private var lastPairScanAt = 0L
    private var lastPairClickAt = 0L
    private var seenNodes = 0

    private val lastClickAt = HashMap<String, Long>()

    /** 这些包名的窗口弹出不代表目标应用离开前台 */
    private fun isTransient(pkg: String): Boolean {
        val p = pkg.lowercase()
        return p.contains("systemui") ||
            p.contains("inputmethod") ||
            p.contains("permissioncontroller") ||
            p.contains("packageinstaller") ||
            p.contains("launcher")
    }

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
            val p = e.packageName?.toString()
            if (!p.isNullOrBlank()) fgPkg = p
        }
        when (e.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                onWindowChanged(e)
                pairScan()
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                if (running) {
                    val now = SystemClock.uptimeMillis()
                    if (now - lastScanAt >= 60L) scan()
                }
                pairScan()
            }
            else -> Unit
        }
    }

    private fun onWindowChanged(e: AccessibilityEvent) {
        val pkg = e.packageName?.toString() ?: return
        val cls = e.className?.toString() ?: ""
        val now = SystemClock.uptimeMillis()
        val r = Rules.forPackage(pkg)

        if (r != null) {
            val startNew = leftForeground || !running || sessionPkg != pkg
            if (startNew) {
                sessionPkg = pkg
                rule = r
                clicks = 0
                startAt = now
                running = true
                leftForeground = false
                currentClass = cls
                LogBus.add("观察 $pkg / $cls")
                handler.removeCallbacks(poll)
                scan()
                if (running) handler.postDelayed(poll, 80L)
            } else {
                currentClass = cls
            }
            lastTargetSeen = now
            return
        }

        if (isTransient(pkg)) return

        // 真正切到别的应用
        if (running) {
            leftForeground = true
            if (now - lastTargetSeen > 1200L) {
                running = false
                handler.removeCallbacks(poll)
                LogBus.add("会话结束 $sessionPkg（节点 $seenNodes，文本 ${RuleEngine.lastSample}）")
            }
        } else {
            leftForeground = true
        }
    }

    private fun scan() {
        val r = rule ?: return
        if (!running || clicks >= r.maxClicks) return

        val elapsed = SystemClock.uptimeMillis() - startAt
        if (elapsed > r.windowMs) {
            running = false
            handler.removeCallbacks(poll)
            LogBus.add("窗口结束 ${r.pkg}（节点 $seenNodes，文本 ${RuleEngine.lastSample}）")
            return
        }
        lastScanAt = SystemClock.uptimeMillis()

        val root = try {
            rootInActiveWindow
        } catch (t: Throwable) {
            null
        }
        if (root == null) {
            seenNodes = 0
            return
        }
        seenNodes = RuleEngine.lastNodeCount

        val hit = RuleEngine.find(this, root, r, elapsed, currentClass) ?: return
        if (click(hit)) {
            clicks++
            lastClickAt[r.pkg] = SystemClock.uptimeMillis()
            running = false
            handler.removeCallbacks(poll)
            LogBus.add("已跳过 ${r.pkg} · ${hit.via} · ${elapsed}ms")
        }
    }

    /** 条件点击通道：与开屏窗口无关，只要目标包在前台就持续检查 */
    private fun pairScan() {
        val pkg = fgPkg ?: return
        val r = Rules.forPackage(pkg) ?: return
        if (r.pairs.isEmpty()) return

        val now = SystemClock.uptimeMillis()
        if (now - lastPairScanAt < 400L) return
        if (now - lastPairClickAt < 1500L) return
        lastPairScanAt = now

        val root = try {
            rootInActiveWindow
        } catch (t: Throwable) {
            null
        } ?: return

        val hit = RuleEngine.findPair(root, r.pairs) ?: return
        if (click(hit)) {
            lastPairClickAt = SystemClock.uptimeMillis()
            LogBus.add("已点击 ${hit.via} · $pkg")
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
