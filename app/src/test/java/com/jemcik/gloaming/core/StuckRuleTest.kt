package com.jemcik.gloaming.core

import android.app.NotificationManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * "The phone is not filtering while we want zen on" - when is that a fault?
 *
 * The question matters more than it looks, because the answer decides whether
 * [ZenController.setActive] may take its early return. Get it wrong in the
 * permissive direction and a reboot leaves Do Not Disturb off for a whole
 * night; get it wrong in the other and the rule is rewritten on every call,
 * which does not merely waste a push - it loops, because a rewrite makes the
 * system broadcast a status change that lands straight back here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StuckRuleTest {

    private val all = NotificationManager.INTERRUPTION_FILTER_ALL
    private val priority = NotificationManager.INTERRUPTION_FILTER_PRIORITY

    @Test
    fun `wanting zen on while nothing is filtered is the fault it was written for`() {
        // The reboot case: the rule's condition survives as TRUE, zen_mode is
        // reset to 0, and the two disagree. The filter is the honest reading.
        assertTrue(ZenController.looksStuck(active = true, wantsDnd = true, filter = all))
    }

    @Test
    fun `a rule that MEANT to filter nothing is not stuck`() {
        // With the Do Not Disturb switch off the rule sets INTERRUPTION_FILTER_ALL
        // on purpose - it is there to carry grayscale and wallpaper dimming and
        // to filter nothing at all. Reading that back as a fault made the
        // condition permanently true, so the early return could never be taken
        // and every call rewrote the rule. Reported from the phone as "grayscale
        // and dim wallpaper only work if Do Not Disturb is on", which is what
        // the resulting flicker looks like: each rewrite clears the condition,
        // and the effects go with it until it is re-asserted a millisecond later.
        assertFalse(ZenController.looksStuck(active = true, wantsDnd = false, filter = all))
    }

    @Test
    fun `a filtering phone is never stuck, whatever the switch says`() {
        assertFalse(ZenController.looksStuck(active = true, wantsDnd = true, filter = priority))
        assertFalse(ZenController.looksStuck(active = true, wantsDnd = false, filter = priority))
    }

    @Test
    fun `nothing is stuck when we are switching the night OFF`() {
        // `active = false` is the END of a window. There is nothing to be stuck
        // about: not filtering is exactly what we are asking for.
        assertFalse(ZenController.looksStuck(active = false, wantsDnd = true, filter = all))
        assertFalse(ZenController.looksStuck(active = false, wantsDnd = false, filter = all))
    }
}
