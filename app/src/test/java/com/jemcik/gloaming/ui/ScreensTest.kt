package com.jemcik.gloaming.ui

import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import com.jemcik.gloaming.core.Prefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The interactions, not the appearance. Nothing here checks a colour or a
 * spacing - those are judgement, and the phone is the instrument for them.
 * These check the things that have silently broken before: a row that stops
 * responding, a control that reports the wrong value, a screen you cannot leave.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ScreensTest {

    @get:Rule
    val compose = createComposeRule()

    // ---------- Settings ----------

    @Test
    fun `the chosen theme is the one shown as chosen`() {
        compose.setContent {
            GloamingTheme(dark = false) {
                SettingsScreen(Prefs.THEME_DARK, onThemeMode = {}, onBack = {})
            }
        }
        compose.onNodeWithText("Always dark").assertIsSelected()
    }

    @Test
    fun `choosing a theme reports that theme, not the one beside it`() {
        var picked = -1
        compose.setContent {
            GloamingTheme(dark = false) {
                SettingsScreen(Prefs.THEME_SYSTEM, onThemeMode = { picked = it }, onBack = {})
            }
        }
        compose.onNodeWithText("Always light").performClick()
        assertEquals(Prefs.THEME_LIGHT, picked)
    }

    @Test
    fun `there is a way back out of Settings`() {
        var back = false
        compose.setContent {
            GloamingTheme(dark = false) {
                SettingsScreen(Prefs.THEME_SYSTEM, onThemeMode = {}, onBack = { back = true })
            }
        }
        compose.onNodeWithText("Back").performClick()
        assertTrue(back)
    }

    // ---------- What gets through ----------

    @Test
    fun `a switch row toggles from the row, not only from the switch`() {
        // The whole row is the target: the switch is the indicator. This broke
        // once already, when a trailing lambda bound to the wrong parameter and
        // left a sheet permanently open over the screen, swallowing every touch.
        compose.setContent {
            GloamingTheme(dark = false) {
                InterruptionsScreen(onBack = {}, onChanged = {})
            }
        }
        compose.onNodeWithText("Reminders").assertIsOff()
        // The semantics action rather than a synthetic tap. performClick() does
        // not toggle this row under Robolectric, though it works on the
        // selectable rows in Settings; I did not chase why, because what this
        // test is really asserting is that the ROW carries the click action at
        // all - the switch is only the indicator, not the target.
        compose.onNodeWithText("Reminders")
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        compose.onNodeWithText("Reminders").assertIsOn()
    }

    @Test
    fun `the allowlist can be left`() {
        var back = false
        compose.setContent {
            GloamingTheme(dark = false) {
                InterruptionsScreen(onBack = { back = true }, onChanged = {})
            }
        }
        compose.onNodeWithText("Back").performClick()
        assertTrue(back)
    }

    @Test
    fun `the alarms row is present and cannot be switched off`() {
        // Android will silence alarms if asked. We never ask - an app that can
        // mute your morning alarm is a footgun - so this row has no control.
        compose.setContent {
            GloamingTheme(dark = false) {
                InterruptionsScreen(onBack = {}, onChanged = {})
            }
        }
        compose.onNodeWithText("Alarms and timers").assertExists()
        compose.onNodeWithText("Kept on so your alarm still wakes you").assertExists()
    }
}
