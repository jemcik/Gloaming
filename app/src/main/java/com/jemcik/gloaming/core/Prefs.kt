package com.jemcik.gloaming.core

import android.content.Context
import androidx.core.content.edit
import java.time.DayOfWeek
import java.time.LocalTime

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("gloaming", Context.MODE_PRIVATE)

    init {
        // `days` used to mean the evening a window started on; it now means the
        // morning it ends on. Without this, an existing overnight schedule
        // would quietly shift by a day on upgrade.
        if (!sp.getBoolean("daysAreMornings", false)) {
            val crossed = sp.getLong("end", 8 * 3600L) <= sp.getLong("start", 81000L)
            val old = sp.getStringSet("days", null)
            if (crossed && old != null) {
                sp.edit {
                    putStringSet("days", old.map { DayOfWeek.valueOf(it).plus(1).name }.toSet())
                }
            }
            sp.edit { putBoolean("daysAreMornings", true) }
        }
    }

    var enabled: Boolean
        get() = sp.getBoolean("enabled", false)
        set(v) = sp.edit { putBoolean("enabled", v) }

    /** Last values written to the journal, so unchanged state stays quiet. */
    var lastLoggedZen: String?
        get() = sp.getString("logZen", null)
        set(v) = sp.edit { putString("logZen", v) }
    var lastLoggedAlarms: String?
        get() = sp.getString("logAlarms", null)
        set(v) = sp.edit { putString("logAlarms", v) }

    /**
     * The DATE the running window began on, as an epoch day; [NO_DAY] when none.
     *
     * Only the day is pinned. Both handles then still describe the window, on
     * that day, via the same start-plus-duration model the rest of Scheduler
     * uses - so day-of-week edits cannot cut the night short, while dragging
     * either handle does what dragging it should. Pinning an instant instead
     * was too blunt in both directions: pin the end and the wake handle stops
     * working (the dial read 13:40 while the card said "until 16:00"); pin the
     * beginning and the bedtime handle stops working (drag it past now and the
     * app still claims to be running).
     */
    var activeDay: Long
        get() = sp.getLong("activeDay", NO_DAY)
        set(v) = sp.edit { putLong("activeDay", v) }

    /**
     * The boot the last handled BOOT_COMPLETED belonged to; [NO_BOOT] when we
     * have never handled one. See [BootWatch] - a mismatch means the phone
     * restarted and the broadcast never reached us.
     */
    var bootStamp: Long
        get() = sp.getLong("bootStamp", NO_BOOT)
        set(v) = sp.edit { putLong("bootStamp", v) }

    /**
     * The instant the currently armed END is due, and the instant of the last
     * END we actually handled. [NO_DUE] when nothing is armed.
     *
     * The pair is the whole detector: arming writes [endDue], the receiver
     * copies it into [endSeen], and a due time that has passed without ever
     * being seen is an alarm the phone ate. See [AlarmWatch].
     */
    var endDue: Long
        get() = sp.getLong("endDue", NO_DUE)
        set(v) = sp.edit { putLong("endDue", v) }

    var endSeen: Long
        get() = sp.getLong("endSeen", NO_DUE)
        set(v) = sp.edit { putLong("endSeen", v) }

    /**
     * Latched: an END went missing, and stays reported until one arrives.
     *
     * It has to be sticky. Re-arming overwrites [endDue] with the NEXT window,
     * and re-arming is the first thing that happens when the app comes back -
     * so comparing the pair at the moment the screen asks would always find a
     * fresh, innocent-looking due time. The evidence is destroyed by the act of
     * recovering, which cost a whole test to discover.
     */
    var alarmMissed: Boolean
        get() = sp.getBoolean("alarmMissed", false)
        set(v) = sp.edit { putBoolean("alarmMissed", v) }

    /**
     * The user has been sent to the vendor's launch screen at least once.
     *
     * Set when they tap the card, not when they finish - because whether they
     * finished is not knowable. Toggling both of Honor's switches off and on
     * again changes NOTHING readable: no key in secure, system or global, no
     * appop recorded, nothing in the package dump. Measured by doing exactly
     * that, twice. So the card is told once and then trusts the person, and the
     * missed-alarm notice catches it if the trust was misplaced.
     */
    var launchAcknowledged: Boolean
        get() = sp.getBoolean("launchAcknowledged", false)
        set(v) = sp.edit { putBoolean("launchAcknowledged", v) }

    companion object {
        const val NO_BOOT = Long.MIN_VALUE
        const val NO_DUE = Long.MIN_VALUE
        const val NO_DAY = Long.MIN_VALUE
        const val THEME_SYSTEM = 0
        const val THEME_LIGHT = 1
        const val THEME_DARK = 2
    }

    /** What the rule looked like when we last pushed it, so we can not push
     *  an identical one again - updating a live rule re-applies its effects. */
    var ruleSignature: String?
        get() = sp.getString("ruleSig", null)
        set(v) = sp.edit { putString("ruleSig", v) }

    /**
     * 0 follow the system, 1 always light, 2 always dark. The system is the
     * default because a bedtime app that fights the phone's own night schedule
     * is the wrong way round.
     */
    var themeMode: Int
        get() = sp.getInt("themeMode", THEME_SYSTEM)
        set(v) = sp.edit { putInt("themeMode", v) }

    var ruleId: String?
        get() = sp.getString("ruleId", null)
        set(v) = sp.edit { putString("ruleId", v) }

    var startTime: LocalTime
        get() = LocalTime.ofSecondOfDay(sp.getLong("start", 22 * 3600L + 30 * 60))
        set(v) = sp.edit { putLong("start", v.toSecondOfDay().toLong()) }

    var endTime: LocalTime
        get() = LocalTime.ofSecondOfDay(sp.getLong("end", 8 * 3600L))
        set(v) = sp.edit { putLong("end", v.toSecondOfDay().toLong()) }

    var days: Set<DayOfWeek>
        get() = sp.getStringSet("days", null)?.map { DayOfWeek.valueOf(it) }?.toSet()
            ?: DayOfWeek.entries.toSet()
        set(v) = sp.edit { putStringSet("days", v.map { it.name }.toSet()) }

    // --- what bedtime actually does, all backed by ZenDeviceEffects ---

    /** Silence notifications (the AutomaticZenRule's interruption filter). */
    var fxDnd: Boolean
        get() = sp.getBoolean("fxDnd", true)
        set(v) = sp.edit { putBoolean("fxDnd", v) }

    var fxGrayscale: Boolean
        get() = sp.getBoolean("fxGrayscale", true)
        set(v) = sp.edit { putBoolean("fxGrayscale", v) }

    var fxDimWallpaper: Boolean
        get() = sp.getBoolean("fxDimWallpaper", true)
        set(v) = sp.edit { putBoolean("fxDimWallpaper", v) }

    var fxDarkTheme: Boolean
        get() = sp.getBoolean("fxDarkTheme", false)
        set(v) = sp.edit { putBoolean("fxDarkTheme", v) }




    var fxHideAmbient: Boolean
        get() = sp.getBoolean("fxHideAmbient", false)
        set(v) = sp.edit { putBoolean("fxHideAmbient", v) }

    /**
     * The vendor always-on values as they were before we switched them off, as
     * `key=value|key=value`; null when the keys are not ours. So END restores
     * what the user had rather than a guess, and a restore that fails is retried
     * instead of forgotten with the display still off. See [AmbientControl].
     */
    var ambientSaved: String?
        get() = sp.getString("ambientSaved", null)
        set(v) = sp.edit { putString("ambientSaved", v) }

    // --- who can interrupt, mapped onto ZenPolicy ---
    // ZenPolicy.PEOPLE_TYPE_*: 1 anyone, 2 contacts, 3 starred, 4 none
    // ZenPolicy.CONVERSATION_SENDERS_*: 1 anyone, 2 important, 3 none

    var allowCalls: Int
        get() = sp.getInt("allowCalls", 3)
        set(v) = sp.edit { putInt("allowCalls", v) }

    var allowMessages: Int
        get() = sp.getInt("allowMessages", 4)
        set(v) = sp.edit { putInt("allowMessages", v) }

    var allowConversations: Int
        get() = sp.getInt("allowConversations", 3)
        set(v) = sp.edit { putInt("allowConversations", v) }

    var allowRepeatCallers: Boolean
        get() = sp.getBoolean("allowRepeatCallers", true)
        set(v) = sp.edit { putBoolean("allowRepeatCallers", v) }

    var allowAlarms: Boolean
        get() = sp.getBoolean("allowAlarms", true)
        set(v) = sp.edit { putBoolean("allowAlarms", v) }

    var allowMedia: Boolean
        get() = sp.getBoolean("allowMedia", true)
        set(v) = sp.edit { putBoolean("allowMedia", v) }

    var allowReminders: Boolean
        get() = sp.getBoolean("allowReminders", false)
        set(v) = sp.edit { putBoolean("allowReminders", v) }

    var allowEvents: Boolean
        get() = sp.getBoolean("allowEvents", false)
        set(v) = sp.edit { putBoolean("allowEvents", v) }
}
