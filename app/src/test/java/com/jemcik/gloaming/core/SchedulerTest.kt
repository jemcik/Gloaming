package com.jemcik.gloaming.core

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The scheduling core, on the JVM.
 *
 * Every case below is a fact this app got wrong at least once. They are written
 * as the QUESTION the code answers - "if I deselect today while tonight is
 * already running, does tonight survive?" - rather than as coverage of a method,
 * because the bugs were never in a method, they were in an assumption.
 *
 * Fixed calendar, so the day names are checkable by eye:
 *     2026-08-28  Friday
 *     2026-08-29  Saturday
 *     2026-08-30  Sunday
 */
class SchedulerTest {

    private val fri = LocalDate.of(2026, 8, 28)
    private val sat = LocalDate.of(2026, 8, 29)
    private val sun = LocalDate.of(2026, 8, 30)

    private val bedtime: LocalTime = LocalTime.of(22, 30)
    private val wake: LocalTime = LocalTime.of(8, 0)

    private fun at(d: LocalDate, h: Int, m: Int = 0) = LocalDateTime.of(d, LocalTime.of(h, m))

    @Test
    fun `the calendar these tests assume is the real one`() {
        assertEquals(DayOfWeek.FRIDAY, fri.dayOfWeek)
        assertEquals(DayOfWeek.SATURDAY, sat.dayOfWeek)
        assertEquals(DayOfWeek.SUNDAY, sun.dayOfWeek)
    }

    // ---------- duration: a window is a span, never two loose times ----------

    @Test
    fun `a window that crosses midnight is measured forwards, not backwards`() {
        assertEquals(Duration.ofMinutes(9 * 60 + 30), Scheduler.duration(bedtime, wake))
    }

    @Test
    fun `a window inside one day is just the difference`() {
        assertEquals(Duration.ofHours(1), Scheduler.duration(LocalTime.of(22, 0), LocalTime.of(23, 0)))
    }

    @Test
    fun `equal handles mean no window at all, not a full day`() {
        assertEquals(Duration.ZERO, Scheduler.duration(bedtime, bedtime))
    }

    // ---------- the day chips are MORNINGS ----------
    // Asking for the weekend used to light up Friday and Saturday, because the
    // days meant the evening a window STARTS on. They mean the morning it ends.

    @Test
    fun `choosing Saturday starts the window on Friday evening`() {
        val next = Scheduler.nextOccurrence(bedtime, wake, setOf(DayOfWeek.SATURDAY), at(fri, 12))
        assertEquals(at(fri, 22, 30), next)
    }

    @Test
    fun `choosing the weekend means Saturday and Sunday mornings`() {
        val days = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
        assertEquals(at(fri, 22, 30), Scheduler.nextOccurrence(bedtime, wake, days, at(fri, 12)))
        assertEquals(at(sat, 22, 30), Scheduler.nextOccurrence(bedtime, wake, days, at(sat, 12)))
    }

    @Test
    fun `a window that does not cross midnight starts on the chosen day itself`() {
        val next = Scheduler.nextOccurrence(
            LocalTime.of(13, 0), LocalTime.of(14, 0), setOf(DayOfWeek.SATURDAY), at(fri, 12)
        )
        assertEquals(at(sat, 13, 0), next)
    }

    @Test
    fun `no days chosen has no next occurrence`() {
        assertNull(Scheduler.nextOccurrence(bedtime, wake, emptySet(), at(fri, 12)))
    }

    // ---------- currentWindowEnd ----------

    @Test
    fun `inside a wrapped window, the end is this morning`() {
        val end = Scheduler.currentWindowEnd(bedtime, wake, setOf(DayOfWeek.SATURDAY), at(sat, 2))
        assertEquals(at(sat, 8, 0), end)
    }

    @Test
    fun `outside every window there is no end`() {
        assertNull(Scheduler.currentWindowEnd(bedtime, wake, setOf(DayOfWeek.SATURDAY), at(sat, 12)))
    }

    @Test
    fun `a window whose morning was not chosen is not running`() {
        assertNull(Scheduler.currentWindowEnd(bedtime, wake, setOf(DayOfWeek.MONDAY), at(sat, 2)))
    }

    // ---------- the activeDay pin ----------
    // Editing the schedule mid-window used to drop Do Not Disturb on the spot.
    // Two earlier fixes pinned an instant and each froze one of the handles.

    private val pinnedToFriday = fri.toEpochDay()

