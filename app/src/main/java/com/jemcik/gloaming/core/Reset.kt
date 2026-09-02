package com.jemcik.gloaming.core

import android.content.Context

/**
 * Back to a fresh install - in the one order that is safe.
 *
 * This exists because the obvious route is a trap. Android's own "Clear
 * storage" wipes the prefs and nothing else, and [Prefs.ruleId] is the app's
 * ONLY handle on its rule: the rule survives, still enabled, still applying its
 * device effects, while the app that owned it has forgotten it exists. Measured
 * on the Honor after a `pm clear` - bedtime read off, `zen_mode` read 0, and the
 * screen stayed grey, with nothing on screen able to explain it or clear it.
 *
 * So the order below is the feature, and every step is load-bearing:
 *
 *  1. SWITCH OFF FIRST. That ends the window, drops zen, cancels both alarms -
 *     and, through [ZenController.setActive], hands [AmbientControl] the chance
 *     to put the vendor's always-on keys back. That restore reads
 *     [Prefs.ambientSaved], which is about to be deleted: do it after the wipe
 *     and the only record of what the display was set to is gone, leaving it off
 *     for good with nothing able to retry.
 *  2. REMOVE THE RULE, which also sweeps any we have lost the id for. After
 *     step 4 no id exists to remove it by.
 *  3. Only now, clear the store.
 *
 * The journal is deliberately kept. Someone resetting is usually resetting
 * because something went wrong, and it is the only record of what.
 */
object Reset {

    fun toDefaults(ctx: Context) {
        val p = Prefs(ctx)
        Bedtime.set(ctx, p, false)
        ZenController.removeRule(ctx, p)
        p.clear()
        // After the wipe, so it is the first line of the new life rather than
        // the last of the old one - and so a report sent later still says that
        // a reset happened, which explains a store that looks untouched.
        Journal.write(ctx, "reset to defaults")
    }
}
