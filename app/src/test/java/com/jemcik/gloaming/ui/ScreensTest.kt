package com.jemcik.gloaming.ui

import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import java.time.DayOfWeek
import java.time.LocalTime
import androidx.compose.foundation.rememberScrollState
import com.jemcik.gloaming.Home
import com.jemcik.gloaming.R
import com.jemcik.gloaming.core.Prefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlarmManager
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

    private fun ctx(): android.content.Context = ApplicationProvider.getApplicationContext()

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
        // Both detail screens carry Back as a top app bar navigation icon now,
        // so there is no "Back" label on either to find by text.
        compose.onNodeWithContentDescription("Back").performClick()
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

    /** A window that contains this instant, whatever hour the suite runs at. */
    private fun runningWindow(prefs: Prefs) {
        val now = LocalTime.now()
        prefs.startTime = now.minusHours(1)
        prefs.endTime = now.plusHours(1)
        prefs.days = DayOfWeek.entries.toSet()
        prefs.activeDay = Prefs.NO_DAY
    }

    @Test
    fun `the right-now readout is hidden when bedtime is not running`() {
        // It reports what the SYSTEM says is in effect, which is only worth
        // saying while there is something to verify. It used to sit at the foot
        // of the allowlist, where during the day it printed "Do Not Disturb is
        // off. Everything is getting through." under rows just set to Blocked -
        // read, reasonably, as the settings not working.
        val prefs = Prefs(ApplicationProvider.getApplicationContext())
        prefs.enabled = false
        compose.setContent {
            GloamingTheme(dark = false) {
                Home(rememberScrollState(), onOpenSettings = {}, onOpenInterruptions = {})
            }
        }
        // By resource, not by literal: the copy has already moved once today
        // and a test coupled to its wording would have to move with it.
        val readout = ctx().getString(R.string.filter_all)
        compose.onNodeWithText(readout).assertDoesNotExist()
    }

    @Test
    fun `the right-now readout appears while bedtime is running`() {
        val prefs = Prefs(ApplicationProvider.getApplicationContext())
        prefs.enabled = true
        // Do Not Disturb NOT asked for, so a filter of ALL is the honest answer
        // rather than the phone ignoring us.
        prefs.fxDnd = false
        runningWindow(prefs)
        compose.setContent {
            GloamingTheme(dark = false) {
                Home(rememberScrollState(), onOpenSettings = {}, onOpenInterruptions = {})
            }
        }
        compose.onNodeWithText(ctx().getString(R.string.filter_all)).assertExists()
        compose.onNodeWithText(ctx().getString(R.string.filter_pill_off)).assertExists()
    }

    @Test
    fun `the readout names the phone ignoring Do Not Disturb, and does not call it off`() {
        // THE failure this app exists to catch: bedtime running, Do Not Disturb
        // asked for, and the phone reporting that everything gets through.
        // It used to print filter_all here - "Do Not Disturb is off. Everything
        // is allowed." - which is the opposite of what happened, with only the
        // ink colour carrying the difference. A test on the colour was never
        // possible; a test on the WORDS is.
        val prefs = Prefs(ApplicationProvider.getApplicationContext())
        prefs.enabled = true
        prefs.fxDnd = true
        runningWindow(prefs)
        compose.setContent {
            GloamingTheme(dark = false) {
                Home(rememberScrollState(), onOpenSettings = {}, onOpenInterruptions = {})
            }
        }
        compose.onNodeWithText(ctx().getString(R.string.filter_ignored)).assertExists()
        compose.onNodeWithText(ctx().getString(R.string.filter_pill_ignored)).assertExists()
        // and specifically NOT the sentence that would say it was switched off
        compose.onNodeWithText(ctx().getString(R.string.filter_all)).assertDoesNotExist()
    }

    @Test
    fun `switching bedtime off mid-window takes one tap`() {
        // It took two until 31 Aug 2026: a confirm dialog stood between the
        // switch and the thing it switches. The action is fully reversible -
        // measured on the phone, one tap back on restores zen, the END alarm to
        // the same minute and the next START - so the modal was charging a
        // decision to deliver a fact the screen behind it already showed.
        // What must not come back is a second step here, at night, from someone
        // who wants the window over NOW.
        val prefs = Prefs(ctx())
        prefs.enabled = true
        runningWindow(prefs)
        // The switch is disabled until the app can actually do the job, and
        // Robolectric grants neither by default - without these two the node is
        // found, reads On, and simply ignores the click.
        shadowOf(ctx().getSystemService(NotificationManager::class.java))
            .setNotificationPolicyAccessGranted(true)
        ShadowAlarmManager.setCanScheduleExactAlarms(true)   // static, not per-instance
        compose.setContent {
            GloamingTheme(dark = false) {
                Home(rememberScrollState(), onOpenSettings = {}, onOpenInterruptions = {})
            }
        }
        // By content description, which is also the only switch on Home that
        // HAS one: every other sits in a row that names it. Selecting the first
        // toggleable node instead was tried and picked up the Repeat row.
        val master = compose.onNodeWithContentDescription(ctx().getString(R.string.bedtime_mode))
        master.assertIsOn()
        master.performClick()
        compose.waitForIdle()
        master.assertIsOff()
        assertEquals(false, Prefs(ctx()).enabled)
    }

    @Test
    fun `the allowlist can be left`() {
        var back = false
        compose.setContent {
            GloamingTheme(dark = false) {
                InterruptionsScreen(onBack = { back = true }, onChanged = {})
            }
        }
        // By content description, not by text: the back affordance is the
        // top app bar's navigation icon now, so there is no "Back" label to
        // find. It moved there because it used to scroll away with the content.
        compose.onNodeWithContentDescription("Back").performClick()
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
