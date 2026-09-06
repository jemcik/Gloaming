package com.jemcik.gloaming.core

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.net.toUri
import androidx.core.os.bundleOf

/**
 * Samsung's Modes and Routines, driven through the door it leaves open.
 *
 * WHY. One UI stores the zen rule's device effects and applies none of them
 * ([ScreenEffects]), and every direct route to the display was measured shut:
 * grayscale is CONTROL_DISPLAY_SATURATION, the dark theme is
 * MODIFY_DAY_NIGHT_MODE behind a locked UiModeManager, wallpaper dimming is
 * SET_WALLPAPER_DIM_AMOUNT - all signature|privileged. Modes and Routines
 * holds all of them, and it is what applies them for One UI's own Sleep mode:
 * `SemColorDisplayManager.setSaturationLevel` and `UiModeManager.setNightMode`,
 * read from its own decompiled action handlers (Routines 4.9.04.13, Galaxy S23,
 * One UI 8, 6 Sep 2026).
 *
 * WHAT IT LEAVES OPEN. Its manifest exports a content provider at [AUTHORITY]
 * behind [PERMISSION] - and that permission is `protectionLevel normal`, the
 * one level an ordinary install holds with no prompt. The provider lists the
 * user's MANUAL routines (condition "Start button tapped") with their running
 * state, and answers `start_manual_routine` / `end_manual_routine` by uuid,
 * handing any caller's request to its own execution service. Read from the
 * decompiled `ExternalRoutineContentProvider`, because there is no
 * documentation to read; the Routines SDK proper (`ACCESS_ROUTINES`) is
 * signature|privileged and its discovery is closed - see DECISIONS.
 *
 * SO. The user builds one manual routine in Modes and Routines with whatever
 * the night should do - Sleep mode on, the dark theme on, always-on off - and
 * this object starts it when the window opens and ends it when the window
 * closes. Samsung applies the effects with its own privileged code and reverts
 * them the way it reverts any routine that ends. WHICH effects that is stays
 * the user's choice inside Samsung's app, which is why nothing here names one.
 *
 * A CAPABILITY probe, never a manufacturer test: [available] asks whether the
 * provider resolves and the permission is held. It answers yes on no phone this
 * app has run on but the Galaxy, and it will be right about a Samsung nobody
 * here has seen - and about the day Samsung closes the door.
 *
 * OWNERSHIP. The routine runs exactly while bedtime does. [sync] is called from
 * [ZenController.setActive], which every path funnels through - alarms, boot,
 * the UI, reconcile - so a phone that dies mid-window ends the routine when it
 * comes back, with no case of its own. [Prefs.routineStarted] records what WE
 * started, so reconcile in the daytime never ends a run the user began by
 * hand, and a refused end is retried rather than forgotten.
 */
object Routines {
    const val PACKAGE = "com.samsung.android.app.routines"
    const val AUTHORITY = "com.samsung.android.app.routines.externalprovider"
    const val PERMISSION = "com.samsung.android.app.routines.permission.READ_ROUTINE_INFO"

    /**
     * Manual routines only, because those are the only ones the provider will
     * start or end. `only_enabled=0` so a routine the user has switched off is
     * still FOUND, and reported as switched off rather than as gone.
     */
    private val LIST: Uri =
        "content://$AUTHORITY/routine_list?condition_type=manual&only_enabled=0".toUri()
    private val BASE: Uri = "content://$AUTHORITY".toUri()

    /** The Routines tab of Samsung's app, exported with no permission. */
    private val ROUTINES_TAB = ComponentName(PACKAGE, "$PACKAGE.ui.main.MainRoutineTabLaunchActivity")

    class Routine(val uuid: Long, val name: String, val running: Boolean, val enabled: Boolean)

    fun available(ctx: Context): Boolean =
        ctx.packageManager.resolveContentProvider(AUTHORITY, 0) != null &&
            ctx.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED

    /** What Samsung's app currently holds. Empty, never a throw, where it answers nothing. */
    fun list(ctx: Context): List<Routine> = runCatching {
        ctx.contentResolver.query(LIST, null, null, null, null)?.use { c ->
            val uuid = c.getColumnIndex("uuid")
            val name = c.getColumnIndex("name")
            val running = c.getColumnIndex("is_running")
            val enabled = c.getColumnIndex("is_enabled")
            if (uuid < 0 || name < 0) return@use emptyList()
            buildList {
                while (c.moveToNext()) add(
                    Routine(
                        c.getLong(uuid),
                        c.getString(name) ?: "",
                        running >= 0 && c.getInt(running) == 1,
                        enabled < 0 || c.getInt(enabled) == 1
                    )
                )
            }
        } ?: emptyList()
    }.getOrDefault(emptyList())

    /** The one bedtime runs, as Samsung's app sees it now - null when nothing is chosen or it is gone. */
    fun chosen(ctx: Context, p: Prefs): Routine? {
        val id = p.routineUuid
        if (id == 0L) return null
        return list(ctx).firstOrNull { it.uuid == id }
    }

    /**
     * Make the phone match: the chosen routine running while the window is,
     * and only then. Idempotent, so it can sit on every path through setActive.
     */
    fun sync(ctx: Context, p: Prefs, windowActive: Boolean) {
        val want = if (windowActive) p.routineUuid else 0L
        val started = p.routineStarted
        if (started == want) return
        if (started != 0L) {
            // The window closed, or the choice moved mid-window. Either way the
            // one we started must end first. A refusal keeps the latch, so the
            // next sync - alarm, boot, resume - tries again rather than leaving
            // the phone in the night.
            if (!call(ctx, "end_manual_routine", started)) return
            p.routineStarted = 0L
        }
        if (want != 0L && call(ctx, "start_manual_routine", want)) p.routineStarted = want
    }

    /**
     * Log what the call ANSWERED, not that it did not throw. The provider
     * returns `success` and, on a refusal, an `error` naming why - "Routine is
     * disabled", "Not a manual routine" - which is the whole diagnosis when a
     * night on a Galaxy goes wrong.
     */
    private fun call(ctx: Context, method: String, uuid: Long): Boolean {
        val verb = method.substringBefore('_')
        val answer = runCatching { ctx.contentResolver.call(BASE, method, null, bundleOf("uuid" to uuid)) }
        val b = answer.getOrNull()
        return when {
            answer.isFailure -> {
                Journal.write(ctx, "routine $verb $uuid threw: " + answer.exceptionOrNull())
                false
            }
            b == null -> {
                Journal.write(ctx, "routine $verb $uuid: no answer")
                false
            }
            else -> {
                val ok = b.getBoolean("success", false)
                val why = b.getString("error")?.let { " - $it" } ?: ""
                Journal.write(ctx, "routine $verb $uuid: " + (if (ok) "ok" else "refused") + why)
                ok
            }
        }
    }

    /** Samsung's app, on its Routines tab, where a manual routine is made. */
    fun openApp(ctx: Context) {
        val tab = Intent().setComponent(ROUTINES_TAB).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { ctx.startActivity(tab) }.isSuccess) return
        val launch = ctx.packageManager.getLaunchIntentForPackage(PACKAGE) ?: return
        runCatching { ctx.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}
