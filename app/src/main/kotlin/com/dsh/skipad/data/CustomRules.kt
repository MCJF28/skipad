package com.dsh.skipad.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

/**
 * 用户自己标记的规则：包名 -> 跳过按钮的相对坐标。
 * 用户在目标应用里点一下「跳过」按钮，我们记录位置比例。
 */
object CustomRules {

    private const val KEY = "custom_rules"

    @Volatile
    private var sp: SharedPreferences? = null

    @Volatile
    private var cache: Map<String, Pair<Float, Float>> = emptyMap()

    fun init(ctx: Context) {
        if (sp != null) return
        sp = ctx.applicationContext.getSharedPreferences("skipad_custom", Context.MODE_PRIVATE)
        reload()
    }

    private fun reload() {
        val raw = sp?.getString(KEY, "") ?: ""
        if (raw.isBlank()) {
            cache = emptyMap()
            return
        }
        val out = HashMap<String, Pair<Float, Float>>()
        try {
            val o = JSONObject(raw)
            for (k in o.keys()) {
                val v = o.optJSONObject(k) ?: continue
                out[k] = v.optDouble("x", 0.0).toFloat() to v.optDouble("y", 0.0).toFloat()
            }
        } catch (t: Throwable) {
            // 解析失败就当空
        }
        cache = out
    }

    fun all(): Map<String, Pair<Float, Float>> = cache

    fun get(pkg: String): Pair<Float, Float>? = cache[pkg]

    fun save(pkg: String, fx: Float, fy: Float) {
        val o = JSONObject()
        for ((k, v) in cache) {
            o.put(k, JSONObject().put("x", v.first.toDouble()).put("y", v.second.toDouble()))
        }
        o.put(pkg, JSONObject().put("x", fx.toDouble()).put("y", fy.toDouble()))
        sp?.edit()?.putString(KEY, o.toString())?.apply()
        reload()
    }

    fun remove(pkg: String) {
        val o = JSONObject()
        for ((k, v) in cache) {
            if (k == pkg) continue
            o.put(k, JSONObject().put("x", v.first.toDouble()).put("y", v.second.toDouble()))
        }
        sp?.edit()?.putString(KEY, o.toString())?.apply()
        reload()
    }
}
