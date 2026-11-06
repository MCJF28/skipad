package com.dsh.skipad.data

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 轻量持久化：规则开关 + 跳过统计 */
object Prefs {

    private const val NAME = "skipad"
    private const val K_TOTAL = "total_skips"
    private const val K_DAY = "day_key"
    private const val K_DAY_COUNT = "day_count"

    @Volatile
    private var sp: SharedPreferences? = null

    fun init(ctx: Context) {
        if (sp == null) {
            sp = ctx.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        }
    }

    fun isRuleEnabled(pkg: String, def: Boolean): Boolean =
        sp?.getBoolean("rule_$pkg", def) ?: def

    fun setRuleEnabled(pkg: String, value: Boolean) {
        sp?.edit()?.putBoolean("rule_$pkg", value)?.apply()
    }

    fun totalSkips(): Int = sp?.getInt(K_TOTAL, 0) ?: 0

    fun skipsToday(): Int {
        val today = dayKey()
        return if (sp?.getString(K_DAY, "") == today) sp?.getInt(K_DAY_COUNT, 0) ?: 0 else 0
    }

    @Synchronized
    fun onSkip(pkg: String) {
        val s = sp ?: return
        val today = dayKey()
        val sameDay = s.getString(K_DAY, "") == today
        val dayCount = if (sameDay) s.getInt(K_DAY_COUNT, 0) else 0
        s.edit()
            .putInt(K_TOTAL, s.getInt(K_TOTAL, 0) + 1)
            .putString(K_DAY, today)
            .putInt(K_DAY_COUNT, dayCount + 1)
            .putInt("stat_$pkg", s.getInt("stat_$pkg", 0) + 1)
            .apply()
    }

    fun skipsFor(pkg: String): Int = sp?.getInt("stat_$pkg", 0) ?: 0

    /** 按跳过次数排序的应用统计 */
    fun topApps(): List<Pair<String, Int>> {
        val s = sp ?: return emptyList()
        return s.all.entries
            .filter { it.key.startsWith("stat_") && it.value is Int && (it.value as Int) > 0 }
            .map { it.key.removePrefix("stat_") to (it.value as Int) }
            .sortedByDescending { it.second }
    }

    // ---- 校准（学习模式）----
    fun pendingCalibration(): String = sp?.getString("pending_calibration", "") ?: ""

    fun setPendingCalibration(pkg: String) {
        sp?.edit()?.putString("pending_calibration", pkg)?.apply()
    }

    fun clearPendingCalibration() {
        sp?.edit()?.remove("pending_calibration")?.apply()
    }

    fun skippedVersion(): String = sp?.getString("skipped_version", "") ?: ""

    fun setSkippedVersion(v: String) {
        sp?.edit()?.putString("skipped_version", v)?.apply()
    }

    fun markUpdateChecked() {
        sp?.edit()?.putLong("update_checked_at", System.currentTimeMillis())?.apply()
    }

    fun autoCheckEnabled(): Boolean = sp?.getBoolean("auto_check", true) ?: true

    fun setAutoCheck(v: Boolean) {
        sp?.edit()?.putBoolean("auto_check", v)?.apply()
    }

    /** 每次启动都检查（仅受「自动检查」开关控制） */
    fun shouldAutoCheck(): Boolean = autoCheckEnabled()

    private fun dayKey(): String =
        SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
}