    @Test
    fun `deselecting today does not cut tonight short`() {
        val end = Scheduler.liveWindowEnd(
            enabled = true, activeDay = pinnedToFriday,
            start = bedtime, end = wake, days = emptySet(), from = at(sat, 2)
        )
        assertEquals(at(sat, 8, 0), end)
    }

    @Test
    fun `dragging the wake handle mid-window still moves the end`() {
        val end = Scheduler.liveWindowEnd(
            enabled = true, activeDay = pinnedToFriday,
            start = bedtime, end = LocalTime.of(9, 30),
            days = setOf(DayOfWeek.SATURDAY), from = at(sat, 2)
        )
        assertEquals(at(sat, 9, 30), end)
    }

    @Test
    fun `dragging the bedtime handle mid-window still moves the start`() {
        // Pushing bedtime later shortens the night from the front: the window
        // now begins at 23:30 and still ends at 08:00.
        val end = Scheduler.liveWindowEnd(
            enabled = true, activeDay = pinnedToFriday,
            start = LocalTime.of(23, 30), end = wake,
            days = setOf(DayOfWeek.SATURDAY), from = at(sat, 2)
        )
        assertEquals(at(sat, 8, 0), end)
    }

    @Test
    fun `once the pinned window is over it stops being live`() {
        val end = Scheduler.liveWindowEnd(
            enabled = true, activeDay = pinnedToFriday,
            start = bedtime, end = wake, days = setOf(DayOfWeek.SATURDAY), from = at(sat, 9)
        )
        assertNull(end)
    }

    @Test
    fun `switched off, a repeating window is still DESCRIBED`() {
        // Deliberately not null. The dial draws its marker on insideWindow, not
        // on runningNow, so that an unarmed schedule still shows where you would
        // be - the marker goes hollow rather than vanishing. The switch is
        // applied by the caller: runningNow = enabled && insideWindow.
        val end = Scheduler.liveWindowEnd(
            enabled = false, activeDay = pinnedToFriday,
            start = bedtime, end = wake, days = setOf(DayOfWeek.SATURDAY), from = at(sat, 2)
        )
        assertEquals(at(sat, 8, 0), end)
    }

    @Test
    fun `switched off, nothing is active however the window falls`() {
        assertFalse(
            Scheduler.isActiveNow(
                false, pinnedToFriday, bedtime, wake, setOf(DayOfWeek.SATURDAY), at(sat, 2)
            )
        )
    }

    // ---------- the one-off ----------
    // No days chosen runs the window ONCE, the way an alarm clock does.

    @Test
    fun `no days chosen is a one-off`() {
        assertTrue(Scheduler.isOneOff(emptySet()))
        assertFalse(Scheduler.isOneOff(setOf(DayOfWeek.MONDAY)))
    }

    @Test
    fun `a one-off runs tonight whatever day it is`() {
        val end = Scheduler.liveWindowEnd(
            enabled = true, activeDay = Prefs.NO_DAY,
            start = bedtime, end = wake, days = emptySet(), from = at(sat, 2)
        )
        assertEquals(at(sat, 8, 0), end)
    }

    @Test
    fun `a one-off draws no window while the app is off`() {
        // Otherwise the dial shows a phantom window on every day of the week.
        val end = Scheduler.liveWindowEnd(
            enabled = false, activeDay = Prefs.NO_DAY,
            start = bedtime, end = wake, days = emptySet(), from = at(sat, 2)
        )
        assertNull(end)
    }

    @Test
    fun `a one-off starts tonight if tonight has not begun, else tomorrow`() {
        assertEquals(at(sat, 22, 30), Scheduler.nextStart(bedtime, wake, emptySet(), at(sat, 12)))
        assertEquals(at(sun, 22, 30), Scheduler.nextStart(bedtime, wake, emptySet(), at(sat, 23)))
    }

    // ---------- isActiveNow ----------

    @Test
    fun `isActiveNow agrees with liveWindowEnd`() {
        assertTrue(
            Scheduler.isActiveNow(true, pinnedToFriday, bedtime, wake, emptySet(), at(sat, 2))
        )
        assertFalse(
            Scheduler.isActiveNow(true, pinnedToFriday, bedtime, wake, emptySet(), at(sat, 9))
        )
    }

    // ---------- ending at the next alarm ----------
    //
    // The alarm SETS the end, either way, when it is this night's: it rings
    // after the night began, and either inside the window or later on the
    // same calendar day the scheduled end falls on. It was AOSP's shorten-only
    // rule - ScheduleCalendar.shouldExitForAlarm - until 11 Sep 2026, and
    // DECISIONS has the report that changed it: "End bedtime at your alarm,
    // 09:00" sitting ON over a night that ended at 08:30.

