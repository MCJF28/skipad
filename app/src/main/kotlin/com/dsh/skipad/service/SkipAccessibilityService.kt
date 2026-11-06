package com.dsh.skipad.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.dsh.skipad.data.LogBus

/**
 * M1: 只做窗口事件观察，验证服务能被系统拉起、事件能收到。
 * M2 在此基础上接入规则引擎与点击。
 */
class SkipAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        LogBus.add("无障碍服务已连接")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val e = event ?: return
        when (e.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val pkg = e.packageName?.toString() ?: return
                LogBus.add("窗口切换: $pkg / ${e.className}")
            }
            else -> Unit
        }
    }

    override fun onInterrupt() {
        LogBus.add("服务被中断")
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    companion object {
        @Volatile
        var instance: SkipAccessibilityService? = null
            private set
    }
}
