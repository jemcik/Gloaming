package com.jemcik.gloaming.core

import android.content.BroadcastReceiver
import android.content.Context
import android.app.AlarmManager
import android.content.Intent
import com.jemcik.gloaming.ui.BedtimeTile
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class BedtimeReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val p = Prefs(ctx)
        // Everything but the clock app's broadcast re-asserts the zen state
        // rather than trusting what the rule claims: across a reboot the
        // rule's condition survives as STATE_TRUE while zen_mode is reset to
        // 0, so believing it loses the window; and an alarm is the moment the
        // state is meant to change, so "the system already says so" is not a
        // reason to skip. The reschedule at the foot does the asserting, ONCE,
        // after it has decided what the state should be. START used to switch
        // zen on here and reschedule after, and a START landing early beyond
        // the tolerance was then on for a moment and off again when the
        // reschedule found the window not yet begun - a blink, and a posted
        // "Do Not Disturb is on" at a bedtime that had not started.
        var force = false
        // Boot and upgrade: the clock app may not have re-registered its
        // alarms yet, so "no alarm" is not yet a fact. See rescheduleAll.
        var alarmsKnown = true
        // The instant the schedule is judged at. Now, except for an alarm that
        // landed a little EARLY, which is judged at the instant it was armed
        // for - the Honor delivers on a five-minute grid of its own, and an END
        // forty seconds early otherwise ends the night and walks straight back
        // into it. See Delivery.asOf.
        var asOf = System.currentTimeMillis()
        when (intent.action) {
            Scheduler.ACTION_START -> {
                val due = intent.getLongExtra(Scheduler.EXTRA_DUE, Prefs.NO_DUE)
                Journal.write(ctx, "START fired" + offset(due, asOf))
                asOf = Delivery.asOf(due, asOf)
                force = true
            }
            Scheduler.ACTION_END -> {
                // How late, not just that it happened. Punctual is the norm - the
                // scheduled second, measured in forced light and deep idle - so a
                // number here at all is the interesting case.
                val endNow = System.currentTimeMillis()
                // The instant THIS alarm was armed for. Not p.endDue, which the
                // resume's reschedule may already have moved to tomorrow by the
                // time a parked END lands; see AlarmWatch.handled.
                val due = intent.getLongExtra(Scheduler.EXTRA_DUE, p.endDue)
                val open = AlarmWatch.appOpen()
                Journal.write(ctx, "END fired" + offset(due, endNow) + (if (open) ", app on screen" else ""))
                AlarmWatch.handled(p, due, endNow, open)
                asOf = Delivery.asOf(due, endNow)
                // Drop the pin first: if the alarm lands a hair early, a live
                // pin would reopen the window and rearm END for the same
                // instant, over and over.
                p.activeDay = Prefs.NO_DAY
                // And record the night as OVER, by the instant this END was
                // due. The reschedule below reads the next alarm afresh, and
                // by now the clock app has moved it to tomorrow - so judged
                // from the handles the night would still contain this moment
                // and be re-entered. See Scheduler.over. The due instant, not
                // the landing: a parked END released tonight must not end
                // tonight's night. And only when this END is judged to have
                // ENDED anything: one that lands early beyond the tolerance
                // is judged at its landing (Delivery.asOf), the window
                // re-opens and a fresh END is armed for the real end - writing
                // the future due here would have closed the night early and
                // silently instead.
                val ended = if (due != Prefs.NO_DUE) due else endNow
                if (asOf >= ended) p.endedAt = ended
                // A one-off is done: switch the app off rather than leaving it
                // armed with nothing to run.
                if (Scheduler.isOneOff(p.days)) {
                    p.enabled = false
                    Journal.write(ctx, "one-off finished - switching off")
                }
                force = true
            }
            AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED -> {
                // Only interesting when the alarm is allowed to end the night.
                // Re-deriving costs a reschedule; doing it for everyone would
                // rewrite the rule every time any clock app is touched. The
                // end moves either way - earlier or later - and a night the
                // END has already closed stays closed (Prefs.endedAt), so an
                // alarm snoozed or re-set the moment it rang does not reopen
                // the morning.
                if (!p.exitAtAlarm) return
                Journal.write(ctx, "next alarm changed - re-deriving the end")
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
                alarmsKnown = false
                // syncRule skips identical rules, so nothing would ever repair
                // one edited from Settings. Boot and upgrade force a push.
                p.ruleSignature = null
                force = true
                // Before anything reads it: the reboot cancelled the probe's
                // alarm, so the outstanding question has no answer coming and
                // must not be scored as one. Void it, then ask again.
                BackgroundProbe.voidPending(p)
                Scheduler.armProbe(ctx, p)
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
        Scheduler.rescheduleAll(
            ctx, p, force,
            from = LocalDateTime.ofInstant(Instant.ofEpochMilli(asOf), ZoneId.systemDefault()),
            alarmsKnown = alarmsKnown
        )
        // The shade cannot see any of this happen. Ask the tile to re-read, or
        // it keeps showing the face it had when it was last looked at - a tick
        // through the whole night that should have been a moon.
        BedtimeTile.refresh(ctx)
    }

    /**
     * " 257s late", " 40s early", or nothing - the journal's one number for an
     * alarm. Early is worth a word too now: it is how the Honor's grid was read
     * off the phone.
     */
    private fun offset(due: Long, now: Long): String {
        if (due == Prefs.NO_DUE) return ""
        val s = (now - due) / 1000
        return when {
            s > 2 -> " ${s}s late"
            s < -2 -> " ${-s}s early"
            else -> ""
        }
    }
}
