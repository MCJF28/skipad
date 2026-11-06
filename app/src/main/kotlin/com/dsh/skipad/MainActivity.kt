package com.dsh.skipad

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.dsh.skipad.data.LogBus
import com.dsh.skipad.data.Prefs
import com.dsh.skipad.engine.Rules
import com.dsh.skipad.service.SkipAccessibilityService
import com.dsh.skipad.ui.HomeScreen
import com.dsh.skipad.ui.LogScreen
import com.dsh.skipad.ui.RulesScreen
import com.dsh.skipad.ui.SkipAdTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ctx = applicationContext
        Prefs.init(ctx)
        LogBus.init(ctx)
        Rules.load(ctx)

        setContent {
            SkipAdTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    var tab by remember { mutableIntStateOf(0) }
                    val tabs = listOf("首页" to "🏠", "规则" to "📋", "日志" to "📝")

                    Scaffold(
                        bottomBar = {
                            NavigationBar {
                                tabs.forEachIndexed { index, item ->
                                    NavigationBarItem(
                                        selected = tab == index,
                                        onClick = { tab = index },
                                        icon = { Text(item.second) },
                                        label = { Text(item.first) }
                                    )
                                }
                            }
                        }
                    ) { padding ->
                        Box(Modifier.padding(padding)) {
                            when (tab) {
                                0 -> HomeScreen(ctx)
                                1 -> RulesScreen(ctx)
                                else -> LogScreen()
                            }
                        }
                    }
                }
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
