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
 *  3) 固定坐标兜底，但必须同时满足：锚点文字在屏、锚点位于屏幕上部、已过延迟
 */
object RuleEngine {

    private const val MAX_NODES = 600

    /** 出现即放弃：这些是真实交互，不该被开屏规则碰到 */
    private val DANGER = listOf(
        "同意", "允许", "授权", "登录", "支付", "购买", "立即领取", "下载安装", "确认支付",
        "青少年模式", "隐私政策", "用户协议"
    )

    /** 形如「跳过」但其实是播放器功能的文案 */
    private val NOT_SPLASH = listOf("片头", "片尾", "弹幕", "跳过设置", "跳过片头")

    fun find(
        svc: AccessibilityService,
        root: AccessibilityNodeInfo,
        rule: Rule,
        elapsedMs: Long
    ): Hit? {
        val nodes = ArrayList<AccessibilityNodeInfo>()
        collect(root, nodes)
        if (nodes.isEmpty()) return null

        val screenH = svc.resources.displayMetrics.heightPixels
        val screenW = svc.resources.displayMetrics.widthPixels

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

        // 固定坐标兜底：必须看到锚点在屏幕上部
        if (rule.hasRegion && elapsedMs >= rule.delayMs + 250L) {
            val anchored = nodes.any { n ->
                val t = textOf(n)
                t != null && t.contains(rule.anchor) && topOf(n) < screenH * 0.35f
            }
            if (anchored) {
                val x = (screenW * rule.regionFx).toInt()
                val y = (screenH * rule.regionFy).toInt()
                return Hit(null, x, y, "region(anchor=${rule.anchor})")
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

    /** 可见、尺寸合理、可交互，避免点到全屏大图或不可见节点 */
    private fun isSane(n: AccessibilityNodeInfo): Boolean {
        val r = Rect()
        n.getBoundsInScreen(r)
        if (r.width() <= 0 || r.height() <= 0) return false
        if (r.width() > 2000 || r.height() > 2000) return false
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
