package com.jemcik.gloaming.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.jemcik.gloaming.R
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

    Column(
        Modifier
            .fillMaxSize()
            .background(g.surface)
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(horizontal = 24.dp)
            .padding(top = 8.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        BackRow(onBack)

        Text(
            stringResource(R.string.settings_title),
            style = MaterialTheme.typography.titleLarge, color = g.onSurface
        )

        Spacer(Modifier.height(8.dp))
        SectionLabel(stringResource(R.string.section_appearance))
        listOf(
            Prefs.THEME_SYSTEM to R.string.theme_system,
            Prefs.THEME_LIGHT to R.string.theme_light,
            Prefs.THEME_DARK to R.string.theme_dark
        ).forEach { (mode, label) ->
            ChoiceRow(stringResource(label), themeMode == mode) {
                haptics.select(); onThemeMode(mode)
            }
        }

        Spacer(Modifier.height(18.dp))
        SectionLabel(stringResource(R.string.section_language))
        LinkRow(
            stringResource(R.string.settings_language),
            stringResource(R.string.settings_language_why)
        ) {
            haptics.open()
            runCatching {
                ctx.startActivity(
                    Intent(Settings.ACTION_APP_LOCALE_SETTINGS)
                        .setData(Uri.fromParts("package", ctx.packageName, null))
                )
            }
        }

        Spacer(Modifier.height(18.dp))
        SectionLabel(stringResource(R.string.section_about))
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

/** Filled when chosen, hollow when not - the same grammar as the day row. */
@Composable
private fun ChoiceRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    val g = gloam
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(20.dp)
                .clip(CircleShape)
                .then(
                    if (selected) Modifier.background(g.selectFill)
                    else Modifier.border(1.5.dp, g.line, CircleShape)
                )
        )
        Spacer(Modifier.width(14.dp))
        Text(label, style = MaterialTheme.typography.titleMedium, color = g.onSurface)
    }
}

@Composable
private fun LinkRow(title: String, subtitle: String, onClick: () -> Unit) {
    val g = gloam
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = g.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = g.onSurfaceLow)
        }
        Text("›", style = MaterialTheme.typography.titleLarge, color = g.onSurfaceLow)
    }
}

/**
 * The way back, shared with the allowlist screen. The offset cancels the row's
 * own padding plus the icon's - Google's arrow_back path spans x=4..20 of a 24
 * viewport - so it is the arrow's INK that lands on the content margin.
 */
@Composable
fun BackRow(onBack: () -> Unit) {
    val g = gloam
    Row(
        Modifier
            .offset(x = (-12).dp)
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onBack)
            .heightIn(min = 48.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painterResource(R.drawable.ic_back),
            contentDescription = null,
            tint = g.onSurface,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            stringResource(R.string.action_back),
            style = MaterialTheme.typography.titleMedium, color = g.onSurface
        )
    }
}
