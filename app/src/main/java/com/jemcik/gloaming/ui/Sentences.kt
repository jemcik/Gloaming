package com.jemcik.gloaming.ui

import android.content.Context
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import com.jemcik.gloaming.R
import com.jemcik.gloaming.core.Clock
import com.jemcik.gloaming.core.Prefs
import com.jemcik.gloaming.core.Scheduler
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Everything on Home that turns a schedule into a SENTENCE.
 *
 * Lifted out of MainActivity.kt, which was 1,639 lines. These are the only
 * parts of that file with no Compose state and no side effects - they take a
 * window and some resources and return a string - so they are the part a test
 * can drive directly. They were `private`, which meant the two test files
 * covering them had to render the whole Home screen and read the result back
 * out of the semantics tree to see one sentence. They are `internal` here, and
 * the render-level tests stay: those check the sentence REACHES the screen,
 * which is a different question from whether it is built correctly.
 *
 * The rule these all share, and the reason they are together: a clock time with
 * no day word reads as TODAY. That has cost two separate fixes - the window
 * sentence and the plan note - so `dayWord` is shared rather than duplicated,
 * and anything new that prints a time has one obvious function to call.
 */

/**
 * The window in plain language: "From 11:05 PM today to 7:15 AM tomorrow".
 *
 * The dial says this spatially and the centre says it as a duration. Neither
 * answers the question a circle is worst at - WHICH morning - and a window that
 * crosses midnight is only obvious on a clock face to someone who already reads
 * clock faces. This is the same fact in the register the screen was missing, and
 * it is the only part of the block a screen reader can make sense of at all.
 *
 * Built from the ACTUAL next or current window rather than from the two handles,
 * so the day words cannot drift from what is scheduled: a one-off, a window five
 * days out and a window running right now each name their own days.
 */
@Composable
internal fun windowSentence(
    ctx: Context,
    prefs: Prefs,
    start: LocalTime,
    end: LocalTime,
    days: Set<DayOfWeek>
): String? {
    val res = ctx.resources
    val locale = LocalLocale.current.platformLocale
    val now = LocalDateTime.now()
    // The window you are IN, or else the next one - and "in" is asked with
    // enabled = true regardless of the switch, deliberately.
    //
    // Asking with the real switch was wrong in a way that only shows at night.
    // Off, at 23:34, inside a 6:55 PM window, it fell through to nextStart and
    // said "from 6:55 PM TOMORROW" - while the dial above it drew the marker
    // inside the arc. Reported as exactly that. Worse, it was a wrong
    // prediction rather than merely an odd one: switch on at 23:34 and bedtime
    // begins immediately, because liveWindowEnd treats a one-off as running the
    // moment the switch is on. The sentence would have promised tomorrow and
    // the app would have started that second.
    // Deliberately asked WITHOUT the alarm, because this answer is only used to
    // find where the window BEGAN - and an alarm-shortened end minus the full
    // duration is not a start time, it is nonsense an hour before the real one.
    val endsAt = Scheduler.liveWindowEnd(
        enabled = true, activeDay = prefs.activeDay,
        start = start, end = end, days = days, from = now
    )
    val from = (endsAt?.minus(Scheduler.duration(start, end))
        ?: Scheduler.nextStart(start, end, days, now)) ?: return null
    // The alarm belongs on the OTHER end. Without it this sentence said "to 8:30
    // AM today" while the app bar and the alarm row both said 7:30 - the same
    // screen answering "when does tonight end" two ways. endAt is the rule
    // itself, so a 2pm alarm outside the window still changes nothing here.
    val alarm = Scheduler.endingAlarm(ctx, prefs.exitAtAlarm)
    val to = Scheduler.endAt(
        from, from.plus(Scheduler.duration(start, end)), alarm, prefs.exitAtAlarm
    )

    fun day(at: LocalDateTime): String = dayWord(ctx, at, now, DaySlot.SPAN)
    // One day word when both ends fall on it. "From 2:40 AM tomorrow to 8:40 AM
    // tomorrow" is correct and says it twice.
    return if (from.toLocalDate() == to.toLocalDate()) res.getString(
        R.string.window_span_same_day,
        hhmm(ctx, from.hour, from.minute), hhmm(ctx, to.hour, to.minute), day(to)
    ) else res.getString(
        R.string.window_span,
        hhmm(ctx, from.hour, from.minute), day(from),
        hhmm(ctx, to.hour, to.minute), day(to)
    )
}

/**
 * What to say at the foot of a card whose switches are not in effect.
 *
 * Every switch on Home is a PLAN. It is written to prefs the moment it is
 * tapped and it reaches the phone when the window opens - so with bedtime off,
 * turning Do Not Disturb "on" is a real and correct action that silences
 * nothing tonight, and looks identical to one that would. Reported as exactly
 * that, and it is the same fault while ARMED but not yet running, where the
 * master switch is green and still nothing is being silenced.
 *
 * Three readings, because the reason differs and a person can act on the
 * difference: switched off (turn it on), armed (wait, or it says when), and
 * armed with nothing to run - a one-off already spent, or no days chosen.
 */
