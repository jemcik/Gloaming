package com.jemcik.gloaming.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.jemcik.gloaming.core.AlarmWatch
import com.jemcik.gloaming.core.BackgroundLimit
import com.jemcik.gloaming.core.Bedtime
import com.jemcik.gloaming.core.BackgroundProbe
import com.jemcik.gloaming.core.BootWatch
import com.jemcik.gloaming.core.Doors
import com.jemcik.gloaming.core.Prefs
import com.jemcik.gloaming.core.ScreenEffects
import com.jemcik.gloaming.core.Scheduler
import com.jemcik.gloaming.core.ZenController

/**
 * Everything Home remembers, and the four things it can do about it.
 *
 * `Home` was a 938-line composable holding fourteen pieces of state, a
 * `commit()` closure, a `setBedtime()` closure and a lifecycle observer, all in
 * one scope. Nothing could be lifted out of it because every section read half
 * a dozen locals, so the file could be split but the FUNCTION could not.
 *
 * The state lives here instead, which is what makes a section extractable: a
 * sub-composable takes this one object rather than fifteen parameters, and the
 * next person adding a row does not have to thread another `var` through the
 * whole screen.
 *
 * WHAT IS DELIBERATELY NOT HERE. The cached derivations - `now`,
 * `insideWindow`, the ambient capability probes - stay in the composable, keyed
 * on [tick] with `remember`. They read the PLATFORM rather than this state, so
 * `derivedStateOf` would not know when to recompute them, and the existing
 * keying is load-bearing: `ambientZen` is keyed on tick so an adb grant is
 * picked up on the next resume, while `missedBoot` deliberately is NOT, because
 * it writes prefs on first run and re-asking it every minute would be both
 * pointless and impure. Moving those in would have meant re-deciding each one.
 *
 * [tick] is the screen's "something changed" signal: the minute ticker, every
 * commit, and every resume bump it, and everything time-dependent keys off it.
 */
