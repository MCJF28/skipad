package com.dsh.skipad.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private data class Page(val tag: String, val title: String, val body: String, val points: List<String>)

private val PAGES = listOf(
    Page(
        tag = "两层防护",
        title = "开屏跳过怎么工作",
        body = "它不去破解应用，也不改系统。只是在你打开应用的那几秒里，帮你把「跳过」按钮点掉。",
        points = listOf(
            "第一层 · 界面跳过：靠无障碍服务识别并点击「跳过」",
            "第二层 · 网络过滤：从 DNS 层屏蔽第三方广告域名（可选）",
            "两层互相独立，可以只开第一层"
        )
    ),
    Page(
        tag = "第一层",
        title = "界面跳过",
        body = "这是主要能力，也是你最需要的那个。",
        points = listOf(
            "只在应用回到前台后的几秒内工作，平时完全不碰屏幕",
            "点击前会确认当前窗口就是目标应用，不会误点到桌面",
            "找不到文字时，按该应用固定的按钮位置点一次",
            "支持的应用在「规则」里管理，也能自己添加"
        )
    ),
    Page(
        tag = "第二层",
        title = "网络过滤（可选）",
        body = "从域名层面拦掉第三方广告 SDK 的请求，默认关闭。",
        points = listOf(
            "需要占用系统 VPN 槽位，和你正在用的 VPN 互斥",
            "开启别的 VPN 时，这一层会自动关闭，保证你不断网",
            "它拦不住开屏广告本身，主要针对贴片、信息流、埋点",
            "域名黑名单可能误伤，出现异常请立即关闭"
        )
    ),
    Page(
        tag = "开始使用",
        title = "接下来做两件事",
        body = "打开无障碍服务，剩下的交给我。",
        points = listOf(
            "在系统设置里开启「开屏跳过」的无障碍服务",
            "需要时到「规则」页给其他应用补充规则"
        )
    )
)

@Composable
fun Onboarding(ctx: Context, onFinish: () -> Unit) {
    var index by remember { mutableIntStateOf(0) }
    val page = PAGES[index]
    val last = index == PAGES.lastIndex

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "开屏跳过",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.weight(1f))
                if (!last) {
                    TextButton(onClick = onFinish) { Text("跳过") }
                }
            }

            Spacer(Modifier.height(24.dp))

            Text(
                text = page.tag,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = page.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = page.body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(18.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(Modifier.padding(18.dp)) {
                    page.points.forEachIndexed { i, point ->
                        Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
                            Box(
                                Modifier
                                    .size(18.dp)
                                    .background(
                                        MaterialTheme.colorScheme.primaryContainer,
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${i + 1}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Spacer(Modifier.size(10.dp))
                            Text(
                                text = point,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                repeat(PAGES.size) { i ->
                    Box(
                        Modifier
                            .padding(horizontal = 4.dp)
                            .size(if (i == index) 9.dp else 7.dp)
                            .background(
                                if (i == index) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant,
                                CircleShape
                            )
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (index > 0) {
                    TextButton(onClick = { index-- }) { Text("上一步") }
                }
                Spacer(Modifier.weight(1f))
                Button(onClick = {
                    if (last) {
                        onFinish()
                    } else {
                        index++
                    }
                }) {
                    Text(if (last) "开始使用" else "下一步")
                }
            }

            if (last) {
                Spacer(Modifier.height(8.dp))
                TextButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        openAccessibilitySettings(ctx)
                    }
                ) { Text("去开启无障碍服务") }
            }
        }
    }
}
