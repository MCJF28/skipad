package com.dsh.skipad

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dsh.skipad.data.LogBus
import com.dsh.skipad.engine.Rules
import com.dsh.skipad.service.SkipAccessibilityService
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ctx = applicationContext
        LogBus.init(ctx)
        Rules.load(ctx)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    HomeScreen(ctx)
                }
            }
        }
    }
}

@Composable
fun HomeScreen(ctx: Context) {
    var enabled by remember { mutableStateOf(isServiceEnabled(ctx)) }

    LaunchedEffect(Unit) {
        while (true) {
            enabled = isServiceEnabled(ctx)
            delay(1000)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Text(text = "开屏跳过", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (enabled) "无障碍服务：已开启 ✓" else "无障碍服务：未开启",
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            text = "规则库：${Rules.size()} 条",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = {
            val i = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(i)
        }) {
            Text(text = if (enabled) "打开无障碍设置" else "去开启无障碍服务")
        }
        Spacer(Modifier.height(24.dp))
        Text(text = "事件日志（${LogBus.entries.size}）", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(LogBus.entries) { line ->
                Text(text = line, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

fun isServiceEnabled(ctx: Context): Boolean {
    val expected = ComponentName(ctx, SkipAccessibilityService::class.java)
    val raw = try {
        Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
    } catch (t: Throwable) {
        null
    } ?: return false
    return raw.split(':')
        .mapNotNull { ComponentName.unflattenFromString(it) }
        .any { it == expected }
}
