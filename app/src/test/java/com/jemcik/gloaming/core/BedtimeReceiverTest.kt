package com.jemcik.gloaming.core

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager

/**
 * What each broadcast the receiver takes actually DOES - the START and the END
 * as the phone delivers them, the boot, the upgrade, the probe. `NextAlarmTest`
 * drives the END and the clock app's broadcast for the alarm rule; this file
 * covers the rest, and the one thing all of them share: the reschedule at the
 * foot is what asserts the zen state, once, after deciding what it should be.
 *
 * The receiver reads the wall clock, so every window here is built around
 * now rather than on a fixed calendar - to the second, because that is what
 * the prefs hold, and because a START armed for 22:30:00 and judged at
 * 22:29:59.6 is the case a whole night turned on.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BedtimeReceiverTest {

    private val zone: ZoneId = ZoneId.systemDefault()
    private fun ctx(): Context = ApplicationProvider.getApplicationContext()
    private fun am() = ctx().getSystemService(AlarmManager::class.java)
    private fun ms(t: LocalDateTime) = t.atZone(zone).toInstant().toEpochMilli()
    private fun at(ms: Long): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), zone)
    private fun now(): LocalDateTime = LocalDateTime.now().withNano(0)

    /** A phone that lets us hold a rule and arm exact alarms - the granted state. */
    private fun granted() {
        shadowOf(ctx().getSystemService(NotificationManager::class.java))
            .setNotificationPolicyAccessGranted(true)
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
    }

    /** Every night, two hours long, beginning at [start]. */
    private fun prefs(start: LocalDateTime): Prefs = Prefs(ctx()).apply {
        enabled = true
        startTime = start.toLocalTime()
        endTime = start.plusHours(2).toLocalTime()
        days = DayOfWeek.entries.toSet()
    }

    /** Deliver [action] the way AlarmManager does, carrying the instant it was armed for. */
    private fun fire(action: String, due: LocalDateTime? = null) {
        val i = Intent(ctx(), BedtimeReceiver::class.java).setAction(action)
        if (due != null) i.putExtra(Scheduler.EXTRA_DUE, ms(due))
        BedtimeReceiver().onReceive(ctx(), i)
    }

    private fun armed(action: String): LocalDateTime? =
        shadowOf(am()).scheduledAlarms
            .firstOrNull { shadowOf(it.operation).savedIntent.action == action }
            ?.let { at(it.triggerAtTime) }

    /** The last state the rule was PUSHED to; nothing pushed reads null. */
    private fun zen(p: Prefs): String? = p.lastLoggedZen

    // ---------- START ----------

    @Test
    fun `a START on time opens the night - zen on, the night pinned, the END armed`() {
        granted()
        val due = now().minusSeconds(1)
        val p = prefs(due)
        fire(Scheduler.ACTION_START, due)
        assertEquals("zen was not switched on", "ON", zen(p))
        assertEquals(due.toLocalDate().toEpochDay(), p.activeDay)
        assertEquals(due.plusHours(2), armed(Scheduler.ACTION_END))
    }

    @Test
    fun `a START forty seconds early is the phone's grid, and opens the night at its hour`() {
        // The Honor lands alarms up to a grid point early. Judged at the
        // instant it was armed for, the window has begun.
        granted()
        val due = now().plusSeconds(40)
        val p = prefs(due)
        fire(Scheduler.ACTION_START, due)
        assertEquals("ON", zen(p))
        assertEquals(due.toLocalDate().toEpochDay(), p.activeDay)
        assertEquals(due.plusHours(2), armed(Scheduler.ACTION_END))
    }

    @Test
    fun `a START landing early beyond the tolerance does not open the night, and is re-armed for its hour`() {
        // Ten minutes early is a different fault, judged at its landing: the
        // window has not begun. The receiver used to switch zen on FIRST and
        // let the reschedule switch it off again - on, then off, and a posted
        // "Do Not Disturb is on" at a bedtime that had not started.
        granted()
        val due = now().plusMinutes(10)
        val p = prefs(due)
        fire(Scheduler.ACTION_START, due)
        // Asserted OFF, and only off: the reschedule forces the state it
        // decided on, and it decided the night has not begun.
        assertEquals("zen was switched on ten minutes before bedtime", "OFF", zen(p))
        assertEquals(Prefs.NO_DAY, p.activeDay)
        assertEquals("the START waits for its hour", due, armed(Scheduler.ACTION_START))
        assertTrue(Journal.read(ctx()).none { it.contains("zen state -> ON") })
    }

    // ---------- END ----------

    @Test
    fun `the END of a one-off switches bedtime off and arms nothing`() {
        granted()
        val due = now().minusSeconds(1)
        val p = prefs(due.minusHours(2)).apply { days = emptySet() }
        AlarmWatch.arming(p, ms(due))
        fire(Scheduler.ACTION_END, due)
        assertFalse("a one-off is spent", p.enabled)
        assertEquals("and the night is closed by its due instant", ms(due), p.endedAt)
        assertNull(armed(Scheduler.ACTION_START))
        assertNull(armed(Scheduler.ACTION_END))
    }

    @Test
    fun `a repeating END closes tonight and queues tomorrow`() {
        granted()
        val due = now().minusSeconds(1)
        val p = prefs(due.minusHours(2))
        AlarmWatch.arming(p, ms(due))
        fire(Scheduler.ACTION_END, due)
        assertTrue(p.enabled)
        assertEquals(Prefs.NO_DAY, p.activeDay)
        assertEquals(due.minusHours(2).plusDays(1), armed(Scheduler.ACTION_START))
        assertEquals(due.plusDays(1), armed(Scheduler.ACTION_END))
        assertFalse("it arrived on the second", AlarmWatch.missed(p))
    }

    // ---------- boot and upgrade ----------

    @Test
    fun `a boot that reaches us is recorded, keeps the alarm rule, and asks the probe again`() {
        granted()
        val p = prefs(now().plusHours(1)).apply {
            // An earlier boot: the phone has restarted since.
            bootStamp = 12_345L
            // No alarm on the phone yet - the clock app has not re-registered.
            exitAtAlarm = true
            // A probe the reboot threw away with every other alarm.
            probeDue = System.currentTimeMillis() + 5 * 60_000
        }
        assertTrue(BootWatch.missed(p))
        fire(Intent.ACTION_BOOT_COMPLETED)
        assertFalse("the boot was handled", BootWatch.missed(p))
        assertTrue("no alarm YET is not no alarm", p.exitAtAlarm)
        assertFalse("a voided probe is not a verdict", BackgroundProbe.blocked(p))
        assertTrue("and it is asked again, from scratch", p.probeDue - System.currentTimeMillis() > 10 * 60_000)
        assertNotNull("the alarms are back", armed(Scheduler.ACTION_START))
    }

    @Test
    fun `an upgrade re-arms but is not a boot`() {
        granted()
        val p = prefs(now().plusHours(1)).apply { bootStamp = 12_345L }
        fire(Intent.ACTION_MY_PACKAGE_REPLACED)
        assertEquals("an upgrade must not pass for the boot we missed", 12_345L, p.bootStamp)
        assertNotNull(armed(Scheduler.ACTION_START))
    }

    // ---------- the probe ----------

    @Test
    fun `the probe arriving on time answers the question, and arms nothing`() {
        granted()
        val p = prefs(now().plusHours(1))
        p.probeDue = System.currentTimeMillis() - 5_000
        fire(Scheduler.ACTION_PROBE)
        assertTrue(BackgroundProbe.answered(p))
        assertFalse(BackgroundProbe.blocked(p))
        assertNull("the probe is not a reschedule", armed(Scheduler.ACTION_START))
    }

    @Test
    fun `the probe arriving late is the phone holding us`() {
        granted()
        val p = prefs(now().plusHours(1))
        p.probeDue = System.currentTimeMillis() - 10 * 60_000
        fire(Scheduler.ACTION_PROBE)
        assertTrue(BackgroundProbe.blocked(p))
        assertTrue(Journal.read(ctx()).any { it.contains("TOO LATE") })
    }

    // ---------- everything else ----------

    @Test
    fun `a broadcast that is not ours changes nothing`() {
        granted()
        prefs(now().plusHours(1))
        fire("com.example.NOT_OURS")
        assertNull(armed(Scheduler.ACTION_START))
    }

    @Test
    fun `the clock app's broadcast is ignored while the alarm rule is off`() {
        // Re-deriving costs a reschedule, and doing it for everyone would
        // rewrite the rule every time any clock app is touched.
        granted()
        prefs(now().plusHours(1)).apply { exitAtAlarm = false }
        fire(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED)
        assertNull("no reschedule for a rule that is off", armed(Scheduler.ACTION_START))
    }
}
