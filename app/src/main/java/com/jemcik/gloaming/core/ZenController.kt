package com.jemcik.gloaming.core

import android.app.AutomaticZenRule
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import android.service.notification.Condition
import android.service.notification.ZenDeviceEffects
import android.service.notification.ZenPolicy
import com.jemcik.gloaming.MainActivity

/**
 * Owns the AutomaticZenRule carrying the DND policy and the screen effects.
 *
 * The rule machinery itself works fine on MagicOS - only Google's
 * WorkManager-based trigger is blocked, which is why we drive state ourselves.
 */
object ZenController {

    private const val RULE_NAME = "Gloaming"
    val CONDITION: Uri = "condition://com.jemcik.gloaming/bedtime".toUri()

    private fun nm(ctx: Context) = ctx.getSystemService(NotificationManager::class.java)

    fun hasDndAccess(ctx: Context) = nm(ctx).isNotificationPolicyAccessGranted

    /**
     * What the SYSTEM reports is in effect right now - not what we believe we
     * set. Worth asking separately: this app exists because the platform does
     * not always do what an app asks it to.
     */
    fun currentFilter(ctx: Context): Int = nm(ctx).currentInterruptionFilter

    private fun buildRule(ctx: Context, p: Prefs): AutomaticZenRule {
        val policy = ZenPolicy.Builder()
            .allowAlarms(p.allowAlarms)
            .allowMedia(p.allowMedia)
            .allowRepeatCallers(p.allowRepeatCallers)
            .allowCalls(p.allowCalls)
            .allowMessages(p.allowMessages)
            .allowConversations(p.allowConversations)
            .allowReminders(p.allowReminders)
            .allowEvents(p.allowEvents)
            .allowSystem(false)
            // The seven visual effects, PINNED rather than inherited. Left
            // unset they are filled in from whatever the phone's default Do Not
            // Disturb policy happened to be, which is not a decision anyone
            // made. Measured identical on a OnePlus/LineageOS and a Magic8 Pro
            // 2 Sep 2026 - luck, not a guarantee. A phone whose default allowed
            // peek would serve heads-up banners at 3am and nothing here would
            // know, because there is no way to read these back: the getter is
            // on a NotificationListenerService ranking, which needs a grant far
            // heavier than this app asks for.
            //
            // THREE OF THESE DO NOTHING, and the measurement matters because
            // they were proposed as the fix for a mail found on the lock screen
            // at 03:02 and they are not it. Measured on both phones 2 Sep 2026,
            // Android 16: rule active, notification intercepted, the record
            // carrying suppressedVisualEffects=511 - all nine bits, with
            // NOTIFICATION_LIST and STATUS_BAR among them - and BOTH SystemUIs
            // drew it on the lock screen regardless. AOSP's own did; so did
            // MagicOS. Google's wellbeing Bedtime rule carries the identical
            // seven and fares no better, so this is the platform, not us.
            //
            // They stay because the four that DO work - peek, lights, ambient,
            // fullScreenIntent - are worth pinning, and the other three cost
            // nothing and are what the API documents. But nothing in the UI may
            // ever claim they hide anything. The only route that actually hides
            // a notification overnight is Settings.Secure
            // lock_screen_show_notifications, behind WRITE_SECURE_SETTINGS -
            // AmbientControl's terms exactly, and adb-only.
            .showPeeking(false)
            .showLights(false)
            .showInAmbientDisplay(false)
            .showFullScreenIntent(false)
            .showStatusBarIcons(false)
            .showBadges(false)
            .showInNotificationList(false)
            .build()

        // All four effects are public API since Android 15 and need no permission
        // beyond notification policy access.
        val effects = ZenDeviceEffects.Builder()
            .setShouldDisplayGrayscale(p.fxGrayscale)
            .setShouldDimWallpaper(p.fxDimWallpaper)
            .setShouldUseNightMode(p.fxDarkTheme)
            .setShouldSuppressAmbientDisplay(
                p.fxHideAmbient && AmbientCapability.isSupported(ctx)
            )
            .build()

        // NOTE: TYPE_BEDTIME is reserved for the SYSTEM_WELLBEING role holder.
        // A configurationActivity is REQUIRED - without a ConditionProviderService
        // or a config activity the system throws "Lacking enabled CPS or config activity".
        return AutomaticZenRule.Builder(RULE_NAME, CONDITION)
            .setType(AutomaticZenRule.TYPE_OTHER)
            .setIconResId(com.jemcik.gloaming.R.drawable.ic_gloaming)
            .setTriggerDescription(
                Clock.hhmm(ctx, p.startTime) + " \u2013 " + Clock.hhmm(ctx, p.endTime)
            )
            .setManualInvocationAllowed(true)
            .setInterruptionFilter(
                if (p.fxDnd) NotificationManager.INTERRUPTION_FILTER_PRIORITY
                else NotificationManager.INTERRUPTION_FILTER_ALL
            )
            .setZenPolicy(policy)
            .setDeviceEffects(effects)
            .setConfigurationActivity(ComponentName(ctx, MainActivity::class.java))
            .setEnabled(true)
            .build()
    }

