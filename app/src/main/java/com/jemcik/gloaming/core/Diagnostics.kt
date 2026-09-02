package com.jemcik.gloaming.core

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.notification.Condition
import com.jemcik.gloaming.R
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The phone's own answer to "why did bedtime not do what I asked", as plain
 * text a user can read and send in one tap.
 *
 * This exists because of an evening spent proving the app innocent. Diagnosing
 * one report took five rounds of adb across three handsets - and on a RELEASE
 * build `run-as` is refused, so the app's whole side was unreadable until a
 * debug build was installed over it, which wiped the very prefs being asked
 * about. A user has no debug build at all. Until now, "bedtime did not fire"
 * was simply unanswerable unless the phone was on my desk.
 *
 * TWO SECTIONS, DELIBERATELY SEPARATE: what the SYSTEM reports, and what the
 * app INTENDED. Every bug worth having this for lives in the gap between them -
 * a rule switched off in Settings, a condition cleared by a rewrite, effects
 * the vendor stored and ignored. Merging them into one "status" would hide
 * exactly the disagreement the report is for.
 *
 * It cannot tell you whether a notification made a SOUND. That needs
 * `Ranking.getSuppressedVisualEffects` or the event log, and the first wants a
 * NotificationListenerService grant far heavier than this app asks for. So the
 * report narrows the question to which device and which state; it does not
 * close it. Said plainly here because the first version of this idea was
 * written down as "a user could have sent that in one tap", and that was not
 * true of the evidence that actually mattered.
 *
 * Everything is wrapped: a diagnostics report that crashes is worse than none,
 * and it runs on exactly the phones where something is already wrong.
 */
object Diagnostics {

    private const val UNKNOWN = "?"

    private val STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    /** Chronological, oldest first - a night reads forwards. */
    private const val JOURNAL_LINES = 200

