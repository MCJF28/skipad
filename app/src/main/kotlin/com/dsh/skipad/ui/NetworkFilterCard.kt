package com.dsh.skipad.ui

import android.app.Activity
import android.content.Context
import android.net.VpnService
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dsh.skipad.data.NetRules
import com.dsh.skipad.vpn.AdFilterVpnService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.InetAddress
import kotlinx.coroutines.launch

@Composable
fun NetworkFilterCard(ctx: Context) {
    val scope = rememberCoroutineScope()

    var enabled by remember { mutableStateOf(AdFilterVpnService.running) }
    var blocked by remember { mutableIntStateOf(AdFilterVpnService.blocked) }
    var rules by remember { mutableIntStateOf(NetRules.count) }
    var showWarn by remember { mutableStateOf(false) }
    var showConflict by remember { mutableStateOf(false) }
    var pendingEnable by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf("") }
    var recent by remember { mutableStateOf(listOf<String>()) }

    LaunchedEffect(Unit) {
        while (true) {
            enabled = AdFilterVpnService.running
            blocked = AdFilterVpnService.blocked
            rules = NetRules.count
            recent = AdFilterVpnService.recentBlocked().take(5)
            delay(1000)
        }
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val ok = result.resultCode == Activity.RESULT_OK
        pendingEnable = false
        if (ok) {
            AdFilterVpnService.start(ctx)
        } else {
            Toast.makeText(ctx, "未授权 VPN，网络层拦截未开启", Toast.LENGTH_SHORT).show()
        }
    }

    fun enableNow() {
        if (NetRules.count == 0) {
            scope.launch { NetRules.refresh(ctx) }
        }
        val prep = try {
            VpnService.prepare(ctx)
        } catch (t: Throwable) {
            null
        }
        if (prep != null) {
            pendingEnable = true
            launcher.launch(prep)
        } else {
            AdFilterVpnService.start(ctx)
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "网络层拦截（实验）",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (enabled) "已开启 · 已拦截 $blocked 个请求" else "已关闭",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (enabled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = { want ->
                        if (want) {
                            if (AdFilterVpnService.otherVpnActive(ctx)) showConflict = true
                            else showWarn = true
                        } else {
                            AdFilterVpnService.stop(ctx)
                        }
                    }
                )
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = "通过本地 VPN 过滤广告域名。会占用系统 VPN 槽位，与你正在使用的 VPN / 代理冲突；" +
                    "域名黑名单可能误伤，导致个别应用登录或加载异常。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = NetRules.status.ifBlank { "域名规则：$rules 条" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    enabled = !busy,
                    onClick = {
                        busy = true
                        scope.launch {
                            NetRules.refresh(ctx)
                            busy = false
                        }
                    }
                ) { Text(if (rules > 0) "更新规则" else "下载规则") }
            }

            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    enabled = enabled && !testing,
                    onClick = {
                        testing = true
                        scope.launch {
                            testResult = runBlockTest()
                            testing = false
                        }
                    }
                ) { Text(if (testing) "测试中…" else "测试拦截效果") }
                if (!enabled) {
                    Text(
                        text = "需先开启本功能",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (testResult.isNotBlank()) {
                Text(
                    text = testResult,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (recent.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "最近拦截",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                recent.forEach { d ->
                    Text(
                        text = "· $d",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showWarn) {
        AlertDialog(
            onDismissRequest = { showWarn = false },
            title = { Text("开启前请确认") },
            text = {
                Column {
                    Text("1. 会占用系统 VPN，开启期间你原来的 VPN / 代理将无法使用。")
                    Spacer(Modifier.height(6.dp))
                    Text("2. 域名黑名单可能误伤，若出现应用登录失败、视频加载异常，请立即关闭本功能。")
                    Spacer(Modifier.height(6.dp))
                    Text("3. 它拦不住开屏广告（素材来自应用自身域名），主要针对第三方广告 SDK。")
                    Spacer(Modifier.height(6.dp))
                    Text("4. 建议先点「下载规则」，否则没有可拦截的域名。")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showWarn = false
                    enableNow()
                }) { Text("继续开启") }
            },
            dismissButton = {
                TextButton(onClick = { showWarn = false }) { Text("取消") }
            }
        )
    }

    if (showConflict) {
        AlertDialog(
            onDismissRequest = { showConflict = false },
            title = { Text("检测到其他 VPN") },
            text = {
                Text("系统同一时间只允许一个 VPN。请先关闭正在使用的 VPN / 代理，再回来开启网络层拦截。")
            },
            confirmButton = {
                TextButton(onClick = {
                    showConflict = false
                    openVpnSettings(ctx)
                }) { Text("去设置") }
            },
            dismissButton = {
                TextButton(onClick = { showConflict = false }) { Text("知道了") }
            }
        )
    }
}

/** 真机验证：规则是否命中 + 实际解析是否被拦 */
private suspend fun runBlockTest(): String = withContext(Dispatchers.IO) {
    val domains = listOf("doubleclick.net", "googleadservices.com")
    buildString {
        for (d in domains) {
            val inRule = NetRules.isBlocked(d)
            val resolved = try {
                InetAddress.getByName(d)
                true
            } catch (t: Throwable) {
                false
            }
            append(d)
            append("：")
            append(if (inRule) "规则命中" else "规则未命中")
            append(" · ")
            append(if (resolved) "解析成功（未被拦截）" else "解析失败（已拦截）")
            append("\n")
        }
    }.trim()
}

private fun openVpnSettings(ctx: Context) {
    try {
        val i = android.content.Intent(android.provider.Settings.ACTION_VPN_SETTINGS)
        i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
    } catch (t: Throwable) {
        // 忽略
    }
}
