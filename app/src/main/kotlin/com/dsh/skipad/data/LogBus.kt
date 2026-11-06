package com.dsh.skipad.data

import androidx.compose.runtime.mutableStateListOf
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 进程内事件日志，UI 直接观察。后续可落盘持久化。 */
object LogBus {
    val entries = mutableStateListOf<String>()
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)

    @Synchronized
    fun add(msg: String) {
        val line = "${fmt.format(Date())}  $msg"
        entries.add(0, line)
        while (entries.size > 200) {
            entries.removeAt(entries.size - 1)
        }
    }
}
