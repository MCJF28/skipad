package com.dsh.skipad

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.dsh.skipad.data.LogBus
import com.dsh.skipad.data.Prefs
import com.dsh.skipad.data.UpdateChecker
import com.dsh.skipad.engine.Rules
import com.dsh.skipad.service.SkipAccessibilityService
import com.dsh.skipad.ui.AboutScreen
import com.dsh.skipad.ui.HomeScreen
import com.dsh.skipad.ui.LogScreen
import com.dsh.skipad.ui.RulesScreen
import com.dsh.skipad.ui.SkipAdTheme
import com.dsh.skipad.ui.UpdateHost

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ctx = applicationContext
        Prefs.init(ctx)
        LogBus.init(ctx)
        Rules.load(ctx)

        setContent {
            LaunchedEffect(Unit) {
                if (Prefs.shouldAutoCheck()) {
                    UpdateChecker.silentCheck(ctx)
                }
            }
            SkipAdTheme {
                UpdateHost(ctx)
                Surface(color = MaterialTheme.colorScheme.background) {
                    var tab by remember { mutableIntStateOf(0) }

                    Scaffold(
                        bottomBar = {
                            NavigationBar {
                                NavItem(0, tab, Icons.Filled.Home, "首页") { tab = it }
                                NavItem(1, tab, Icons.Filled.List, "规则") { tab = it }
                                NavItem(2, tab, Icons.Filled.Notifications, "日志") { tab = it }
                                NavItem(3, tab, Icons.Filled.Info, "关于") { tab = it }
                            }
                        }
                    ) { padding ->
                        Box(Modifier.padding(padding)) {
                            AnimatedContent(
                                targetState = tab,
                                transitionSpec = {
                                    val forward = targetState > initialState
                                    val enter = slideInHorizontally(tween(280)) { w ->
                                        if (forward) w / 8 else -w / 8
                                    } + fadeIn(tween(220))
                                    val exit = slideOutHorizontally(tween(280)) { w ->
                                        if (forward) -w / 10 else w / 10
                                    } + fadeOut(tween(140))
                                    enter togetherWith exit
                                },
                                label = "tab"
                            ) { t ->
                                when (t) {
                                    0 -> HomeScreen(ctx)
                                    1 -> RulesScreen(ctx)
                                    2 -> LogScreen()
                                    else -> AboutScreen(ctx)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun RowScope.NavItem(
    index: Int,
    current: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onSelect: (Int) -> Unit
) {
    NavigationBarItem(
        selected = current == index,
        onClick = { onSelect(index) },
        icon = { Icon(icon, contentDescription = label) },
        label = { Text(label) }
    )
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
