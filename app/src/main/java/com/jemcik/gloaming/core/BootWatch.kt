package com.jemcik.gloaming.core

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import androidx.core.net.toUri
import kotlin.math.abs

/**
 * Whether the phone has restarted without telling us.
 *
 * Several vendors ship an "auto-launch" manager that withholds
 * ACTION_BOOT_COMPLETED from apps it has decided are unimportant. Measured on an
 * Honor BKQ-N49, 30 Aug 2026: with the app set to "Manage automatically" the
 * broadcast never arrived - four reboots, waited out to five minutes, journal
 * unchanged and NO alarms - so bedtime silently did nothing until the app was
 * next opened. Switching that one setting to auto-launch delivered
 * BOOT_COMPLETED 32 seconds after boot. It is the same shape as the bug this app
 * exists to route around, and it is invisible: nothing tells you it happened.
 *
 * Note what this does NOT do. It does not ask which phone this is. A vendor test
 * would fire on every Honor whether or not anything is wrong, could never tell
 * whether the user acted on it - the setting lives inside
 * com.hihonor.systemmanager and is not readable - and would miss every other
 * vendor doing the same thing under another name. So we measure the symptom
 * instead: the app knows whether it actually missed a boot, says so only then,
 * and goes quiet by itself once the boot is handled. That self-clearing is the
 * verification we cannot otherwise get.
 *
 * Battery optimisation is NOT the mechanism and was tested twice, once over adb
 * and once granted from Honor's own Settings: exempt, surviving the reboot, in
 * standby bucket 5, and the broadcast still never came.
 */
object BootWatch {

    /**
     * The boot the device is currently on: wall clock minus uptime.
     *
     * It drifts when the clock is corrected, so it is compared with a tolerance
     * rather than for equality. A minute and a half is far longer than NTP
     * moves a phone and far shorter than any real uptime.
     */
    private fun stamp() = System.currentTimeMillis() - SystemClock.elapsedRealtime()

    private const val TOLERANCE_MS = 90_000L

    /** Called when BOOT_COMPLETED actually reaches us. */
    fun record(p: Prefs) {
        p.bootStamp = stamp()
    }

    /**
     * True when the device has booted since the last boot we handled.
     *
     * On first run there is nothing to compare against, so this adopts the
     * current boot rather than accusing the phone of eating a broadcast we were
     * not installed to receive.
     */
    fun missed(p: Prefs): Boolean {
        val seen = p.bootStamp
        if (seen == Prefs.NO_BOOT) {
            p.bootStamp = stamp()
            return false
        }
        return abs(stamp() - seen) > TOLERANCE_MS
    }

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
