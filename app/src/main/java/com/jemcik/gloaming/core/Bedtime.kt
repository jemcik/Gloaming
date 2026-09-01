package com.jemcik.gloaming.core

import android.content.Context

/**
 * The master switch, as one function, so the two places that flip it cannot
 * drift apart.
 *
 * The app bar's switch and the Quick Settings tile are the same control shown
 * twice, and the tile is the one that runs with no Activity, no Compose state
 * and no `HomeState` to lean on. Duplicating the sequence there would have been
 * three lines and a latent bug: the order matters - cancel the alarms and drop
 * zen BEFORE rescheduling, or `rescheduleAll` re-arms what was just cancelled.
 *
 * Switching OFF mid-window is deliberately cheap and needs no confirmation.
 * Measured on the phone: off gives `zen_mode` 0 with `activeDay` cleared, and
 * one tap back on gives `zen_mode` 1 with `activeDay` re-derived, the END alarm
 * restored to the same minute and the next START re-queued. Nothing is spent,
 * so there is nothing to warn about - which is what makes a tile safe to put
 * one thumb away from a sleeping person.
 */
object Bedtime {

    fun set(ctx: Context, p: Prefs, on: Boolean) {
        p.enabled = on
        if (!on) {
            Scheduler.cancelAll(ctx)
            ZenController.setActive(ctx, p, false)
        }
        Scheduler.rescheduleAll(ctx, p)
    }

    /**
     * Armed is not running, and the difference is the whole reason the switch
     * carries two icons. See [Scheduler.isActiveNow]: a window is running only
     * once its night has begun.
     */
    fun runningNow(ctx: Context, p: Prefs): Boolean = Scheduler.isActiveNow(
        p, alarm = if (p.exitAtAlarm) Scheduler.nextAlarm(ctx) else null
    )
}