    private val alarmBed = LocalTime.of(22, 30)
    private val alarmWake = LocalTime.of(8, 55)
    private val everyNight = DayOfWeek.entries.toSet()

    /** The window that begins on Friday 2026-08-28 at 22:30 and ends 08:55. */
    private fun began() = LocalDateTime.of(2026, 8, 28, 22, 30)
    private fun scheduledEnd() = LocalDateTime.of(2026, 8, 29, 8, 55)

    @Test
    fun `an alarm inside the window brings the end forward to it`() {
        val alarm = LocalDateTime.of(2026, 8, 29, 7, 30)
        assertEquals(
            alarm,
            Scheduler.endAt(began(), scheduledEnd(), alarm, exitAtAlarm = true)
        )
    }

    @Test
    fun `a later alarm on the same morning extends the night to it`() {
        // The tester's case: a 09:00 weekend alarm over an 08:30 handle. The
        // heading promises the alarm sets the end, and the night used to end
        // at the handle while the row went on naming the alarm.
        val alarm = LocalDateTime.of(2026, 8, 29, 9, 30)
        assertEquals(
            alarm,
            Scheduler.endAt(began(), scheduledEnd(), alarm, exitAtAlarm = true)
        )
    }

    @Test
    fun `an alarm in the afternoon of that day extends it too - the trade the rule makes`() {
        // 2pm on the morning's own date. AOSP's inside-the-window bound
        // ignored it for free; this rule honours it, draws it on the dial the
        // evening before, and one drag undoes it. Pinned so the choice stays
        // visible rather than accidental.
        val alarm = LocalDateTime.of(2026, 8, 29, 14, 0)
        assertEquals(
            alarm,
            Scheduler.endAt(began(), scheduledEnd(), alarm, exitAtAlarm = true)
        )
    }

    @Test
    fun `an alarm past the next day's start is not this night's, whatever its date`() {
        // 23:00 on Saturday is on the morning's date, and honouring it would
        // run Friday's night straight through Saturday's - and the END that
        // closed it would close Saturday's night too, as begun before it. The
        // one bound that is not an arbitrary number: before the next day's
        // start.
        val alarm = LocalDateTime.of(2026, 8, 29, 23, 0)
        assertEquals(
            scheduledEnd(),
            Scheduler.endAt(began(), scheduledEnd(), alarm, exitAtAlarm = true)
        )
        // Right up to it, still this night's.
        val late = LocalDateTime.of(2026, 8, 29, 22, 29)
        assertEquals(late, Scheduler.endAt(began(), scheduledEnd(), late, exitAtAlarm = true))
    }

    @Test
    fun `an alarm on another morning is not this night's`() {
        // Monday's alarm, seen from Friday night - the weekday alarm once
        // Friday's has rung. The handle stands.
        val alarm = LocalDateTime.of(2026, 8, 31, 6, 30)
        assertEquals(
            scheduledEnd(),
            Scheduler.endAt(began(), scheduledEnd(), alarm, exitAtAlarm = true)
        )
    }

    @Test
    fun `an alarm before bedtime starts is not this night's alarm`() {
        val alarm = LocalDateTime.of(2026, 8, 28, 7, 30)
        assertEquals(
            scheduledEnd(),
            Scheduler.endAt(began(), scheduledEnd(), alarm, exitAtAlarm = true)
        )
    }

    @Test
    fun `an alarm before midnight inside a late window still counts`() {
        // On the EVENING's date, so "the same day as the scheduled end" alone
        // would miss it. Inside the window is kept as the first half of the
        // rule for exactly this.
        val alarm = LocalDateTime.of(2026, 8, 28, 23, 45)
        assertEquals(
            alarm,
            Scheduler.endAt(began(), scheduledEnd(), alarm, exitAtAlarm = true)
        )
    }

    @Test
    fun `no alarm leaves the configured end alone`() {
        assertEquals(
            scheduledEnd(),
            Scheduler.endAt(began(), scheduledEnd(), null, exitAtAlarm = true)
        )
    }

    @Test
    fun `switched off, an alarm inside the window changes nothing`() {
        val alarm = LocalDateTime.of(2026, 8, 29, 7, 30)
        assertEquals(
            scheduledEnd(),
            Scheduler.endAt(began(), scheduledEnd(), alarm, exitAtAlarm = false)
        )
    }

