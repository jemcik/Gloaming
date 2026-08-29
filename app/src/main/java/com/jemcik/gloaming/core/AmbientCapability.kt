package com.jemcik.gloaming.core

import android.content.Context
import android.provider.Settings

/**
 * ZenDeviceEffects.setShouldSuppressAmbientDisplay targets AOSP's ambient
 * display, driven by Settings.Secure "doze_always_on". A vendor that ships its
 * own always-on implementation and never reads that key will accept the request
 * into the rule and silently ignore it.
 *
 * There is no way to observe this the way night mode can be observed: AOD only
 * appears once the screen is off, so nothing is measurable while the app runs.
 * Everything below is therefore inference, and it is deliberately narrow -
 * only devices positively identified as running a parallel implementation are
 * treated as unsupported. Everywhere else we ask and let the system answer,
 * because a switch that might work beats a chip that definitely does nothing.
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
