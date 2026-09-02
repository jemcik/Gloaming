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
 *
 * And so is [Prefs.launchTipSeen], for a reason worth stating as a rule: A RESET
 * MAY DISCARD ANYTHING THE APP CAN RE-LEARN. The delivery probe re-runs, the
 * boot stamp re-adopts, background restriction is read fresh - all measurable,
 * so losing them costs nothing but a little time. The launch tip is the one
 * flag that can NEVER be re-derived: Honor's auto-launch state is unreadable,
 * which is the whole reason that card is a refusable offer rather than a
 * notice. Those two switches also live on the PHONE, not in this app, so they
 * survive a reset untouched - clearing the flag re-offers advice for something
 * still configured, and nothing here could ever find out. Reported after
 * exactly that: reset the app, and was told again to set up something already
 * set up.
 */
object Reset {

    fun toDefaults(ctx: Context) {
        val p = Prefs(ctx)
        val tipAnswered = p.launchTipSeen
        Bedtime.set(ctx, p, false)
        ZenController.removeRule(ctx, p)
        p.clear()
        // After the wipe, and only when it was true: a reset must not be able
        // to ANSWER the offer on the user's behalf either.
        if (tipAnswered) p.launchTipSeen = true
        // After the wipe, so it is the first line of the new life rather than
        // the last of the old one - and so a report sent later still says that
        // a reset happened, which explains a store that looks untouched.
        Journal.write(ctx, "reset to defaults")
    }
}
