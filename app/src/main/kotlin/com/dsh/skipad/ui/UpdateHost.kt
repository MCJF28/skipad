package com.dsh.skipad.ui

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.dsh.skipad.data.Prefs
import com.dsh.skipad.data.UpdateChecker
import kotlinx.coroutines.launch

/**
 * 新版本弹窗。
 *  - 主版本号提升（如 1.x → 2.0）：强制更新，不可关闭
 *  - 次版本 / 修订号提升：可「稍后」，同一版本不再重复打扰
 */
@Composable
fun UpdateHost(ctx: Context) {
    val scope = rememberCoroutineScope()
    val state = UpdateChecker.state

    var target by remember { mutableStateOf<UpdateChecker.State.Available?>(null) }
    var forced by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(false) }
    var skipped by remember { mutableStateOf(Prefs.skippedVersion()) }

    LaunchedEffect(state) {
        val s = state
        if (s is UpdateChecker.State.Available) {
            target = s
            forced = UpdateChecker.isMajorUpdate(s.version, UpdateChecker.currentVersion(ctx))
            if (forced || skipped != s.version) visible = true
        }
    }

    val t = target
    if (!visible || t == null) return

    AlertDialog(
        onDismissRequest = {
            if (!forced) {
                Prefs.setSkippedVersion(t.version)
                skipped = t.version
                visible = false
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = !forced,
            dismissOnClickOutside = !forced
        ),
        title = {
            Text(if (forced) "需要更新后才能使用" else "发现新版本 v${t.version}")
        },
        text = {
            Column {
                if (forced) {
                    Text(
                        text = "这是一次重要版本更新（v${t.version}），需要安装新版才能继续正常使用。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    Text(
                        text = "当前版本 v${UpdateChecker.currentVersion(ctx)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(8.dp))

                when (val s = state) {
                    is UpdateChecker.State.Downloading -> {
                        Text("正在下载… ${s.percent}%", style = MaterialTheme.typography.bodyMedium)
                    }
                    is UpdateChecker.State.Downloaded -> {
                        Text("下载完成", style = MaterialTheme.typography.bodyMedium)
                    }
                    is UpdateChecker.State.Failed -> {
                        Text(
                            s.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    else -> {
                        Text(
                            text = t.notes.ifBlank { "包含问题修复与体验改进。" }.take(400),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            when (val s = state) {
                is UpdateChecker.State.Downloaded -> {
                    Button(onClick = { UpdateChecker.install(ctx, s.file) }) { Text("安装更新") }
                }
                is UpdateChecker.State.Downloading -> {
                    Button(onClick = {}, enabled = false) { Text("下载中 ${s.percent}%") }
                }
                else -> {
                    Button(onClick = { scope.launch { UpdateChecker.download(ctx, t.url) } }) {
                        Text("立即更新")
                    }
                }
            }
        },
        dismissButton = {
            if (!forced) {
                TextButton(onClick = {
                    Prefs.setSkippedVersion(t.version)
                    skipped = t.version
                    visible = false
                }) { Text("稍后") }
            }
        }
    )
}
