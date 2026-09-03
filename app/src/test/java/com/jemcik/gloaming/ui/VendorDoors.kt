package com.jemcik.gloaming.ui

import android.content.ComponentName
import android.content.Context
import android.content.IntentFilter
import com.jemcik.gloaming.core.Doors
import org.robolectric.Shadows.shadowOf

/**
 * Make this phone answer for the doors `Doors` knows how to open.
 *
 * Robolectric resolves nothing by default, which is a phone with no vendor
 * launch manager and no system bedtime screen - correct as a default, and the
 * reason a test that forgets to arrange them measures neither.
 *
 * The launch manager is taken from [Doors.VENDOR_SCREENS] rather than written
 * out here. Both suites used to carry their own copy of the component name, and
 * a copy is a thing that stops matching: the day that list moves, a hardcoded
 * arrangement resolves nothing, every row it was meant to draw is absent, and a
 * test that skips absent rows goes green having measured nothing. That is not
 * hypothetical - it is what RowFitTest had been doing.
 */
internal fun Context.withLaunchManager() {
    val pm = shadowOf(packageManager)
    Doors.VENDOR_SCREENS.forEach { pm.addActivityIfNotPresent(it) }
}

/**
 * And the system's own bedtime screen, which is resolved by ACTION rather than
 * by component - so it needs an intent filter rather than just an activity.
 */
internal fun Context.withSystemBedtime() {
    val pm = shadowOf(packageManager)
    val bedtime = ComponentName("com.example.wellbeing", "Bedtime")
    pm.addActivityIfNotPresent(bedtime)
    pm.addIntentFilterForActivity(bedtime, IntentFilter("android.settings.BEDTIME_SETTINGS"))
}
