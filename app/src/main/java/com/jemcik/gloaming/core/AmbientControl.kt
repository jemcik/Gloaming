package com.jemcik.gloaming.core

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings

/**
 * Switching the VENDOR's always-on display off directly, for phones where the
 * platform's own suppression is ignored.
 *
 * [AmbientCapability] decides which of the two routes a device gets, and the
 * supported one is always preferred. This exists because on some phones that
 * route cannot work at all. Measured on an Honor BKQ-N49, 30 Aug 2026: the
 * platform records the request correctly - `dumpsys power` shows
 * `ambientDisplaySuppressed=true` after `cmd power suppress-ambient-display` -
 * and com.hihonor.aod ignores it. A tap on the sleeping screen raises the clock
 * either way, so the zen effect can never work there whatever the rule asks.
 *
 * What DOES work is writing the keys the vendor's own toggle writes. They were
 * found by diffing every Settings namespace across a toggle made by hand in
 * Settings: four keys move together, and `aod_display_type` is the one that
 * decides. Writing `aod_switch` alone changes nothing - which is exactly why an
 * earlier pass here filed these as inert mirrors, alongside the eye-comfort
 * keys that genuinely are.
 *
 * The catch is the permission. These are `Settings.Secure`, so a writer needs
 * WRITE_SECURE_SETTINGS - signature|privileged, with no runtime prompt, so no
 * ordinary install can hold it. It can only be granted over adb:
 *
 *     adb shell pm grant com.jemcik.gloaming android.permission.WRITE_SECURE_SETTINGS
 *
 * [canControl] is therefore false on a normal phone, the row is not drawn, and
 * nothing in here runs unless someone deliberately opted in. This is the app's
 * only write to Settings.Secure, and it stays behind that gate.
 */
object AmbientControl {

    private const val SEP = "|"

    /**
     * The keys a vendor's own always-on toggle writes, with the values that mean
     * OFF. All four move together when the toggle is used; `aod_display_type` is
     * the one that actually decides.
     */
    private val HONOR_OFF = linkedMapOf(
        "aod_switch" to "0",
        "aod_touch_time" to "0",
        "fingerprint_touch_time" to "0",
        "aod_display_type" to "0"
    )

    /**
     * Samsung, and the namespace is the whole story.
     *
     * Found the same way as Honor's, by diffing every Settings namespace across
     * a toggle - here One UI's own Sleep mode, on a Galaxy S23 / One UI 8,
     * 1 Sep 2026. Every AOD key on that phone lives in `Settings.System`, not
     * `Settings.Secure`, and that is the difference between an adb-only feature
     * and one an ordinary person can turn on: `Settings.System` is guarded by
     * WRITE_SETTINGS, which the user grants on a normal settings screen.
     *
     * `aod_mode` is the one that decides, proved end to end rather than
     * assumed: written to 1 the phone reports `aod_show_state=1` while dozing,
     * written to 0 it reports 0. `aod_tap_to_show_mode` is deliberately NOT
     * touched - it chooses between always-on and tap-to-show, which is the
     * user's preference about their own display, and we are only borrowing the
     * off switch for the night.
     */
    private val SAMSUNG_OFF = linkedMapOf("aod_mode" to "0")

    /** Which table the keys live in, and therefore who is allowed to write. */
    private enum class Table { SECURE, SYSTEM }

    private class Route(val off: Map<String, String>, val table: Table)

    /** Null on any device whose keys have not been confirmed by hand. */
    private fun route(): Route? = when {
        Build.MANUFACTURER.equals("HONOR", true) ||
            Build.MANUFACTURER.equals("HUAWEI", true) -> Route(HONOR_OFF, Table.SECURE)
        Build.MANUFACTURER.equals("samsung", true) -> Route(SAMSUNG_OFF, Table.SYSTEM)
        else -> null
    }

    private fun read(ctx: Context, r: Route, key: String): String? = when (r.table) {
        Table.SECURE -> Settings.Secure.getString(ctx.contentResolver, key)
        Table.SYSTEM -> Settings.System.getString(ctx.contentResolver, key)
    }

    private fun write(ctx: Context, r: Route, key: String, value: String) {
        when (r.table) {
            Table.SECURE -> Settings.Secure.putString(ctx.contentResolver, key, value)
            Table.SYSTEM -> Settings.System.putString(ctx.contentResolver, key, value)
        }
    }

    private fun permitted(ctx: Context, r: Route): Boolean = when (r.table) {
        Table.SECURE -> ctx.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED
        Table.SYSTEM -> Settings.System.canWrite(ctx)
    }

    fun canControl(ctx: Context): Boolean = route()?.let { permitted(ctx, it) } == true

    /**
     * The phone has a route we could use and the user has not granted it yet -
     * true only where the grant is actually ASKABLE. On the Secure route it is
     * not: WRITE_SECURE_SETTINGS has no prompt, so offering one would be a
     * button that cannot work.
     */
    fun needsGrant(ctx: Context): Boolean {
        val r = route() ?: return false
        return r.table == Table.SYSTEM && !permitted(ctx, r)
    }

    /** The system's own screen for granting WRITE_SETTINGS. */
    fun requestGrant(ctx: Context) {
        runCatching {
            ctx.startActivity(
                android.content.Intent(
                    Settings.ACTION_MANAGE_WRITE_SETTINGS,
                    ("package:" + ctx.packageName).let(android.net.Uri::parse)
                ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    /**
     * Called from [ZenController.setActive], which every path funnels through -
     * the alarms, boot, the UI and reconcile alike. That is deliberate: a phone
     * that dies mid-window comes back, reschedules, and restores the display
     * without needing a case of its own.
     */
    fun sync(ctx: Context, p: Prefs, windowActive: Boolean) {
        val r = route() ?: return
        if (!permitted(ctx, r)) return
        if (windowActive && p.fxHideAmbient) suppress(ctx, p, r) else restore(ctx, p, r)
    }

    private fun suppress(ctx: Context, p: Prefs, r: Route) {
        if (p.ambientSaved != null) return // already ours; do not save over it
        val prior = r.off.keys.mapNotNull { k -> read(ctx, r, k)?.let { "$k=$it" } }
        // Recorded BEFORE the first write, deliberately. A write that fails
        // halfway with no record behind it would leave the display off with
        // nothing able to put it back.
        p.ambientSaved = prior.joinToString(SEP)
        try {
            r.off.forEach { (k, v) -> write(ctx, r, k, v) }
            Journal.write(ctx, "ambient off (was " + prior.joinToString(" ") + ")")
        } catch (e: Exception) {
            Journal.write(ctx, "ambient off failed: " + e)
        }
    }

    private fun restore(ctx: Context, p: Prefs, r: Route) {
        val saved = p.ambientSaved ?: return
        try {
            saved.split(SEP).filter { it.isNotEmpty() }.forEach {
                write(ctx, r, it.substringBefore('='), it.substringAfter('='))
            }
            // Cleared only on success, so a failed restore is retried by the next
            // sync rather than forgotten with the display still off.
            p.ambientSaved = null
            Journal.write(ctx, "ambient restored (" + saved.replace(SEP, " ") + ")")
        } catch (e: Exception) {
            Journal.write(ctx, "ambient restore failed: " + e)
        }
    }
}
