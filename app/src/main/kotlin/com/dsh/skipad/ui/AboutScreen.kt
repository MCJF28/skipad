package com.dsh.skipad.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.dsh.skipad.data.UpdateChecker
import kotlinx.coroutines.launch
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private const val REPO_URL = "https://github.com/MCJF28/skipad"

/** 版本更新说明（应用内展示，不联网） */
private val CHANGELOG: List<Pair<String, List<String>>> = listOf(
    "1.5" to listOf(
        "版本更新改为弹窗提示，可直接在弹窗内下载并安装",
        "主版本号更新（如 2.0）时强制更新，不可跳过",
        "更新日志按次版本归类，小版本合并展示"
    ),
    "1.4" to listOf(
        "每次启动自动检查更新",
        "应用内下载与一键安装，首页显示新版本提示",
        "新增网络权限，仅用于检查更新与下载"
    ),
    "1.3" to listOf(
        "新增「关于」页：工作原理、隐私承诺、能力边界",
        "应用内更新日志，仓库补充 README"
    ),
    "1.2" to listOf(
        "修复应用图标与名称读取不到的问题",
        "新增 7 个应用的开屏规则",
        "移除高德地图"
    ),
    "1.1" to listOf(
        "改用 Material 图标，页签切换与卡片加入过渡动画",
        "新增侧载应用「允许受限设置」引导"
    ),
    "1.0" to listOf(
        "界面重构为 首页 / 规则 / 日志",
        "新增跳过统计、单应用开关、深色模式"
    )
)

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

        item { UpdateCard(ctx) }

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
                Bullet("·", "应用唯一的联网行为是：向你确认后访问 GitHub，读取版本号并下载新版安装包。")
                Bullet("·", "代码里没有任何上传逻辑，不发送设备信息、不上报使用数据。")
                Bullet("·", "事件日志只写入本机 Download/SkipAd/events.log，供你自己排查问题。")
                Bullet("·", "跳过次数等统计只保存在本机应用数据里，卸载即清除。")
                Bullet("·", "不收集账号信息、不读取剪贴板和输入内容。")
                Spacer(Modifier.height(6.dp))
                Text(
                    "说明：1.4.0 之前的版本不含网络权限；为了支持自动更新，本版本新增了网络权限，" +
                        "用途仅限上述检查更新与下载。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
            SectionCard("更新日志") {
                CHANGELOG.forEachIndexed { index, entry ->
                    Text(
                        text = "v${entry.first}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (index == 0) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface
                    )
                    entry.second.forEach { Bullet("·", it) }
                    if (index != CHANGELOG.lastIndex) Spacer(Modifier.height(10.dp))
                }
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
private fun UpdateCard(ctx: Context) {
    val scope = rememberCoroutineScope()
    val state = UpdateChecker.state
    val current = remember { UpdateChecker.currentVersion(ctx) }

    SectionCard("版本与更新") {
        Text(
            "当前版本 v$current",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(8.dp))

        when (state) {
            is UpdateChecker.State.Checking -> {
                Text("正在检查更新…", style = MaterialTheme.typography.bodySmall)
            }
            is UpdateChecker.State.UpToDate -> {
                Text(
                    "已是最新版本",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            is UpdateChecker.State.Available -> {
                Text(
                    "发现新版本 v${state.version}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    scope.launch { UpdateChecker.download(ctx, state.url) }
                }) { Text("下载更新") }
            }
            is UpdateChecker.State.Downloading -> {
                Text("正在下载 ${state.percent}%", style = MaterialTheme.typography.bodySmall)
            }
            is UpdateChecker.State.Downloaded -> {
                Text("下载完成", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                Button(onClick = { UpdateChecker.install(ctx, state.file) }) {
                    Text("安装更新")
                }
            }
            is UpdateChecker.State.Failed -> {
                Text(
                    state.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            else -> {}
        }

        Spacer(Modifier.height(4.dp))
        TextButton(
            enabled = state !is UpdateChecker.State.Checking &&
                state !is UpdateChecker.State.Downloading,
            onClick = { scope.launch { UpdateChecker.check(ctx) } }
        ) { Text("检查更新") }
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
