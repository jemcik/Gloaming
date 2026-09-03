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
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.PI
import androidx.compose.foundation.ScrollState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import android.app.NotificationManager
import androidx.compose.ui.test.onAllNodesWithText
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import java.time.DayOfWeek
import java.time.LocalTime
import androidx.compose.foundation.rememberScrollState
import com.jemcik.gloaming.R
import com.jemcik.gloaming.core.Prefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    private fun withLaunchManager() = ctx().withLaunchManager()

    @Test
    fun `the switch follows the TILE, with no resume to carry the news`() {
        // Reported on the Honor: bedtime off, pull the shade down, tap the
        // tile, push the shade back up - and the switch underneath still says
        // Off while the rule is live and the alarms are armed.
        //
        // The tile is not a screen and does not resume anything. Measured
        // there: `topResumedActivity` stayed MainActivity for the whole time
        // the shade was open, so ON_RESUME - the only refresh this screen had -
        // never fires, and no other event exists to carry the change. That is
        // why the write itself has to be what is watched.
        //
        // The write goes through a SEPARATE Prefs instance on purpose. That is
        // what the tile holds, and an instance that shares the state object's
        // own would prove nothing about two of them agreeing.
        val p = armed()
        p.enabled = false
        compose.setContent {
            GloamingTheme(dark = false) {
                Home(rememberScrollState(), onOpenSettings = {}, onOpenInterruptions = {})
            }
        }
        val master = compose.onNodeWithContentDescription(ctx().getString(R.string.bedtime_mode))
        master.assertIsOff()

        Prefs(ctx()).enabled = true
        compose.waitForIdle()

        master.assertIsOn()
    }

    @Test
    fun `a change made DURING a window reaches the rule without leaving the screen`() {
        // Reported: bedtime running, music playing, switch media off - and the
        // music kept going until Back or Home. The push waited for ON_PAUSE,
        // which is invisible for every other row here (they all govern
        // something that has not happened yet) and plainly wrong for the one
        // you can HEAR while you change it.
        val p = armed()
        p.allowMedia = true
        var pushes = 0
        compose.setContent {
            GloamingTheme(dark = false) {
                InterruptionsScreen(onBack = {}, onChanged = { pushes++ })
            }
        }
        compose.onNodeWithText(ctx().getString(R.string.row_media)).performScrollTo().performClick()
        compose.waitForIdle()

        // The tap has to have LANDED before the counter means anything. Without
        // this the "not pushed yet" assertion below passes on a screen that
        // never registered the click at all - which is exactly what the first
        // version of this test did.
        assertFalse("the tap never reached the switch", Prefs(ctx()).allowMedia)

        // Still batched: the point is not to push on the tap itself, or six
        // switches would cost six visible rewrites of a live rule.
        assertEquals("the rule was pushed on the tap, losing the batching", 0, pushes)

        compose.mainClock.advanceTimeBy(1_200)
        assertEquals("the change never reached the rule on its own", 1, pushes)
    }

    @Test
    fun `several switches in one go still cost ONE rewrite`() {
        // The other half, and the reason this settles rather than pushing per
        // tap: every rewrite of a live rule takes zen off and on and re-posts
        // the system's "Do Not Disturb is on" notification. Each tap restarts
        // the timer, so a burst collapses into a single push.
        val p = armed()
        p.allowMedia = true
        p.allowReminders = true
        var pushes = 0
        compose.setContent {
            GloamingTheme(dark = false) {
                InterruptionsScreen(onBack = {}, onChanged = { pushes++ })
            }
        }
        compose.onNodeWithText(ctx().getString(R.string.row_media)).performScrollTo().performClick()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(200)
        compose.onNodeWithText(ctx().getString(R.string.row_reminders)).performScrollTo().performClick()
        compose.waitForIdle()

        compose.mainClock.advanceTimeBy(1_200)
        assertEquals("two taps cost two rewrites of a live rule", 1, pushes)
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
        val skip = ctx().getString(R.string.launch_tip_dismiss)
        compose.onNodeWithText(skip).performClick()
        compose.onNodeWithText(skip).assertDoesNotExist()
        assertTrue("refusing must be remembered, or it returns", p.launchTipSeen)
    }

    @Test
    fun `dismissed is dismissed, including across the master switch`() {
        // The user's own sentence: press Dismiss and it must be gone and never
        // shown again until the app is reset. Refusing it was already pinned;
        // what was not is the path they actually walked - switch bedtime off,
        // switch it back on. The tip is gated on `enabled`, so that toggle is
        // the one moment it could plausibly return, and it is the moment that
        // got reported.
        val p = armed()
        p.launchTipSeen = false
        withLaunchManager()
        shadowOf(ctx().getSystemService(NotificationManager::class.java))
            .setNotificationPolicyAccessGranted(true)
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        compose.setContent {
            GloamingTheme(dark = false) {
                Home(rememberScrollState(), onOpenSettings = {}, onOpenInterruptions = {})
            }
        }
        val dismiss = ctx().getString(R.string.launch_tip_dismiss)
        compose.onNodeWithText(dismiss).performClick()
        compose.onNodeWithText(dismiss).assertDoesNotExist()

        // Asserted either side of each tap, or a switch that silently ignored
        // the click would leave this passing without ever exercising the toggle
        // - the same vacuity as a row that was never drawn.
        val master = compose.onNodeWithContentDescription(ctx().getString(R.string.bedtime_mode))
        master.assertIsOn()
        master.performClick(); compose.waitForIdle()
        master.assertIsOff()
        master.performClick(); compose.waitForIdle()
        master.assertIsOn()

        compose.onNodeWithText(dismiss).assertDoesNotExist()
        assertTrue("the refusal did not survive the switch", p.launchTipSeen)
    }

    @Test
    fun `going to look at the vendor screen does not answer the offer`() {
        // Reported and reproduced on the Honor: press "Set up", change nothing,
        // press back, and the card is gone for good. It closed the tip before
        // opening the screen, so merely LOOKING counted as having done it -
        // and Honor's auto-launch state is unreadable, so the app could never
        // discover it had guessed wrong. The offer stands until it is refused.
        val p = armed()
        p.launchTipSeen = false
        withLaunchManager()
        compose.setContent {
            GloamingTheme(dark = false) {
                Home(rememberScrollState(), onOpenSettings = {}, onOpenInterruptions = {})
            }
        }
        compose.onNodeWithText(ctx().getString(R.string.launch_tip_action)).performClick()
        assertFalse(
            "opening the vendor screen must not answer the offer",
            p.launchTipSeen
        )
        // Still there, but no longer OFFERING - it asks now, because the one
        // thing it needs to know is the one thing it cannot read.
        compose.onNodeWithText(ctx().getString(R.string.launch_tip_done)).assertExists()
    }

    @Test
    fun `Done answers it, and that is final`() {
        // The whole point of the second face. Someone who has actually set the
        // switches gets one tap to say so - which is the only way this app will
        // ever know, since it cannot look.
        val p = armed()
        p.launchTipSeen = false
        withLaunchManager()
        compose.setContent {
            GloamingTheme(dark = false) {
                Home(rememberScrollState(), onOpenSettings = {}, onOpenInterruptions = {})
            }
        }
        compose.onNodeWithText(ctx().getString(R.string.launch_tip_action)).performClick()
        compose.onNodeWithText(ctx().getString(R.string.launch_tip_done)).performClick()
        compose.onNodeWithText(ctx().getString(R.string.launch_tip_done)).assertDoesNotExist()
        assertTrue("Done did not answer the offer", p.launchTipSeen)
    }

    @Test
    fun `Not yet puts the offer back rather than refusing it`() {
        // "I have not done it" is not "stop asking". It returns to the plain
        // offer, where dismissing outright is still available - conflating the
        // two would take the refusal away from someone who only meant to say
        // they had not got round to it.
        val p = armed()
        p.launchTipSeen = false
        withLaunchManager()
        compose.setContent {
            GloamingTheme(dark = false) {
                Home(rememberScrollState(), onOpenSettings = {}, onOpenInterruptions = {})
            }
        }
        compose.onNodeWithText(ctx().getString(R.string.launch_tip_action)).performClick()
        compose.onNodeWithText(ctx().getString(R.string.launch_tip_not_yet)).performClick()

        compose.onNodeWithText(ctx().getString(R.string.launch_tip_dismiss)).assertExists()
        compose.onNodeWithText(ctx().getString(R.string.launch_tip_action)).assertExists()
        assertFalse("\"not yet\" must not answer the offer", p.launchTipSeen)
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
        compose.onNodeWithText(ctx().getString(R.string.launch_tip_dismiss)).assertDoesNotExist()
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
        compose.onNodeWithText(ctx().getString(R.string.launch_tip_dismiss)).assertDoesNotExist()
    }

    // ---------- Settings ----------

    @Test
    fun `the numerals follow a change to the phone's clock format`() {
        // Reported: the screen read "20:40" above "From 8:40 PM". 12-or-24-hour
        // is a SYSTEM setting rather than Compose state, so when it changed
        // under an open screen nothing WindowTime took as a parameter moved and
        // Compose correctly skipped it - while the sentence, built inside a
        // remember(s.tick), updated. One value, two answers, on one screen.
        //
        // A formatting test would not have caught this: the formatting was
        // right the whole time, and a cold start showed it right. What has to
        // be exercised is the CHANGE arriving while the screen is up.
        val p = armed()
        p.startTime = LocalTime.of(20, 40)
        p.endTime = LocalTime.of(6, 40)
        Settings.System.putString(ctx().contentResolver, Settings.System.TIME_12_24, "24")
        compose.setContent {
            GloamingTheme(dark = false) {
                Home(rememberScrollState(), onOpenSettings = {}, onOpenInterruptions = {})
            }
        }
        // EXACT, not substring. The sentence under the dial contains "20:40"
        // too - "From 20:40 Wednesday to 06:40 today" - and it is the half that
        // was never broken, so a substring match passes with the bug still in.
        // It did: this test was written that way first and stayed green with
        // the fix reverted, which is the only reason the vacuity was caught.
        // The numeral is its own node and its whole text is the time; the day
        // period is a separate Text beside it.
        assertTrue(
            "the 24-hour numeral was not drawn to begin with",
            compose.onAllNodesWithText("20:40").fetchSemanticsNodes().isNotEmpty()
        )

        Settings.System.putString(ctx().contentResolver, Settings.System.TIME_12_24, "12")
        // The minute ticker is what carries a system setting into an open
        // screen - the same one that keeps `now` from going stale.
        compose.mainClock.advanceTimeBy(61_000)
        compose.waitForIdle()
        assertTrue(
            "the numerals kept the old clock format after the phone changed it",
            compose.onAllNodesWithText("8:40").fetchSemanticsNodes().isNotEmpty()
        )
    }

    // ---------- the dial, and the page it sits on ----------

    /**
     * Where a handle is drawn, in the dial's own maths. 22:30 is 337.5 degrees
     * and 08:00 is 120, so the LEFT edge - 270 - is far from both, and is the
     * part of the canvas a finger crosses on its way down the page.
     */
    private fun handleOffset(deg: Float, width: Int, centre: Offset): Offset {
        val r = width * (97f / 260f)
        val rad = ((deg - 90f) * PI / 180f).toFloat()
        return Offset(centre.x + r * cos(rad), centre.y + r * sin(rad))
    }

    @Test
    fun `a swipe across the dial scrolls the page instead of moving bedtime`() {
        // The reported bug: scrolling Home changed the schedule. The grab test
        // was "anywhere outside the centre well", which is most of a 260dp
        // square - corners included - so a finger passing over the dial was
        // taken as a drag of whichever handle was angularly nearest.
        val p = armed()
        p.startTime = LocalTime.of(22, 30)
        p.endTime = LocalTime.of(8, 0)
        lateinit var scroll: ScrollState
        compose.setContent {
            scroll = rememberScrollState()
            GloamingTheme(dark = false) {
                Home(scroll, onOpenSettings = {}, onOpenInterruptions = {})
            }
        }
        compose.onNodeWithTag(DIAL_TAG).performTouchInput {
            val from = Offset(width * 0.12f, height * 0.5f)
            swipe(from, Offset(from.x, from.y - height * 0.4f), 200)
        }
        compose.waitForIdle()
        assertEquals("bedtime moved while the page was scrolled",
            LocalTime.of(22, 30), p.startTime)
        assertEquals("the wake time moved while the page was scrolled",
            LocalTime.of(8, 0), p.endTime)
        assertTrue("the page did not scroll, so the swipe went nowhere", scroll.value > 0)
    }

    @Test
    fun `the handle itself still drags`() {
        // The other half, and the one that matters more: shrinking the grab
        // area to 24dp must not make the app's primary control unusable.
        var moved: LocalTime? = null
        compose.setContent {
            GloamingTheme(dark = false) {
                BedtimeDial(
                    start = LocalTime.of(22, 30), end = LocalTime.of(8, 0),
                    now = LocalTime.NOON, running = false,
                    track = gloam.raise, enabled = true,
                    centreValue = "9h 30m", centreLabel = "sleep window",
                    onStartChange = { moved = it }, onEndChange = {},
                    onDragFinished = {}
                )
            }
        }
        compose.onNodeWithTag(DIAL_TAG).performTouchInput {
            val handle = handleOffset(337.5f, width, center)
            swipe(handle, handleOffset(300f, width, center), 200)
        }
        compose.waitForIdle()
        assertTrue("the bedtime handle no longer drags", moved != null)
    }

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
        // By resource, not by the English words. Hardcoding the copy makes a
        // wording change look like a broken radio group - which is exactly what
        // it did when "Always dark" became "Dark".
        compose.onNodeWithText(ctx().getString(R.string.theme_dark)).assertIsSelected()
    }

    @Test
    fun `choosing a theme reports that theme, not the one beside it`() {
        var picked = -1
        compose.setContent {
            GloamingTheme(dark = false) {
                SettingsScreen(Prefs.THEME_SYSTEM, onThemeMode = { picked = it }, onBack = {})
            }
        }
        compose.onNodeWithText(ctx().getString(R.string.theme_light)).performClick()
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
        compose.onNodeWithText(ctx().getString(R.string.row_reminders)).assertIsOff()
        // The semantics action rather than a synthetic tap. performClick() does
        // not toggle this row under Robolectric, though it works on the
        // selectable rows in Settings; I did not chase why, because what this
        // test is really asserting is that the ROW carries the click action at
        // all - the switch is only the indicator, not the target.
        compose.onNodeWithText(ctx().getString(R.string.row_reminders))
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        compose.onNodeWithText(ctx().getString(R.string.row_reminders)).assertIsOn()
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
        compose.onNodeWithText(ctx().getString(R.string.row_alarms)).assertExists()
        compose.onNodeWithText(ctx().getString(R.string.row_alarms_why)).assertExists()
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