package com.jemcik.gloaming.core

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Google schedules bedtime via a WorkManager job. On MagicOS that job carries an
 * undocumented HN_USER_EXPERIENCE constraint which is never satisfied, so the job
 * never runs and bedtime only triggers when the app is foregrounded.
 *
 * setExactAndAllowWhileIdle bypasses JobScheduler entirely. Measured on a
 * Magic8 Pro (MagicOS 10.0.0.199): fired 149ms after the scheduled instant with
 * the app swiped from recents and the screen off.
 *
 * A window is treated as a single span (start + duration), never as two
 * independent times.
 *
 * The day-of-week selection is the MORNING the window ends on, not the evening
 * it starts. Google Clock dodges this question by giving bedtime and wake-up
 * separate day pickers; with one window there is only one set of days, and it
 * has to mean something. It means the morning, because that is what a user is
 * actually choosing - "do not wake me on Saturday" - and because a set that
 * means the evening shows Fri+Sat when you ask for the weekend, which reads as
 * a bug. Scheduler works backwards from the morning to the evening that reaches
 * it.
 */
object Scheduler {

    const val ACTION_START = "com.jemcik.gloaming.START"
    const val ACTION_END = "com.jemcik.gloaming.END"

    private fun am(ctx: Context) = ctx.getSystemService(AlarmManager::class.java)

    fun canScheduleExact(ctx: Context) = am(ctx).canScheduleExactAlarms()

    /** Length of the window, handling the midnight wrap. Zero means "no window". */
    fun duration(start: LocalTime, end: LocalTime): Duration {
        val secs = ((end.toSecondOfDay() - start.toSecondOfDay() + 86400) % 86400).toLong()
        return Duration.ofSeconds(secs)
    }

    /**
     * No days chosen means the window runs ONCE and then switches itself off,
     * the convention alarm clocks use for a non-repeating alarm. It is also the
     * honest reading of the switch: turning bedtime on has to do something, and
     * "on, but never" is a promise the app cannot keep.
     */
    fun isOneOff(days: Set<DayOfWeek>) = days.isEmpty()

    /** The next start, for a repeating schedule or a one-off alike. */
    fun nextStart(
        start: LocalTime,
        end: LocalTime,
        days: Set<DayOfWeek>,
        from: LocalDateTime = LocalDateTime.now()
    ): LocalDateTime? {
        if (!isOneOff(days)) return nextOccurrence(start, end, days, from)
        val today = LocalDateTime.of(from.toLocalDate(), start)
        return if (today.isAfter(from)) today else today.plusDays(1)
    }

    /** Like [currentWindowEnd] but with every day eligible - the one-off case. */
    private fun windowEndAnyDay(
        start: LocalTime,
        end: LocalTime,
        from: LocalDateTime
    ): LocalDateTime? {
        val dur = duration(start, end)
        if (dur.isZero) return null
        for (back in 0L..1L) {
            val began = LocalDateTime.of(from.toLocalDate().minusDays(back), start)
            val ends = began.plus(dur)
            if (!from.isBefore(began) && from.isBefore(ends)) return ends
        }
        return null
    }

    /**
     * The next start, strictly after [from], whose window ENDS on a chosen day.
     */
    fun nextOccurrence(
        start: LocalTime,
        end: LocalTime,
        days: Set<DayOfWeek>,
        from: LocalDateTime = LocalDateTime.now()
    ): LocalDateTime? {
        if (days.isEmpty()) return null
        val dur = duration(start, end)
        var d: LocalDate = from.toLocalDate()
        repeat(8) {
            val cand = LocalDateTime.of(d, start)
            if (cand.isAfter(from) && cand.plus(dur).toLocalDate().dayOfWeek in days) return cand
            d = d.plusDays(1)
        }
        return null
    }

    /**
     * If [from] currently falls inside a window, returns when that window ends.
     * Null when we are outside any window.
     */
    fun currentWindowEnd(
        start: LocalTime,
        end: LocalTime,
        days: Set<DayOfWeek>,
        from: LocalDateTime = LocalDateTime.now()
    ): LocalDateTime? {
        val dur = duration(start, end)
        if (dur.isZero || days.isEmpty()) return null
        // A window can only have begun today or yesterday (max length is 24h).
        for (back in 0L..1L) {
            val day = from.toLocalDate().minusDays(back)
            val began = LocalDateTime.of(day, start)
            val ends = began.plus(dur)
            // Matched on the morning it ends, not the evening it starts.
            if (ends.toLocalDate().dayOfWeek !in days) continue
            if (!from.isBefore(began) && from.isBefore(ends)) return ends
        }
        return null
    }