    private const val COSMETIC = "\u2016"

    /** Everything buildRule reads that the rule ACTS on. */
    private fun liveSignature(ctx: Context, p: Prefs) = listOf(
        p.fxDnd, p.fxGrayscale, p.fxDimWallpaper, p.fxDarkTheme,
        // What the RULE carries, not the raw pref. Where the effect is not
        // supported the pref moves and the rule does not, and signing the pref
        // would push an identical rule - which blinks a live one for nothing.
        p.fxHideAmbient && AmbientCapability.isSupported(ctx),
        p.allowCalls, p.allowMessages, p.allowConversations, p.allowRepeatCallers,
        p.allowAlarms, p.allowMedia, p.allowReminders, p.allowEvents
    ).joinToString("|")

    /**
     * Everything buildRule reads. Same signature, same rule, nothing to push.
     *
     * The tail past [COSMETIC] changes nothing about what the rule does - the
     * times only compose the "22:30 - 08:00" line the system shows on its own
     * settings screen - so a live rule does not get rewritten for it.
     */
    private fun signature(ctx: Context, p: Prefs) =
        liveSignature(ctx, p) + COSMETIC + p.startTime + "-" + p.endTime

    /**
     * What the rule CARRIES after a sync, which is not always what we asked it
     * to carry.
     *
     * A cosmetic-only change to a LIVE rule is skipped on purpose - every
     * rewrite blinks Do Not Disturb off and on - so the new caption is still
     * OWED. Recording [sig] regardless says the opposite: the next sync compares
     * equal, finds nothing to do, and the phone's own Do Not Disturb screen
     * keeps the old times for good. Measured on a OnePlus on 3 Sep 2026 - the
     * app read 18:45 to 08:00 while the rule still said "6:20 pm - 8:00 am", and
     * a whole START activation ran back through here without repairing it,
     * because the stored signature already claimed to be current.
     *
     * Keeping [was] leaves the debt visible, so the first sync after the window
     * ends - when the rule is no longer live and the WHOLE signature is
     * compared - pays it. A null [was] is not that case: nothing was skipped
     * there, the rule was built from scratch and carries exactly [sig].
     */
    internal fun carriedSignature(pushed: Boolean, was: String?, sig: String): String =
        if (pushed || was == null) sig else was

