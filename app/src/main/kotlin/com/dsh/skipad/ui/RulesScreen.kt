package com.dsh.skipad.ui

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.dsh.skipad.data.Prefs
import com.dsh.skipad.engine.Rule
import com.dsh.skipad.engine.Rules

@Composable
fun RulesScreen(ctx: Context) {
    val rules = remember { Rules.all() }
    var tick by remember { mutableIntStateOf(0) }

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

        items(rules, key = { it.pkg }) { r ->
            RuleRow(ctx, r, tick) { tick++ }
        }

        item {
            Text(
                text = "更多应用适配中。如果某个应用没被跳过，可以在「日志」里看识别情况。",
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
    val icon = remember(r.pkg) {
        runCatching {
            pm.getApplicationIcon(r.pkg).toBitmap(96, 96).asImageBitmap()
        }.getOrNull()
    }
    val appName = remember(r.pkg) {
        runCatching {
            pm.getApplicationLabel(pm.getApplicationInfo(r.pkg, 0)).toString()
        }.getOrNull() ?: r.pkg
    }
    var on by remember(r.pkg, tick) { mutableStateOf(Prefs.isRuleEnabled(r.pkg, r.enabled)) }

    Card(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Image(
                    bitmap = icon,
                    contentDescription = appName,
                    modifier = Modifier.size(42.dp).clip(CircleShape)
                )
            } else {
                Box(
                    Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(text = appName, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(2.dp))
                Text(
                    text = r.pkg,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
