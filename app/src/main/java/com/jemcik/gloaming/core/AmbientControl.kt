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

    /** Null on any device whose keys have not been confirmed by hand. */
    private fun offValues(): Map<String, String>? = when {
        Build.MANUFACTURER.equals("HONOR", true) ||
            Build.MANUFACTURER.equals("HUAWEI", true) -> HONOR_OFF
        else -> null
    }

    fun canControl(ctx: Context): Boolean =
        offValues() != null &&
            ctx.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Called from [ZenController.setActive], which every path funnels through -
     * the alarms, boot, the UI and reconcile alike. That is deliberate: a phone
     * that dies mid-window comes back, reschedules, and restores the display
     * without needing a case of its own.
     */
    fun sync(ctx: Context, p: Prefs, windowActive: Boolean) {
        val off = offValues() ?: return
        if (!canControl(ctx)) return
        if (windowActive && p.fxHideAmbient) suppress(ctx, p, off) else restore(ctx, p)
    }

    private fun suppress(ctx: Context, p: Prefs, off: Map<String, String>) {
        if (p.ambientSaved != null) return // already ours; do not save over it
        val cr = ctx.contentResolver
        val prior = off.keys.mapNotNull { k ->
            Settings.Secure.getString(cr, k)?.let { "$k=$it" }
        }
        // Recorded BEFORE the first write, deliberately. A write that fails
        // halfway with no record behind it would leave the display off with
        // nothing able to put it back.
        p.ambientSaved = prior.joinToString(SEP)
        try {
            off.forEach { (k, v) -> Settings.Secure.putString(cr, k, v) }
            Journal.write(ctx, "ambient off (was " + prior.joinToString(" ") + ")")
        } catch (e: Exception) {
            Journal.write(ctx, "ambient off failed: " + e)
        }
    }

    private fun restore(ctx: Context, p: Prefs) {
        val saved = p.ambientSaved ?: return
        val cr = ctx.contentResolver
        try {
            saved.split(SEP).filter { it.isNotEmpty() }.forEach {
                Settings.Secure.putString(cr, it.substringBefore('='), it.substringAfter('='))
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