    /**
     * Creates the rule, or updates the existing one in place. Updating matters:
     * recreating would orphan the rule id and silently drop the live schedule.
     *
     * It only updates when something actually changed. Pushing an identical
     * rule makes the system re-apply it, and a live rule re-applied blinks its
     * device effects - which is why grayscale and Do Not Disturb appeared to
     * re-trigger on every settings tap, screen return and app launch.
     */
    fun syncRule(ctx: Context, p: Prefs): String? {
        val n = nm(ctx)
        val existing = p.ruleId
        val sig = signature(ctx, p)
        return try {
            val current = if (existing != null) n.getAutomaticZenRule(existing) else null
            if (current != null) {
                val was = p.ruleSignature
                // A live rule is rewritten only for something it acts on. Every
                // rewrite nulls the rule's condition, so the system drops Do Not
                // Disturb and announces it afresh when we re-arm - which is what
                // made dragging the dial mid-window flash the notification for
                // nothing but a changed caption. Skipping leaves the signature
                // stored as it was, so the caption is picked up by the next push
                // once the window is over.
                // A rule switched off from the phone's own Do Not Disturb screen
                // never activates again, so any push we make must also re-enable
                // it - but only while our own switch is on, or we would undo a
                // choice the user just made in Settings.
                val changed = (!current.isEnabled && p.enabled) ||
                    if (was == null)
                        // Forced: boot and app upgrade clear the signature so a
                        // rule edited from Settings gets repaired. Ask the rule
                        // itself rather than pushing blind - an upgrade normally
                        // changes nothing, and a needless rewrite costs a real
                        // off/on of Do Not Disturb. That is not only a blink: it
                        // left the phone's own status-bar moon stuck OFF while
                        // zen was still on, so bedtime looked dead when it was
                        // running.
                        current != buildRule(ctx, p)
                    else if (ruleState(ctx, existing!!) == Condition.STATE_TRUE)
                        was.substringBefore(COSMETIC) != liveSignature(ctx, p)
                    else was != sig
                if (changed) {
                    n.updateAutomaticZenRule(existing, buildRule(ctx, p))
                }
                // Written either way: when the rule already matched there is
                // nothing for the next sync to compare against unless we do -
                // but written as what the rule now HOLDS, which is not `sig`
                // when the push above was skipped.
                p.ruleSignature = carriedSignature(changed, was, sig)
                existing
            } else {
                n.addAutomaticZenRule(buildRule(ctx, p)).also {
                    p.ruleId = it
                    p.ruleSignature = sig
                    // Creating is exactly when an orphan is born: prefs had no
                    // id, so any rule already out there is now unreachable.
                    sweepOrphans(ctx, p)
                }
            }
        } catch (e: Exception) {
            Journal.write(ctx, "rule sync failed: " + e)
            null
        }
    }

    fun ensureRule(ctx: Context, p: Prefs): String? = syncRule(ctx, p)

    /**
     * Delete rules we own but no longer track.
     *
     * [Prefs.ruleId] is the app's ONLY handle on its rule, so losing it strands
     * the rule the system still holds - "Clear storage" does it, and so does any
     * restore that drops prefs. The next launch creates a fresh rule and the old
     * one stays behind: enabled, listed on the phone's own Do Not Disturb screen
     * as a second "Gloaming", and unreachable by us forever. Measured on the
     * Honor, which was holding two.
     *
     * getAutomaticZenRules only returns rules owned by the caller, so another
     * app's rule cannot be reached from here. The conditionId is checked anyway:
     * this is a vendor Android, and "the API returns exactly what it documents"
     * has not been a safe assumption anywhere else in this file.
     */
    /**
     * Remove every rule of ours that is not the one we are holding.
     *
     * `keep` is deliberately NULLABLE and this deliberately does not return
     * early when it is null. With no ruleId there is nothing to preserve, so
     * every Gloaming rule on the phone is an orphan and all of them should go.
     *
     * It used to `?: return` there, and that stranded a LIVE rule: clearing the
     * app's data wipes prefs but leaves the rule registered with the system,
     * still active, still applying its device effects. Measured on the Honor
     * 1 Sep 2026 after a `pm clear` - the app showed bedtime off, zen_mode read
     * 0, and the screen stayed GRAYSCALE, because an orphan nobody could see was
     * still holding it. Nothing could clean it up either: the sweep returned at
     * the first line every time, so the only escape was to switch bedtime on,
     * which minted a ruleId and finally let the sweep run.
     */
    private fun sweepOrphans(ctx: Context, p: Prefs) {
        val keep = p.ruleId
        val n = nm(ctx)
        val ours = runCatching { n.automaticZenRules }.getOrNull() ?: return
        ours.filter { (id, rule) -> id != keep && rule.conditionId == CONDITION }
            .keys.forEach { id ->
                // removeAutomaticZenRule RETURNS whether it removed anything,
                // so runCatching alone would report a plain `false` as success -
                // which it did, and sent me looking for a removal that had not
                // happened. Report what the call answered, not that it survived.
                runCatching { n.removeAutomaticZenRule(id) }
                    .onSuccess {
                        Journal.write(
                            ctx,
                            (if (it) "removed orphaned rule " else "orphan refused removal ") + id
                        )
                    }
                    .onFailure { Journal.write(ctx, "orphan sweep failed: " + it) }
            }
    }

