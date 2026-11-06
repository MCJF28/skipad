package com.dsh.skipad.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * 更新检查与下载。
 *
 * 网络用途仅限：
 *   1. GET https://api.github.com/.../releases/latest 读取版本号与下载地址
 *   2. 下载对应的 APK
 * 没有任何上传逻辑，不发送任何本地数据。
 */
object UpdateChecker {

    private const val API = "https://api.github.com/repos/MCJF28/skipad/releases/latest"

    sealed interface State {
        data object Idle : State
        data object Checking : State
        data object UpToDate : State
        data class Available(val version: String, val url: String, val notes: String) : State
        data class Downloading(val percent: Int) : State
        data class Downloaded(val file: File) : State
        data class Failed(val message: String) : State
    }

    var state: State by mutableStateOf(State.Idle)
        private set

    fun currentVersion(ctx: Context): String = runCatching {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName
    }.getOrNull() ?: "?"

    /** 静默检查（自动检查用） */
    suspend fun silentCheck(ctx: Context) {
        if (state is State.Available || state is State.Downloaded) return
        check(ctx, quiet = true)
    }

    suspend fun check(ctx: Context, quiet: Boolean = false) = withContext(Dispatchers.IO) {
        if (state is State.Checking) return@withContext
        if (!quiet) state = State.Checking
        try {
            val conn = (URL(API).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "skipad-updater")
            }
            val code = conn.responseCode
            if (code != 200) {
                state = State.Failed("无法获取版本信息（HTTP $code）")
                return@withContext
            }
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()

            val o = JSONObject(text)
            val tag = o.optString("tag_name").removePrefix("v")
            val notes = o.optString("body").take(600)
            val assets = o.optJSONArray("assets")
            var apkUrl = ""
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val a = assets.optJSONObject(i) ?: continue
                    if (a.optString("name").endsWith(".apk")) {
                        apkUrl = a.optString("browser_download_url")
                        break
                    }
                }
            }
            if (tag.isBlank() || apkUrl.isBlank()) {
                state = State.Failed("版本信息不完整")
                return@withContext
            }
            state = if (isNewer(tag, currentVersion(ctx))) {
                State.Available(tag, apkUrl, notes)
            } else {
                State.UpToDate
            }
            Prefs.markUpdateChecked()
        } catch (t: Throwable) {
            state = State.Failed(t.message ?: "网络错误")
        }
    }

    suspend fun download(ctx: Context, url: String) = withContext(Dispatchers.IO) {
        try {
            state = State.Downloading(0)
            val dir = File(ctx.cacheDir, "update").apply { mkdirs() }
            val out = File(dir, "skipad-new.apk")
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 30000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "skipad-updater")
            }
            val total = conn.contentLengthLong
            conn.inputStream.use { input ->
                out.outputStream().use { os ->
                    val buf = ByteArray(64 * 1024)
                    var read: Int
                    var done = 0L
                    while (true) {
                        read = input.read(buf)
                        if (read <= 0) break
                        os.write(buf, 0, read)
                        done += read
                        if (total > 0) {
                            val p = (done * 100 / total).toInt().coerceIn(0, 100)
                            state = State.Downloading(p)
                        }
                    }
                }
            }
            conn.disconnect()
            state = State.Downloaded(out)
        } catch (t: Throwable) {
            state = State.Failed("下载失败：${t.message}")
        }
    }

    /** 调起系统安装器；若没有「安装未知应用」权限会先跳去授权 */
    fun install(ctx: Context, file: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !ctx.packageManager.canRequestPackageInstalls()
        ) {
            runCatching {
                val i = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + ctx.packageName)
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ctx.startActivity(i)
            }
            return
        }
        runCatching {
            val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", file)
            val i = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(i)
        }
    }

    /** 主版本号提升（如 1.x → 2.0）视为必须更新 */
    fun isMajorUpdate(remote: String, local: String): Boolean {
        val r = remote.split('.').firstOrNull()?.toIntOrNull() ?: 0
        val l = local.split('.').firstOrNull()?.toIntOrNull() ?: 0
        return r > l
    }

    /** 语义化版本比较：1.10.0 > 1.9.3 */
    private fun isNewer(remote: String, local: String): Boolean {
        fun parts(v: String) = v.split('.', '-', '+')
            .map { it.toIntOrNull() ?: 0 }
        val a = parts(remote)
        val b = parts(local)
        val n = maxOf(a.size, b.size)
        for (i in 0 until n) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}
