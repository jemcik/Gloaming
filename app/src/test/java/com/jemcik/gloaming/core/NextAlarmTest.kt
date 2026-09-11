package com.jemcik.gloaming.core

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager

/**
 * The next alarm through the REAL path - `rescheduleAll` reading
 * `getNextAlarmClock`, arming the END, pinning the night - and the receiver
 * recording the night as over. `SchedulerTest` pins the rule on plain values;
 * this pins that the phone-facing code asks it the right question.
 *
 *     2026-09-11  Friday
 *     2026-09-12  Saturday
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NextAlarmTest {

    private val zone: ZoneId = ZoneId.systemDefault()
    private fun ctx(): Context = ApplicationProvider.getApplicationContext()
    private fun am() = ctx().getSystemService(AlarmManager::class.java)
    private fun ms(t: LocalDateTime) = t.atZone(zone).toInstant().toEpochMilli()
    private fun at(ms: Long): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), zone)

    /** 22:30 to 08:30, every night, following the alarm. */
    private fun prefs(): Prefs = Prefs(ctx()).apply {
        enabled = true
        startTime = LocalTime.of(22, 30)
        endTime = LocalTime.of(8, 30)
        days = DayOfWeek.entries.toSet()
        exitAtAlarm = true
    }

    private fun setAlarm(t: LocalDateTime) {
        am().setAlarmClock(
            AlarmManager.AlarmClockInfo(ms(t), null),
            PendingIntent.getBroadcast(ctx(), 0, Intent("test.alarm"), PendingIntent.FLAG_IMMUTABLE)
        )
    }

    /** When our own alarm for [action] is armed, or null. */
    private fun armed(action: String): LocalDateTime? =
        shadowOf(am()).scheduledAlarms
            .firstOrNull { shadowOf(it.operation).savedIntent.action == action }
            ?.let { at(it.triggerAtTime) }

    private val friday = LocalDate.of(2026, 9, 11)

    @Test
    fun `a later alarm on the same morning arms the END at the alarm, pinned to the night it began on`() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        val p = prefs()
        val alarm = LocalDateTime.of(2026, 9, 12, 9, 0)
        setAlarm(alarm)
        // 08:45: past the handle, before the alarm. Still Friday's night.
        Scheduler.rescheduleAll(ctx(), p, from = LocalDateTime.of(2026, 9, 12, 8, 45))
        assertEquals(friday.toEpochDay(), p.activeDay)
        assertEquals(alarm, armed(Scheduler.ACTION_END))
    }

    @Test
    fun `an alarm in the afternoon pins the night to the evening it began on`() {
        // "End minus duration" put this pin on Saturday for a night that began
        // on Friday, and a pin on the wrong date never sticks.
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        val p = prefs()
        val alarm = LocalDateTime.of(2026, 9, 12, 14, 0)
        setAlarm(alarm)
        Scheduler.rescheduleAll(ctx(), p, from = LocalDateTime.of(2026, 9, 12, 9, 0))
        assertEquals(friday.toEpochDay(), p.activeDay)
        assertEquals(alarm, armed(Scheduler.ACTION_END))
    }

    @Test
    fun `the END at the alarm closes the night, and tomorrow's alarm does not reopen it`() {
        // The phone's actual sequence: the END due 06:30 fires; by then the
        // clock app has moved "next alarm" to Sunday; the reschedule judges at
        // 06:30 and, from the handles, the night runs to 08:30 and contains it.
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        val p = prefs()
        val rang = LocalDateTime.of(2026, 9, 12, 6, 30)
        // What the receiver does on END, in this order.
        p.activeDay = Prefs.NO_DAY
        p.endedAt = ms(rang)
        setAlarm(rang.plusDays(1))
        Scheduler.rescheduleAll(ctx(), p, from = rang)
        assertEquals("the night must not be re-entered", Prefs.NO_DAY, p.activeDay)
        assertEquals(LocalDateTime.of(2026, 9, 12, 22, 30), armed(Scheduler.ACTION_START))
        assertEquals("the next night ends at Sunday's alarm", rang.plusDays(1), armed(Scheduler.ACTION_END))
    }

    @Test
    fun `without the record the same instant walks back into the night`() {
        // The bug the record exists for, through the real path, so the guard
        // cannot be removed by accident.
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        val p = prefs()
        val rang = LocalDateTime.of(2026, 9, 12, 6, 30)
        p.activeDay = Prefs.NO_DAY
        p.endedAt = Prefs.NO_DUE
        setAlarm(rang.plusDays(1))
        Scheduler.rescheduleAll(ctx(), p, from = rang)
        assertTrue("re-entered: the pin is back", p.activeDay != Prefs.NO_DAY)
        assertEquals(LocalDateTime.of(2026, 9, 12, 8, 30), armed(Scheduler.ACTION_END))
    }

    @Test
    fun `a night that ended does not block the next test window the same afternoon`() {
        // From the phone's own journal, 11 Sep 2026: running until 13:05, then
        // running until 13:30.
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        val p = prefs().apply {
            startTime = LocalTime.of(13, 12); endTime = LocalTime.of(13, 30); exitAtAlarm = false
        }
        p.endedAt = ms(LocalDateTime.of(2026, 9, 11, 13, 5))
        Scheduler.rescheduleAll(ctx(), p, from = LocalDateTime.of(2026, 9, 11, 13, 15))
        assertEquals(friday.toEpochDay(), p.activeDay)
        assertEquals(LocalDateTime.of(2026, 9, 11, 13, 30), armed(Scheduler.ACTION_END))
    }

    @Test
    fun `no alarm switches the rule off even while bedtime itself is off`() {
        // The rule is about the alarm, not about bedtime. Placed after the
        // bedtime-off return the first time, so a phone with bedtime off kept
        // the switch on-but-disabled - the look this whole change removes.
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        val p = prefs().apply { enabled = false }
        Scheduler.rescheduleAll(ctx(), p, from = LocalDateTime.of(2026, 9, 11, 12, 0))
        assertEquals(false, p.exitAtAlarm)
    }

    @Test
    fun `the clock app's broadcast re-arms the END at the moved alarm`() {
        // NEXT_ALARM_CLOCK_CHANGED through the receiver: the alarm moves from
        // 09:00 to 08:00 while the app is closed, and the END follows.
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        val p = prefs()
        setAlarm(LocalDateTime.of(2026, 9, 12, 9, 0))
        Scheduler.rescheduleAll(ctx(), p, from = LocalDateTime.of(2026, 9, 11, 12, 0))
        assertEquals(LocalDateTime.of(2026, 9, 12, 9, 0), armed(Scheduler.ACTION_END))
        setAlarm(LocalDateTime.of(2026, 9, 12, 8, 0))
        BedtimeReceiver().onReceive(
            ctx(), Intent(android.app.AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED)
        )
        assertEquals(LocalDateTime.of(2026, 9, 12, 8, 0), armed(Scheduler.ACTION_END))
    }

    @Test
    fun `no alarm on the phone switches the rule off, and an alarm appearing does not switch it back`() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        val p = prefs()
        Scheduler.rescheduleAll(ctx(), p, from = LocalDateTime.of(2026, 9, 11, 12, 0))
        assertEquals("nothing to link to", false, p.exitAtAlarm)
        setAlarm(LocalDateTime.of(2026, 9, 12, 9, 0))
        Scheduler.rescheduleAll(ctx(), p, from = LocalDateTime.of(2026, 9, 11, 12, 1))
        assertEquals("off is off until the user says otherwise", false, p.exitAtAlarm)
    }

    @Test
    fun `the receiver records the END by its DUE instant, not its landing`() {
        // A parked END released at 23:00 by opening the app must not end the
        // night that began at 22:30 - see Scheduler.over.
        val p = prefs()
        val due = LocalDateTime.of(2026, 9, 12, 8, 30)
        AlarmWatch.arming(p, ms(due))
        BedtimeReceiver().onReceive(
            ctx(),
            Intent(ctx(), BedtimeReceiver::class.java)
                .setAction(Scheduler.ACTION_END)
                .putExtra(Scheduler.EXTRA_DUE, ms(due))
        )
        assertEquals(ms(due), p.endedAt)
        assertEquals("and the pin is dropped", Prefs.NO_DAY, p.activeDay)
    }
}