    @Test
    fun `switched off, a later alarm changes nothing either`() {
        val alarm = LocalDateTime.of(2026, 8, 29, 9, 30)
        assertEquals(
            scheduledEnd(),
            Scheduler.endAt(began(), scheduledEnd(), alarm, exitAtAlarm = false)
        )
    }

    @Test
    fun `before the alarm the night is still running, and ends at the alarm`() {
        val alarm = LocalDateTime.of(2026, 8, 29, 7, 30)
        val justBefore = LocalDateTime.of(2026, 8, 29, 7, 29)
        assertEquals(
            alarm,
            Scheduler.liveWindowEnd(
                enabled = true, activeDay = LocalDate.of(2026, 8, 28).toEpochDay(),
                start = alarmBed, end = alarmWake, days = everyNight, from = justBefore,
                alarm = alarm, exitAtAlarm = true
            )
        )
    }

    @Test
    fun `past the handle but before a later alarm, the night is still running`() {
        // 09:00 under an 08:55 handle and a 09:30 alarm: asked without the
        // alarm this is "over", and two readers were asking without it.
        val alarm = LocalDateTime.of(2026, 8, 29, 9, 30)
        val pastHandle = LocalDateTime.of(2026, 8, 29, 9, 0)
        assertEquals(
            alarm,
            Scheduler.liveWindowEnd(
                enabled = true, activeDay = Prefs.NO_DAY,
                start = alarmBed, end = alarmWake, days = everyNight, from = pastHandle,
                alarm = alarm, exitAtAlarm = true
            )
        )
    }

    @Test
    fun `an extended night still BEGAN on its own evening`() {
        // The pin is the date the night began on. Worked back from the end -
        // end minus duration - a night extended to 14:00 landed on Saturday,
        // for a window that began on Friday, and a pin on the wrong date never
        // sticks. liveWindow says where it began directly.
        val alarm = LocalDateTime.of(2026, 8, 29, 14, 0)
        val w = Scheduler.liveWindow(
            enabled = true, activeDay = Prefs.NO_DAY,
            start = alarmBed, end = alarmWake, days = everyNight,
            from = LocalDateTime.of(2026, 8, 29, 10, 0),
            alarm = alarm, exitAtAlarm = true
        )
        assertEquals(began(), w?.began)
        assertEquals(alarm, w?.ends)
    }

    @Test
    fun `a night is matched on the morning it is SCHEDULED to end on, not the alarm's date`() {
        // Saturday mornings only, and an alarm before midnight on Friday: the
        // night ending Saturday must be found, and end at that alarm. Matched
        // on the alarm's own date it was Friday's night, and skipped.
        val alarm = LocalDateTime.of(2026, 8, 28, 23, 45)
        assertEquals(
            alarm,
            Scheduler.liveWindowEnd(
                enabled = true, activeDay = Prefs.NO_DAY,
                start = alarmBed, end = alarmWake, days = setOf(DayOfWeek.SATURDAY),
                from = LocalDateTime.of(2026, 8, 28, 23, 0),
                alarm = alarm, exitAtAlarm = true
            )
        )
    }

    @Test
    fun `with the setting off the same night runs to its configured end`() {
        val alarm = LocalDateTime.of(2026, 8, 29, 7, 30)
        val justAfter = LocalDateTime.of(2026, 8, 29, 7, 31)
        assertEquals(
            scheduledEnd(),
            Scheduler.liveWindowEnd(
                enabled = true, activeDay = LocalDate.of(2026, 8, 28).toEpochDay(),
                start = alarmBed, end = alarmWake, days = everyNight, from = justAfter,
                alarm = alarm, exitAtAlarm = false
            )
        )
    }

    // ---------- a night that has ended stays ended ----------
    //
    // The END fires at the alarm, and in the same second the clock app moves
    // "next alarm" to tomorrow. Judged from the handles alone the night ends
    // at 08:55 again, contains 07:30, and the reschedule walks straight back
    // in. This block used to pass the RUNG alarm at 07:31 - which the phone
    // never will - and read that as "the night is over". Prefs.endedAt is the
    // END's due instant; a window that had begun by then is over.

    @Test
    fun `after the END the night is OVER, even though the next alarm now reads tomorrow`() {
        val rang = LocalDateTime.of(2026, 8, 29, 7, 30)
        assertNull(
            "the END closed this night; tomorrow's alarm must not reopen it",
            Scheduler.liveWindowEnd(
                enabled = true, activeDay = Prefs.NO_DAY,
                start = alarmBed, end = alarmWake, days = everyNight,
                from = rang.plusSeconds(1),
                alarm = rang.plusDays(1), exitAtAlarm = true, endedAt = rang
            )
        )
    }

