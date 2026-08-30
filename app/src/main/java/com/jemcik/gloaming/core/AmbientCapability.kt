package com.jemcik.gloaming.core

import android.content.Context
import android.provider.Settings

/**
 * ZenDeviceEffects.setShouldSuppressAmbientDisplay targets AOSP's ambient
 * display, driven by Settings.Secure "doze_always_on". A vendor that ships its
 * own always-on implementation and never reads that key will accept the request
 * into the rule and silently ignore it.
 *
 * Half of this is observable and half is not, and the distinction cost a day.
 * Whether the PLATFORM asked is easy: `dumpsys power` prints
 * AmbientDisplaySuppressionController with ambientDisplaySuppressed and
 * mSuppressionTokens. Whether the VENDOR obeyed is the hard half, and the
 * obvious readouts all measure nothing - mWakefulness, Display State,
 * aod_doze_state, the AOD layers in SurfaceFlinger, window focus, that layer's
 * frame counter, and screencap read identically with AOD switched on and off.
 * Do not reach for them. See CLAUDE.md for the table.
 *
 * Measured on the Honor BKQ-N49, 30 Aug 2026, with the one witness that does
 * move - tapping the sleeping screen and looking, validated by a negative
 * control so it is known to be capable of reading false. With `cmd power
 * suppress-ambient-display` armed, the platform's own lever, a tap shows the
 * clock exactly as it does with the token released. So the platform records the
 * request and com.hihonor.aod ignores it.
 *
 * Honor's AOD is not beyond reach, though: its Settings toggle writes four
 * Secure keys together (aod_display_type, aod_switch, aod_touch_time,
 * fingerprint_touch_time) and writing them back restores it. aod_display_type is
 * the gate; aod_switch alone does nothing, which is what made an earlier attempt
 * call them inert. Those are Settings.Secure, so writing needs
 * WRITE_SECURE_SETTINGS - not holdable by an ordinary install, grantable only
 * over adb. Hence still no switch here.
 *
 * The inference below therefore stands for Honor, now measured rather than
 * guessed. The OTHER branch - doze_always_on present, so we return true and ship
 * a live switch - has NOT been verified on any device, and that is the
 * permissive direction. Check it with the tap protocol, not with dumpsys.
 *
 * VERIFIED, not guessed. Add an entry only after confirming on real hardware
 * that doze_always_on is absent and the vendor key is present.
 */
object AmbientCapability {

    /** Vendor keys confirmed to accompany a parallel AOD implementation. */
    private val KNOWN_PARALLEL_AOD = listOf(
        "aod_switch" // Honor MagicOS 10 / Huawei EMUI - confirmed on BKQ-N49
    )

    fun isSupported(ctx: Context): Boolean {
        val cr = ctx.contentResolver
        // The AOSP key exists: the effect has something to act on.
        if (Settings.Secure.getString(cr, "doze_always_on") != null) return true
        // No AOSP key, and a confirmed vendor implementation instead.
        val parallel = KNOWN_PARALLEL_AOD.any { Settings.Secure.getString(cr, it) != null }
        return !parallel
    }
}
