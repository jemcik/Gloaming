package com.jemcik.gloaming.core

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
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
 * READ THAT MEASUREMENT NARROWLY. A screen that is off is not a device in doze:
 * light idle takes about half an hour of stillness and deep idle longer, so the
 * 149ms says nothing about the state the app actually spends the night in. It
 * was quoted for a year as proof the overnight path works, which it never was.
 *
 * The overnight path IS now measured, 1 Sep 2026, by forcing the states rather
 * than waiting for them - `dumpsys battery unplug` then
 * `dumpsys deviceidle force-idle [light|deep]`, with a window ending a few
 * minutes out. Both arms fired at the scheduled SECOND, in forced light idle and
 * in forced deep idle. Doze is not the problem.
 *
 * What does hold an alarm, indefinitely, is the app being background-restricted:
 * AlarmManager parks it in a queue named "Pending user blocked background
 * alarms" and only foregrounding the app frees it. That is not visible from
 * here - see BackgroundLimit, which reads the appop and says so.
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

    /**
     * A throwaway alarm that exists only to be waited for. See [BackgroundProbe]
     * - nothing happens when it fires except the note that it did.
     */
    const val ACTION_PROBE = "com.jemcik.gloaming.PROBE"

    /**
     * The instant a START or END was armed for, carried IN the alarm. The
     * receiver judges the alarm by this rather than by what prefs say is due,
     * because prefs can have moved on: a parked END released by the app being
     * opened can land after the resume's reschedule has re-armed `endDue` for
     * tomorrow. See [AlarmWatch.handled] and [Delivery.asOf].
     */
    const val EXTRA_DUE = "due"

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

    /**
     * The end, as the next alarm may move it - either way.
     *
     * With the switch on, the night ends at the next alarm when that alarm is
     * THIS NIGHT'S: it rings after the night began, and either inside the
     * window or later on the same calendar day the scheduled end falls on -
     * the "morning" the day-of-week picker already means. Earlier than the
     * wake handle or later; the handle is the fallback for a morning with no
     * alarm on it, not a ceiling. An alarm on another day (Monday's, seen from
     * Friday night) leaves the handle alone, so does one before bedtime
     * starts, and so does no alarm at all.
     *
     * This WAS AOSP's rule - `ScheduleCalendar.shouldExitForAlarm`, the alarm
     * only ever shortens, and only from inside the window - until 11 Sep 2026.
     * A later alarm was ignored while the row above the switch went on naming
     * it, so "End bedtime at your alarm, 09:00" sat ON over a night that ended
     * at 08:30; and switching on used to COPY the alarm into the handle, which
     * froze that morning's alarm as every morning's wake time. The heading
     * promises that the alarm sets the end. Now it does. The one thing the old
     * bound gave for free is gone with it: an alarm at 2pm on that same day
     * extends the night to 2pm. It is drawn on the dial the evening before and
     * one drag undoes it, which is the trade DECISIONS records.
     *
     * Applied HERE, where a window's end is decided, rather than when the END
     * alarm is armed. Shortening only the alarm would end the night and then
     * walk straight back into it: the window would still contain `now`, so the
     * next reschedule would re-enter it and switch zen back on. Ending the
     * WINDOW early is what makes the night actually over - together with
     * [Prefs.endedAt], for the moment the alarm itself has moved on to
     * tomorrow; see [over].
     */
    fun endAt(
        began: LocalDateTime,
        scheduledEnd: LocalDateTime,
        alarm: LocalDateTime?,
        exitAtAlarm: Boolean
    ): LocalDateTime =
        if (exitAtAlarm && alarm != null && alarm.isAfter(began) &&
            (alarm.isBefore(scheduledEnd) || alarm.toLocalDate() == scheduledEnd.toLocalDate())
        ) alarm else scheduledEnd

    /**
     * The next alarm, but only where it is allowed to end the night.
     *
     * `if (exitAtAlarm) nextAlarm(ctx) else null` was written out at six call
     * sites across five files, which is six chances to forget the gate - and
     * forgetting to pass the alarm at all is exactly the bug that reached the
     * phone twice, once in the dial and once in the sentence beneath it. One
     * name for the rule, so a seventh reader asks for it rather than rebuilding
     * it.
     */
    fun endingAlarm(ctx: Context, exitAtAlarm: Boolean): LocalDateTime? =
        if (exitAtAlarm) nextAlarm(ctx) else null

    /**
     * The next alarm clock the USER can see - the one that puts the icon in the
     * status bar. Any app that wants that icon must use `setAlarmClock`, which
     * is the same thing this reads, so it is not tied to a particular clock app
     * or vendor: measured on Honor's own deskclock and on Samsung's.
     *
     * It is also the ONLY alarm the platform will name. There is no list and
     * no label - `getNextAlarmClock` is the whole API - so "which alarm" is
     * answered the way the lock screen answers it: the next one to ring.
     */
    fun nextAlarm(ctx: Context): LocalDateTime? =
        am(ctx).nextAlarmClock?.triggerTime?.let {
            LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneId.systemDefault())
        }

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

    /** A window as the instant it began and the instant it ends. */
    data class Window(val began: LocalDateTime, val ends: LocalDateTime)

    /**
     * Is this night already OVER - ended by an END that fired for it?
     *
     * [endedAt] is the instant the last END was DUE ([Prefs.endedAt]). A window
     * that had begun by then is the night that END ended, whatever the handles
     * or the alarm read now, and it stays over until a window that begins
     * later - tonight's, or the next test window ten minutes on.
     *
     * The case this exists for: the END fires at the alarm, 06:30, and in the
     * same second the clock app moves its "next alarm" to tomorrow. Judged
     * from the handles alone the night now ends at 08:30, contains 06:30, and
     * the reschedule walks straight back in - the very re-entry [endAt] was
     * moved into the window calculation to prevent, arriving by another door.
     * `SchedulerTest` used to pass the rung alarm at 07:31, which is what the
     * phone will not do. A snoozed alarm is the same door ten minutes later.
     *
     * Keyed on the instant the night BEGAN, not its date: two test windows on
     * one afternoon are two nights, and the second must be allowed to start.
     * And on the END's due instant, not its landing: an END parked overnight
     * and released at 23:00 by opening the app would otherwise end tonight's
     * night, which began at 22:30.
     */
    private fun over(began: LocalDateTime, endedAt: LocalDateTime?) =
        endedAt != null && !began.isAfter(endedAt)

    /** Like [currentWindow] but with every day eligible - the one-off case. */
    private fun windowAnyDay(
        start: LocalTime,
        end: LocalTime,
        from: LocalDateTime,
        alarm: LocalDateTime?,
        exitAtAlarm: Boolean,
        endedAt: LocalDateTime?
    ): Window? {
        val dur = duration(start, end)
        if (dur.isZero) return null
        for (back in 0L..1L) {
            val began = LocalDateTime.of(from.toLocalDate().minusDays(back), start)
            if (over(began, endedAt)) continue
            val ends = endAt(began, began.plus(dur), alarm, exitAtAlarm)
            if (!from.isBefore(began) && from.isBefore(ends)) return Window(began, ends)
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
     * If [from] currently falls inside a window, that window. Null when we are
     * outside any window.
     */
    private fun currentWindow(
        start: LocalTime,
        end: LocalTime,
        days: Set<DayOfWeek>,
        from: LocalDateTime,
        alarm: LocalDateTime?,
        exitAtAlarm: Boolean,
        endedAt: LocalDateTime?
    ): Window? {
        val dur = duration(start, end)
        if (dur.isZero || days.isEmpty()) return null
        // A window can only have begun today or yesterday: the schedule is at
        // most 24h, and an alarm can extend it only to the end of the day the
        // schedule ends on.
        for (back in 0L..1L) {
            val day = from.toLocalDate().minusDays(back)
            val began = LocalDateTime.of(day, start)
            if (over(began, endedAt)) continue
            val scheduled = began.plus(dur)
            // Matched on the morning it is SCHEDULED to end, not the evening it
            // starts - and not the alarm's own instant, which for an alarm
            // before midnight inside a late window is the evening's date.
            if (scheduled.toLocalDate().dayOfWeek !in days) continue
            val ends = endAt(began, scheduled, alarm, exitAtAlarm)
            if (!from.isBefore(began) && from.isBefore(ends)) return Window(began, ends)
        }
        return null
    }

    /** When the window [from] falls inside ends; null outside any window. */
    fun currentWindowEnd(
        start: LocalTime,
        end: LocalTime,
        days: Set<DayOfWeek>,
        from: LocalDateTime = LocalDateTime.now(),
        alarm: LocalDateTime? = null,
        exitAtAlarm: Boolean = false,
        endedAt: LocalDateTime? = null
    ): LocalDateTime? = currentWindow(start, end, days, from, alarm, exitAtAlarm, endedAt)?.ends

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
    fun liveWindow(
        enabled: Boolean,
        activeDay: Long,
        start: LocalTime,
        end: LocalTime,
        days: Set<DayOfWeek>,
        from: LocalDateTime = LocalDateTime.now(),
        alarm: LocalDateTime? = null,
        exitAtAlarm: Boolean = false,
        endedAt: LocalDateTime? = null
    ): Window? {
        if (enabled && activeDay != Prefs.NO_DAY) {
            // The pinned day, described by whatever the handles say now.
            val began = LocalDateTime.of(LocalDate.ofEpochDay(activeDay), start)
            if (over(began, endedAt)) return null
            val ends = endAt(began, began.plus(duration(start, end)), alarm, exitAtAlarm)
            // Null once it is over or not yet begun: the caller clears the pin
            // rather than falling through to a fresh window.
            return if (!from.isBefore(began) && from.isBefore(ends)) Window(began, ends) else null
        }
        // A one-off has no eligible days to match, so it is only "running" once
        // the switch is on; otherwise the dial would show a phantom window on
        // every day of the week.
        return if (isOneOff(days)) {
            if (enabled) windowAnyDay(start, end, from, alarm, exitAtAlarm, endedAt) else null
        } else currentWindow(start, end, days, from, alarm, exitAtAlarm, endedAt)
    }

    fun liveWindowEnd(
        enabled: Boolean,
        activeDay: Long,
        start: LocalTime,
        end: LocalTime,
        days: Set<DayOfWeek>,
        from: LocalDateTime = LocalDateTime.now(),
        alarm: LocalDateTime? = null,
        exitAtAlarm: Boolean = false,
        endedAt: LocalDateTime? = null
    ): LocalDateTime? =
        liveWindow(enabled, activeDay, start, end, days, from, alarm, exitAtAlarm, endedAt)?.ends

    fun liveWindow(
        p: Prefs,
        start: LocalTime,
        end: LocalTime,
        days: Set<DayOfWeek>,
        from: LocalDateTime = LocalDateTime.now(),
        alarm: LocalDateTime? = null
    ): Window? = liveWindow(
        p.enabled, p.activeDay, start, end, days, from, alarm, p.exitAtAlarm, endedAt(p)
    )

    fun liveWindowEnd(
        p: Prefs,
        start: LocalTime,
        end: LocalTime,
        days: Set<DayOfWeek>,
        from: LocalDateTime = LocalDateTime.now(),
        alarm: LocalDateTime? = null
    ): LocalDateTime? = liveWindow(p, start, end, days, from, alarm)?.ends

    /** [Prefs.endedAt] as a time, or null while no END has ever fired. */
    fun endedAt(p: Prefs): LocalDateTime? =
        p.endedAt.takeIf { it != Prefs.NO_DUE }?.let {
            LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneId.systemDefault())
        }

    fun isActiveNow(
        enabled: Boolean,
        activeDay: Long,
        start: LocalTime,
        end: LocalTime,
        days: Set<DayOfWeek>,
        from: LocalDateTime = LocalDateTime.now(),
        endedAt: LocalDateTime? = null
    ): Boolean = enabled &&
        liveWindowEnd(enabled, activeDay, start, end, days, from, endedAt = endedAt) != null

    fun isActiveNow(
        p: Prefs,
        from: LocalDateTime = LocalDateTime.now(),
        alarm: LocalDateTime? = null
    ): Boolean =
        p.enabled &&
            liveWindowEnd(p, p.startTime, p.endTime, p.days, from, alarm) != null

    // Extras play no part in matching a PendingIntent, so the one cancelAll
    // builds without a due instant still cancels the one setExact armed with it.
    private fun pending(ctx: Context, action: String, code: Int, due: Long? = null): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, code,
            Intent(ctx, BedtimeReceiver::class.java).setAction(action)
                .apply { if (due != null) putExtra(EXTRA_DUE, due) },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun setExact(ctx: Context, at: LocalDateTime, action: String, code: Int) {
        val ms = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        // Only END is watched. A missed START is visible - bedtime simply does
        // not begin - while a missed END leaves the phone silent all morning and
        // says nothing, which is the failure worth catching.
        if (action == ACTION_END) AlarmWatch.arming(Prefs(ctx), ms)
        try {
            am(ctx).setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pending(ctx, action, code, ms))
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
    /**
     * Arm the background probe if this phone has never answered.
     *
     * Deliberately NOT cancelled by [cancelAll]: it is not part of the window,
     * it survives the app being switched off, and it must be allowed to run to
     * its own conclusion once - otherwise turning bedtime off and on would keep
     * restarting the question and it would never get answered.
     */
    fun armProbe(ctx: Context, p: Prefs, retest: Boolean = false) {
        // Latch first. Arming rewrites probeDue, which IS the evidence that the
        // outstanding probe was missed - read the verdict before destroying it.
        BackgroundProbe.check(p)
        if (!retest && !BackgroundProbe.needsArming(p)) return
        val at = System.currentTimeMillis() + BackgroundProbe.DELAY_MS
        try {
            am(ctx).setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, at, pending(ctx, ACTION_PROBE, 102)
            )
            // Recorded only once the alarm EXISTS. The other way round, a
            // refused permission leaves a due instant with nothing behind it,
            // and the probe then reports the phone holding an alarm that was
            // never scheduled - an accusation manufactured out of our own
            // failure to ask. Nothing scheduled means nothing measured.
            BackgroundProbe.arming(p, at)
        } catch (e: SecurityException) {
            Journal.write(ctx, "probe not armed: " + e)
        }
    }

    /**
     * [from] is the instant the schedule is judged at - now, for everyone but
     * the receiver, which passes the alarm's own due instant when the alarm
     * landed a little before it. See [Delivery.asOf]: judged at the moment it
     * landed, a forty-second-early END found the night still running and
     * walked straight back into it.
     */
    fun rescheduleAll(
        ctx: Context,
        p: Prefs,
        force: Boolean = false,
        from: LocalDateTime = LocalDateTime.now()
    ) {
        // BEFORE anything is armed: arming overwrites the due instant, so this is
        // the last moment the previous one can still be judged. Logged as what
        // it answered: this line is the only trace of an END that never came.
        if (AlarmWatch.check(p)) AlarmWatch.report(p)?.let {
            Journal.write(
                ctx,
                "END never arrived - ended here " + (it.endedAt - it.due) / 1000 + "s late" +
                    (if (it.atOpen) ", app on screen" else "")
            )
        }
        cancelAll(ctx)

        // NO ALARM ON THE PHONE, NO RULE. The switch means "linked to an
        // alarm", and with nothing to link to it goes off - here, because every
        // path that learns the alarm has gone passes through here: the clock
        // app's broadcast, a resume, a boot. BEFORE the bedtime-off return
        // below: the rule is about the alarm, not about bedtime, and placed
        // after it the switch sat on-but-disabled whenever bedtime was off,
        // which is the look the owner rejected. His decision, taken after
        // seeing the standing version, which sat ON over "No alarm set" and
        // read as on-but-doing-nothing. The cost is a one-time alarm: rung, it
        // reads as no alarm, and the rule is off the next evening until it is
        // switched on again. Nothing switches it on by itself.
        if (p.exitAtAlarm && nextAlarm(ctx) == null) {
            p.exitAtAlarm = false
            Journal.write(ctx, "no alarm on the phone - end at alarm switched off")
        }

        if (!p.enabled) {
            p.activeDay = Prefs.NO_DAY
            // Nothing armed, so nothing can be owed - otherwise the END we just
            // cancelled would come due and read as eaten.
            AlarmWatch.clear(p)
            ZenController.setActive(ctx, p, false, force)
            logPlan(ctx, p, "off")
            return
        }

        val now = from
        // Read once and used for both branches, so the window we open and the
        // END we arm cannot disagree about when the morning is.
        val alarm = if (p.exitAtAlarm) nextAlarm(ctx) else null
        val open = liveWindow(p, p.startTime, p.endTime, p.days, now, alarm)

        if (open != null) {
            // We are already inside a window: switch on now, end at the right time,
            // and queue the following night's start.
            //
            // Pinned to the night the window BEGAN on, which the window itself
            // now says. It used to be worked back from the end - end minus the
            // scheduled duration - which was right only while the alarm could
            // shorten a night: an alarm that EXTENDS one to the afternoon puts
            // "end minus duration" on the wrong date, and a pin on the wrong
            // date never sticks.
            if (p.activeDay == Prefs.NO_DAY) {
                p.activeDay = open.began.toLocalDate().toEpochDay()
            }
            ZenController.setActive(ctx, p, true, force)
            setExact(ctx, open.ends, ACTION_END, 101)
            // A one-off queues no following night; END switches the app off.
            if (!isOneOff(p.days)) {
                nextOccurrence(p.startTime, p.endTime, p.days, now)
                    ?.let { setExact(ctx, it, ACTION_START, 100) }
            }
            logPlan(ctx, p, "running until " + open.ends +
                (if (isOneOff(p.days)) " (once)" else ""))
        } else {
            p.activeDay = Prefs.NO_DAY
            ZenController.setActive(ctx, p, false, force)
            val start = nextStart(p.startTime, p.endTime, p.days, now)
            if (start == null) {
                AlarmWatch.clear(p)
                logPlan(ctx, p, "nothing to schedule")
                return
            }
            val ends = endAt(
                start, start.plus(duration(p.startTime, p.endTime)), alarm, p.exitAtAlarm
            )
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
