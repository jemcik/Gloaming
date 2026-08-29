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
}
