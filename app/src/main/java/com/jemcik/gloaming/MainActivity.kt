package com.jemcik.gloaming

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.jemcik.gloaming.core.*
import com.jemcik.gloaming.ui.GloamingTheme
import com.jemcik.gloaming.ui.Home
import com.jemcik.gloaming.ui.InterruptionsScreen
import com.jemcik.gloaming.ui.SettingsScreen


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Let the sky run behind the status and gesture bars. Light icons are
        // pinned regardless of system theme - the ground is always dark enough
        // in Dusk, and Dawn's cream is light enough that we flip per theme.
        enableEdgeToEdge()
        setContent {
            // The theme mode lives ABOVE GloamingTheme, because it decides which
            // scheme the theme is built from - so it cannot live inside it.
            val prefs = remember { Prefs(this) }
            var themeMode by remember { mutableIntStateOf(prefs.themeMode) }
            val dark = when (themeMode) {
                Prefs.THEME_LIGHT -> false
                Prefs.THEME_DARK -> true
                else -> isSystemInDarkTheme()
            }
            LaunchedEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = if (dark) SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                    else SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
                    navigationBarStyle = if (dark) SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                    else SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                )
            }
            GloamingTheme(dark) {
                Root(themeMode) { themeMode = it; prefs.themeMode = it }
            }
        }
    }
}

@Composable
private fun Root(themeMode: Int, onThemeMode: (Int) -> Unit) {
    val ctx = LocalContext.current
    var screen by remember { mutableIntStateOf(HOME) }
    // Held here, not in Home: Root swaps the two screens, so Home is disposed
    // while the allowlist is up and would otherwise come back scrolled to the
    // top. Its other state is re-read from prefs on return, which is correct.
    val homeScroll = rememberScrollState()

    if (screen == ALLOWLIST) {
        BackHandler { screen = HOME }
        InterruptionsScreen(
            onBack = { screen = HOME },
            onChanged = {
                val p = Prefs(ctx)
                // Not syncRule alone: rewriting the rule clears its condition,
                // so a running window has to be re-armed, not just re-described.
                if (p.enabled) Scheduler.rescheduleAll(ctx, p)
            }
        )
    } else if (screen == SETTINGS) {
        BackHandler { screen = HOME }
        SettingsScreen(themeMode, onThemeMode) { screen = HOME }
    } else {
        Home(homeScroll, onOpenSettings = { screen = SETTINGS }) { screen = ALLOWLIST }
    }
}

private const val HOME = 0
private const val ALLOWLIST = 1
private const val SETTINGS = 2
