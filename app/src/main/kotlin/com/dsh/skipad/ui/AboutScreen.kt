package com.dsh.skipad.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private const val REPO_URL = "https://github.com/MCJF28/skipad"

@Composable
fun AboutScreen(ctx: Context) {
    val version = remember {
        runCatching {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName
        }.getOrNull() ?: "?"
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column {
                Text(
                    text = "关于",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "开屏跳过 v$version",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            SectionCard("它做什么") {
                Text(
                    "开屏广告（也就是应用启动时先弹出来的那几秒广告）本身无法被阻止显示，本应用做的是：" +
                        "在广告出现的瞬间帮你把「跳过」按钮点掉，让你尽快进入应用正常界面。",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        item {
            SectionCard("工作原理") {
                Bullet("1", "使用系统「无障碍服务」读取当前屏幕上的控件信息（文字、位置、是否可点击）。")
                Bullet("2", "当你打开某个受支持的应用时，在开始的几秒内寻找「跳过」「跳过广告」「4 跳过」这类按钮。")
                Bullet("3", "找到后模拟一次点击；找不到文字时，按该应用固定的按钮位置点一次。")
                Bullet("4", "只在你离开后又重新回到该应用时才开始工作，平时完全不碰屏幕。")
                Spacer(Modifier.height(6.dp))
                Text(
                    "点击前会核对当前窗口的包名必须就是目标应用，因此不会误点到桌面或其他应用里。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            SectionCard("隐私") {
                Text(
                    "承诺：不上传任何数据到云端。",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(6.dp))
                Bullet("·", "本应用没有申请网络权限（INTERNET），从系统层面就不具备联网能力。")
                Bullet("·", "事件日志只写入本机 Download/SkipAd/events.log，用于你自己排查问题。")
                Bullet("·", "跳过次数等统计只保存在本机应用数据里，卸载即清除。")
                Bullet("·", "不收集设备信息、不收集账号、不读取剪贴板和输入内容。")
            }
        }

        item {
            SectionCard("能力边界") {
                Text(
                    "不能保证开屏广告完全不显示，只是帮你自动跳过。",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.height(6.dp))
                Bullet("·", "广告页面本身仍会短暂出现（通常零点几秒），无法做到完全看不到。")
                Bullet("·", "纯图片绘制的广告、自绘控件、没有可读文字的界面，可能识别不到按钮。")
                Bullet("·", "应用改版、换广告 SDK 后，原有规则可能失效，需要更新规则库。")
                Bullet("·", "不同厂商系统的无障碍行为有差异，效果因机型和系统版本而不同。")
            }
        }

        item {
            SectionCard("开源") {
                Text(
                    "源码与更新都发布在 GitHub，欢迎反馈失效的应用。",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { openUrl(ctx, REPO_URL) }) {
                    Text(REPO_URL)
                }
            }
        }

        item {
            Text(
                text = "本应用不隶属于任何第三方应用或广告平台。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun Bullet(mark: String, text: String) {
    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
        Text(
            text = mark,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.width(7.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun openUrl(ctx: Context, url: String) {
    try {
        val i = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
    } catch (t: Throwable) {
        // 忽略
    }
}
