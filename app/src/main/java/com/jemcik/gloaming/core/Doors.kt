package com.jemcik.gloaming.core

import android.app.AlarmManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
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
    /**
     * `internal` rather than private so a TEST can arrange the phone from the
     * same list the app resolves against. Both suites used to hardcode this
     * component, which is a copy that must move in step with this one or the
     * tests quietly stop arranging anything - and a Settings row that is never
     * drawn is a row RowFitTest silently skips. That happened.
     */
    internal val VENDOR_SCREENS = listOf(
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
     * The clock app's alarm list - or the next alarm itself, where the app
     * that set it says how.
     *
     * `AlarmClock.ACTION_SHOW_ALARMS` is a platform intent, API 19, and every
     * stock clock declares it because Google Assistant's "show my alarms" goes
     * through it: AOSP, Samsung, Honor (measured 11 Sep 2026, it resolves to
     * `AlarmsMainActivity`), and the third-party alarm apps too. Not a vendor
     * branch. Still PROBED, because a phone with no handler is possible, and
     * package visibility hides the answer unless the manifest declares the
     * intent under <queries>.
     *
     * NOT `ACTION_SET_ALARM` with the wake time filled in, which read as the
     * obvious door and is not one. Measured on the Honor: with and without
     * hour and minute extras it opened the editor of the FIRST EXISTING alarm,
     * 07:30 Mon-Fri, ignored every extra, and created nothing. On Google's
     * Clock the same intent creates the alarm at once and shows no editor - a
     * side effect a "go and set one" row must not have. The list is what
     * every clock answers the same way, and its + is one tap.
     *
     * Where the clock supplies a `showIntent` with its next alarm it is
     * preferred: it opens THAT alarm in the app that set it, which is the
     * exact answer to "which alarm". AOSP and Samsung fill it; Honor's Clock
     * leaves it null, measured the same day.
     */
    fun hasAlarms(ctx: Context): Boolean =
        ctx.getSystemService(AlarmManager::class.java)?.nextAlarmClock?.showIntent != null ||
            ctx.packageManager.resolveActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS), 0) != null

    /**
     * The name of the app the alarm door opens - "Clock" almost everywhere,
     * "Alarmy" where that is the truth - so the chip can say where it leads
     * rather than promising a "Clock" the phone may not have. The alarm's own
     * app where it says how to show itself, else whichever app answers the
     * list. Null where neither can be named; the chip then says "alarms".
     */
    fun alarmsApp(ctx: Context): String? = runCatching {
        val pm = ctx.packageManager
        val creator = ctx.getSystemService(AlarmManager::class.java)
            ?.nextAlarmClock?.showIntent?.creatorPackage
        if (creator != null) return@runCatching pm.getApplicationInfo(creator, 0).loadLabel(pm).toString()
        pm.resolveActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS), 0)
            ?.activityInfo?.applicationInfo?.loadLabel(pm)?.toString()
    }.getOrNull()?.takeIf { it.isNotBlank() }

    fun openAlarms(ctx: Context): Boolean {
        val show = ctx.getSystemService(AlarmManager::class.java)?.nextAlarmClock?.showIntent
        if (show != null) {
            runCatching { show.send() }
                .onSuccess { return true }
                .onFailure { Journal.write(ctx, "alarm's showIntent refused: " + it) }
        }
        return runCatching {
            ctx.startActivity(
                Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.onFailure {
            // Say what the call answered. A door that resolves and then does
            // nothing on a tap is the failure this line exists to name: the
            // list's activity is behind a normal permission the manifest has
            // to declare, and the first build did not.
            Journal.write(ctx, "alarm list refused: " + it)
        }.isSuccess
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
