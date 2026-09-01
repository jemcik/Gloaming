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
import com.jemcik.gloaming.core.BootWatch
import com.jemcik.gloaming.core.Prefs
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

    /** The minute ticker, and anything else that only needs a redraw. */
    fun bump() { tick++ }

    fun commit() {
        prefs.enabled = enabled; prefs.startTime = start
        prefs.endTime = end; prefs.days = days
        prefs.fxDnd = fxDnd; prefs.fxGrayscale = fxGray
        prefs.fxDimWallpaper = fxDim; prefs.fxDarkTheme = fxDark
        prefs.fxHideAmbient = fxAmbient
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
        if (!on) {
            Scheduler.cancelAll(ctx)
            ZenController.setActive(ctx, prefs, false)
        }
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
        // Re-asked here so the notice clears itself the moment a boot is
        // handled properly - the only confirmation available, since the
        // vendor's own setting cannot be read.
        missedBoot = BootWatch.missed(prefs)
        restricted = BackgroundLimit.isRestricted(ctx)
        missedAlarm = AlarmWatch.missed(prefs)
        tick++
    }

    /** On first composition: re-arm, in case an alarm was lost. */
    fun rearmIfEnabled() {
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
