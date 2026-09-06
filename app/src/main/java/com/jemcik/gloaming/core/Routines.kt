package com.jemcik.gloaming.core

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import java.io.File

/**
 * Samsung's Modes and Routines, driven through the door it leaves open.
 *
 * WHY. One UI stores the zen rule's device effects and applies none of them
 * ([ScreenEffects]), and every direct route to the display was measured shut:
 * grayscale is CONTROL_DISPLAY_SATURATION, the dark theme is
 * MODIFY_DAY_NIGHT_MODE behind a locked UiModeManager - both
 * signature|privileged. Modes and Routines holds them, and its BUILT-IN action
 * catalogue carries both effects as routine actions in their own right:
 * `gray_scale` calls `SemColorDisplayManager.setSaturationLevel`,
 * `dark_mode_v3` calls `UiModeManager.setNightMode` (read from its decompiled
 * handlers, Routines 4.9.04.13, Galaxy S23, One UI 8, 6 Sep 2026).
 *
 * WHAT IT LEAVES OPEN. Its manifest exports a content provider at [AUTHORITY]
 * behind [PERMISSION] - and that permission is `protectionLevel normal`, the
 * one level an ordinary install holds with no prompt. The provider lists the
 * user's MANUAL routines with their running state and answers
 * `start_manual_routine` / `end_manual_routine` by uuid, handing any caller's
 * request to its own execution service. And its importer for shared routine
 * files is exported for a plain VIEW, so a routine can be handed to it
 * ready-made ([RoutineFile]); the one step nobody can take for the user is the
 * Save in Samsung's editor, because inserting directly is WRITE_ROUTINE_INFO,
 * signature-level.
 *
 * SO. Each screen effect the phone would otherwise ignore is ONE generated
 * routine - grayscale, the dark theme - saved once, then started when the
 * window opens and ended when it closes, exactly as the app's own switches ask.
 * Samsung applies the effect with its own privileged code and reverts it the
 * way it reverts any routine that ends. The switches on Home are therefore
 * real switches on a Galaxy, backed by these routines rather than by the rule.
 *
 * A CAPABILITY probe, never a manufacturer test: [available] asks whether the
 * provider resolves and the permission is held. It answers yes on no phone this
 * app has run on but the Galaxy, and it will be right about a Samsung nobody
 * here has seen - and about the day Samsung closes the door. One report says
 * One UI 8.5 already changed something here for another automation app; where
 * the probe answers no, nothing below is drawn.
 *
 * OWNERSHIP. The routines run exactly while bedtime does. [sync] is called from
 * [ZenController.setActive], which every path funnels through - alarms, boot,
 * the UI, reconcile - so a phone that dies mid-window ends them when it comes
 * back, with no case of its own. [Prefs.routinesStarted] records what WE
 * started, so reconcile in the daytime never ends a run the user began by hand,
 * and a refused end is retried rather than forgotten.
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

    /**
     * Samsung's importer for shared routine files, exported with no permission
     * for a plain VIEW - only its own SHARE_SAVE action checks the caller's
     * signature. It opens the editor with the routine filled in; Save inserts.
     */
    private val IMPORTER = ComponentName(PACKAGE, "$PACKAGE.ui.share.RoutineFileHandleActivity")
    private const val FILE_AUTHORITY = "com.jemcik.gloaming.routine"

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

    /** Which effects have a routine of their own here, in the order Home draws them. */
    fun adopted(p: Prefs): List<RoutineEffect> = RoutineEffect.entries.filter { p.routineUuid(it) != 0L }

    /**
     * Hand Samsung's app a ready-made routine for one effect, so nobody has to
     * build it. The file is [RoutineFile], written to our cache and offered
     * through a FileProvider with a one-off read grant. Samsung's editor opens
     * with the condition and the action already in place, and the user's whole
     * job is Save.
     *
     * Records the offer - the effect and the exact name the file carries - which
     * is what lets [adopt] pick the result up afterwards, on EVIDENCE, once the
     * provider lists a routine by that name. Never because the screen was
     * shown: going to look is not an answer.
     */
    fun offer(ctx: Context, p: Prefs, effect: RoutineEffect, name: String): Boolean {
        val file = runCatching {
            val dir = File(ctx.cacheDir, "routine").apply { mkdirs() }
            File(dir, "gloaming-${effect.key}.rtn").apply { writeBytes(RoutineFile.bytes(name, effect)) }
        }.getOrElse {
            Journal.write(ctx, "routine file not written: $it")
            return false
        }
        val uri = FileProvider.getUriForFile(ctx, FILE_AUTHORITY, file)
        // In OUR task when an Activity is asking, deliberately: Samsung's editor
        // then sits on top of Home, and Save drops the user straight back onto
        // the row that sent them - which is where the adoption shows. Started
        // as a new task it lands in Samsung's own, on top of whatever that app
        // was last showing, and Save returns there instead. Measured.
        val i = Intent(Intent.ACTION_VIEW)
            .setComponent(IMPORTER)
            .setDataAndType(uri, RoutineFile.MIME)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (ctx !is android.app.Activity) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val ok = runCatching { ctx.startActivity(i) }.isSuccess
        Journal.write(ctx, "routine ${effect.key} " + (if (ok) "offered to Samsung's importer" else "importer refused to open"))
        if (ok) {
            p.routineOffered = effect.key
            p.routineOfferedName = name
        }
        return ok
    }

    /**
     * After an offer: is the routine there now? Adopted the moment Samsung's
     * own list carries an enabled routine by the name we offered, and only
     * then - a cancelled import leaves nothing to find and nothing is taken.
     * The effect's own switch goes ON with it, because a routine just saved
     * for the night is a routine wanted for the night. Called on resume, which
     * is when the user comes back from Samsung's editor.
     */
    fun adopt(ctx: Context, p: Prefs): RoutineEffect? {
        val effect = RoutineEffect.entries.firstOrNull { it.key == p.routineOffered } ?: return null
        val name = p.routineOfferedName ?: return null
        val found = list(ctx).firstOrNull { it.name == name && it.enabled } ?: return null
        p.setRoutineUuid(effect, found.uuid)
        when (effect) {
            RoutineEffect.GRAYSCALE -> p.fxGrayscale = true
            RoutineEffect.DARK -> p.fxDarkTheme = true
        }
        p.routineOffered = null
        p.routineOfferedName = null
        Journal.write(ctx, "routine ${effect.key} adopted: " + found.uuid)
        return effect
    }

    /**
     * Forget a routine the user has since deleted in Samsung's app, so its row
     * goes back to offering one rather than wearing a switch over nothing.
     * Read from the provider, never assumed; a routine merely switched off
     * there is kept, and reported as such by Diagnostics.
     */
    fun prune(ctx: Context, p: Prefs): List<RoutineEffect> {
        val have = adopted(p)
        if (have.isEmpty()) return emptyList()
        val listed = list(ctx).map { it.uuid }.toSet()
        return have.filter { p.routineUuid(it) !in listed }.onEach {
            Journal.write(ctx, "routine ${it.key} gone from Samsung's app: " + p.routineUuid(it))
            p.setRoutineUuid(it, 0L)
        }
    }

    /** The routines the window wants running: adopted, and switched on. */
    private fun wanted(p: Prefs): Set<Long> = RoutineEffect.entries
        .filter {
            when (it) {
                RoutineEffect.GRAYSCALE -> p.fxGrayscale
                RoutineEffect.DARK -> p.fxDarkTheme
            }
        }
        .map { p.routineUuid(it) }
        .filter { it != 0L }
        .toSet()

    /**
     * Make the phone match: each wanted routine running while the window is,
     * and only then. Idempotent, so it can sit on every path through setActive.
     * What we started is what we end - a refusal keeps the entry, so the next
     * sync tries again rather than leaving the phone in the night; a start that
     * is refused stays unrecorded, so the next sync asks again.
     */
    fun sync(ctx: Context, p: Prefs, windowActive: Boolean) {
        // Only where the zen effects are thrown away. The day a Galaxy applies
        // them - ScreenEffects notices the transition on its own - the rule
        // does the work and the routines fall silent, rather than two
        // mechanisms driving one display.
        val want = if (windowActive && !ScreenEffects.applied(ctx)) wanted(p) else emptySet()
        val started = p.routinesStarted
        if (started == want) return
        val still = started.toMutableSet()
        for (uuid in started - want) if (call(ctx, "end_manual_routine", uuid)) still -= uuid
        for (uuid in want - started) if (call(ctx, "start_manual_routine", uuid)) still += uuid
        p.routinesStarted = still
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

    /** Samsung's app, on its Routines tab, where the routines can be seen and edited. */
    fun openApp(ctx: Context) {
        val tab = Intent().setComponent(ROUTINES_TAB).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { ctx.startActivity(tab) }.isSuccess) return
        val launch = ctx.packageManager.getLaunchIntentForPackage(PACKAGE) ?: return
        runCatching { ctx.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}
