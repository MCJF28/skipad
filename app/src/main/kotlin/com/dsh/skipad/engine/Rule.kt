package com.dsh.skipad.engine

/** 单条匹配器 */
data class Matcher(
    val type: String,          // "text" | "regex"
    val value: String
)

/**
 * 条件点击：屏幕上出现 whenText 时，点击 tapText（精确匹配）。
 * 用于「暂停时弹出 3秒后将展示广告 + 取消」这类随时可能出现的浮层。
 */
data class PairRule(
    val whenText: String,
    val tapText: String
)

/** 一次命中结果 */
data class Hit(
    val node: android.view.accessibility.AccessibilityNodeInfo?,
    val x: Int,
    val y: Int,
    val via: String
)

/**
 * 一个包的开屏跳过规则。
 *
 * regionFx/regionFy：固定坐标兜底（相对屏幕比例，0 表示不启用）。
 * anchor：坐标兜底的前置锚点文字，且该文字必须出现在屏幕上部（<35% 高度）。
 *   这是防止「切出去再切回来」时在正常界面误点的关键。
 * activity：可选，窗口类名正则；命中时视为高置信开屏页。
 * cooldownMs：同一包名两次点击的最小间隔，防止反复误触。
 */
data class Rule(
    val pkg: String,
    val enabled: Boolean = true,
    val delayMs: Long = 60L,
    val windowMs: Long = 5000L,
    val maxClicks: Int = 1,
    val anchor: String = "",
    val activity: String = "",
    val cooldownMs: Long = 0L,
    val regionFx: Float = 0f,
    val regionFy: Float = 0f,
    val matchers: List<Matcher> = emptyList(),
    val pairs: List<PairRule> = emptyList()
) {
    val hasRegion: Boolean get() = regionFx > 0f && regionFy > 0f
}
