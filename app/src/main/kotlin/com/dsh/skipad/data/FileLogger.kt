package com.dsh.skipad.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import java.util.concurrent.Executors

/**
 * 把事件日志同步写一份到手机「Download/SkipAd/events.log」。
 * 这样即使无障碍读屏不可用，也能从外部查看到 App 的判断过程。
 */
object FileLogger {

    private val io = Executors.newSingleThreadExecutor()
    private var uri: Uri? = null
    private var inited = false

    fun init(ctx: Context) {
        if (inited) return
        inited = true
        if (Build.VERSION.SDK_INT < 29) return
        io.execute {
            try {
                val res = ctx.contentResolver
                uri = findExisting(ctx) ?: create(ctx)
            } catch (t: Throwable) {
                uri = null
            }
        }
    }

    fun append(ctx: Context, line: String) {
        if (Build.VERSION.SDK_INT < 29) return
        io.execute {
            try {
                val u = uri ?: findExisting(ctx) ?: create(ctx)
                uri = u
                ctx.contentResolver.openOutputStream(u, "wa")?.use { out ->
                    out.write((line + "\n").toByteArray())
                }
            } catch (t: Throwable) {
                uri = null
            }
        }
    }

    private fun create(ctx: Context): Uri? {
        val cv = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "events.log")
            put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/SkipAd")
        }
        return ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv)
    }

    private fun findExisting(ctx: Context): Uri? {
        val proj = arrayOf(MediaStore.MediaColumns._ID)
        val sel = "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH}=?"
        val args = arrayOf("events.log", "Download/SkipAd/")
        ctx.contentResolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI, proj, sel, args, null
        )?.use { c ->
            if (c.moveToFirst()) {
                val id = c.getLong(0)
                return Uri.withAppendedPath(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI, id.toString()
                )
            }
        }
        return null
    }
}
