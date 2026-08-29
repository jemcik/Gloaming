package com.jemcik.gloaming.ui

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * Compose's HapticFeedbackType exposes a handful of effects; the platform
 * constants carry the full vocabulary, and at minSdk 34 all of these are
 * available. Distinct textures matter here: a drag that ticks the same way at
 * every step tells you nothing about where the hour boundaries are.
 *
 * One effect per KIND of interaction, applied the same way everywhere:
 *
 *   toggle   something turned on or off - switch, chip, day
 *   select   one of several chosen - preset, sheet option, centre reading
 *   open     a sheet, a picker or another screen appears
 *   confirm  a decision committed in a dialog
 *   grab / tick / hourTick / release   the dial's own drag vocabulary
 */
class Haptics(private val view: View) {
    private fun fire(constant: Int) = view.performHapticFeedback(constant)

    /** Each 5-minute step while dragging a handle. */
    fun tick() = fire(HapticFeedbackConstants.SEGMENT_FREQUENT_TICK)

    /** Crossing a whole hour - deliberately heavier than tick(). */
    fun hourTick() = fire(HapticFeedbackConstants.SEGMENT_TICK)

    fun grab() = fire(HapticFeedbackConstants.GESTURE_START)
    fun release() = fire(HapticFeedbackConstants.GESTURE_END)

    fun toggle(on: Boolean) = fire(
        if (on) HapticFeedbackConstants.TOGGLE_ON else HapticFeedbackConstants.TOGGLE_OFF
    )

    /**
     * Picking one of several: a preset, an option in a sheet, the next reading
     * in the dial's centre. Not a toggle - nothing turned on or off - and not a
     * confirmation, which is a decision being committed.
     */
    fun select() = fire(HapticFeedbackConstants.CLOCK_TICK)

    /** Opening something: a sheet, the time picker, another screen. */
    fun open() = fire(HapticFeedbackConstants.VIRTUAL_KEY)

    /** Committing a decision in a dialog - Set, End now. */
    fun confirm() = fire(HapticFeedbackConstants.CONFIRM)

    fun reject() = fire(HapticFeedbackConstants.REJECT)
}

@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    return remember(view) { Haptics(view) }
}
