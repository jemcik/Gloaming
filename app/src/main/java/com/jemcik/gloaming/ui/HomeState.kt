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
import com.jemcik.gloaming.core.RoutineEffect
import com.jemcik.gloaming.core.Routines
import com.jemcik.gloaming.R
import com.jemcik.gloaming.core.AlarmWatch
import com.jemcik.gloaming.core.BackgroundLimit
import com.jemcik.gloaming.core.Bedtime
import com.jemcik.gloaming.core.BackgroundProbe
import com.jemcik.gloaming.core.BootWatch
import com.jemcik.gloaming.core.Doors
import com.jemcik.gloaming.core.Prefs
import com.jemcik.gloaming.core.ScreenEffects
import com.jemcik.gloaming.core.Scheduler
import java.time.LocalDateTime
import java.time.LocalTime
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
    /**
     * The routines Samsung's app holds for our screen effects, on a Galaxy -
     * one per effect, 0 while none. Re-read on resume, which is when the user
     * comes back from Samsung's editor with a new one saved.
     */
    var adopted by mutableStateOf(Routines.adopted(prefs).toSet())
    fun hasRoutine(e: RoutineEffect): Boolean = e in adopted

    /**
     * The effect we last handed Samsung's app a routine for and have not yet
     * seen come back - so its row can say "not saved yet" rather than repeat
     * the first invitation. Mirrors [Prefs.routineOffered].
     */
    var pendingOffer by mutableStateOf(RoutineEffect.entries.firstOrNull { it.key == prefs.routineOffered })

    /** The effect whose one-time explainer is on screen, or null. */
    var explaining by mutableStateOf<RoutineEffect?>(null)

    /**
     * The effect whose routine was adopted on THIS resume - the one thing the
     * screen confirms out loud, because a switch quietly turning on is a change
     * the eye does not always catch after a trip through another app.
     * Cleared by the screen once shown.
     */
    var justAdopted by mutableStateOf<RoutineEffect?>(null)

    /** Whether the next alarm sets when the night ends. See [Scheduler.endAt]. */
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
     * What the miss looked like - when bedtime actually ended, and whether
     * opening the app is what ended it - so the card can say so rather than
     * guess. Null for a latch with no record behind it.
     */
    var miss by mutableStateOf(AlarmWatch.report(prefs))
        private set

    /**
     * Allow has been pressed for THIS miss. The switch behind the button is
     * unreadable, so on return the card stops accusing and says when it will
     * know - the launch tip's second face, which this card did not have, and
     * the reason "I pressed Allow and it is still there" was reported as a bug.
     */
    var missVisited by mutableStateOf(AlarmWatch.visited(prefs))
        private set

    /** Got it has been pressed for THIS miss. The next late END is a new one. */
    var missAcked by mutableStateOf(AlarmWatch.acknowledged(prefs))
        private set

    fun showMissedAlarm(): Boolean = missedAlarm && !missAcked

    fun visitMiss() {
        AlarmWatch.visit(prefs)
        missVisited = true
    }

    fun ackMiss() {
        AlarmWatch.acknowledge(prefs)
        missAcked = true
    }

    /**
     * Tonight's window as the SCHEDULE describes it - the one running, or else
     * the next - before the alarm has its say. Null with nothing to run. The
     * rule is [Scheduler.tonight]'s; this only feeds it the screen's state.
     */
    private fun scheduledTonight(alarm: LocalDateTime?): Scheduler.Window? = Scheduler.tonight(
        enabled, prefs.activeDay, start, end, days, alarm, endAtAlarm, Scheduler.endedAt(prefs)
    )

    /** The next alarm where the switch lets it act; null otherwise. One read per question. */
    private fun endingAlarm(): LocalDateTime? = Scheduler.endingAlarm(ctx, endAtAlarm)

    /**
     * WHAT TONIGHT ACTUALLY ENDS AT, which is not always the wake handle.
     *
     * With "at your alarm" on and an alarm that is tonight's, the night ends
     * at the alarm, and every reading that names tonight's end has to say so -
     * the numeral, the arc, the handle, the countdown, the sentence, and the
     * missed-END card's "it will know at". Shipping the alarm to some of
     * them and not others produced one screen giving two answers to when
     * tonight ends, which is what was reported, twice. Derived HERE, once, and
     * called from wherever the answer is drawn.
     */
    fun endsTonight(): LocalDateTime? = Scheduler.endsTonight(
        enabled, prefs.activeDay, start, end, days, endingAlarm(), endAtAlarm, Scheduler.endedAt(prefs)
    )

    /**
     * Is [alarm] THIS NIGHT'S - the one tonight would end at, were the switch
     * on? Asked with the switch forced on, deliberately: the row shows the
     * alarm's day whenever it is not tonight's, whatever the switch says. The
     * window itself is found under the real switch, so with it off the alarm
     * is judged against the night the handles describe.
     */
    fun alarmIsTonights(alarm: LocalDateTime): Boolean =
        scheduledTonight(alarm)?.let {
            Scheduler.endAt(it.began, it.ends, alarm, exitAtAlarm = true) == alarm
        } ?: false

    /**
     * Tonight ends at the next alarm: the switch is on and the alarm is
     * tonight's. The state the dial marks - WAKE UP becomes NEXT ALARM.
     */
    fun followingAlarm(): Boolean =
        endAtAlarm && Scheduler.nextAlarm(ctx)?.let { alarmIsTonights(it) } == true

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
     * They have been to the vendor screen. Not that they DID anything there -
     * that is the unreadable part - only that the card should stop offering and
     * start asking.
     */
    var tipVisited by mutableStateOf(prefs.launchTipVisited)
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
     * Silent when any measured notice is up - the probe's, the appop's, or a
     * late END's. Each of those reports something that IS wrong and carries the
     * same instruction; a suggestion stacked on top would be one more card
     * saying a version of the same thing, and the weakest of them.
     */
    fun showLaunchTip(): Boolean =
        hasLaunchManager && enabled && !tipSeen && !blocked && !restricted && !showMissedAlarm()

    /**
     * They have gone to look. The card cannot verify what happened there - the
     * switches are unreadable - so on return it stops offering and ASKS.
     */
    fun visitLaunchTip() {
        prefs.launchTipVisited = true
        tipVisited = true
    }

    /** "Not yet" - back to the plain offer, which is exactly where they are. */
    fun unvisitLaunchTip() {
        prefs.launchTipVisited = false
        tipVisited = false
    }

    /**
     * Answered, and it never comes back. Reached two ways and only two: the
     * dismiss button on the offer, and "Done" on the question afterwards.
     * Merely going to LOOK is not an answer, because nothing here can read
     * whether the switches were actually changed.
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

    /**
     * Hand Samsung's app the ready-made routine for one effect. Nothing is
     * chosen here: the switch appears on return, in [onResume], on evidence.
     */
    fun offerRoutine(e: RoutineEffect): Boolean {
        val ok = Routines.offer(ctx, prefs, e, ctx.getString(e.nameRes))
        if (ok) pendingOffer = e
        return ok
    }

    /**
     * The wallpaper switch on a Galaxy. Flipping it to a state the phone's own
     * setting already gives is just a flip; flipping it to the other state
     * needs the routine of that polarity, which is offered once if it has not
     * been saved yet - the switch then flips on adoption, not before, so it is
     * never shown in a state the night cannot deliver.
     */
    fun tapDim() {
        val want = !fxDim
        val needed = Routines.dimRoutineFor(ctx, prefs, want)
        if (needed == null || hasRoutine(needed)) {
            fxDim = want; haptics.toggle(want); commit()
        } else {
            haptics.open(); offerRoutine(needed)
        }
    }

    /**
     * The row's tap. The first time, an explainer stands between the tap and
     * Samsung's editor, because a jump into another app with nothing said is
     * the one part of this flow a first-time user cannot work out alone. Once
     * seen and acted on, every later tap goes straight through.
     */
    fun tapRoutine(e: RoutineEffect) {
        if (prefs.routineExplained) offerRoutine(e) else explaining = e
    }

    /** The explainer's one button: remember it was seen, then go. */
    fun explainedAndGo() {
        val e = explaining ?: return
        explaining = null
        prefs.routineExplained = true
        offerRoutine(e)
    }


    fun commit() {
        writePrefs()
        // rescheduleAll -> setActive -> syncRule, so syncing here as well
        // pushed the rule twice for every tap.
        Scheduler.rescheduleAll(ctx, prefs)
        // The one value the scheduler may write back: with no alarm on the
        // phone the rule switches itself off, and the switch must show it.
        endAtAlarm = prefs.exitAtAlarm
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
     * The switch's whole action: the rule, on or off. Nothing moves.
     *
     * It used to COPY the alarm into the wake handle, so that "on" meant "the
     * handle equals the alarm" and the two could not disagree. They could,
     * the moment the alarm moved in the clock app: an earlier alarm shortened
     * the night while the handle kept the OLD alarm's time for every other
     * morning, and a later one was ignored while the row went on naming it.
     * And the user's own wake time was gone. The handle is now theirs, and it
     * is what a morning with no alarm on it falls back to; the alarm is drawn
     * over it on the nights it applies, see [followingAlarm].
     */
    fun followAlarm(on: Boolean) {
        haptics.toggle(on)
        endAtAlarm = on
        commit()
    }

    /**
     * The wake handle is moving under a finger. Following ends HERE, at the
     * first movement, and not at the release: with the rule still on for the
     * length of the drag, the numeral, the arc and the overline went on
     * showing the alarm while the handle followed the finger away from it -
     * one screen, two answers, for as long as the finger was down. Every
     * reading keys on [endAtAlarm], so flipping it is what makes them follow.
     * A finger that has not moved does not reach here; see BedtimeDial.
     */
    fun dragWake(t: LocalTime) {
        end = t
        endAtAlarm = false
    }

    /**
     * A wake time SET, in the picker. Set is an act whatever the number: the
     * picker opens on the time the numeral shows, which while following is
     * the alarm, and confirming it - or the handle's own old time - is a
     * choice of that time by hand. So following ends here without a
     * condition. A drag has its own path ([dragWake], on movement), and a
     * grab that never moves reaches neither, which is what keeps it from
     * counting as a change of mind.
     */
    fun setWake(t: LocalTime) {
        end = t
        endAtAlarm = false
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
        // Back from Samsung's editor, perhaps: if the routine we offered is
        // there now, its switch is on - and it is started at once if the
        // window is open, through the same commit every switch takes.
        val adoptedNow = Routines.refresh(ctx, prefs)
        adopted = Routines.adopted(prefs).toSet()
        pendingOffer = RoutineEffect.entries.firstOrNull { it.key == prefs.routineOffered }
        if (adoptedNow != null) {
            fxGray = prefs.fxGrayscale; fxDark = prefs.fxDarkTheme; fxDim = prefs.fxDimWallpaper
            justAdopted = adoptedNow
            commit()
        }
        // Re-asked here so the notice clears itself the moment a boot is
        // handled properly - the only confirmation available, since the
        // vendor's own setting cannot be read.
        missedBoot = BootWatch.missed(prefs)
        restricted = BackgroundLimit.isRestricted(ctx)
        missedAlarm = AlarmWatch.missed(prefs)
        miss = AlarmWatch.report(prefs)
        missVisited = AlarmWatch.visited(prefs)
        missAcked = AlarmWatch.acknowledged(prefs)
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

    /**
     * Follow the master switch while it is changed from OUTSIDE this screen.
     *
     * The fifth way this state goes stale, and the only one with no event
     * behind it at all. The other four are in CLAUDE.md; this one is the tile,
     * which writes [Prefs.enabled] from the same process while Home is still
     * the resumed activity, because opening the quick-settings shade never
     * pauses what is underneath it. [onResume] therefore never runs, and the
     * switch keeps whatever it last drew.
     *
     * ONLY [enabled] and the alarm rule are re-read, deliberately. Calling
     * [onResume] here instead would reconcile the rule and re-check the probe
     * on every write this screen makes of its own - the dial commits on every
     * drag - and would re-read the wake handle out from under a finger that is
     * still moving it. The tile changes one value and the receiver another, so
     * those two are what this follows.
     *
     * The caller owns the returned handle and must close it; see [Prefs.watch]
     * for why letting go of it silently stops the callbacks.
     */
    fun watchStore(): AutoCloseable = prefs.watch { key ->
        // Null is a cleared store, which is Reset - "everything changed".
        if (key == null || key == Prefs.KEY_ENABLED) enabled = prefs.enabled
        // The receiver switches the alarm rule off when the last alarm goes -
        // a one-time alarm ringing at 06:30 under an open Home is enough -
        // and nothing else would tell the switch. With a tick, so the app bar
        // and "running now", which key on the tick and not on the rule, move
        // with the numeral and the dial rather than a minute behind them. And
        // only on a real change: this fires for the screen's own commits too.
        if ((key == null || key == Prefs.KEY_EXIT_AT_ALARM) && endAtAlarm != prefs.exitAtAlarm) {
            endAtAlarm = prefs.exitAtAlarm
            tick++
        }
    }

    /**
     * "Are we inside the window" is a different question from "is it armed".
     * WITH the alarm: it can extend a night past the handle, and asked without
     * it this said "not running" at 08:45 while zen ran to a 09:00 alarm.
     */
    fun insideWindow(): Boolean =
        Scheduler.liveWindowEnd(
            prefs, start, end, days, alarm = Scheduler.endingAlarm(ctx, endAtAlarm)
        ) != null

    fun runningNow(): Boolean = enabled && insideWindow()
}

@Composable
fun rememberHomeState(): HomeState {
    val ctx = LocalContext.current
    val haptics = rememberHaptics()
    val prefs = remember { Prefs(ctx) }
    return remember { HomeState(ctx, prefs, haptics) }
}

/** The strings an effect's routine carries, and the one that confirms it - beside the state that uses them. */
internal val RoutineEffect.nameRes: Int
    get() = when (this) {
        RoutineEffect.GRAYSCALE -> R.string.routine_name_gray
        RoutineEffect.DARK -> R.string.routine_name_dark
        RoutineEffect.DIM -> R.string.routine_name_dim
        RoutineEffect.DIM_OFF -> R.string.routine_name_dimoff
    }

internal val RoutineEffect.doneRes: Int
    get() = when (this) {
        RoutineEffect.GRAYSCALE -> R.string.routine_done_gray
        RoutineEffect.DARK -> R.string.routine_done_dark
        RoutineEffect.DIM -> R.string.routine_done_dim
        RoutineEffect.DIM_OFF -> R.string.routine_done_dimoff
    }
