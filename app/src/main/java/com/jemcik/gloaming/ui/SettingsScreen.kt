package com.jemcik.gloaming.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jemcik.gloaming.R
import com.jemcik.gloaming.ui.LinkRow
import com.jemcik.gloaming.ui.RadioRow
import com.jemcik.gloaming.core.BootWatch
import com.jemcik.gloaming.core.Prefs

/**
 * Everything that is a preference about the APP rather than about tonight.
 *
 * Two of the three rows hand off to Android rather than reimplementing it. The
 * language row opens the system's own per-app picker, which exists because the
 * manifest declares a localeConfig - Google's guidance is to provide the entry
 * point, not a second picker that drifts from the first. There is deliberately
 * no 12/24-hour setting: the phone already has one and [com.jemcik.gloaming.core.Clock]
 * follows it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(themeMode: Int, onThemeMode: (Int) -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val g = gloam
    val haptics = rememberHaptics()
    val version = remember {
        runCatching {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName
        }.getOrNull() ?: ""
    }

    DetailScaffold(stringResource(R.string.settings_title), onBack) { inner ->
    // 24dp like every other screen. It used to be 8, with 16 added back onto
    // each non-row element, because bare ListItems bring their own 16dp start
    // padding - and that compensation had to be remembered at every call site.
    // It was not: ABOUT, its body and the version line were left at 8dp while
    // everything above them sat at 24, so one screen had three left edges. The
    // rows live in cards now, like every other list in the app, which puts the
    // ListItem's own padding INSIDE the card where it belongs.
    Column(
        Modifier
            .fillMaxSize()
            .padding(inner)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = SCREEN_PAD)
            .padding(top = 8.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(GROUP)
    ) {
        Section(stringResource(R.string.section_appearance), rule = false) {
            SettingsCard {
                // No dividers between these: a radio group is ONE control, and
                // the choice sheets - the same control - do not divide either.
                listOf(
                    Prefs.THEME_SYSTEM to R.string.theme_system,
                    Prefs.THEME_LIGHT to R.string.theme_light,
                    Prefs.THEME_DARK to R.string.theme_dark
                ).forEach { (mode, label) ->
                    RadioRow(stringResource(label), themeMode == mode) {
                        haptics.select(); onThemeMode(mode)
                    }
                }
            }
        }

        Section(stringResource(R.string.section_language)) {
            SettingsCard {
                LinkRow(
                    stringResource(R.string.settings_language),
                    supporting = stringResource(R.string.settings_language_why),
                    // Without it this row's text started at 40dp where the radio
                    // rows above start at 80 - the same ragged left edge "what
                    // can wake you" had.
                    leading = {
                        Icon(
                            painterResource(R.drawable.ic_language),
                            contentDescription = null,
                            tint = LocalContentColor.current,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                ) {
                    haptics.open()
                    runCatching {
                        ctx.startActivity(
                            Intent(Settings.ACTION_APP_LOCALE_SETTINGS)
                                .setData(Uri.fromParts("package", ctx.packageName, null))
                        )
                    }
                }
            }
        }

        // The door that is always open.
        //
        // Every other route to this screen is a NOTICE - the phone was caught
        // holding an alarm, or a restart went unhandled - so until now the only
        // way to reach the vendor's launch manager was to be told something was
        // broken. That is backwards for the one setting on this phone that
        // cannot be read: someone who simply wants to be sure had nowhere to go,
        // and the only alternative on offer was a card that nagged everyone.
        // A quiet link costs nothing to the people who do not need it.
        //
        // Hidden where no launch manager resolves, on the same capability probe
        // the notices use - never a Build.MANUFACTURER test.
        val launchManager = BootWatch.hasLaunchManager(ctx)
        val systemBedtime = BootWatch.hasSystemBedtime(ctx)
        if (launchManager || systemBedtime) {
            Section(stringResource(R.string.section_this_phone)) {
                SettingsCard {
                    if (launchManager) {
                        LinkRow(
                            stringResource(R.string.launch_setup_row),
                            supporting = stringResource(R.string.launch_link_why),
                            leading = {
                                Icon(
                                    painterResource(R.drawable.ic_restart),
                                    contentDescription = null,
                                    tint = LocalContentColor.current,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        ) { haptics.open(); BootWatch.openAutoStart(ctx) }
                    }
                    if (systemBedtime) {
                        LinkRow(
                            stringResource(R.string.bedtime_settings_row),
                            supporting = stringResource(R.string.bedtime_settings_why),
                            leading = {
                                Icon(
                                    painterResource(R.drawable.ic_bedtime),
                                    contentDescription = null,
                                    tint = LocalContentColor.current,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        ) { haptics.open(); BootWatch.openSystemBedtime(ctx) }
                    }
                }
            }
        }

        Section(stringResource(R.string.section_about)) {
            Text(
                stringResource(R.string.settings_about_body),
                style = MaterialTheme.typography.bodyLarge, color = g.onSurfaceLow
            )
            Text(
                stringResource(R.string.settings_version, version),
                style = MaterialTheme.typography.bodyLarge, color = g.onSurfaceLow
            )
        }
    }
    }
}

/** The app's card, holding a group of rows. */
@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = gloam.raise,
        shape = RoundedCornerShape(CORNER),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(content = content)
    }
}
