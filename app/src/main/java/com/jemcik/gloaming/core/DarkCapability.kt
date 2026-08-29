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
import kotlinx.coroutines.delay

/**
 * Answers "does this phone let an app drive the system dark theme?" in a few
 * seconds, instead of waiting for bedtime to start.
 *
 * No app can call UiModeManager.setNightMode - MODIFY_DAY_NIGHT_MODE is
 * role-managed and even `pm grant` refuses it. The one legitimate lever is
 * ZenDeviceEffects.setShouldUseNightMode, which asks the system to go dark
 * while a zen rule is active. Whether that request is honoured is up to the
 * vendor: AOSP applies it, MagicOS stores it and does nothing.
 *
 * So we ask, briefly, with a disposable rule and watch what happens.
 */
object DarkCapability {

    const val UNKNOWN = 0
    const val SUPPORTED = 1
    const val UNSUPPORTED = 2

    private val COND: Uri = "condition://com.jemcik.gloaming/capabilitycheck".toUri()

    /**
     * Returns SUPPORTED, UNSUPPORTED, or UNKNOWN when the result would prove
     * nothing (the system is already dark, or the rule could not be created).
     */
    suspend fun probe(ctx: Context): Int {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (!nm.isNotificationPolicyAccessGranted) return UNKNOWN
        // Already dark: asking for dark tells us nothing.
        if (SystemTheme.isDark(ctx)) return UNKNOWN

        // Deliberately permissive: this rule exists to carry one device effect,
        // not to silence anything during the couple of seconds it is active.
        val policy = ZenPolicy.Builder()
            .allowAlarms(true).allowMedia(true).allowSystem(true)
            .allowReminders(true).allowEvents(true).allowRepeatCallers(true)
            .allowCalls(ZenPolicy.PEOPLE_TYPE_ANYONE)
            .allowMessages(ZenPolicy.PEOPLE_TYPE_ANYONE)
            .allowConversations(ZenPolicy.CONVERSATION_SENDERS_ANYONE)
            .build()

        val rule = AutomaticZenRule.Builder("Gloaming check", COND)
            .setType(AutomaticZenRule.TYPE_OTHER)
            .setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
            .setZenPolicy(policy)
            .setDeviceEffects(
                ZenDeviceEffects.Builder().setShouldUseNightMode(true).build()
            )
            .setConfigurationActivity(ComponentName(ctx, MainActivity::class.java))
            .setEnabled(true)
            .build()

        var id: String? = null
        return try {
            id = nm.addAutomaticZenRule(rule)
            nm.setAutomaticZenRuleState(id, Condition(COND, "check", Condition.STATE_TRUE))
            delay(2500)
            if (SystemTheme.isDark(ctx)) SUPPORTED else UNSUPPORTED
        } catch (e: Exception) {
            Journal.write(ctx, "dark capability check failed: " + e)
            UNKNOWN
        } finally {
            id?.let {
                runCatching {
                    nm.setAutomaticZenRuleState(it, Condition(COND, "check", Condition.STATE_FALSE))
                }
                runCatching { nm.removeAutomaticZenRule(it) }
            }
        }
    }
}
