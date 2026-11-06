package com.dsh.skipad.engine

/** 单条匹配器 */
data class Matcher(
    val type: String,          // "text" | "regex"
    val value: String
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
 * regionFx/regionFy 为固定漏斗坐标（相对屏幕宽高的比例，0 表示不启用）。
 * anchor 为兜底点击的前置锚点文字（例如 bilibili 开屏右上角的「广告」角标），
 * 只有在屏幕上看到它时才允许按坐标点，避免在主界面上误点。
 */
data class Rule(
    val pkg: String,
    val enabled: Boolean = true,
    val delayMs: Long = 200L,
    val windowMs: Long = 6000L,
    val maxClicks: Int = 1,
    val anchor: String = "",
    val regionFx: Float = 0f,
    val regionFy: Float = 0f,
    val matchers: List<Matcher> = emptyList()
) {
    val hasRegion: Boolean get() = regionFx > 0f && regionFy > 0f
}
