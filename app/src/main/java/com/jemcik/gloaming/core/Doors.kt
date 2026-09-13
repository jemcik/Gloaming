package com.jemcik.gloaming.core

import android.app.ActivityOptions
import android.app.AlarmManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
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
     *
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

    /**
     * The next alarm in the app that set it, else the list.
     *
     * The showIntent is sent WITH THIS APP'S LEAVE TO LAUNCH. A PendingIntent
     * starts its activity as the app that CREATED it, and the clock app is in
     * the background the moment we are in front, so on its own it may start
     * nothing. Reproduced 13 Sep 2026 on the Galaxy and the OnePlus, and read
     * on the OnePlus (LineageOS, Android 16), where logcat is plain:
     * `ActivityTaskManager` blocked the start - `callingUidProcState:
     * CACHED_RECENT ... BAL_BLOCK` - and said in the same line that the
     * sender's visible window would have carried it had the sender said so,
     * `resultIfPiSenderAllowsBal: BAL_ALLOW_VISIBLE_WINDOW`. With the ask in
     * the bundle the same tap reads `BAL_ALLOW_VISIBLE_WINDOW [realCaller]`,
     * result code 0, and the Clock is in front - on the OnePlus and, the same
     * evening, on the Galaxy, whose Clock answers with its own VIEWALARM
     * handler and lands on its main screen.
     * Since Android 14 a sender lends its own standing only by asking; this
     * is the ask. A bare `send()` was the whole bug: it does not REPORT the
     * block - the start's result code comes back through a hidden overload
     * and the public one returns normally - so `onSuccess` ran, the list
     * never did, and the row opened the clock app once, through the list,
     * and never again once an alarm existed to prefer. Honor's Clock leaves
     * the showIntent null, which is why the Honor never showed it.
     *
     * What is lent is VISIBILITY and nothing more: Android 16 names that mode
     * exactly and deprecates the older "whatever standing the sender has",
     * which is all Android 15 offers. A tap is a visible window; nothing
     * here ever sends from anywhere else.
     */
    fun openAlarms(ctx: Context): Boolean {
        val show = ctx.getSystemService(AlarmManager::class.java)?.nextAlarmClock?.showIntent
        if (show != null) {
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA)
                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE
            else @Suppress("DEPRECATION") ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
            val leave = ActivityOptions.makeBasic()
                .setPendingIntentBackgroundActivityStartMode(mode)
                .toBundle()
            runCatching { show.send(ctx, 0, null, null, null, null, leave) }
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
        runCatching { ctx.startActivity(appDetails(ctx)) }
    }

    /**
     * The system's own per-app language picker, for this app.
     *
     * Android 13 added it, and Settings answers it on every phone here; some
     * skins are reported to ship without the screen, and until 13 Sep 2026
     * the row caught the refusal and opened nothing - the door onto nothing
     * this file exists to prevent, on the one row that was not asked here.
     * The package rides as `package:` data because Settings' filter matches
     * on that scheme: a probe without it resolves nothing where the screen
     * exists, and the manifest's <queries> carries the scheme for the same
     * reason.
     */
    private fun languagePicker(ctx: Context) =
        Intent(Settings.ACTION_APP_LOCALE_SETTINGS, ("package:" + ctx.packageName).toUri())

    fun hasLanguagePicker(ctx: Context): Boolean =
        ctx.packageManager.resolveActivity(languagePicker(ctx), 0) != null

    fun openLanguagePicker(ctx: Context): Boolean =
        runCatching { ctx.startActivity(languagePicker(ctx)) }
            .onFailure { Journal.write(ctx, "language picker refused: " + it) }
            .isSuccess

    /**
     * The two permission screens Home's cards open. Never probed and never
     * hidden: without either permission the app cannot work, so a card that
     * vanished would leave nothing on screen to act on. What a refusal gets
     * instead is a journal line and app details, the closest screen there is
     * - the same fallback the launch manager takes. These were the only two
     * launches in the app with no catch around them, found in the survey
     * after the alarm door's showIntent died silently (13 Sep 2026): a
     * Settings that did not answer would have crashed the app at the tap,
     * which is the one failure worse than a door onto nothing.
     */
    fun openDndAccess(ctx: Context) =
        openOrDetails(ctx, Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS), "DND access screen")

    fun openExactAlarms(ctx: Context) =
        openOrDetails(ctx, Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM), "exact alarm screen")

    private fun openOrDetails(ctx: Context, screen: Intent, name: String) {
        val opened = runCatching { ctx.startActivity(screen) }
            .onFailure { Journal.write(ctx, "$name refused: $it") }
            .isSuccess
        if (!opened) runCatching { ctx.startActivity(appDetails(ctx)) }
    }

    private fun appDetails(ctx: Context) = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        ("package:" + ctx.packageName).toUri()
    )
}
