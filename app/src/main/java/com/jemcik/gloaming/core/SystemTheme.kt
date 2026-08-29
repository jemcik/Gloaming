package com.jemcik.gloaming.core

import android.content.Context
import android.content.res.Configuration
import android.os.Build

/**
 * Whether the system is currently in dark mode.
 *
 * No app can *set* this: MODIFY_DAY_NIGHT_MODE is role-managed, so even
 * `pm grant` refuses it ("managed by role"). Writing Settings.Secure
 * ui_night_mode does not work either - UiModeManagerService holds the live
 * state and ignores writes behind its back. Only the role holder (Digital
 * Wellbeing) and shell can change it.
 *
 * We can still ask for it through ZenDeviceEffects.setShouldUseNightMode and
 * observe whether the vendor honours it. MagicOS accepts the request into the
 * rule and never applies it; a Pixel is expected to apply it.
 */
object SystemTheme {

    /** Changes when the device takes an OS update, which may change the verdict. */
    fun buildId(): String = Build.FINGERPRINT

    /**
     * Compares the current theme against the baseline captured at bedtime start.
     * Returns 1 (applied), 2 (ignored), or 0 when the result proves nothing
     * because the system was already dark beforehand.
     */
    fun verdict(ctx: Context, baselineWasDark: Boolean): Int = when {
        baselineWasDark -> 0
        isDark(ctx) -> 1
        else -> 2
    }

    fun isDark(ctx: Context): Boolean =
        (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
}
