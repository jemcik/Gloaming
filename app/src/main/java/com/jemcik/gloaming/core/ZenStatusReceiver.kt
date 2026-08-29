package com.jemcik.gloaming.core

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * The system tells a rule's owner when that rule is enabled, disabled or
 * removed. It is the only warning we get that someone edited our rule from the
 * phone's own Do Not Disturb screen - which on this device offers a Delete
 * button and a toggle, either of which ends the night without a word.
 *
 * Exported, because the sender is the system and a private receiver would never
 * hear it. [BedtimeReceiver] stays private: it takes the alarms, and nothing
 * outside this app has any business firing those. Nothing here trusts the
 * broadcast's contents either - [ZenController.reconcile] goes and reads the
 * rule itself.
 */
class ZenStatusReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != NotificationManager.ACTION_AUTOMATIC_ZEN_RULE_STATUS_CHANGED) return
        ZenController.reconcile(ctx, Prefs(ctx))
    }
}
