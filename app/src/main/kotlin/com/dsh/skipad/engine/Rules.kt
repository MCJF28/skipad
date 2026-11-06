package com.dsh.skipad.engine

import android.content.Context
import com.dsh.skipad.data.LogBus
import org.json.JSONObject

/** 规则库：从 assets/rules.json 载入，按包名索引。 */
object Rules {

    @Volatile
    private var map: Map<String, Rule> = emptyMap()

    @Volatile
    private var loaded = false

    fun load(ctx: Context) {
        if (loaded) return
        try {
            val text = ctx.assets.open("rules.json").bufferedReader().use { it.readText() }
            map = parse(text)
            loaded = true
            LogBus.add("规则库载入 ${map.size} 条")
        } catch (t: Throwable) {
            LogBus.add("规则库载入失败: ${t.message}")
        }
    }

    fun forPackage(pkg: String): Rule? {
        val r = map[pkg] ?: return null
        return if (r.enabled) r else null
    }

    fun size(): Int = map.size

    private fun parse(text: String): Map<String, Rule> {
        val out = HashMap<String, Rule>()
        val root = JSONObject(text)
        val arr = root.optJSONArray("rules") ?: return out
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val pkg = o.optString("pkg", "")
            if (pkg.isBlank()) continue

            val matchers = ArrayList<Matcher>()
            val ma = o.optJSONArray("matchers")
            if (ma != null) {
                for (j in 0 until ma.length()) {
                    val m = ma.optJSONObject(j) ?: continue
                    val v = m.optString("value", "")
                    if (v.isBlank()) continue
                    matchers.add(Matcher(type = m.optString("type", "text"), value = v))
                }
            }

            out[pkg] = Rule(
                pkg = pkg,
                enabled = o.optBoolean("enabled", true),
                delayMs = o.optLong("delayMs", 60L),
                windowMs = o.optLong("windowMs", 5000L),
                maxClicks = o.optInt("maxClicks", 1),
                anchor = o.optString("anchor", ""),
                activity = o.optString("activity", ""),
                cooldownMs = o.optLong("cooldownMs", 3000L),
                regionFx = o.optDouble("regionFx", 0.0).toFloat(),
                regionFy = o.optDouble("regionFy", 0.0).toFloat(),
                matchers = matchers
            )
        }
        return out
    }
}
