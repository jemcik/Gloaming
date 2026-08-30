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
