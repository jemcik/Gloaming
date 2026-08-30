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
 * Whether the phone restarted without telling us.
 *
 * This is the detection behind the worst bug found on this app: MagicOS
 * withholds ACTION_BOOT_COMPLETED from an app its launch manager has filed under
 * "manage automatically", so bedtime sat armed with no alarms until the app was
 * next opened - silently, all night. The notice is only as good as this
 * comparison, and it is pure enough to test.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BootWatchTest {

    private fun prefs(): Prefs = Prefs(ApplicationProvider.getApplicationContext<Context>())

    @Test
    fun `a fresh install adopts this boot rather than accusing the phone`() {
        // Nothing to compare against yet. Reporting a missed boot here would be
        // blaming the phone for a broadcast we were not installed to receive.
        val p = prefs()
        p.bootStamp = Prefs.NO_BOOT
        assertFalse(BootWatch.missed(p))
    }

    @Test
    fun `adopting it also records it, so the next check is a comparison`() {
        val p = prefs()
        p.bootStamp = Prefs.NO_BOOT
        BootWatch.missed(p)
        assertTrue(p.bootStamp != Prefs.NO_BOOT)
    }

    @Test
    fun `a boot we handled is not reported as missed`() {
        val p = prefs()
        BootWatch.record(p)
        assertFalse(BootWatch.missed(p))
    }

    @Test
    fun `a boot we never saw is reported`() {
        // Far enough back that no clock correction could explain it.
        val p = prefs()
        BootWatch.record(p)
        p.bootStamp = p.bootStamp - 60 * 60 * 1000L
        assertTrue(BootWatch.missed(p))
    }

    @Test
    fun `a small clock correction is not a reboot`() {
        // The stamp is wall clock minus uptime, so NTP moving the clock shifts
        // it. Under the tolerance that must not read as a restart, or the notice
        // would appear for a phone that never rebooted.
        val p = prefs()
        BootWatch.record(p)
        p.bootStamp = p.bootStamp - 30_000L
        assertFalse(BootWatch.missed(p))
    }
}
