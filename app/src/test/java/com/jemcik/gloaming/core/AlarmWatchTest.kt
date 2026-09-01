package com.jemcik.gloaming.core

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
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
        AlarmWatch.handled(p, due + 400)
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
        AlarmWatch.handled(p, due + 12 * 60_000 + 17_000)
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
