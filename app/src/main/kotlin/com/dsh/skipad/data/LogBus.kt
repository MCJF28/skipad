package com.dsh.skipad.data

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 进程内事件日志：UI 直接观察，同时落一份到 Download/SkipAd/events.log。 */
object LogBus {

    val entries = mutableStateListOf<String>()
    private val fmt = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

    private var appCtx: Context? = null

    fun init(ctx: Context) {
        if (appCtx == null) {
            appCtx = ctx.applicationContext
            FileLogger.init(ctx.applicationContext)
        }
    }

    @Synchronized
    fun clear() {
        entries.clear()
    }

    @Synchronized
    fun add(msg: String) {
        val line = "${fmt.format(Date())}  $msg"
        entries.add(0, line)
        while (entries.size > 200) {
            entries.removeAt(entries.size - 1)
        }
        appCtx?.let { FileLogger.append(it, line) }
    }
}
