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
    private fun liveSignature(p: Prefs) = listOf(
        p.fxDnd, p.fxGrayscale, p.fxDimWallpaper, p.fxDarkTheme, p.fxHideAmbient,
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
    private fun signature(p: Prefs) =
        liveSignature(p) + COSMETIC + p.startTime + "-" + p.endTime

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
        val sig = signature(p)
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
                        was.substringBefore(COSMETIC) != liveSignature(p)
                    else was != sig
                if (changed) {
                    n.updateAutomaticZenRule(existing, buildRule(ctx, p))
                }
                // Written either way: when the rule already matched there is
                // nothing for the next sync to compare against unless we do.
                p.ruleSignature = sig
                existing
            } else {
                n.addAutomaticZenRule(buildRule(ctx, p)).also {
                    p.ruleId = it
                    p.ruleSignature = sig
                }
            }
        } catch (e: Exception) {
            Journal.write(ctx, "rule sync failed: " + e)
            null
        }
    }

    fun ensureRule(ctx: Context, p: Prefs): String? = syncRule(ctx, p)

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
    fun setActive(
        ctx: Context,
        p: Prefs,
        active: Boolean,
        force: Boolean = false
    ): Boolean {
        val id = syncRule(ctx, p) ?: return false
        val want = if (active) Condition.STATE_TRUE else Condition.STATE_FALSE
        val label = if (active) "ON" else "OFF"
        if (!force && ruleState(ctx, id) == want) return true
        return try {
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

    fun removeRule(ctx: Context, p: Prefs) {
        p.ruleId?.let { runCatching { nm(ctx).removeAutomaticZenRule(it) } }
        p.ruleId = null
    }
}