    /**
     * The end of the window actually running, which is not always the one the
     * current settings describe. Editing days or dragging a handle mid-window
     * used to make [currentWindowEnd] return null, and the next reschedule
     * dropped Do Not Disturb on the spot. A window that has begun is pinned to
     * its own end until that instant passes.
     */
    /**
     * Takes the two values it reads rather than a whole [Prefs], so the entire
     * scheduling core is a pure function of (times, days, now) and can be tested
     * without a device, a Context or a clock. The [Prefs] overload below is the
     * convenience for callers that have one.
     */
    fun liveWindowEnd(
        enabled: Boolean,
        activeDay: Long,
        start: LocalTime,
        end: LocalTime,
        days: Set<DayOfWeek>,
        from: LocalDateTime = LocalDateTime.now()
    ): LocalDateTime? {
        if (enabled && activeDay != Prefs.NO_DAY) {
            // The pinned day, described by whatever the handles say now.
            val began = LocalDateTime.of(LocalDate.ofEpochDay(activeDay), start)
            val ends = began.plus(duration(start, end))
            // Null once it is over or not yet begun: the caller clears the pin
            // rather than falling through to a fresh window.
            return if (!from.isBefore(began) && from.isBefore(ends)) ends else null
        }
        // A one-off has no eligible days to match, so it is only "running" once
        // the switch is on; otherwise the dial would show a phantom window on
        // every day of the week.
        return if (isOneOff(days)) {
            if (enabled) windowEndAnyDay(start, end, from) else null
        } else currentWindowEnd(start, end, days, from)
    }

    fun liveWindowEnd(
        p: Prefs,
        start: LocalTime,
        end: LocalTime,
        days: Set<DayOfWeek>,
        from: LocalDateTime = LocalDateTime.now()
    ): LocalDateTime? = liveWindowEnd(p.enabled, p.activeDay, start, end, days, from)

    fun isActiveNow(
        enabled: Boolean,
        activeDay: Long,
        start: LocalTime,
        end: LocalTime,
        days: Set<DayOfWeek>,
        from: LocalDateTime = LocalDateTime.now()
    ): Boolean = enabled && liveWindowEnd(enabled, activeDay, start, end, days, from) != null

    fun isActiveNow(p: Prefs, from: LocalDateTime = LocalDateTime.now()): Boolean =
        isActiveNow(p.enabled, p.activeDay, p.startTime, p.endTime, p.days, from)

    private fun pending(ctx: Context, action: String, code: Int): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, code,
            Intent(ctx, BedtimeReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun setExact(ctx: Context, at: LocalDateTime, action: String, code: Int) {
        val ms = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        try {
            am(ctx).setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pending(ctx, action, code))
        } catch (e: SecurityException) {
            Journal.write(ctx, "exact alarm denied: " + e)
        }
    }

    /** One line per genuine change of plan, instead of one per settings tap. */
    private fun logPlan(ctx: Context, p: Prefs, summary: String) {
        if (p.lastLoggedAlarms != summary) {
            Journal.write(ctx, summary)
            p.lastLoggedAlarms = summary
        }
    }

    /**
     * Rebuilds both alarms and brings the zen rule in line with the present moment.
     * Safe to call repeatedly; it is idempotent.
     *
     * [force] re-asserts the zen state even when the system already claims it.
     * Boot and upgrade pass it, because the world moved underneath us while we
     * were not running - see the reboot note in ZenController.setActive. The UI
     * does NOT, since re-asserting on a live rule re-applies its device effects.
     */
    fun rescheduleAll(ctx: Context, p: Prefs, force: Boolean = false) {
        cancelAll(ctx)

        if (!p.enabled) {
            p.activeDay = Prefs.NO_DAY
            ZenController.setActive(ctx, p, false, force)
            logPlan(ctx, p, "off")
            return
        }

        val now = LocalDateTime.now()
        val openUntil = liveWindowEnd(p, p.startTime, p.endTime, p.days, now)

        if (openUntil != null) {
            // We are already inside a window: switch on now, end at the right time,
            // and queue the following night's start.
            if (p.activeDay == Prefs.NO_DAY) {
                p.activeDay = openUntil.minus(duration(p.startTime, p.endTime))
                    .toLocalDate().toEpochDay()
            }
            ZenController.setActive(ctx, p, true, force)
            setExact(ctx, openUntil, ACTION_END, 101)
            // A one-off queues no following night; END switches the app off.
            if (!isOneOff(p.days)) {
                nextOccurrence(p.startTime, p.endTime, p.days, now)
                    ?.let { setExact(ctx, it, ACTION_START, 100) }
            }
            logPlan(ctx, p, "running until " + openUntil +
                (if (isOneOff(p.days)) " (once)" else ""))
        } else {
            p.activeDay = Prefs.NO_DAY
            ZenController.setActive(ctx, p, false, force)
            val start = nextStart(p.startTime, p.endTime, p.days, now)
            if (start == null) {
                logPlan(ctx, p, "nothing to schedule")
                return
            }
            val ends = start.plus(duration(p.startTime, p.endTime))
            setExact(ctx, start, ACTION_START, 100)
            setExact(ctx, ends, ACTION_END, 101)
            logPlan(ctx, p, "next " + start + " to " + ends +
                (if (isOneOff(p.days)) " (once)" else ""))
        }
    }

    fun cancelAll(ctx: Context) {
        am(ctx).cancel(pending(ctx, ACTION_START, 100))
        am(ctx).cancel(pending(ctx, ACTION_END, 101))
    }
}
