package com.jemcik.gloaming.core

import android.content.Context
import androidx.core.content.edit
import java.time.DayOfWeek
import java.time.LocalTime

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("gloaming", Context.MODE_PRIVATE)

    init {
        // Read BEFORE anything below writes, which is what makes it usable: the
        // days migration puts a key in on every first construction, fresh
        // install included, so after this line "empty" is no longer available as
        // a signal. Empty means the app has never run here.
        val fresh = sp.all.isEmpty()

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

        // Grayscale and wallpaper dim used to be ON out of the box. They are the
        // two effects a person notices the instant bedtime starts, and turning
        // the screen grey is not something to do to someone who has just
        // installed an app and switched it on to see what it does. Off now, like
        // the other two, so the first night is quiet and every visible change is
        // one they asked for.
        //
        // Changing a read-through default changes it RETROACTIVELY - the value
        // is only ever in the code, so an install that never touched these rows
        // would have lost grayscale overnight with nothing to say why. So the
        // old defaults are written down for anyone who was already here, and
        // only a genuinely new install gets the new ones. `fresh` is the whole
        // distinction; the keys being absent is not, because they are absent in
        // both cases.
        // Latched, exactly like the days migration above, and for a sharper
        // reason: `fresh` is only true on the FIRST construction, because that
        // construction is what stops the store being empty. Keyed on freshness
        // alone, the second Prefs of a new install would see a non-empty store
        // with the keys absent, read that as an upgrade, and write the old
        // defaults straight back in. Caught by its own test rather than by a
        // phone, which is the only reason it is not in the release.
        if (!sp.getBoolean("fxDefaultsOff", false)) {
            if (!fresh) {
                if (!sp.contains("fxGrayscale")) sp.edit { putBoolean("fxGrayscale", true) }
                if (!sp.contains("fxDimWallpaper")) sp.edit { putBoolean("fxDimWallpaper", true) }
            }
            sp.edit { putBoolean("fxDefaultsOff", true) }
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
    /**
     * The probe: when a throwaway alarm was due, and when one was last handled.
     *
     * [NO_DUE] on both means no probe has ever been armed. Same shape as
     * endDue/endSeen, and for the same reason - a due instant that passed
     * unhandled is the evidence.
     */
    var probeDue: Long
        get() = sp.getLong("probeDue", NO_DUE)
        set(v) = sp.edit { putLong("probeDue", v) }

    var probeSeen: Long
        get() = sp.getLong("probeSeen", NO_DUE)
        set(v) = sp.edit { putLong("probeSeen", v) }

    /**
     * What night mode read the last time our rule was NOT active: 1, 0, or -1
     * for never seen. The baseline half of the transition test in
     * [ScreenEffects.observeApplied].
     */
    var nightWhenIdle: Int
        get() = sp.getInt("nightWhenIdle", -1)
        set(v) = sp.edit { putInt("nightWhenIdle", v) }

    /**
     * We have SEEN the zen rule's device effects actually take hold here.
     *
     * One-directional and permanent: it overrules the manufacturer prior in
     * [ScreenEffects], so a phone that starts out assumed broken can prove
     * itself without an app update.
     */
    var effectsSeen: Boolean
        get() = sp.getBoolean("effectsSeen", false)
        set(v) = sp.edit { putBoolean("effectsSeen", v) }

    /**
     * Let the morning alarm end the window early.
     *
     * AOSP's own schedule rules carry exactly this, as `exitAtAlarm` in the
     * condition URI, and Settings shows it as "Alarm can override end time";
     * Google's Bedtime mode calls it "Turn off Bedtime mode at next alarm". Off
     * by default, as it is there - silently moving when bedtime ends is not
     * something to spring on someone.
     */
    var exitAtAlarm: Boolean
        get() = sp.getBoolean("exitAtAlarm", false)
        set(v) = sp.edit { putBoolean("exitAtAlarm", v) }

    /**
     * The one-time launch-setup tip has been answered, either way.
     *
     * Not a verdict and not a measurement - just "we have offered this once".
     * It exists because the offer must never repeat: a suggestion shown twice
     * reads as a demand, and this one cannot be confirmed, so it can never
     * dismiss itself the way a readable setting does.
     */
    var launchTipSeen: Boolean
        get() = sp.getBoolean("launchTipSeen", false)
        set(v) = sp.edit { putBoolean("launchTipSeen", v) }

    /**
     * Latched: a probe came due and never arrived. Sticky for the same reason
     * `alarmMissed` is - the evidence is destroyed by the next arming, so the
     * verdict has to outlive the measurement that produced it.
     */
    var probeFailed: Boolean
        get() = sp.getBoolean("probeFailed", false)
        set(v) = sp.edit { putBoolean("probeFailed", v) }

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

    /**
     * Every key, gone - so the next [Prefs] sees an EMPTY store and takes the
     * fresh-install branch of the migrations above rather than the upgrade one.
     * That is the difference between a reset and a downgrade: an upgrade path
     * would write the old grayscale defaults back in and turn the screen grey
     * on a first night nobody asked for.
     *
     * It does not touch [Journal]. Someone resetting is usually resetting
     * because something went wrong, and the log is the only record of what.
     */
    fun clear() = sp.edit { clear() }

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
        get() = sp.getBoolean("fxGrayscale", false)
        set(v) = sp.edit { putBoolean("fxGrayscale", v) }

    var fxDimWallpaper: Boolean
        get() = sp.getBoolean("fxDimWallpaper", false)
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
