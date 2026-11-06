package com.dsh.skipad.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dsh.skipad.data.Prefs
import com.dsh.skipad.isServiceEnabled
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(ctx: Context) {
    var enabled by remember { mutableStateOf(isServiceEnabled(ctx)) }
    var today by remember { mutableIntStateOf(Prefs.skipsToday()) }
    var total by remember { mutableIntStateOf(Prefs.totalSkips()) }
    var showHelp by remember { mutableStateOf(false) }
    var tipsOpen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            enabled = isServiceEnabled(ctx)
            today = Prefs.skipsToday()
            total = Prefs.totalSkips()
            delay(1000)
        }
    }

    val cardColor by animateColorAsState(
        targetValue = if (enabled) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.errorContainer
        },
        animationSpec = tween(500),
        label = "statusColor"
    )
    val onCardColor by animateColorAsState(
        targetValue = if (enabled) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onErrorContainer
        },
        animationSpec = tween(500),
        label = "statusText"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 16.dp)
    ) {
        Text(
            text = "开屏跳过",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "自动点掉应用启动时的开屏广告",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(18.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = cardColor)
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    text = if (enabled) "防护已开启" else "防护未开启",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = onCardColor
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (enabled) {
                        "已获得无障碍授权，进入受支持的应用时会自动跳过开屏广告。"
                    } else {
                        "需要开启无障碍服务才能工作。本应用只读取当前屏幕的控件信息，不联网、不上传。"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = onCardColor
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = { openAccessibilitySettings(ctx) }) {
                    Text(if (enabled) "查看无障碍设置" else "立即开启")
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("今日跳过", today, Modifier.weight(1f))
            StatCard("累计跳过", total, Modifier.weight(1f))
        }

        Spacer(Modifier.height(14.dp))

        // 侧载应用的系统限制提示
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showHelp = true },
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Column(Modifier.padding(18.dp)) {
                Text(
                    text = "开不了无障碍？",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "安卓对侧载应用有安全限制，开关会变灰点不动。点这里看解除方法。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
                .clickable { tipsOpen = !tipsOpen }
        ) {
            Column(Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "使用说明",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = if (tipsOpen) "收起" else "展开",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (tipsOpen) {
                    Spacer(Modifier.height(10.dp))
                    Tip("1", "应用更新后，系统会关闭无障碍服务，需要重新开启一次。")
                    Tip("2", "在「规则」里可以单独关掉某个应用，不影响其它应用。")
                    Tip("3", "没跳过时去「日志」看：是没识别到，还是点了没反应。")
                    Tip("4", "开屏广告的「跳过」位置由各应用自己决定，个别应用可能适配不全。")
                }
            }
        }

        Spacer(Modifier.height(18.dp))
    }

    if (showHelp) {
        AlertDialog(
            onDismissRequest = { showHelp = false },
            title = { Text("解除系统限制") },
            text = {
                Column {
                    Text(
                        "从浏览器或文件管理安装的应用，安卓默认禁止开启无障碍，开关会显示为灰色点不动。解除位置在「应用详情 → 权限管理」里。",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(12.dp))
                    HelpStep("1", "点下面的「打开应用详情」，或手动去 设置 → 应用 → 应用管理 → 开屏跳过")
                    HelpStep("2", "进入「权限管理」，在列表里找到「允许受限制的设置」并打开（部分机型在页面右上角 ⋮ 菜单里）")
                    HelpStep("3", "回到 设置 → 无障碍 → 开屏跳过，此时开关已可打开")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showHelp = false
                    openAppDetails(ctx)
                }) { Text("打开应用详情") }
            },
            dismissButton = {
                TextButton(onClick = { showHelp = false }) { Text("知道了") }
            }
        )
    }
}

@Composable
private fun StatCard(label: String, value: Int, modifier: Modifier = Modifier) {
    val animated by animateIntAsState(targetValue = value, animationSpec = tween(600), label = label)
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(vertical = 16.dp, horizontal = 18.dp)) {
            Text(
                text = animated.toString(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun Tip(index: String, text: String) {
    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
        Text(
            text = index,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun HelpStep(index: String, text: String) {
    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
        Text(
            text = "$index.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.width(8.dp))
        Text(text = text, style = MaterialTheme.typography.bodyMedium)
    }
}

fun openAccessibilitySettings(ctx: Context) {
    try {
        val i = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
    } catch (t: Throwable) {
        openAppDetails(ctx)
    }
}

fun openAppDetails(ctx: Context) {
    try {
        val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        i.data = Uri.parse("package:" + ctx.packageName)
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
    } catch (t: Throwable) {
        // 忽略
    }
}
