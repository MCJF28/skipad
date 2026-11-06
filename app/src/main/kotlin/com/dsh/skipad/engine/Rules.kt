package com.dsh.skipad.engine

import android.content.Context
import com.dsh.skipad.data.CustomRules
import com.dsh.skipad.data.LogBus
import com.dsh.skipad.data.Prefs
import org.json.JSONObject

/** 规则库：从 assets/rules.json 载入，按包名索引。 */
object Rules {

    @Volatile
    private var map: Map<String, Rule> = emptyMap()

    @Volatile
    private var loaded = false

    fun load(ctx: Context) {
        if (loaded) return
        CustomRules.init(ctx)
        var merged = emptyMap<String, Rule>()
        try {
            val text = ctx.assets.open("rules.json").bufferedReader().use { it.readText() }
            merged = parse(text)
        } catch (t: Throwable) {
            LogBus.add("内置规则载入失败: ${t.message}")
        }
        // 在线规则覆盖内置规则，字段不冲突时以在线为准
        try {
            val f = java.io.File(ctx.filesDir, "rules_remote.json")
            if (f.exists()) {
                val remote = parse(f.readText())
                if (remote.isNotEmpty()) {
                    merged = merged + remote
                    LogBus.add("在线规则载入 ${remote.size} 条")
                }
            }
        } catch (t: Throwable) {
            LogBus.add("在线规则载入失败: ${t.message}")
        }
        map = merged
        loaded = true
        LogBus.add("规则库载入 ${map.size} 条")
    }

    /** 规则库被在线更新后重新载入 */
    fun reload(ctx: Context) {
        loaded = false
        load(ctx)
    }

    fun forPackage(pkg: String): Rule? {
        if (!Prefs.isRuleEnabled(pkg, true)) return null

        // 用户自己标记的规则优先
        val c = CustomRules.get(pkg)
        if (c != null) {
            return Rule(
                pkg = pkg,
                anchor = "",
                regionFx = c.first,
                regionFy = c.second,
                matchers = listOf(
                    Matcher("text", "跳过"),
                    Matcher("regex", "^\\d*\\s*跳过\\s*\\d*$")
                )
            )
        }

        val r = map[pkg] ?: return null
        return if (r.enabled) r else null
    }

    fun all(): List<Rule> {
        val custom = CustomRules.all().map { (pkg, xy) ->
            Rule(
                pkg = pkg,
                anchor = "",
                regionFx = xy.first,
                regionFy = xy.second,
                matchers = listOf(
                    Matcher("text", "跳过"),
                    Matcher("regex", "^\\d*\\s*跳过\\s*\\d*$")
                )
            )
        }
        val merged = LinkedHashMap<String, Rule>()
        for (r in map.values) merged[r.pkg] = r
        for (r in custom) merged[r.pkg] = r
        return merged.values.sortedBy { it.pkg }
    }

    fun isCustom(pkg: String): Boolean = CustomRules.get(pkg) != null

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

            val pairs = ArrayList<PairRule>()
            val pa = o.optJSONArray("pairs")
            if (pa != null) {
                for (j in 0 until pa.length()) {
                    val e = pa.optJSONObject(j) ?: continue
                    val w = e.optString("when", "")
                    val t = e.optString("tap", "")
                    if (w.isNotBlank() && t.isNotBlank()) pairs.add(PairRule(w, t))
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
                cooldownMs = o.optLong("cooldownMs", 0L),
                regionFx = o.optDouble("regionFx", 0.0).toFloat(),
                regionFy = o.optDouble("regionFy", 0.0).toFloat(),
                matchers = matchers,
                pairs = pairs
            )
        }
        return out
    }
}
