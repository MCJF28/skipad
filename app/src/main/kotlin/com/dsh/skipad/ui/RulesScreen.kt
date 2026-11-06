package com.dsh.skipad.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.dsh.skipad.data.CustomRules
import com.dsh.skipad.data.Prefs
import com.dsh.skipad.data.RulesSync
import com.dsh.skipad.engine.Rule
import com.dsh.skipad.engine.Rules
import kotlinx.coroutines.launch

data class AppEntry(val pkg: String, val label: String)

@Composable
fun RulesScreen(ctx: Context) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var tick by remember { mutableIntStateOf(0) }

    val rules = remember(tick) { Rules.all() }
    val supported = remember(tick) { rules.map { it.pkg }.toSet() }
    val installed = remember { loadLaunchableApps(ctx) }
    val others = remember(query, tick) {
        installed.filter { it.pkg !in supported && it.label.contains(query, ignoreCase = true) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Column {
                Text(
                    text = "应用规则",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "已支持 ${rules.size} 个应用，可单独关闭",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = "规则库",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = RulesSync.status.ifBlank {
                            if (RulesSync.hasRemote(ctx)) "已加载在线规则" else "当前为内置规则"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(Modifier.height(6.dp))
                    TextButton(onClick = { scope.launch { RulesSync.refresh(ctx); tick++ } }) {
                        Text("更新规则库")
                    }
                }
            }
        }

        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("搜索应用") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (rules.isNotEmpty()) {
            item {
                Text(
                    text = "受到保护的应用",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            items(rules, key = { it.pkg }) { r ->
                RuleRow(ctx, r, tick) { tick++ }
            }
        }

        item {
            Text(
                text = if (query.isBlank()) "添加其他应用（点一下开始学习）"
                else "搜索结果（${others.size}）",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        items(others.take(if (query.isBlank()) 60 else 200), key = { it.pkg }) { app ->
            AddRow(ctx, app)
        }

        item {
            Text(
                text = "「学习模式」：点一下应用图标 → 打开该应用 → 在屏幕上点一下它的「跳过」按钮即可记录位置。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun RuleRow(ctx: Context, r: Rule, tick: Int, onChange: () -> Unit) {
    val pm = ctx.packageManager
    val icon = remember(r.pkg, tick) { loadIcon(pm, r.pkg) }
    val appName = remember(r.pkg) { loadLabel(pm, r.pkg) }
    val skips = remember(r.pkg, tick) { Prefs.skipsFor(r.pkg) }
    val custom = remember(r.pkg, tick) { Rules.isCustom(r.pkg) }
    var on by remember(r.pkg, tick) { mutableStateOf(Prefs.isRuleEnabled(r.pkg, true)) }

    Card(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBox(icon, 42)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = appName, style = MaterialTheme.typography.titleMedium)
                    if (custom) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "自定义",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Text(
                    text = if (skips > 0) "${r.pkg} · 已跳过 $skips 次" else r.pkg,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (custom) {
                TextButton(onClick = {
                    CustomRules.remove(r.pkg)
                    onChange()
                }) { Text("删除") }
            }
            Switch(
                checked = on,
                onCheckedChange = {
                    Prefs.setRuleEnabled(r.pkg, it)
                    on = it
                    onChange()
                }
            )
        }
    }
}

@Composable
private fun AddRow(ctx: Context, app: AppEntry) {
    val pm = ctx.packageManager
    val icon = remember(app.pkg) { loadIcon(pm, app.pkg) }

    Card(
        modifier = Modifier.fillMaxWidth().clickable { startCalibration(ctx, app.pkg) }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBox(icon, 36)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(text = app.label, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = app.pkg,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "添加",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun IconBox(icon: androidx.compose.ui.graphics.ImageBitmap?, size: Int) {
    if (icon != null) {
        Image(
            bitmap = icon,
            contentDescription = null,
            modifier = Modifier.size(size.dp).clip(CircleShape)
        )
    } else {
        Box(
            Modifier
                .size(size.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
    }
}

fun startCalibration(ctx: Context, pkg: String) {
    Prefs.setPendingCalibration(pkg)
    val i = ctx.packageManager.getLaunchIntentForPackage(pkg)
    if (i == null) {
        Toast.makeText(ctx, "无法启动该应用", Toast.LENGTH_SHORT).show()
        Prefs.clearPendingCalibration()
        return
    }
    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    Toast.makeText(ctx, "打开后点一下它的「跳过」按钮", Toast.LENGTH_LONG).show()
    ctx.startActivity(i)
}

fun loadLaunchableApps(ctx: Context): List<AppEntry> {
    val pm = ctx.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val list = try {
        pm.queryIntentActivities(intent, 0)
    } catch (t: Throwable) {
        emptyList()
    }
    return list.mapNotNull { ri ->
        val pkg = ri.activityInfo?.packageName ?: return@mapNotNull null
        if (pkg == ctx.packageName) return@mapNotNull null
        runCatching { AppEntry(pkg, ri.loadLabel(pm).toString()) }.getOrNull()
    }.distinctBy { it.pkg }.sortedBy { it.label }
}

private fun loadIcon(pm: PackageManager, pkg: String): androidx.compose.ui.graphics.ImageBitmap? =
    runCatching { pm.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap() }.getOrNull()

fun loadLabel(pm: PackageManager, pkg: String): String =
    runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrNull() ?: pkg
