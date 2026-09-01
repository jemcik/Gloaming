package com.jemcik.gloaming.core

import android.app.ActivityManager
import android.content.Context

/**
 * Whether the phone has told this app it may not run in the background.
 *
 * This is the one vendor restriction that can be READ. Auto-launch cannot -
 * the setting lives inside `com.hihonor.systemmanager` and is not readable, so
 * [BootWatch] has to measure the symptom instead. Background restriction is
 * different: whatever the vendor calls its switch, it lands on the AOSP appop
 * `RUN_ANY_IN_BACKGROUND`, and `isBackgroundRestricted` reports it directly.
 *
 * WHY IT MATTERS, measured on an Honor BKQ-N49 on 1 Sep 2026. With Honor's
 * "Run in background" off, the appop reads `ignore`, and AlarmManager parks our
 * alarms in a queue it calls "Pending user blocked background alarms". They are
 * held there indefinitely: not released by the device leaving doze, not by
 * unfreezing the process, not by unlocking the phone. Only foregrounding the
 * app frees them. A bedtime that should have ended at 08:55 ended at 09:07,
 * when the app was opened, and nothing anywhere said why.
 *
 * The app cannot fix this - flipping the appop needs MANAGE_APP_OPS_MODES,
 * which is signature-level - so all it can do is notice and say so. That is
 * still the whole difference between a silent failure and a one-tap one.
 */
object BackgroundLimit {

    fun isRestricted(ctx: Context): Boolean =
        ctx.getSystemService(ActivityManager::class.java).isBackgroundRestricted

    /**
     * The vendor's own screen, which is where the switch lives. On Honor it is
     * the same "App launch" screen [BootWatch] already resolves - auto-launch
     * and "Run in background" are two rows of one dialog there - so this
     * deliberately reuses it rather than inventing a second guess at the path.
     */
    fun openSettings(ctx: Context) = BootWatch.openAutoStart(ctx)
}
