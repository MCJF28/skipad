package com.dsh.skipad.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.dsh.skipad.engine.Rules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * 在线规则库同步：从仓库拉最新的 rules.json，缓存到应用私有目录。
 * 只在用户点击或随更新检查时触发，不上传任何本地数据。
 */
object RulesSync {

    private const val URL_PRIMARY =
        "https://api.github.com/repos/MCJF28/skipad/contents/app/src/main/assets/rules.json"
    private const val URL_FALLBACK =
        "https://raw.githubusercontent.com/MCJF28/skipad/main/app/src/main/assets/rules.json"

    var status: String by mutableStateOf("")
        private set

    var lastCount: Int by mutableStateOf(0)
        private set

    fun hasRemote(ctx: Context): Boolean = File(ctx.filesDir, "rules_remote.json").exists()

    suspend fun refresh(ctx: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            val text = fetch(URL_PRIMARY) ?: fetch(URL_FALLBACK)
            if (text == null) {
                status = "规则库更新失败：网络不可用"
                return@withContext false
            }
            val o = JSONObject(text)
            val arr = o.optJSONArray("rules") ?: run {
                status = "规则库格式不正确"
                return@withContext false
            }
            File(ctx.filesDir, "rules_remote.json").writeText(text)
            Rules.reload(ctx)
            lastCount = arr.length()
            status = "规则库已更新：${arr.length()} 条"
            true
        } catch (t: Throwable) {
            status = "规则库更新失败：${t.message}"
            false
        }
    }

    private fun fetch(url: String): String? {
        return try {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 12000
                instanceFollowRedirects = true
                setRequestProperty("Accept", "application/vnd.github.raw")
                setRequestProperty("User-Agent", "skipad-rules")
            }
            if (conn.responseCode != 200) return null
            conn.inputStream.bufferedReader().use { it.readText() }
        } catch (t: Throwable) {
            null
        }
    }
}
