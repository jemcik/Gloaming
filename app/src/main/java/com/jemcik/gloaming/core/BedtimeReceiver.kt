package com.jemcik.gloaming.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BedtimeReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val p = Prefs(ctx)
        when (intent.action) {
            Scheduler.ACTION_START -> {
                Journal.write(ctx, "START fired")
                ZenController.setActive(ctx, p, true, force = true)
            }
            Scheduler.ACTION_END -> {
                Journal.write(ctx, "END fired")
                // Drop the pin first: if the alarm lands a hair early, a live
                // pin would reopen the window and rearm END for the same
                // instant, over and over.
                p.activeDay = Prefs.NO_DAY
                // A one-off is done: switch the app off rather than leaving it
                // armed with nothing to run.
                if (Scheduler.isOneOff(p.days)) {
                    p.enabled = false
                    Journal.write(ctx, "one-off finished - switching off")
                }
                ZenController.setActive(ctx, p, false, force = true)
            }
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                // Name which one. They are very different events and the
                // shared line cost real time: a clock change re-delivered
                // BOOT_COMPLETED mid-boot, and the journal could not say
                // whether that was a boot, an upgrade, or a second copy of the
                // same boot.
                Journal.write(
                    ctx,
                    if (intent.action == Intent.ACTION_BOOT_COMPLETED) "boot - rearming"
                    else "upgrade - rearming"
                )
                // Only a real boot, and only when it actually reached us: this
                // is the whole evidence that the vendor is not withholding the
                // broadcast. An app upgrade is not a boot and must not clear it.
                if (intent.action == Intent.ACTION_BOOT_COMPLETED) BootWatch.record(p)
                // syncRule skips identical rules, so nothing would ever repair
                // one edited from Settings. Boot and upgrade force a push.
                p.ruleSignature = null
            }
            else -> return
        }
        // Always rearm: exact alarms are one-shot.
        Scheduler.rescheduleAll(ctx, p)
    }
}