internal fun planNote(
    ctx: Context,
    enabled: Boolean,
    start: LocalTime,
    end: LocalTime,
    days: Set<DayOfWeek>,
    locale: Locale
): String {
    if (!enabled) return ctx.getString(R.string.note_off)
    val now = LocalDateTime.now()
    val next = Scheduler.nextStart(start, end, days, now)
        ?: return ctx.getString(R.string.note_unscheduled)
    // The day word is not decoration. A clock time alone reads as TODAY, and
    // the next window can be days out - reported as exactly that, with the bar
    // saying "Starts in 35h 20m" over a note saying "until 12:00 PM". Same lie
    // by omission the window sentence was fixed for, so it uses the same rule.
    return ctx.getString(
        R.string.note_until,
        hhmm(ctx, next.hour, next.minute), dayWord(ctx, next, now, DaySlot.NOTE)
    )
}

/**
 * "today", "tomorrow", or the weekday - the app's one rule for naming the day
 * something falls on, shared by the window sentence and the plan note so the
 * two cannot disagree on the same screen.
 *
 * Past a day the relative words stop helping and start lying by omission -
 * "tomorrow" for something five days out. The weekday is localised by
 * java.time, so it needs no string of ours.
 */
/** Which sentence is asking; the two need different grammar, see strings.xml. */
internal enum class DaySlot { SPAN, NOTE }

internal val DAY_SPAN = intArrayOf(
    R.string.day_span_monday, R.string.day_span_tuesday, R.string.day_span_wednesday,
    R.string.day_span_thursday, R.string.day_span_friday, R.string.day_span_saturday,
    R.string.day_span_sunday
)
internal val DAY_NOTE = intArrayOf(
    R.string.day_note_monday, R.string.day_note_tuesday, R.string.day_note_wednesday,
    R.string.day_note_thursday, R.string.day_note_friday, R.string.day_note_saturday,
    R.string.day_note_sunday
)

internal fun dayWord(
    ctx: Context,
    at: LocalDateTime,
    now: LocalDateTime,
    slot: DaySlot
): String = when (ChronoUnit.DAYS.between(now.toLocalDate(), at.toLocalDate())) {
    0L -> ctx.getString(R.string.day_today)
    1L -> ctx.getString(R.string.day_tomorrow)
    // NOT java.time: it returns the nominative in every TextStyle, and the two
    // sentences that name a weekday decline it differently. See strings.xml.
    else -> ctx.getString(
        (if (slot == DaySlot.SPAN) DAY_SPAN else DAY_NOTE)[at.dayOfWeek.ordinal]
    )
}

/** Clock time. 24-hour throughout, which is what the dial is. */
internal fun hhmm(ctx: Context, h: Int, m: Int): String = Clock.hhmm(ctx, h, m)

/**
 * The dial centre's compact duration - "5h 20m", or just "20m" under an hour.
 *
 * The hours half is dropped rather than shown as "0h", which said nothing and
 * cost a third of the width of the longest thing this string has to fit: the app
 * bar's status line, where it is capped at one line and truncates rather than
 * wraps. The minutes stay zero-padded WITH an hour ("5h 05m") so the countdown
 * does not change width every ten minutes, and unpadded without one ("5m"),
 * where there is no column to hold.
 */
internal fun span(res: Resources, minutes: Long): String =
    if (minutes < 60) res.getString(R.string.dur_minutes, minutes)
    else res.getString(R.string.dur_compact, minutes / 60, minutes % 60)


/**
 * The one line that says what bedtime is doing: off, armed, or running.
 *
 * Lives here rather than inside the app bar because it is said in two places
 * now. The Quick Settings tile is the same control as the master switch, so it
 * has to be able to say the same three things - and a tile that phrased them
 * its own way would be a second vocabulary for one fact.
 *
 * Takes plain values rather than a `Prefs`, like the rest of this file, so the
 * whole thing is a pure function of (state, times, days, now) and can be tested
 * without a device or a clock.
 */
fun statusLine(
    ctx: Context,
    res: Resources,
    enabled: Boolean,
    activeDay: Long,
    start: LocalTime,
    end: LocalTime,
    days: Set<DayOfWeek>,
    now: LocalDateTime = LocalDateTime.now(),
    alarm: LocalDateTime? = null,
    exitAtAlarm: Boolean = false,
    /**
     * Can the app actually do the job - both permissions in place? Without them
     * every path here is a promise it cannot keep.
     */
    ready: Boolean = true
): String {
    if (!enabled) return res.getString(R.string.bedtime_off)
    // BEFORE any countdown. Revoke Do Not Disturb access mid-schedule and the
    // system deletes the rule, so nothing will happen at the hour - but this
    // line went on saying "starts in 12h 51m" directly above a card explaining
    // that the permission was missing. One screen, two answers, and the more
    // confident of them was the wrong one. Reported exactly that way.
    //
    // Said here rather than at either call site because the app bar and the
    // tile both read this function, and the tile is the harder of the two to
    // notice going stale.
    if (!ready) return res.getString(R.string.state_needs_permission)
    // The end the alarm actually produces, so the bar and the rule agree - and
    // so flipping the switch changes the headline reading, not just a subtitle.
    val ends = Scheduler.liveWindowEnd(
        enabled, activeDay, start, end, days, now, alarm, exitAtAlarm
    )
    if (ends != null) {
        return res.getString(R.string.state_on_until, hhmm(ctx, ends.hour, ends.minute))
    }
    val next = Scheduler.nextStart(start, end, days, now)
        ?: return res.getString(R.string.state_nothing_scheduled)
    // The SAME formatter the dial centre uses, on the same quantity, so the two
    // readings of one duration cannot disagree.
    return res.getString(R.string.state_starts_in, span(res, Duration.between(now, next).toMinutes()))
}
