package com.jemcik.gloaming.core

import android.content.Context
import android.content.Intent
import android.provider.Settings

object AmbientSettings {

    /**
     * Honor locks every route to its Always On Display screen:
     *   com.hihonor.aod/.ui.AODSettingsActivity - WRITE_SECURE_SETTINGS
     *   settings/.navigation.HomeStyleActivity  - HW_SIGNATURE_OR_SYSTEM
     *   settings/.LauncherModeSettingsActivity  - not exported
     * Display settings is the closest an app is permitted to get. DontKillMyApp
     * reaches the same conclusion for Huawei and prints the path instead.
     */
    fun open(ctx: Context) {
        runCatching { ctx.startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS)) }
    }

    /** Only shown where the path is known to be correct. */
    fun locationHint(): String? = when {
        android.os.Build.MANUFACTURER.equals("HONOR", true) ||
            android.os.Build.MANUFACTURER.equals("HUAWEI", true) ->
            "Settings \u203A Home screen & style \u203A Always On Display"
        else -> null
    }
}
