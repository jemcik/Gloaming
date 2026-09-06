package com.jemcik.gloaming.ui

import android.content.ComponentName
import android.content.Context
import com.jemcik.gloaming.core.Doors
import com.jemcik.gloaming.core.FakeRoutines
import com.jemcik.gloaming.core.RoutineEffect
import org.robolectric.Shadows.shadowOf

/**
 * Make this phone answer for the doors `Doors` knows how to open.
 *
 * Robolectric resolves nothing by default, which is a phone with no vendor
 * launch manager - correct as a default, and the
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
 * Make this phone a Galaxy for the screen section: the zen effects thrown away
 * (the one manufacturer prior in the app, see ScreenEffects) and Samsung's
 * routine provider present with the permission held. Returns the fake so a test
 * can put routines in it.
 */
internal fun Context.asGalaxyWithRoutines(): FakeRoutines {
    org.robolectric.util.ReflectionHelpers.setStaticField(android.os.Build::class.java, "MANUFACTURER", "samsung")
    // FileProvider caches its path strategy per authority in a STATIC map, and
    // Robolectric gives every test a fresh cache directory. The second test
    // in a JVM to offer a routine therefore asked a strategy rooted in the
    // previous test's directory, which threw, and the offer never went out -
    // an artefact of the runner, not of the app. Cleared here, where every
    // Galaxy test starts.
    runCatching {
        val cache = androidx.core.content.FileProvider::class.java.getDeclaredField("sCache")
        cache.isAccessible = true
        (cache.get(null) as MutableMap<*, *>).clear()
    }
    return FakeRoutines.install(this)
}
