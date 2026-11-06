package com.dsh.skipad.ui

import android.content.Context
import android.provider.Settings
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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

    LaunchedEffect(Unit) {
        while (true) {
            enabled = isServiceEnabled(ctx)
            today = Prefs.skipsToday()
            total = Prefs.totalSkips()
            delay(1000)
        }
    }

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

        // 状态卡
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (enabled) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.errorContainer
                }
            )
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    text = if (enabled) "防护已开启" else "防护未开启",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (enabled) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onErrorContainer
                    }
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (enabled) {
                        "已获得无障碍授权。进入受支持的应用时会自动跳过开屏广告。"
                    } else {
                        "需要开启无障碍服务才能工作。开启后本应用只读取当前屏幕的控件信息，不联网、不上传。"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (enabled) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onErrorContainer
                    }
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = { openAccessibilitySettings(ctx) }) {
                    Text(if (enabled) "查看无障碍设置" else "立即开启")
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // 统计
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("今日跳过", today.toString(), Modifier.weight(1f))
            StatCard("累计跳过", total.toString(), Modifier.weight(1f))
        }

        Spacer(Modifier.height(14.dp))

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp)) {
                Text("使用说明", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Tip("1", "应用更新后系统会关闭无障碍服务，需要重新开启一次。")
                Tip("2", "在「规则」里可以单独关掉某个应用，不影响其它应用。")
                Tip("3", "跳过不成功时，去「日志」看看是没识别到，还是点了没反应。")
            }
        }

        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(Modifier.padding(vertical = 16.dp, horizontal = 18.dp)) {
            Text(
                text = value,
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

fun openAccessibilitySettings(ctx: Context) {
    try {
        val i = android.content.Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
    } catch (t: Throwable) {
        // 部分系统需要走应用详情页，忽略
    }
}
