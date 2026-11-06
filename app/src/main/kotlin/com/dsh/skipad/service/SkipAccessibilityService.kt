package com.dsh.skipad.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.dsh.skipad.data.CustomRules
import com.dsh.skipad.data.LogBus
import com.dsh.skipad.data.Prefs
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
            p.contains("packageinstaller")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Prefs.init(this)
        CustomRules.init(this)
        LogBus.init(this)
        Rules.load(this)
        LogBus.add("无障碍服务已连接，规则 ${Rules.size()} 条")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val e = event ?: return
        if (!Prefs.masterEnabled()) {
            if (running) {
                running = false
                handler.removeCallbacks(poll)
                LogBus.add("总开关已关闭，停止观察")
            }
            return
        }
        val evPkg = e.packageName?.toString()
        if (!evPkg.isNullOrBlank()) {
            if (evPkg == packageName) return
            maybeShowCalibration(evPkg)
        }
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
        if (pkg == packageName) return   // 自己的界面（含校准浮层）不算目标
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

        // 硬约束：当前窗口必须属于目标应用，否则说明用户已经离开（比如回了桌面），
        // 绝不能在任何非目标界面上点击。
        val rootPkg = try {
            root.packageName?.toString()
        } catch (t: Throwable) {
            null
        }
        if (rootPkg != null && rootPkg != r.pkg) {
            running = false
            handler.removeCallbacks(poll)
            LogBus.add("离开 ${r.pkg}（当前 $rootPkg），停止观察")
            return
        }

        seenNodes = RuleEngine.lastNodeCount

        val hit = RuleEngine.find(this, root, r, elapsed, currentClass) ?: return
        if (click(hit)) {
            clicks++
            lastClickAt[r.pkg] = SystemClock.uptimeMillis()
            running = false
            handler.removeCallbacks(poll)
            Prefs.onSkip(r.pkg)
            LogBus.add("已跳过 ${r.pkg} · ${hit.via} · ${elapsed}ms")
        }
    }

    // ---------------- 学习模式：校准浮层 ----------------

    @Volatile
    private var overlayView: View? = null

    private fun maybeShowCalibration(pkg: String) {
        if (overlayView != null) return
        val pending = Prefs.pendingCalibration()
        if (pending.isBlank() || pending != pkg) return
        showCalibrationOverlay(pkg)
    }

    private fun showCalibrationOverlay(pkg: String) {
        val wm = getSystemService(WINDOW_SERVICE) as? WindowManager ?: return
        val hint = TextView(this).apply {
            text = "点一下屏幕上的「跳过」按钮\n（点空白处可取消）"
            textSize = 17f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setBackgroundColor(0x99000000.toInt())
            isClickable = true
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        hint.setOnTouchListener { v, ev ->
            if (ev.action == MotionEvent.ACTION_UP) {
                val w = if (v.width > 0) v.width else resources.displayMetrics.widthPixels
                val h = if (v.height > 0) v.height else resources.displayMetrics.heightPixels
                val fx = (ev.rawX / w).coerceIn(0.01f, 0.99f)
                val fy = (ev.rawY / h).coerceIn(0.01f, 0.99f)
                CustomRules.save(pkg, fx, fy)
                Prefs.clearPendingCalibration()
                LogBus.add("学习完成 $pkg → (${"%.3f".format(fx)}, ${"%.3f".format(fy)})")
                Toast.makeText(this, "已记录该应用的跳过按钮位置", Toast.LENGTH_SHORT).show()
                hideCalibrationOverlay()
            }
            true
        }
        try {
            wm.addView(hint, params)
            overlayView = hint
            LogBus.add("开始学习 $pkg，等待点击跳过按钮")
        } catch (t: Throwable) {
            LogBus.add("校准浮层失败: ${t.message}")
        }
    }

    private fun hideCalibrationOverlay() {
        val v = overlayView ?: return
        overlayView = null
        val wm = getSystemService(WINDOW_SERVICE) as? WindowManager ?: return
        try {
            wm.removeView(v)
        } catch (t: Throwable) {
            // 已被移除
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

        val rootPkg = try {
            root.packageName?.toString()
        } catch (t: Throwable) {
            null
        }
        if (rootPkg != null && rootPkg != pkg) return

        val hit = RuleEngine.findPair(root, r.pairs) ?: return
        if (click(hit)) {
            lastPairClickAt = SystemClock.uptimeMillis()
            Prefs.onSkip(r.pkg)
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
        hideCalibrationOverlay()
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
