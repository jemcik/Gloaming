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
 * Does a background alarm arrive on this phone?
 *
 * The question exists because the SETTING cannot be read: Honor's two switches
 * change nothing observable in any settings table, in appops or in the package
 * dump, and the one documented API for it is behind a privileged permission.
 * Behaviour is the only evidence available, so this is the code that turns
 * behaviour into an answer - and it is pure enough to test without a phone.
 *
 * The timings are passed in rather than read from the clock. A test that calls
 * the wall clock and asserts a fixed answer passes only in the hours it was
 * written in, which this file must not do.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BackgroundProbeTest {

    private fun prefs(): Prefs = Prefs(ApplicationProvider.getApplicationContext<Context>())

    private val t0 = 1_700_000_000_000L

    @Test
    fun `an unasked question is not a bad answer`() {
        val p = prefs()
        // Nothing armed yet. Accusing the phone here would show the card to
        // every phone on first launch, which is the whole failure this replaced.
        assertTrue("a fresh install must want to ask", BackgroundProbe.needsArming(p))
        BackgroundProbe.check(p, t0)
        assertFalse("nothing was measured, so nothing is proven", BackgroundProbe.blocked(p))
    }

    @Test
    fun `a probe still in flight is not yet a failure`() {
        val p = prefs()
        BackgroundProbe.arming(p, t0 + BackgroundProbe.DELAY_MS)
        BackgroundProbe.check(p, t0 + 60_000)
        assertFalse("it is not due yet", BackgroundProbe.blocked(p))
        assertFalse("and one is armed, so do not arm another", BackgroundProbe.needsArming(p))
    }

    @Test
    fun `a probe that arrives settles it, and keeps settling it`() {
        val p = prefs()
        BackgroundProbe.arming(p, t0)
        BackgroundProbe.handled(p, t0 + 500)     // on time, and stated: never the
                                                 // wall clock, which would make
                                                 // this pass only today
        assertTrue(BackgroundProbe.answered(p))
        // Long past due, and it must STILL not read as blocked - the answer came.
        BackgroundProbe.check(p, t0 + 10 * 60_000)
        assertFalse("an answered probe cannot go on to fail", BackgroundProbe.blocked(p))
    }

    @Test
    fun `a probe that never arrives is the phone holding us`() {
        val p = prefs()
        BackgroundProbe.arming(p, t0)
        BackgroundProbe.check(p, t0 + 3 * 60_000)
        assertTrue("overdue and unanswered is the whole signal", BackgroundProbe.blocked(p))
    }

    @Test
    fun `the tolerance is real, so a late-but-delivered alarm is not an accusation`() {
        val p = prefs()
        BackgroundProbe.arming(p, t0)
        BackgroundProbe.check(p, t0 + 30_000)
        assertFalse("half a minute late is a doze window, not a block", BackgroundProbe.blocked(p))
    }

    @Test
    fun `an alarm released only by opening the app is a failure, not a pass`() {
        // The one the device found. A parked alarm IS delivered - the moment the
        // app is foregrounded - so arrival alone scores the blocked phone as
        // healthy. Measured: the probe sat in "Pending user blocked background
        // alarms" and landed 244 seconds late as the app came up.
        val p = prefs()
        BackgroundProbe.arming(p, t0)
        BackgroundProbe.handled(p, t0 + 244_000)
        assertTrue("244s late is the failure signature, not a pass", BackgroundProbe.blocked(p))
    }

    @Test
    fun `either order gives the same verdict, so the race cannot decide it`() {
        // Opening the app both releases the parked alarm and runs check(). When
        // delivery won, probeSeen == probeDue and check() returned early, so
        // nothing was latched and the phone read as healthy. Judging by lateness
        // makes both orders agree.
        val late = t0 + 244_000
        val deliveryFirst = prefs()
        BackgroundProbe.arming(deliveryFirst, t0)
        BackgroundProbe.handled(deliveryFirst, late)
        BackgroundProbe.check(deliveryFirst, late)

        val checkFirst = prefs()
        BackgroundProbe.arming(checkFirst, t0)
        BackgroundProbe.check(checkFirst, late)
        BackgroundProbe.handled(checkFirst, late)

        assertTrue(BackgroundProbe.blocked(deliveryFirst))
        assertTrue(BackgroundProbe.blocked(checkFirst))
    }

    @Test
    fun `the verdict survives the retest that overwrites its evidence`() {
        // The one that matters. `blocked` cannot be computed from probeDue,
        // because arming the next probe REPLACES the instant that proves the
        // last one was missed - so a derived answer erases itself the moment the
        // user acts on it. AlarmWatch had exactly this bug: the missed-alarm
        // flag was wiped by its own recovery, and the card vanished before it
        // could be read. Latching is the fix, and this is what pins it.
        val p = prefs()
        BackgroundProbe.arming(p, t0)
        BackgroundProbe.check(p, t0 + 3 * 60_000)
        assertTrue(BackgroundProbe.blocked(p))

        val retest = t0 + 60 * 60_000
        BackgroundProbe.arming(p, retest)                 // they went to fix it
        assertTrue("the verdict must outlive its own evidence", BackgroundProbe.blocked(p))

        BackgroundProbe.handled(p, retest + 500)          // and the retest lands
        assertFalse("a proven fix clears it", BackgroundProbe.blocked(p))
    }
}
