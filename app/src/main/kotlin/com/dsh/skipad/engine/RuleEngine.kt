package com.dsh.skipad.engine

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import java.util.ArrayDeque

/**
 * 在无障碍树上找「跳过」目标。
 *
 * 判定顺序：
 *  1) 屏幕出现危险文案（协议/授权/支付/片头片尾等）→ 整体放弃本次
 *  2) 文本 / 正则精确命中
 *  3) 固定坐标兜底，前置条件（满足其一）：
 *       a. 锚点文字（如「广告」）出现在屏幕上部 35%
 *       b. 当前窗口类名是通用容器（FrameLayout / LinearLayout / ViewGroup …），
 *          这是开屏广告浮层的典型特征；正常页面通常是具体 Activity 类名
 */
object RuleEngine {

    private const val MAX_NODES = 600

    /** 空树判定阈值：节点数不超过这个值，视为"页面还没加载出来/广告画在画布上" */
    private const val EMPTY_TREE_MAX = 6

    /** 最近一次扫描的诊断信息，供日志使用 */
    @Volatile
    var lastNodeCount: Int = 0

    @Volatile
    var lastSample: String = ""

    /** 开屏广告浮层的典型窗口类名 */
    private val OVERLAY_CLASS = Regex(
        "^(android\\.widget\\.(FrameLayout|LinearLayout|RelativeLayout|AbsoluteLayout|ViewGroup)" +
            "|android\\.view\\.ViewGroup|android\\.widget\\.PopupWindow)$"
    )

    private val DANGER = listOf(
        "同意", "允许", "授权", "登录", "支付", "购买", "立即领取", "下载安装", "确认支付",
        "青少年模式", "隐私政策", "用户协议"
    )

    private val NOT_SPLASH = listOf("片头", "片尾", "弹幕", "跳过设置", "跳过片头")

    fun find(
        svc: AccessibilityService,
        root: AccessibilityNodeInfo,
        rule: Rule,
        elapsedMs: Long,
        windowClass: String
    ): Hit? {
        val nodes = ArrayList<AccessibilityNodeInfo>()
        collect(root, nodes)
        lastNodeCount = nodes.size
        lastSample = nodes.asSequence()
            .mapNotNull { textOf(it)?.trim() }
            .filter { it.isNotEmpty() }
            .take(5)
            .joinToString("|")
            .take(80)
        if (nodes.isEmpty()) return null

        val dm = svc.resources.displayMetrics
        val screenH = dm.heightPixels
        val screenW = dm.widthPixels

        for (n in nodes) {
            val t = textOf(n) ?: continue
            if (DANGER.any { t.contains(it) }) return null
            if (NOT_SPLASH.any { t.contains(it) }) return null
        }

        // 文本 / 正则
        for (m in rule.matchers) {
            if (m.type == "regex") {
                val re = try {
                    Regex(m.value)
                } catch (t: Throwable) {
                    continue
                }
                for (n in nodes) {
                    val t = (textOf(n) ?: continue).trim()
                    if (re.matches(t) && isSane(n)) return centerHit(n, "regex:$t")
                }
            } else {
                for (n in nodes) {
                    val t = textOf(n) ?: continue
                    if (!t.contains(m.value, ignoreCase = true)) continue
                    if (isSane(n)) return centerHit(n, "text:$t")
                }
            }
        }

        // 固定坐标兜底
        if (rule.hasRegion && elapsedMs >= rule.delayMs + 250L) {
            val anchorOk = nodes.any { n ->
                val t = textOf(n)
                t != null && t.contains(rule.anchor) && topOf(n) < screenH * 0.35f
            }
            val overlayOk = OVERLAY_CLASS.matches(windowClass)
            // 开屏广告画在应用自己的 Activity 里时，无障碍树几乎是空的（只有 1~3 个节点），
            // 而正常页面加载完节点数会明显更多。用「空树 + 时间窗」作为第三种判据。
            val emptyTreeOk = nodes.size <= EMPTY_TREE_MAX && elapsedMs in 1000L..3500L

            if (anchorOk || overlayOk || emptyTreeOk) {
                val via = when {
                    overlayOk -> "region(overlay=$windowClass)"
                    anchorOk -> "region(anchor=${rule.anchor})"
                    else -> "region(emptyTree=${nodes.size})"
                }
                val x = (screenW * rule.regionFx).toInt()
                val y = (screenH * rule.regionFy).toInt()
                return Hit(null, x, y, via)
            }
        }

        return null
    }

    /**
     * 条件点击：当屏幕上存在 when 文案时，找到并返回 tap 目标。
     * 用于「3秒后将展示广告 + 取消」这类暂停浮层。
     */
    fun findPair(root: AccessibilityNodeInfo, pairs: List<PairRule>): Hit? {
        if (pairs.isEmpty()) return null
        val nodes = ArrayList<AccessibilityNodeInfo>()
        collect(root, nodes)
        lastNodeCount = nodes.size

        for (p in pairs) {
            val hasWhen = nodes.any { n -> textOf(n)?.contains(p.whenText) == true }
            if (!hasWhen) continue

            var loose: AccessibilityNodeInfo? = null
            for (n in nodes) {
                val t = textOf(n)?.trim() ?: continue
                if (t == p.tapText && isSane(n)) return centerHit(n, "pair:${p.tapText}")
                if (loose == null && t.contains(p.tapText) && isSane(n)) loose = n
            }
            if (loose != null) return centerHit(loose, "pair~:${p.tapText}")
        }
        return null
    }

    private fun collect(root: AccessibilityNodeInfo, out: MutableList<AccessibilityNodeInfo>) {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.addLast(root)
        var count = 0
        while (queue.isNotEmpty() && count < MAX_NODES) {
            val n = queue.removeFirst()
            count++
            out.add(n)
            for (i in 0 until n.childCount) {
                val c = try {
                    n.getChild(i)
                } catch (t: Throwable) {
                    null
                }
                if (c != null) queue.addLast(c)
            }
        }
    }

    private fun textOf(n: AccessibilityNodeInfo): String? {
        val a = try {
            n.text?.toString()
        } catch (t: Throwable) {
            null
        }
        if (!a.isNullOrBlank()) return a
        val b = try {
            n.contentDescription?.toString()
        } catch (t: Throwable) {
            null
        }
        if (!b.isNullOrBlank()) return b
        return null
    }

    private fun isSane(n: AccessibilityNodeInfo): Boolean {
        val r = Rect()
        n.getBoundsInScreen(r)
        if (r.width() <= 0 || r.height() <= 0) return false
        if (r.width() > 3000 || r.height() > 3000) return false
        return try {
            n.isVisibleToUser
        } catch (t: Throwable) {
            true
        }
    }

    private fun topOf(n: AccessibilityNodeInfo): Int {
        val r = Rect()
        n.getBoundsInScreen(r)
        return r.top
    }

    private fun centerHit(n: AccessibilityNodeInfo, via: String): Hit {
        val r = Rect()
        n.getBoundsInScreen(r)
        return Hit(n, r.centerX(), r.centerY(), via)
    }
}
