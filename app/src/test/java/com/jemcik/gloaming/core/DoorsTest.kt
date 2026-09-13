package com.jemcik.gloaming.core

import android.app.ActivityOptions
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
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
    fun `a door that resolves and is then refused says so, in the journal`() {
        // The shape of the bug the phone caught: the list resolved, the
        // launch was refused for a permission the manifest lacked, and the
        // refusal was swallowed. Robolectric refuses an unresolvable launch
        // only when asked to; the assertion is that the refusal is spoken.
        shadowOf(RuntimeEnvironment.getApplication()).checkActivities(true)
        assertFalse(Doors.openAlarms(ctx()))
        assertTrue(
            "a refused door must be journaled, never swallowed",
            Journal.read(ctx()).any { it.contains("alarm list refused") }
        )
    }

    @Test
    fun `the alarm's own app is named where it says how to show itself`() {
        // The showIntent's creator is the app that set the alarm - here this
        // very app, whose label the chip would then wear.
        val show = PendingIntent.getActivity(
            ctx(), 0, Intent("test.show"), PendingIntent.FLAG_IMMUTABLE
        )
        ctx().getSystemService(AlarmManager::class.java).setAlarmClock(
            AlarmManager.AlarmClockInfo(System.currentTimeMillis() + 3_600_000, show),
            PendingIntent.getBroadcast(ctx(), 1, Intent("test.alarm"), PendingIntent.FLAG_IMMUTABLE)
        )
        val own = ctx().applicationInfo.loadLabel(ctx().packageManager).toString()
        assertEquals(own, Doors.alarmsApp(ctx()))
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

    @Test
    fun `the alarm's own screen is opened with this app's leave to launch it`() {
        // The bug as two phones showed it, 13 Sep 2026: tap "No alarm set",
        // the list opens; set an alarm there, come back, tap the alarm - and
        // nothing. A PendingIntent starts its activity AS THE CLOCK APP,
        // which is in the background the moment we are in front, and since
        // Android 14 a sender lends its own standing only by asking for it.
        // A bare send() was silently blocked, reported nothing, and the code
        // filed it as success. The send must carry the ask.
        installClock()
        val show = PendingIntent.getActivity(
            ctx(), 0, Intent("test.show"), PendingIntent.FLAG_IMMUTABLE
        )
        ctx().getSystemService(AlarmManager::class.java).setAlarmClock(
            AlarmManager.AlarmClockInfo(System.currentTimeMillis() + 3_600_000, show),
            PendingIntent.getBroadcast(ctx(), 1, Intent("test.alarm"), PendingIntent.FLAG_IMMUTABLE)
        )
        assertTrue(Doors.openAlarms(ctx()))
        val launch = shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivityForResult
        assertEquals("test.show", launch?.intent?.action)
        // Visibility, in the words the OS under test has for it: Android 16
        // names it, Android 15 has only the broader "whatever the sender
        // holds", which at a tap is the same window.
        val visible = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA)
            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE
        else @Suppress("DEPRECATION") ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
        // The platform hides both the getter and fromBundle, so the
        // expectation is written with the same public setter and the bundle
        // the launch carried is compared to it key by key.
        val expected = ActivityOptions.makeBasic()
            .setPendingIntentBackgroundActivityStartMode(visible)
            .toBundle()
        assertFalse("the setter must write something to compare", expected.isEmpty)
        val sent = launch?.options
        @Suppress("DEPRECATION")
        for (key in expected.keySet()) assertEquals(
            "sent without the sender's leave, the clock app may not launch from the background ($key)",
            expected.get(key),
            sent?.get(key)
        )
    }
}