    fun share(ctx: Context) {
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, ctx.getString(R.string.diagnostics_subject))
            .putExtra(Intent.EXTRA_TEXT, report(ctx))
        runCatching {
            ctx.startActivity(
                Intent.createChooser(send, ctx.getString(R.string.diagnostics_row))
            )
        }
    }

    fun report(ctx: Context): String = buildString {
        val p = Prefs(ctx)
        head("GLOAMING DIAGNOSTICS")
        row("taken", LocalDateTime.now().format(STAMP))

        head("APP")
        val pkg = runCatching {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        }.getOrNull()
        row("version", pkg?.versionName ?: UNKNOWN)
        row("installed", pkg?.firstInstallTime?.let(::stamp) ?: UNKNOWN)
        row("updated", pkg?.lastUpdateTime?.let(::stamp) ?: UNKNOWN)

        head("PHONE")
        // Reported, never branched on - see the capability rule in CLAUDE.md.
        // Knowing WHICH vendor ignored something is the whole point of a report
        // that arrives from a phone nobody here owns.
        row("device", Build.MANUFACTURER + " " + Build.MODEL)
        row("android", Build.VERSION.RELEASE + " (SDK " + Build.VERSION.SDK_INT + ")")
        row("build", Build.DISPLAY)
        // Language and region only. The full tag carries every -u- extension
        // the phone has set - "en-UA-u-fw-mon-ms-metric-mu-celsius" - which
        // wrapped onto two lines and answered nothing anyone would ask.
        row("locale", ask {
            ctx.resources.configuration.locales[0].toLanguageTag().substringBefore("-u-")
        })

        head("CAN THE APP WORK HERE")
        row("DND access", askYn { ZenController.hasDndAccess(ctx) })
        row("exact alarms", askYn { Scheduler.canScheduleExact(ctx) })
        row("background", ask {
            if (BackgroundLimit.isRestricted(ctx)) "RESTRICTED - alarms park" else "not restricted"
        })
        row("screen effects", ask {
            if (ScreenEffects.applied(ctx)) "applied" else "not applied on this phone"
        })
        row("ambient keys", askYn { AmbientCapability.isSupported(ctx) })
        row("launch manager", askYn { Doors.hasLaunchManager(ctx) })
        row("system bedtime", askYn { Doors.hasSystemBedtime(ctx) })

        // What the SYSTEM says. Asked of NotificationManager every time, never
        // recalled from prefs - "what we last wrote" is the belief that has
        // already cost this app a whole night of Do Not Disturb.
        head("WHAT THE SYSTEM REPORTS")
        row("zen_mode", ask {
            Settings.Global.getInt(ctx.contentResolver, "zen_mode", -1).toString()
        })
        row("filter", filterName(runCatching { ZenController.currentFilter(ctx) }.getOrNull()))
        val id = p.ruleId
        if (id == null) {
            row("rule", "NONE - the app holds no rule id")
        } else {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            val rule = runCatching { nm.getAutomaticZenRule(id) }
            val r = rule.getOrNull()
            when {
                rule.isFailure -> row("rule", "unreadable: " + rule.exceptionOrNull())
                r == null -> row("rule", "GONE - id " + id + " is not registered")
                else -> {
                    row("rule", id)
                    row("enabled", yn(r.isEnabled))
                    row("state", stateName(runCatching {
                        nm.getAutomaticZenRuleState(id)
                    }.getOrNull()))
                    row("rule filter", filterName(r.interruptionFilter))
                    row("trigger", r.triggerDescription ?: "-")
                    // Verbatim toString. It is long and it is the point: the
                    // visual effects a rule INHERITS rather than sets are only
                    // visible here, and reading them off one user's report is
                    // what a device table on a desk cannot do.
                    row("policy", r.zenPolicy?.toString() ?: "-")
                    row("effects", r.deviceEffects?.toString() ?: "-")
                }
            }
        }

        head("WHAT THE APP INTENDED")
        row("bedtime", if (p.enabled) "on" else "off")
        row("running now", askYn { Bedtime.runningNow(ctx, p) })
        row("window", Clock.hhmm(ctx, p.startTime) + " - " + Clock.hhmm(ctx, p.endTime))
        row("days", p.days.sortedBy { it.value }.joinToString(",") { it.name.take(2) }
            .ifEmpty { "once" })
        row("end at alarm", yn(p.exitAtAlarm))
        row("next alarm", ask { Scheduler.nextAlarm(ctx)?.toString() ?: "none set" })
        // As a DATE. It went out as "20698", which is the pin working
        // correctly and is unreadable to the person being asked about it.
        row("active day", if (p.activeDay == Prefs.NO_DAY) "-"
            else runCatching { LocalDate.ofEpochDay(p.activeDay).toString() }
                .getOrDefault(p.activeDay.toString()))
        row("wants", listOfNotNull(
            "dnd".takeIf { p.fxDnd },
            "grayscale".takeIf { p.fxGrayscale },
            "dim".takeIf { p.fxDimWallpaper },
            "dark".takeIf { p.fxDarkTheme },
            "hideAmbient".takeIf { p.fxHideAmbient }
        ).joinToString(" ").ifEmpty { "nothing" })
        // In words. "calls=3 messages=4 convs=3" is ZenPolicy's own numbering
        // and requires the reader to hold the enum in their head - which is the
        // reader who is already trying to work out why a night went wrong.
        row("allows calls", people(p.allowCalls))
        row("allows messages", people(p.allowMessages))
        row("allows conversations", conversations(p.allowConversations))
        row("allows", listOfNotNull(
            "repeat callers".takeIf { p.allowRepeatCallers },
            "alarms".takeIf { p.allowAlarms },
            "media".takeIf { p.allowMedia },
            "reminders".takeIf { p.allowReminders },
            "events".takeIf { p.allowEvents }
        ).joinToString(", ").ifEmpty { "nothing else" })

        head("OVERNIGHT CHECKS")
        row("END alarm", ask {
            if (AlarmWatch.missed(p)) "MISSED - did not arrive on time" else "no miss recorded"
        })
        row("delivery probe", ask {
            when {
                BackgroundProbe.blocked(p) -> "LATE - this phone holds alarms"
                BackgroundProbe.answered(p) -> "alarms arrive on time"
                else -> "not answered yet"
            }
        })
        row("restart", ask {
            if (BootWatch.missed(p)) "MISSED - no boot broadcast" else "none missed"
        })

        // Last, because it is the longest and the only part that is read rather
        // than scanned.
        val log = runCatching { Journal.read(ctx).reversed() }.getOrDefault(emptyList())
        head("JOURNAL (" + log.size + " lines)")
        if (log.isEmpty()) {
            appendLine("  empty")
        } else {
            log.takeLast(JOURNAL_LINES).forEach { appendLine("  " + it) }
        }
    }

    /**
     * Ask the platform something and never throw.
     *
     * Written out twenty-one times before this: every line of the report is a
     * call that can fail on the phones the report exists for, and a diagnostics
     * report that crashes is worse than none. "?" is deliberately distinct from
     * a "no" - not answering and answering in the negative are different
     * findings, and [yn] keeps that distinction for booleans too.
     */
    private fun ask(f: () -> String): String = runCatching(f).getOrDefault(UNKNOWN)

    private fun askYn(f: () -> Boolean): String = yn(runCatching(f).getOrNull())

    private fun stamp(ms: Long): String =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneId.systemDefault()).format(STAMP)

    /** Null means the call could not be made, which is not the same as "no". */
    private fun yn(b: Boolean?): String = when (b) {
        true -> "yes"
        false -> "no"
        null -> UNKNOWN
    }

    /** [Interruptions]' own constants, so this cannot drift from the screen. */
    private fun people(v: Int): String = when (v) {
        Interruptions.PEOPLE_ANYONE -> "anyone"
        Interruptions.PEOPLE_CONTACTS -> "contacts"
        Interruptions.PEOPLE_STARRED -> "starred contacts"
        Interruptions.PEOPLE_NONE -> "no one"
        else -> "unknown(" + v + ")"
    }

    private fun conversations(v: Int): String = when (v) {
        Interruptions.CONV_ANYONE -> "all"
        Interruptions.CONV_IMPORTANT -> "important"
        Interruptions.CONV_NONE -> "none"
        else -> "unknown(" + v + ")"
    }

    private fun filterName(f: Int?): String = when (f) {
        NotificationManager.INTERRUPTION_FILTER_ALL -> "ALL (nothing filtered)"
        NotificationManager.INTERRUPTION_FILTER_PRIORITY -> "PRIORITY"
        NotificationManager.INTERRUPTION_FILTER_ALARMS -> "ALARMS"
        NotificationManager.INTERRUPTION_FILTER_NONE -> "NONE"
        null -> UNKNOWN
        else -> "unknown(" + f + ")"
    }

    private fun stateName(s: Int?): String = when (s) {
        Condition.STATE_TRUE -> "TRUE (active)"
        Condition.STATE_FALSE -> "FALSE"
        Condition.STATE_ERROR -> "ERROR"
        Condition.STATE_UNKNOWN -> "UNKNOWN"
        null -> UNKNOWN
        else -> "unknown(" + s + ")"
    }

    private fun StringBuilder.head(s: String) {
        if (isNotEmpty()) appendLine()
        appendLine(s)
    }

    /**
     * `label: value`, NOT a padded column. The first version padded labels to
     * 15 and lined the values up, which is right in a terminal and wrong
     * everywhere this actually goes: mail and messaging clients render
     * proportional text, so the padding collapsed and "delivery probe alarms
     * arrive on time" read as a sentence with no separator in it. Read back off
     * a real phone from a real inbox, which is the only place it could be seen.
     */
    private fun StringBuilder.row(label: String, value: String) {
        appendLine("  " + label + ": " + value)
    }
}