    /**
     * The state the SYSTEM holds for our rule. STATE_UNKNOWN when it cannot be
     * read, which counts as disagreement: a redundant assert costs a blink, a
     * missed one costs the night.
     */
    private fun ruleState(ctx: Context, id: String): Int =
        try {
            nm(ctx).getAutomaticZenRuleState(id)
        } catch (e: Exception) {
            Journal.write(ctx, "rule state unreadable: " + e)
            Condition.STATE_UNKNOWN
        }

    /**
     * [force] asserts the state even when the system already reports it. Alarms
     * and boot do that, because the world may have moved underneath us. The UI
     * does not: re-asserting STATE_TRUE on an already-active rule re-applies its
     * device effects, and the screen visibly blinks.
     *
     * Whether to skip is decided by asking the system, never by what we last
     * wrote. Trusting our own memory silently cost a whole night of Do Not
     * Disturb: updateAutomaticZenRule clears the rule's condition, so every
     * rewrite mid-window - an app upgrade, a dragged handle, an edited
     * allowlist - switched the rule off, and we then declined to re-arm it
     * because we still believed it was on.
     */
    /**
     * Does the phone disagree with a rule we believe is ON?
     *
     * "Nothing is being filtered while we want zen on" is a real contradiction
     * and worth forcing a rewrite over - see [setActive] for the reboot that
     * costs a whole night. But it is only a contradiction WHEN WE ASKED FOR
     * FILTERING. With the Do Not Disturb switch off our rule sets
     * INTERRUPTION_FILTER_ALL deliberately: the rule exists to carry device
     * effects and to filter nothing. Reading that back as "stuck" made the
     * condition permanently true, so `setActive` could never take its early
     * return and rewrote the rule on every call.
     *
     * That is not merely wasteful, it LOOPS: every rewrite makes the system
     * broadcast a rule-status change, `ZenStatusReceiver` reconciles, reconcile
     * reschedules, and rescheduling lands back here. Measured on the Honor with
     * a live window and the switch off - `setAzrState` from our own uid every
     * one to four milliseconds, STATE_TRUE / STATE_FALSE / STATE_TRUE without
     * end, and grayscale visibly dropping out on about a third of the samples
     * because each rewrite clears the condition before it is re-asserted.
     * Reported as "grayscale and dim wallpaper only work if Do Not Disturb is
     * on", which is what the flicker looks like from the outside.
     */
    internal fun looksStuck(active: Boolean, wantsDnd: Boolean, filter: Int): Boolean =
        active && wantsDnd && filter == NotificationManager.INTERRUPTION_FILTER_ALL

