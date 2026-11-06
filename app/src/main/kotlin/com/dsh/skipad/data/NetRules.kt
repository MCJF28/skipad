package com.dsh.skipad.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * 广告域名规则（来自 AWAvenue-Ads-Rule 的 hosts 列表）。
 * 只在用户主动开启「网络层拦截」时才会被查询。
 */
object NetRules {

    private const val URL_PRIMARY =
        "https://api.github.com/repos/TG-Twilight/AWAvenue-Ads-Rule/contents/Filters/AWAvenue-Ads-Rule-hosts.txt"
    private const val URL_FALLBACK =
        "https://raw.githubusercontent.com/TG-Twilight/AWAvenue-Ads-Rule/main/Filters/AWAvenue-Ads-Rule-hosts.txt"

    @Volatile
    private var set: HashSet<String> = HashSet()

    var count: Int by mutableStateOf(0)
        private set

    var status: String by mutableStateOf("")
        private set

    fun load(ctx: Context) {
        val f = File(ctx.filesDir, "net_rules.txt")
        if (!f.exists()) return
        try {
            val s = HashSet<String>()
            f.forEachLine { line ->
                val d = parseLine(line)
                if (d != null) s.add(d)
            }
            set = s
            count = s.size
            status = "已加载 ${s.size} 条域名"
        } catch (t: Throwable) {
            status = "规则加载失败：${t.message}"
        }
    }

    fun isBlocked(host: String): Boolean {
        if (set.isEmpty()) return false
        var h = host.lowercase().trimEnd('.')
        if (h.isEmpty()) return false
        var guard = 0
        while (h.isNotEmpty() && guard < 10) {
            if (set.contains(h)) return true
            val idx = h.indexOf('.')
            if (idx < 0) return false
            h = h.substring(idx + 1)
            guard++
        }
        return false
    }

    suspend fun refresh(ctx: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            val text = fetch(URL_PRIMARY) ?: fetch(URL_FALLBACK)
            if (text == null) {
                status = "获取失败：网络不可用"
                return@withContext false
            }
            val s = HashSet<String>()
            text.lineSequence().forEach { line ->
                val d = parseLine(line)
                if (d != null) s.add(d)
            }
            if (s.isEmpty()) {
                status = "规则为空，已忽略"
                return@withContext false
            }
            File(ctx.filesDir, "net_rules.txt").writeText(text)
            set = s
            count = s.size
            status = "已更新 ${s.size} 条域名"
            true
        } catch (t: Throwable) {
            status = "更新失败：${t.message}"
            false
        }
    }

    private fun parseLine(raw: String): String? {
        val line = raw.trim()
        if (line.isEmpty()) return null
        if (line.startsWith("#") || line.startsWith("!") || line.startsWith("//")) return null
        val parts = line.split(' ', '\t').filter { it.isNotBlank() }
        if (parts.size < 2) return null
        val domain = parts.last().lowercase()
        if (domain == "localhost" || domain == "0.0.0.0" || !domain.contains('.')) return null
        if (domain.length > 100) return null
        return domain
    }

    private fun fetch(url: String): String? {
        return try {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 15000
                instanceFollowRedirects = true
                setRequestProperty("Accept", "application/vnd.github.raw")
                setRequestProperty("User-Agent", "skipad-netrules")
            }
            if (conn.responseCode != 200) return null
            conn.inputStream.bufferedReader().use { it.readText() }
        } catch (t: Throwable) {
            null
        }
    }
}