    @Test
    fun `without the record the same second re-enters the night - the bug the record is for`() {
        // Kept so the guard cannot be removed by accident: this is the state
        // the phone is actually in one second after an alarm-ended END.
        val rang = LocalDateTime.of(2026, 8, 29, 7, 30)
        assertEquals(
            scheduledEnd(),
            Scheduler.liveWindowEnd(
                enabled = true, activeDay = Prefs.NO_DAY,
                start = alarmBed, end = alarmWake, days = everyNight,
                from = rang.plusSeconds(1),
                alarm = rang.plusDays(1), exitAtAlarm = true, endedAt = null
            )
        )
    }

    @Test
    fun `a snoozed alarm does not reopen a night the END has closed`() {
        val rang = LocalDateTime.of(2026, 8, 29, 7, 30)
        assertNull(
            Scheduler.liveWindowEnd(
                enabled = true, activeDay = Prefs.NO_DAY,
                start = alarmBed, end = alarmWake, days = everyNight,
                from = rang.plusSeconds(1),
                alarm = rang.plusMinutes(10), exitAtAlarm = true, endedAt = rang
            )
        )
    }

    @Test
    fun `the pinned night is over past its END as well`() {
        val rang = LocalDateTime.of(2026, 8, 29, 7, 30)
        assertNull(
            Scheduler.liveWindowEnd(
                enabled = true, activeDay = LocalDate.of(2026, 8, 28).toEpochDay(),
                start = alarmBed, end = alarmWake, days = everyNight,
                from = rang.plusSeconds(1),
                alarm = rang.plusDays(1), exitAtAlarm = true, endedAt = rang
            )
        )
    }

    @Test
    fun `a window that begins after the END is a new night`() {
        // Two test windows on one afternoon, from the phone's own journal on
        // 11 Sep 2026: running until 13:05, then running until 13:30. Keyed
        // on the instant the night began, not its date, or the second could
        // never start.
        val endedAt = LocalDateTime.of(2026, 9, 11, 13, 5)
        assertEquals(
            LocalDateTime.of(2026, 9, 11, 13, 30),
            Scheduler.liveWindowEnd(
                enabled = true, activeDay = Prefs.NO_DAY,
                start = LocalTime.of(13, 12), end = LocalTime.of(13, 30), days = everyNight,
                from = LocalDateTime.of(2026, 9, 11, 13, 15),
                endedAt = endedAt
            )
        )
    }

    @Test
    fun `a window that begins the instant the last one ended is a new night`() {
        // A test window set as 13:05-13:30 after an END at 13:05. Strict, or
        // it would wait until tomorrow.
        val endedAt = LocalDateTime.of(2026, 9, 11, 13, 5)
        assertEquals(
            LocalDateTime.of(2026, 9, 11, 13, 30),
            Scheduler.liveWindowEnd(
                enabled = true, activeDay = Prefs.NO_DAY,
                start = LocalTime.of(13, 5), end = LocalTime.of(13, 30), days = everyNight,
                from = LocalDateTime.of(2026, 9, 11, 13, 8),
                endedAt = endedAt
            )
        )
    }

    @Test
    fun `keyed on the END's due instant, not its landing, or a parked END would end tonight`() {
        // Yesterday's END, due 08:55, parked by the phone and released at
        // 23:00 by opening the app - after tonight's window began at 22:30.
        val due = LocalDateTime.of(2026, 8, 29, 8, 55)
        val released = LocalDateTime.of(2026, 8, 29, 23, 0)
        val tonight = LocalDateTime.of(2026, 8, 30, 8, 55)
        assertEquals(
            "recorded by its due instant, tonight runs",
            tonight,
            Scheduler.liveWindowEnd(
                enabled = true, activeDay = Prefs.NO_DAY,
                start = alarmBed, end = alarmWake, days = everyNight,
                from = released.plusSeconds(1), endedAt = due
            )
        )
        assertNull(
            "recorded by its landing, tonight would be over before it began - " +
                "which is why the receiver writes the due instant",
            Scheduler.liveWindowEnd(
                enabled = true, activeDay = Prefs.NO_DAY,
                start = alarmBed, end = alarmWake, days = everyNight,
                from = released.plusSeconds(1), endedAt = released
            )
        )
    }
}
