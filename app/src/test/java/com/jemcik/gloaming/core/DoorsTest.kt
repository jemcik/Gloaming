package com.jemcik.gloaming.core

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.provider.AlarmClock
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The door under "end bedtime at your alarm": drawn only where it opens onto
 * something, and opening the alarm itself where the clock app says how.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DoorsTest {

    private fun ctx(): Context = ApplicationProvider.getApplicationContext()

    /** A clock app that lists its alarms, the way every stock one does. */
    private fun installClock() {
        val clock = ComponentName("com.example.clock", "com.example.clock.Alarms")
        shadowOf(ctx().packageManager).apply {
            installPackage(android.content.pm.PackageInfo().apply {
                packageName = clock.packageName
                applicationInfo = android.content.pm.ApplicationInfo().apply {
                    packageName = clock.packageName
                    nonLocalizedLabel = "Clock"
                }
            })
            addActivityIfNotPresent(clock)
            addIntentFilterForActivity(
                clock,
                IntentFilter(AlarmClock.ACTION_SHOW_ALARMS).apply { addCategory(Intent.CATEGORY_DEFAULT) }
            )
        }
    }

    private fun started(): Intent? = shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity

    @Test
    fun `no clock app, no door`() {
        // A door that opens onto nothing is not drawn - the rule every entry
        // in Doors follows.
        assertFalse(Doors.hasAlarms(ctx()))
        assertEquals("and nothing to name", null, Doors.alarmsApp(ctx()))
    }

    @Test
    fun `the chip wears the name of the app it opens`() {
        installClock()
        assertEquals("Clock", Doors.alarmsApp(ctx()))
    }

    @Test
    fun `a clock app that lists alarms is a door, and the list is what opens`() {
        installClock()
        assertTrue(Doors.hasAlarms(ctx()))
        assertTrue(Doors.openAlarms(ctx()))
        assertEquals(AlarmClock.ACTION_SHOW_ALARMS, started()?.action)
    }

    @Test
    fun `an alarm that says how to show itself is a door on its own, and is preferred`() {
        // AOSP and Samsung fill showIntent; Honor's Clock leaves it null. Where
        // it exists it opens THAT alarm in the app that set it, which is the
        // exact answer to "which alarm" - so it wins over the list.
        installClock()
        val show = PendingIntent.getActivity(
            ctx(), 0, Intent("test.show"), PendingIntent.FLAG_IMMUTABLE
        )
        ctx().getSystemService(AlarmManager::class.java).setAlarmClock(
            AlarmManager.AlarmClockInfo(System.currentTimeMillis() + 3_600_000, show),
            PendingIntent.getBroadcast(ctx(), 1, Intent("test.alarm"), PendingIntent.FLAG_IMMUTABLE)
        )
        assertTrue(Doors.hasAlarms(ctx()))
        assertTrue(Doors.openAlarms(ctx()))
        assertEquals("the alarm's own screen, not the list", "test.show", started()?.action)
    }
}
