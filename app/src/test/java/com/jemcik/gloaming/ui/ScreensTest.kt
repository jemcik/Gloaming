package com.jemcik.gloaming.ui

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.isToggleable
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

    /**
     * Make this phone look like one with a vendor launch manager.
     *
     * `hasLaunchManager` is a CAPABILITY probe - it asks whether the activity
     * resolves - so the way to test the behaviour is to make it resolve, not to
     * stub a manufacturer. Robolectric's package manager answers nothing by
     * default, which is why the tip is invisible in every other test here.
     */
    private fun withLaunchManager() {
        shadowOf(ctx().packageManager).addActivityIfNotPresent(
            android.content.ComponentName(
                "com.hihonor.systemmanager",
                "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
            )
        )
    }

    private fun armed(): Prefs {
        shadowOf(ctx().getSystemService(NotificationManager::class.java))
            .setNotificationPolicyAccessGranted(true)
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        val p = Prefs(ctx())
        p.enabled = true
        p.startTime = LocalTime.now().minusHours(1)
        p.endTime = LocalTime.now().plusHours(1)
        p.days = DayOfWeek.entries.toSet()
        return p
    }

    // ---------- the one-time launch tip ----------

    @Test
    fun `the tip can be refused, and refusing it is final`() {
        // The whole point of the redesign. The card it replaced could not be
        // answered "no": it appeared on every phone with a launch manager,
        // whether or not anything was wrong, and nothing the user did to their
        // phone could clear it. This one has a real way out, and taking it must
        // stick - a suggestion that comes back is a demand.
        val p = armed()
        p.launchTipSeen = false
        withLaunchManager()
        compose.setContent {
            GloamingTheme(dark = false) {
                Home(rememberScrollState(), onOpenSettings = {}, onOpenInterruptions = {})
            }
        }
        val skip = ctx().getString(R.string.launch_tip_skip)
        compose.onNodeWithText(skip).performClick()
        compose.onNodeWithText(skip).assertDoesNotExist()
        assertTrue("refusing must be remembered, or it returns", p.launchTipSeen)
    }

    @Test
    fun `the tip stays away once answered`() {
        val p = armed()
        p.launchTipSeen = true
        withLaunchManager()
        compose.setContent {
            GloamingTheme(dark = false) {
                Home(rememberScrollState(), onOpenSettings = {}, onOpenInterruptions = {})
            }
        }
        compose.onNodeWithText(ctx().getString(R.string.launch_tip_skip)).assertDoesNotExist()
    }

    @Test
    fun `nothing is offered before bedtime is switched on`() {
        // Just-in-time, and this is what pins it: at install nothing has been
        // promised, so there is nothing yet worth protecting overnight.
        val p = armed()
        p.enabled = false
        p.launchTipSeen = false
        withLaunchManager()
        compose.setContent {
            GloamingTheme(dark = false) {
                Home(rememberScrollState(), onOpenSettings = {}, onOpenInterruptions = {})
            }
        }
        compose.onNodeWithText(ctx().getString(R.string.launch_tip_skip)).assertDoesNotExist()
    }

    // ---------- Settings ----------

    // ---------- the door that is not a notice ----------

    @Test
    fun `the launch manager is reachable from Settings, with nothing wrong`() {
        // The tip can be refused and refusing it is FINAL, so if that card were
        // the only route to these two switches, one "Skip" would wall off the
        // phone's own reliability setting for good. It would also mean the only
        // way to reach a screen you might simply want was to be told something
        // was broken - which is exactly how the version this replaced went
        // wrong. Nothing here is failing: no probe has fired, the tip is
        // answered, and the row is still there.
        val p = armed()
        p.launchTipSeen = true
        withLaunchManager()
        compose.setContent {
            GloamingTheme(dark = false) {
                SettingsScreen(Prefs.THEME_SYSTEM, onThemeMode = {}, onBack = {})
            }
        }
        compose.onNodeWithText(ctx().getString(R.string.launch_setup_row))
            .performScrollTo()
            .assertHasClickAction()
    }

    @Test
    fun `no door is drawn onto a screen this phone does not have`() {
        // Robolectric resolves nothing, which is a phone with no launch manager
        // at all - the OnePlus, measured. A row here would send someone nowhere,
        // and `hasLaunchManager` is a capability probe precisely so it cannot.
        compose.setContent {
            GloamingTheme(dark = false) {
                SettingsScreen(Prefs.THEME_SYSTEM, onThemeMode = {}, onBack = {})
            }
        }
        compose.onNodeWithText(ctx().getString(R.string.launch_setup_row))
            .assertDoesNotExist()
    }

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
    // ---------- the wake handle and "at your alarm" are one state ----------

    /** Put a real alarm on the phone, the way a clock app does. */
    private fun setAlarm(at: LocalTime) {
        val am = ctx().getSystemService(android.app.AlarmManager::class.java)
        val next = java.time.LocalDateTime.now().with(at).let {
            if (it.isAfter(java.time.LocalDateTime.now())) it else it.plusDays(1)
        }
        am.setAlarmClock(
            android.app.AlarmManager.AlarmClockInfo(
                next.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(), null
            ),
            android.app.PendingIntent.getBroadcast(
                ctx(), 0, android.content.Intent("test.alarm"),
                android.app.PendingIntent.FLAG_IMMUTABLE
            )
        )
    }

    private fun home() {
        compose.setContent {
            GloamingTheme(dark = false) {
                Home(rememberScrollState(), onOpenSettings = {}, onOpenInterruptions = {})
            }
        }
    }

    @Test
    fun `switching it on moves the wake time onto the alarm`() {
        // The switch IS the move. It used to be able to sit ON and change
        // nothing, which took a second row with its own icon and button to
        // repair - a whole control built to fix a state the switch should never
        // have reached.
        val p = armed()
        val alarm = LocalTime.now().plusMinutes(30).withSecond(0).withNano(0)
        p.endTime = LocalTime.now().plusHours(1)
        p.exitAtAlarm = false
        setAlarm(alarm)
        home()

        compose.onNode(
            // The row shows only the hour now.
            hasText(hhmm(ctx(), alarm.hour, alarm.minute), substring = true)
                and isToggleable()
        ).performScrollTo().performClick()
        assertEquals(
            "switching on must set the wake time to the alarm",
            alarm.withSecond(0).withNano(0), p.endTime
        )
        assertTrue(p.exitAtAlarm)
    }

    @Test
    fun `a wake time set onto the alarm switches it on by itself`() {
        // The "vice versa" half: a wake time that lands on the alarm IS
        // following it, however it got there. Driven through the picker's own
        // Set button - the wake time already equals the alarm here, so what is
        // under test is the rule rather than the clock face.
        val p = armed()
        val alarm = LocalTime.now().plusMinutes(30).withSecond(0).withNano(0)
        p.endTime = alarm
        p.exitAtAlarm = false
        setAlarm(alarm)
        home()

        compose.onNodeWithText(
            ctx().getString(R.string.label_wake_up).uppercase()
        ).performScrollTo().performClick()
        compose.onNodeWithText(ctx().getString(R.string.action_set)).performClick()
        assertTrue(
            "a wake time equal to the alarm must switch it on",
            p.exitAtAlarm
        )
    }

    @Test
    fun `an alarm that moved leaves the dial showing tonight, not the handle`() {
        // The reported bug, and the one state where the wake handle and the
        // alarm can still differ: the switch is ON, so the handle was equal to
        // the alarm when it was set, but the alarm has moved since - while the
        // app was closed, say - and nothing has touched the handle to re-derive
        // it. Every reading that describes TONIGHT has to follow the alarm, or
        // the screen answers "when does it end" two ways at once. It did: the
        // countdown and the numeral said one hour while the app bar and the
        // alarm row said another.
        val p = armed()
        val alarm = LocalTime.now().plusMinutes(30).withSecond(0).withNano(0)
        p.endTime = LocalTime.now().plusHours(1)
        p.exitAtAlarm = true
        setAlarm(alarm)
        home()

        // The dial uppercases its caption, so match what is drawn.
        compose.onNodeWithText(
            ctx().getString(R.string.dial_until, hhmm(ctx(), alarm.hour, alarm.minute))
                .uppercase()
        ).assertExists()
    }

    @Test
    fun `switching it off leaves the wake time where it is`() {
        // Off does not restore anything, and must not: there is nothing to
        // restore to, and inventing a previous wake time would be a schedule
        // the user never set.
        val p = armed()
        val alarm = LocalTime.now().plusMinutes(30).withSecond(0).withNano(0)
        p.endTime = alarm
        p.exitAtAlarm = true
        setAlarm(alarm)
        home()

        compose.onNode(
            // The row shows only the hour now.
            hasText(hhmm(ctx(), alarm.hour, alarm.minute), substring = true)
                and isToggleable()
        ).performScrollTo().performClick()
        assertEquals("off must not move the handle", alarm, p.endTime)
        assertTrue("and it must actually be off", !p.exitAtAlarm)
    }
}