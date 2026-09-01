package com.jemcik.gloaming.core

import android.content.res.Configuration
import android.content.Context
import android.os.Build

/**
 * Does this phone actually APPLY the zen rule's device effects?
 *
 * Every other capability question in this app is answered by probing, and this
 * one cannot be, which is why it is written down at length rather than hidden in
 * a boolean.
 *
 * WHAT WAS MEASURED. On a Galaxy S23, One UI 8 / Android 16, 1 Sep 2026: the
 * rule reads `state=STATE_TRUE` carrying
 * `deviceEffects=[grayscale, dimWallpaper, nightMode]`, Do Not Disturb genuinely
 * filters - and NOT ONE of the effects happens. `Global saturation` false,
 * `cmd uimode night` no, wallpaper `adaptive dim: 0.0`. Night mode was
 * re-checked across a screen-off cycle because AOSP defers it there on purpose.
 * It is not a capability gap either: One UI's own Sleep mode flips the very same
 * global saturation, so the machinery exists and third-party rules were simply
 * never wired to it.
 *
 * WHY A MANUFACTURER TEST, against this codebase's own rule. Because the honest
 * probe does not exist. Reading back what was applied needs
 * `ColorDisplayManager.isSaturationActivated` and
 * `WallpaperManager.getWallpaperDimAmount`, both `@hide` - checked, neither
 * compiles against compileSdk 37. Night mode alone is readable, through
 * `Configuration.UI_MODE_NIGHT_MASK`, and that is what [observeApplied] uses.
 * So the manufacturer is the PRIOR, never the verdict.
 *
 * AND IT EXPIRES BY ITSELF. A single observation of night mode actually
 * following our rule overrules the prior permanently, on that device, with no
 * update from us - see [observeApplied]. The day Samsung wires their applier up,
 * the section comes back for anyone who had the dark theme effect on. That is
 * the closest thing to a probe available, and it fails in the safe direction:
 * the worst case is a hidden section on a phone that could have shown it.
 *
 * RE-TEST ON EVERY MAJOR ONE UI. `tools/check.sh` on a Galaxy is the instrument:
 * run a live window and read `dumpsys color_display`.
 */
object ScreenEffects {

    private val IGNORES_EFFECTS = setOf("samsung")

    /** True unless this phone is known to throw the effects away. */
    fun applied(ctx: Context): Boolean {
        val p = Prefs(ctx)
        if (p.effectsSeen) return true
        return !IGNORES_EFFECTS.contains(Build.MANUFACTURER.lowercase())
    }

    /**
     * Watch for night mode FOLLOWING our rule, and only then overrule the prior.
     *
     * A TRANSITION, never a state, and that distinction is not theoretical: the
     * first version asked "is the screen dark while we want it dark?", which on
     * the Galaxy answered yes - because the phone had been in dark mode all
     * along for its own reasons. It recorded proof that the effects worked on
     * the one device measured to ignore them, and un-hid the broken switches.
     *
     * So the evidence is the EDGE: night mode observed OFF while our rule was
     * idle, then ON while the same rule is active and asking for it. A phone
     * already dark contributes nothing, which is correct - it has shown us
     * nothing. Still one-directional: seeing night mode off during a window
     * proves nothing either, since a vendor may defer it to screen-off.
     */
    fun observeApplied(ctx: Context, wantsNight: Boolean, ruleActive: Boolean) {
        val p = Prefs(ctx)
        if (p.effectsSeen) return
        val night = ctx.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

        if (!ruleActive) {
            // The baseline, and only while nothing of ours is asking.
            p.nightWhenIdle = if (night) 1 else 0
            return
        }
        if (!wantsNight) return
        if (night && p.nightWhenIdle == 0) {
            p.effectsSeen = true
            Journal.write(ctx, "device effects observed - night mode followed the rule")
        }
    }
}
