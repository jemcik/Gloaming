package com.jemcik.gloaming.core

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Did our own END actually arrive, and on time?
 *
 * This is the backstop behind every cause the readable settings cannot see - a
 * frozen app misses its alarm with the appop still reading `allow` - so it is
 * the last thing standing between a lost night and a silent one. It had no test
 * at all until a DRY pass compared it against [BackgroundProbe] and found the
 * two disagreeing about what "delivered" means.
 *
 * Every case states its own instants. A test that reads the wall clock passes
 * only in the hours it was written in.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AlarmWatchTest {

    private fun prefs(): Prefs = Prefs(ApplicationProvider.getApplicationContext<Context>())

    private val due = 1_700_000_000_000L

    @Test
    fun `a punctual END is not an accusation`() {
        val p = prefs()
        AlarmWatch.arming(p, due)
        AlarmWatch.handled(p, due, due + 400, atOpen = false)
        AlarmWatch.check(p, due + 60 * 60_000)
        assertFalse("it arrived on the second", AlarmWatch.missed(p))
    }

    @Test
    fun `an END released only by opening the app is a MISS, not a delivery`() {
        // The report this whole branch came from: due 08:55, arrived 09:07,
        // released by the app being foregrounded. handled() used to clear the
        // flag unconditionally, so the one failure the notice exists for was
        // the one failure it could not report.
        val p = prefs()
        AlarmWatch.arming(p, due)
        AlarmWatch.handled(p, due, due + 12 * 60_000 + 17_000, atOpen = true)
        assertTrue("12m17s late is the reported bug", AlarmWatch.missed(p))
    }

    @Test
    fun `an END that never arrives is a miss once the tolerance runs out`() {
        val p = prefs()
        AlarmWatch.arming(p, due)
        AlarmWatch.check(p, due + 30_000)
        assertFalse("half a minute is a doze window", AlarmWatch.missed(p))
        AlarmWatch.check(p, due + 5 * 60_000)
        assertTrue("five minutes is not", AlarmWatch.missed(p))
    }

    @Test
    fun `the verdict survives the re-arm that overwrites its evidence`() {
        // Same shape as the probe's latch, and for the same reason: the next
        // window's arming replaces endDue, which IS the proof the last one was
        // missed. Derived live, the notice would erase itself on the way to
        // being shown.
        val p = prefs()
        AlarmWatch.arming(p, due)
        AlarmWatch.check(p, due + 5 * 60_000)
        assertTrue(AlarmWatch.missed(p))
        AlarmWatch.arming(p, due + 24 * 60 * 60_000)
        assertTrue("re-arming must not wipe the verdict", AlarmWatch.missed(p))
    }

    private val minute = 60_000L
    private val day = 24 * 60 * minute

    @Test
    fun `a late END records when bedtime actually ended, and what ended it`() {
        // The Honor, 11 Sep 2026: due 08:30:00, delivered by the phone itself at
        // 08:34:20 with the app closed. The card used to say "stayed on until
        // you opened the app" about exactly this.
        val p = prefs()
        AlarmWatch.arming(p, due)
        AlarmWatch.handled(p, due, due + 4 * minute + 20_000, atOpen = false)
        val m = AlarmWatch.report(p)!!
        assertEquals(due, m.due)
        assertEquals(due + 4 * minute + 20_000, m.endedAt)
        assertFalse("delivered by the phone, not released by an open", m.atOpen)
    }

    @Test
    fun `an END still pending when the app is opened is ended BY the open`() {
        // The 1 Sep case: nothing arrived, the resume's reschedule notices the
        // window is over and switches zen off. "Now" is when bedtime ended, and
        // opening the app is what ended it.
        val p = prefs()
        AlarmWatch.arming(p, due)
        AlarmWatch.check(p, due + 3 * 60 * minute, atOpen = true)
        val m = AlarmWatch.report(p)!!
        assertEquals(due + 3 * 60 * minute, m.endedAt)
        assertTrue(m.atOpen)
    }

    @Test
    fun `the END that fired is judged by its own due instant, not the one re-armed under it`() {
        // Opening the app both releases a parked END and reschedules, and when
        // the reschedule runs first it has re-armed endDue for tomorrow before
        // the alarm lands. Judged against tomorrow, a ten-minute-late END read
        // as punctual and un-latched the miss recorded a moment earlier.
        val p = prefs()
        AlarmWatch.arming(p, due)
        AlarmWatch.check(p, due + 10 * minute, atOpen = true)
        AlarmWatch.arming(p, due + day)
        AlarmWatch.handled(p, due, due + 10 * minute + 500, atOpen = true)
        assertTrue("the miss must survive the alarm landing after the re-arm", AlarmWatch.missed(p))
        assertTrue("tomorrow's END must not be pre-marked as seen", p.endSeen != due + day)
    }

    @Test
    fun `Got it puts one incident away, and the next late END is a new one`() {
        val p = prefs()
        AlarmWatch.arming(p, due)
        AlarmWatch.handled(p, due, due + 5 * minute, atOpen = false)
        assertFalse(AlarmWatch.acknowledged(p))
        AlarmWatch.acknowledge(p)
        assertTrue(AlarmWatch.acknowledged(p))
        assertTrue("the fact stands; only the card goes", AlarmWatch.missed(p))
        AlarmWatch.arming(p, due + day)
        AlarmWatch.handled(p, due + day, due + day + 5 * minute, atOpen = false)
        assertFalse("a new miss is a new card", AlarmWatch.acknowledged(p))
    }

    @Test
    fun `Allow is remembered for this miss only, and answers nothing`() {
        val p = prefs()
        AlarmWatch.arming(p, due)
        AlarmWatch.handled(p, due, due + 5 * minute, atOpen = false)
        AlarmWatch.visit(p)
        assertTrue(AlarmWatch.visited(p))
        assertTrue("going to look is not an answer", AlarmWatch.missed(p) && !AlarmWatch.acknowledged(p))
        AlarmWatch.arming(p, due + day)
        AlarmWatch.handled(p, due + day, due + day + 5 * minute, atOpen = false)
        assertFalse("the next miss starts from the accusation again", AlarmWatch.visited(p))
    }

    @Test
    fun `a miss latched before the record existed is neither answered nor visited`() {
        // An upgrade with alarmMissed already true and nothing recorded: the
        // defaults of the two answers must not read as "already pressed".
        val p = prefs()
        p.alarmMissed = true
        assertNull(AlarmWatch.report(p))
        assertFalse(AlarmWatch.acknowledged(p))
        assertFalse(AlarmWatch.visited(p))
        AlarmWatch.acknowledge(p)
        assertTrue("Got it must still work on it", AlarmWatch.acknowledged(p))
    }

    @Test
    fun `a punctual END clears the record along with the verdict`() {
        val p = prefs()
        AlarmWatch.arming(p, due)
        AlarmWatch.handled(p, due, due + 5 * minute, atOpen = false)
        AlarmWatch.arming(p, due + day)
        AlarmWatch.handled(p, due + day, due + day + 400, atOpen = false)
        assertFalse(AlarmWatch.missed(p))
        assertNull("nothing to describe once an END is on time", AlarmWatch.report(p))
    }

    @Test
    fun `an alarm a little early is judged at its due instant, one far too early is not`() {
        assertEquals("forty seconds early is the phone's grid", due, Delivery.asOf(due, due - 40_000))
        assertEquals("ten minutes early is a different fault", due - 10 * minute, Delivery.asOf(due, due - 10 * minute))
        assertEquals("on time is now", due + 5, Delivery.asOf(due, due + 5))
        assertEquals("late is now", due + 5 * minute, Delivery.asOf(due, due + 5 * minute))
        assertEquals("nothing armed is now", due, Delivery.asOf(Prefs.NO_DUE, due))
    }

    @Test
    fun `an END forty seconds early ends the night rather than re-entering it`() {
        // Measured on the Honor 11 Sep 2026. The END due 08:30:00 landed at
        // 08:29:20; judged at that moment the night had forty seconds to run,
        // so the reschedule re-opened it, switched zen back on and armed a
        // second END - which the phone held to 08:34:20.
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val p = prefs()
        p.enabled = true
        p.startTime = LocalTime.of(22, 30)
        p.endTime = LocalTime.of(8, 30)
        p.days = DayOfWeek.entries.toSet()
        val zone = ZoneId.systemDefault()
        val dueMs = LocalDateTime.of(2026, 9, 11, 8, 30).atZone(zone).toInstant().toEpochMilli()
        val landed = dueMs - 40_000
        fun at(ms: Long) = LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), zone)

        Scheduler.rescheduleAll(ctx, p, from = at(landed))
        assertTrue("judged as it landed, the night re-opens - the old reading", p.activeDay != Prefs.NO_DAY)

        p.activeDay = Prefs.NO_DAY
        Scheduler.rescheduleAll(ctx, p, from = at(Delivery.asOf(dueMs, landed)))
        assertEquals("judged at its due instant, the night is over", Prefs.NO_DAY, p.activeDay)
    }

    @Test
    fun `switching bedtime off clears the slate rather than leaving an accusation`() {
        val p = prefs()
        AlarmWatch.arming(p, due)
        AlarmWatch.check(p, due + 5 * 60_000)
        assertTrue(AlarmWatch.missed(p))
        AlarmWatch.clear(p)
        AlarmWatch.check(p, due + 60 * 60_000)
        assertFalse("nothing is scheduled, so nothing can be late", AlarmWatch.missed(p))
    }
}
