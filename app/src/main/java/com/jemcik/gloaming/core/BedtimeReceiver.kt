package com.jemcik.gloaming.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BedtimeReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val p = Prefs(ctx)
        // Boot and upgrade re-assert the zen state rather than trusting what the
        // rule claims: across a reboot the rule's condition survives as
        // STATE_TRUE while zen_mode is reset to 0, so believing it loses the
        // window. START and END force for their own reasons, below.
        var force = false
        when (intent.action) {
            Scheduler.ACTION_START -> {
                Journal.write(ctx, "START fired")
                ZenController.setActive(ctx, p, true, force = true)
            }
            Scheduler.ACTION_END -> {
                // How late, not just that it happened. Punctual is the norm - the
                // scheduled second, measured in forced light and deep idle - so a
                // number here at all is the interesting case.
                val late = (System.currentTimeMillis() - p.endDue) / 1000
                Journal.write(
                    ctx,
                    if (p.endDue != Prefs.NO_DUE && late > 2) "END fired ${late}s late"
                    else "END fired"
                )
                AlarmWatch.handled(p)
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
                force = true
            }
            Scheduler.ACTION_PROBE -> {
                // Nothing to do but notice. Arriving at all is the whole answer.
                val now = System.currentTimeMillis()
                val late = (now - p.probeDue) / 1000
                BackgroundProbe.handled(p, now)
                Journal.write(
                    ctx,
                    "background probe arrived (${late}s)" +
                        if (BackgroundProbe.blocked(p)) " - TOO LATE, phone is holding us" else ""
                )
                return
            }
            else -> return
        }
        // Always rearm: exact alarms are one-shot.
        Scheduler.rescheduleAll(ctx, p, force)
    }
}