    fun setActive(
        ctx: Context,
        p: Prefs,
        active: Boolean,
        force: Boolean = false
    ): Boolean {
        // The vendor route, on phones where the zen effect cannot work. Every
        // path reaches this function - alarms, boot, the UI, reconcile - so it
        // goes above the early return below, and a phone that died mid-window
        // restores the display when it reboots without a case of its own.
        AmbientControl.sync(ctx, p, active)
        val id = syncRule(ctx, p) ?: return false
        val want = if (active) Condition.STATE_TRUE else Condition.STATE_FALSE
        val label = if (active) "ON" else "OFF"
        // The rule's own state is the right question to ask (never what we last
        // wrote) - but it can LIE across a reboot, and that cost a whole window.
        // Measured on a OnePlus/LineageOS 1 Sep 2026: reboot mid-window and the
        // platform resets zen_mode to 0 while the rule's condition PERSISTS as
        // STATE_TRUE in the zen config. The two disagree, the rule is the one
        // that is wrong, and this early return believed it: boot re-armed the
        // alarms, `activeDay` stayed pinned, every indicator said "running", and
        // Do Not Disturb was simply off for the rest of the night. Opening the
        // app did not repair it either, because reconcile lands here too.
        //
        // So when we want the rule ON, the state alone is not enough: the system
        // must also report SOMETHING in effect. If the filter is ALL then nothing
        // is filtering, so our rule certainly is not, whatever it claims.
        // STUCK is that disagreement: we want it ON and yet nothing at all is
        // being filtered. Deliberately NOT also testing ruleState here - that was
        // the first attempt and it never fired, because getAutomaticZenRuleState
        // reports the EFFECTIVE state (FALSE, the override winning) while the
        // config stores the condition as TRUE. The two readings are of different
        // things, and the filter is the only one that says what the phone is
        // actually doing.
        val stuck = looksStuck(active, p.fxDnd, currentFilter(ctx))
        if (!force && !stuck && ruleState(ctx, id) == want) return true
        return try {
            // Why the extra STATE_FALSE. A reboot mid-window leaves the rule
            // carrying conditionOverride=OVERRIDE_DEACTIVATE: AOSP's
            // setManualZenMode sets it on every active rule when zen goes off
            // other than by the user in SystemUI, and a reboot qualifies. The
            // override then vetoes the condition, so the rule reads STATE_TRUE in
            // the config, reports STATE_FALSE through the API, and filters
            // nothing - and pushing STATE_TRUE again does not help, because
            // ZenRule.reconsiderConditionOverride only drops an OVERRIDE_DEACTIVATE
            // when the condition goes FALSE. So: push FALSE to clear the override,
            // then TRUE to activate for real. Only in the stuck case - a needless
            // off/on is the visible blink the rest of this file works to avoid.
            if (stuck) {
                nm(ctx).setAutomaticZenRuleState(
                    id, Condition(CONDITION, RULE_NAME, Condition.STATE_FALSE)
                )
            }
            nm(ctx).setAutomaticZenRuleState(id, Condition(CONDITION, RULE_NAME, want))
            if (p.lastLoggedZen != label) {
                Journal.write(ctx, "zen state -> " + label)
                p.lastLoggedZen = label
            }
            true
        } catch (e: Exception) {
            Journal.write(ctx, "setState failed: " + e)
            false
        }
    }

    /**
     * Bring us back in line with whatever the system now holds.
     *
     * The rule can be deleted or switched off from the phone's own Do Not
     * Disturb screen, and we cannot stop either: Honor routes that row to its
     * built-in schedule editor, which offers a Delete button and a toggle.
     * Measured on a Magic8 Pro - deleting leaves Gloaming armed with no rule at
     * all, and the toggle switched back on leaves the rule enabled but inactive,
     * so bedtime silently does nothing for the rest of the night.
     *
     * The broadcast that prompts this is only a hint to go and look. Every
     * decision below is made from what NotificationManager reports, so a
     * spoofed one cannot talk us into anything.
     */
    fun reconcile(ctx: Context, p: Prefs) {
        // Above the enabled check on purpose: an orphan is just as visible on
        // the phone's Do Not Disturb screen while the app is switched off.
        sweepOrphans(ctx, p)
        if (!p.enabled) return
        val id = p.ruleId
        val rule = id?.let { runCatching { nm(ctx).getAutomaticZenRule(it) }.getOrNull() }
        when {
            rule == null -> {
                Journal.write(ctx, "rule gone - rebuilding")
                // The stored signature describes a rule that no longer exists.
                p.ruleSignature = null
            }
            !rule.isEnabled -> {
                Journal.write(ctx, "rule switched off in Settings - following suit")
                // Say so on our own switch rather than stay on doing nothing.
                p.enabled = false
            }
        }
        Scheduler.rescheduleAll(ctx, p)
    }

    /**
     * Every rule of ours, gone - the one we track and any we have lost track of.
     *
     * The sweep is not belt and braces. `ruleId` is the app's only handle, so a
     * rule whose id we no longer hold cannot be removed by id at all, and it is
     * exactly what survives a wiped store: enabled, listed on the phone's own Do
     * Not Disturb screen as a second "Gloaming", greying the screen with nothing
     * on screen to explain it. Measured on the Honor after a `pm clear`. Nulling
     * the id first is what makes the sweep total: with nothing to keep, every
     * Gloaming rule is an orphan.
     */
    fun removeRule(ctx: Context, p: Prefs) {
        p.ruleId?.let { id ->
            runCatching { nm(ctx).removeAutomaticZenRule(id) }
                .onSuccess { Journal.write(ctx, (if (it) "removed rule " else "rule refused removal ") + id) }
                .onFailure { Journal.write(ctx, "rule removal failed: " + it) }
        }
        p.ruleId = null
        sweepOrphans(ctx, p)
    }
}
