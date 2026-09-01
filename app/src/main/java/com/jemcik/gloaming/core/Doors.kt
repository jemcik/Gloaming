package com.jemcik.gloaming.core

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.net.toUri

/**
 * The system screens this app can send someone to, and whether they exist here.
 *
 * Every one is a CAPABILITY probe rather than a manufacturer test: ask whether
 * the screen resolves, and draw nothing where it does not. A door that opens
 * onto nothing is worse than no door, and this app has made that mistake once
 * already.
 *
 * They lived in [BootWatch] until this file existed, which was wrong twice
 * over: only two of that object's members were ever about boots, and
 * [BackgroundLimit] had to reach through it to open a screen that has nothing
 * to do with restarting.
 */
object Doors {
    /**
     * The vendor's own auto-launch screen, resolved rather than assumed.
     *
     * Only actions confirmed on hardware belong here, and the manifest has to
     * declare a matching <queries> entry or package visibility hides the
     * activity and resolve returns null. Honor's
     * StartupNormalAppListActivity is exported with no permission attribute,
     * read from HnSystemManager.apk's manifest - unlike its AOD screens, which
     * are all locked. Everywhere else, app details is the closest we can get.
     */
    private val VENDOR_SCREENS = listOf(
        // Honor MagicOS 10, BKQ-N49. Named explicitly rather than by action:
        // TWO activities answer HSM_STARTUPAPP_MANAGER, and the other one -
        // .appcontrol.activity.StartupAppControlActivity - is gated behind
        // com.hihonor.permission.external_app_settings.USE_COMPONENT, which we
        // do not hold. Resolving the action therefore lands on Honor's chooser,
        // where one of the two choices simply fails. This one is exported with
        // no permission attribute at all.
        ComponentName(
            "com.hihonor.systemmanager",
            "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
        )
    )

    /**
     * Does this phone ship a launch manager at all?
     *
     * A CAPABILITY probe, not a vendor test: it asks whether the screen we would
     * send someone to actually resolves. Present on the Honor, absent on the
     * OnePlus, and it will be right about a vendor nobody here has seen. That is
     * the same shape as AmbientCapability, and the reason this is not a
     * Build.MANUFACTURER check - the codebase keeps exactly one of those.
     */
    fun hasLaunchManager(ctx: Context): Boolean =
        VENDOR_SCREENS.any {
            ctx.packageManager.resolveActivity(Intent().setComponent(it), 0) != null
        }

    /**
     * Spelled out rather than taken from `Settings`, because the constant is
     * hidden: `ACTION_BEDTIME_SETTINGS` exists in AOSP but is not exposed in the
     * SDK, so `Settings.ACTION_BEDTIME_SETTINGS` does not compile against
     * compileSdk 37. This is only an intent ACTION STRING, matched by the
     * package manager like any other - not a private method, and not reflection.
     * The resolve above is what keeps it honest: unanswered, we never send
     * anyone anywhere.
     */
    private const val ACTION_BEDTIME = "android.settings.BEDTIME_SETTINGS"

    /**
     * Does this phone have a bedtime screen of its own?
     *
     * `Settings.ACTION_BEDTIME_SETTINGS` is an AOSP action, not a vendor one, so
     * asking whether it resolves is a capability probe like every other here -
     * each vendor maps it to its own screen or does not answer at all. Measured
     * 1 Sep 2026: on a Galaxy S23 it lands on One UI's Sleep mode editor, and on
     * the Honor nothing answers it.
     *
     * It earns its place because of what One UI does NOT do. Samsung stores our
     * ZenDeviceEffects on the rule and applies none of them, while its own Sleep
     * mode drives the very same global saturation - so on that phone the only
     * working route to a grey screen is the system's own bedtime screen. This is
     * a door to it, never a claim that ours is broken, because that cannot be
     * read.
     */
    fun hasSystemBedtime(ctx: Context): Boolean =
        ctx.packageManager.resolveActivity(Intent(ACTION_BEDTIME), 0) != null

    fun openSystemBedtime(ctx: Context) {
        runCatching {
            ctx.startActivity(
                Intent(ACTION_BEDTIME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    fun openAutoStart(ctx: Context) {
        for (screen in VENDOR_SCREENS) {
            val i = Intent().setComponent(screen)
            if (ctx.packageManager.resolveActivity(i, 0) != null) {
                if (runCatching { ctx.startActivity(i) }.isSuccess) return
            }
        }
        runCatching {
            ctx.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    ("package:" + ctx.packageName).toUri()
                )
            )
        }
    }
}
