package com.dsh.skipad.engine

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import java.util.ArrayDeque

/**
 * 在无障碍树上找「跳过」目标。
 * 顺序：文本/正则命中 → （带锚点校验的）固定坐标兜底。
 * 任何情况下只要屏幕上出现危险文案（协议/授权/支付等）就整体放弃本次点击。
 */
object RuleEngine {

    private const val MAX_NODES = 600

    private val DANGER = listOf(
        "同意", "允许", "授权", "登录", "支付", "购买", "立即领取", "下载安装", "确认支付"
    )

    /** 拦截词：出现即不点，避免把开屏之外的操作搞乱 */
    private val BLOCK_TEXTS = listOf("青少年模式", "隐私政策", "用户协议")

    fun find(svc: AccessibilityService, root: AccessibilityNodeInfo, rule: Rule, elapsedMs: Long): Hit? {
        val nodes = ArrayList<AccessibilityNodeInfo>()
        collect(root, nodes)

        // 1. 危险/拦截文案 → 本次放弃
        if (nodes.any { n -> textOf(n)?.let { t -> DANGER.any { t.contains(it) } } == true }) {
            return null
        }
        if (nodes.any { n -> textOf(n)?.let { t -> BLOCK_TEXTS.any { t.contains(it) } } == true }) {
            return null
        }

        // 2. 文本 / 正则匹配
        for (m in rule.matchers) {
            when (m.type) {
                "regex" -> {
                    val re = try {
                        Regex(m.value)
                    } catch (t: Throwable) {
                        continue
                    }
                    for (n in nodes) {
                        val t = (textOf(n) ?: continue).trim()
                        if (re.matches(t)) return centerHit(n, "regex:$t")
                    }
                }
                else -> {
                    for (n in nodes) {
                        val t = textOf(n) ?: continue
                        if (t.contains(m.value, ignoreCase = true)) {
                            // 只点可见且尺寸合理的节点
                            if (visibleSize(n) >= 24) return centerHit(n, "text:$t")
                        }
                    }
                }
            }
        }

        // 3. 固定坐标兜底（需锚点在屏 + 已过初始延迟）
        if (rule.hasRegion && elapsedMs >= rule.delayMs + 400L) {
            val anchored = if (rule.anchor.isBlank()) {
                true
            } else {
                nodes.any { n -> textOf(n)?.contains(rule.anchor) == true }
            }
            if (anchored) {
                val dm = svc.resources.displayMetrics
                val x = (dm.widthPixels * rule.regionFx).toInt()
                val y = (dm.heightPixels * rule.regionFy).toInt()
                return Hit(null, x, y, "region")
            }
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
        val a = n.text?.toString()
        if (!a.isNullOrBlank()) return a
        val b = n.contentDescription?.toString()
        if (!b.isNullOrBlank()) return b
        return null
    }

    private fun visibleSize(n: AccessibilityNodeInfo): Int {
        val r = Rect()
        n.getBoundsInScreen(r)
        if (r.width() <= 0 || r.height() <= 0) return 0
        if (!n.isVisibleToUser) return 0
        return minOf(r.width(), r.height())
    }

    private fun centerHit(n: AccessibilityNodeInfo, via: String): Hit {
        val r = Rect()
        n.getBoundsInScreen(r)
        return Hit(n, r.centerX(), r.centerY(), via)
    }
}