@Stable
class HomeState(
    private val ctx: Context,
    val prefs: Prefs,
    val haptics: Haptics
) {
    var enabled by mutableStateOf(prefs.enabled)
    var start by mutableStateOf(prefs.startTime)
    var end by mutableStateOf(prefs.endTime)
    var days by mutableStateOf(prefs.days)

    /** Which handle the time picker is editing: "start", "end", or nothing. */
    var picking by mutableStateOf<String?>(null)

    /**
     * Which of the dial centre's readings is showing. Deliberately not
     * persisted, so every visit opens on the most useful one for the state.
     */
    var centreMode by mutableIntStateOf(0)

    /** Bumped by the minute ticker, by [commit] and by [onResume]. */
    var tick by mutableIntStateOf(0)
        private set

    var fxDnd by mutableStateOf(prefs.fxDnd)
    var fxGray by mutableStateOf(prefs.fxGrayscale)
    var fxDim by mutableStateOf(prefs.fxDimWallpaper)
    var fxDark by mutableStateOf(prefs.fxDarkTheme)
    var fxAmbient by mutableStateOf(prefs.fxHideAmbient)

    /** Whether the morning alarm may end the night early. */
    var endAtAlarm by mutableStateOf(prefs.exitAtAlarm)

    var missedBoot by mutableStateOf(BootWatch.missed(prefs))
        private set

    /**
     * Re-read on every resume, because it is a SETTING rather than an event:
     * the user may have just come back from fixing it, and an app update resets
     * it on MagicOS. Cheap - one appop read.
     */
    var restricted by mutableStateOf(BackgroundLimit.isRestricted(ctx))
        private set

    /**
     * An END that came due and never reached us. Re-read on resume, because the
     * moment the user is looking at the screen is exactly when it became
     * knowable - the alarm was eaten while nobody was watching.
     */
    var missedAlarm by mutableStateOf(AlarmWatch.missed(prefs))
        private set

    /**
     * This phone was measured holding one of our alarms, so bedtime cannot be
     * promised to end on time.
     *
     * MEASURED, not guessed. It was previously inferred - "this phone has a
     * launch manager AND has never finished a window" - which showed the card to
     * every Honor owner including the ones already set up correctly, and could
     * not tell a phone that was broken from one that had simply never run yet.
     * The vendor setting cannot be read to settle it either, so [BackgroundProbe]
     * settles it by experiment instead. Nothing is shown until the phone has
     * actually failed to deliver.
     */
    var blocked by mutableStateOf(BackgroundProbe.blocked(prefs))
        private set

    /**
     * This phone can hold apps in the first place, which decides only what the
     * card SAYS - Honor's own switches by name where they exist, the general
     * app-details route where they do not. It never decides whether to show it.
     */
    val hasLaunchManager = Doors.hasLaunchManager(ctx)

    /**
     * The one-time offer has been answered. Held as state as well as in prefs so
     * the card leaves the moment it is answered, without waiting for a resume.
     */
    var tipSeen by mutableStateOf(prefs.launchTipSeen)
        private set

    /**
     * Offer the launch setup ONCE, at the moment bedtime is first switched on.
     *
     * Not on install: at install nothing has been promised yet, and a phone the
     * user is only looking at does not need to survive the night. Switching
     * bedtime on is the moment they start relying on it, which is the moment the
     * advice is worth anything - the just-in-time rule, applied to a setting
     * rather than a permission.
     *
     * Silent when either measured notice is up. Both of those report something
     * that IS wrong and carry the same instruction; a suggestion stacked on top
     * would be a third card saying a version of the same thing, and the weakest
     * of the three.
     */
    fun showLaunchTip(): Boolean =
        hasLaunchManager && enabled && !tipSeen && !blocked && !restricted

    /**
     * Refused, and it never comes back. Called ONLY from the dismiss button:
     * going to look at the vendor screen is not an answer, because nothing here
     * can read whether the switches were actually changed.
     */
    fun closeLaunchTip() {
        prefs.launchTipSeen = true
        tipSeen = true
    }

    /**
     * They have gone to fix it, so ask the question again. Without this the
     * verdict would be permanent: the latch is what stops the card flickering,
     * and a fix would never be believed.
     */
    fun retestBackground() {
        Scheduler.armProbe(ctx, prefs, retest = true)
    }

    /** The minute ticker, and anything else that only needs a redraw. */
    fun bump() { tick++ }

    /** Everything the screen is holding, written down. No side effects. */
    private fun writePrefs() {
        prefs.enabled = enabled; prefs.startTime = start
        prefs.endTime = end; prefs.days = days
        prefs.fxDnd = fxDnd; prefs.fxGrayscale = fxGray
        prefs.fxDimWallpaper = fxDim; prefs.fxDarkTheme = fxDark
        prefs.fxHideAmbient = fxAmbient
        prefs.exitAtAlarm = endAtAlarm
    }

    fun commit() {
        writePrefs()
        // rescheduleAll -> setActive -> syncRule, so syncing here as well
        // pushed the rule twice for every tap.
        Scheduler.rescheduleAll(ctx, prefs)
        tick++
    }

    /**
     * The master switch, through one path, so the row and the switch inside it
     * cannot drift apart.
     *
     * Switching off MID-WINDOW raises no confirmation, and that is deliberate:
     * measured on the phone, off gives zen_mode 0 with activeDay cleared, and
     * one tap back on gives zen_mode 1 with activeDay re-derived, the END alarm
     * restored to the same minute and the next START re-queued. Nothing is
     * spent, so there is nothing to confirm - and the dialog that used to be
     * here fired at the worst possible moment, in a dark room, at someone who
     * wanted the night over now.
     */
    fun setBedtime(on: Boolean) {
        haptics.toggle(on)
        enabled = on
        // Persist what the dial and the effect rows are showing before flipping,
        // then flip through the SAME function the Quick Settings tile calls.
        // The tile has no HomeState to borrow, so the sequence lives in Bedtime
        // and both callers share it rather than agreeing by coincidence.
        writePrefs()
        Bedtime.set(ctx, prefs, on)
        BedtimeTile.refresh(ctx)
        tick++
    }

    /**
     * The wake handle and "at your alarm" are ONE state, in two directions.
     *
     * Turning it on sets the wake time to the alarm; setting the wake time to
     * the alarm turns it on, and moving it away turns it off. So the dial and
     * the switch cannot disagree, which is what all the trouble was: the screen
     * held a wake time of 8:30 and an effective end of 7:30 at the same moment
     * and had to show both somewhere.
     *
     * [followAlarm] is the switch's whole action. Unguarded on purpose - an
     * alarm at 2pm really would make the night nineteen hours, and the row says
     * which two times it is choosing between before the tap, so it is a visible
     * choice rather than a surprise, and one tap back undoes it.
     */
    fun followAlarm(on: Boolean) {
        haptics.toggle(on)
        endAtAlarm = on
        if (on) Scheduler.nextAlarm(ctx)?.let { end = it.toLocalTime() }
        commit()
    }

    /**
     * The other direction: a wake time that lands on the alarm IS following it.
     *
     * Separate from [commit] rather than folded into it, because the switch
     * commits too - and re-deriving there would read "the wake time still equals
     * the alarm" one instant after the user switched it OFF, and turn it back on.
     */
    fun commitWake() {
        val alarm = Scheduler.nextAlarm(ctx)?.toLocalTime()
        endAtAlarm = alarm != null && end == alarm
        commit()
    }

    /**
     * Re-read everything on ON_RESUME.
     *
     * The rule can be deleted or switched off from the phone's own Do Not
     * Disturb screen while we are away, and neither reaches us any other way.
     * Cheap when nothing is wrong: it rewrites nothing and re-asserts nothing.
     */
    fun onResume() {
        ZenController.reconcile(ctx, prefs)
        enabled = prefs.enabled; start = prefs.startTime
        end = prefs.endTime; days = prefs.days
        fxDnd = prefs.fxDnd; fxGray = prefs.fxGrayscale
        fxDim = prefs.fxDimWallpaper; fxDark = prefs.fxDarkTheme
        fxAmbient = prefs.fxHideAmbient
        endAtAlarm = prefs.exitAtAlarm
        // Re-asked here so the notice clears itself the moment a boot is
        // handled properly - the only confirmation available, since the
        // vendor's own setting cannot be read.
        missedBoot = BootWatch.missed(prefs)
        restricted = BackgroundLimit.isRestricted(ctx)
        missedAlarm = AlarmWatch.missed(prefs)
        // Resume is when an overdue probe becomes knowable, exactly as for a
        // missed END - the alarm was eaten while nobody was watching.
        BackgroundProbe.check(prefs)
        blocked = BackgroundProbe.blocked(prefs)
        // Free evidence: if the screen is dark WHILE our rule asks for it, this
        // phone applies device effects after all, whatever the prior says.
        ScreenEffects.observeApplied(ctx, wantsNight = fxDark, ruleActive = runningNow())
        // And ask again here, not only on first composition. Arming needs
        // SCHEDULE_EXACT_ALARM, so on a fresh install the first attempt THROWS
        // and records nothing - correctly, since an alarm we never set cannot
        // be evidence about the phone. Granting that permission returns the user
        // to a RESUME rather than a new composition, so without this the probe
        // would sit unasked until the app was next restarted from cold.
        // Measured on a Galaxy S23, One UI 8: "probe not armed: SecurityException".
        // Self-gating - needsArming is false once one is in flight.
        Scheduler.armProbe(ctx, prefs)
        tick++
    }

    /** On first composition: re-arm, in case an alarm was lost. */
    fun rearmIfEnabled() {
        // Unconditional, and deliberately not tied to `enabled`: the question is
        // whether this PHONE delivers alarms, which is worth answering before
        // the first bedtime rather than after the first one is lost.
        Scheduler.armProbe(ctx, prefs)
        if (prefs.enabled) { Scheduler.rescheduleAll(ctx, prefs); tick++ }
    }

    // ---- reads that are cheap and depend only on state held here ----

    /** "Are we inside the window" is a different question from "is it armed". */
    fun insideWindow(): Boolean =
        Scheduler.liveWindowEnd(prefs, start, end, days) != null

    fun runningNow(): Boolean = enabled && insideWindow()
}

@Composable
fun rememberHomeState(): HomeState {
    val ctx = LocalContext.current
    val haptics = rememberHaptics()
    val prefs = remember { Prefs(ctx) }
    return remember { HomeState(ctx, prefs, haptics) }
}
