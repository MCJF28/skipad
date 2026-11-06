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
    fun onSkip() {
        val s = sp ?: return
        val today = dayKey()
        val sameDay = s.getString(K_DAY, "") == today
        val dayCount = if (sameDay) s.getInt(K_DAY_COUNT, 0) else 0
        s.edit()
            .putInt(K_TOTAL, s.getInt(K_TOTAL, 0) + 1)
            .putString(K_DAY, today)
            .putInt(K_DAY_COUNT, dayCount + 1)
            .apply()
    }

    private fun dayKey(): String =
        SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
}
