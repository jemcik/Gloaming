package com.jemcik.gloaming.core

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.notification.Condition
import com.jemcik.gloaming.R
import java.time.Instant
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
        row("version", pkg?.versionName ?: "?")
        row("installed", pkg?.firstInstallTime?.let(::stamp) ?: "?")
        row("updated", pkg?.lastUpdateTime?.let(::stamp) ?: "?")

        head("PHONE")
        // Reported, never branched on - see the capability rule in CLAUDE.md.
        // Knowing WHICH vendor ignored something is the whole point of a report
        // that arrives from a phone nobody here owns.
        row("device", Build.MANUFACTURER + " " + Build.MODEL)
        row("android", Build.VERSION.RELEASE + " (SDK " + Build.VERSION.SDK_INT + ")")
        row("build", Build.DISPLAY)
        row("locale", runCatching {
            ctx.resources.configuration.locales[0].toLanguageTag()
        }.getOrDefault("?"))

        head("CAN THE APP WORK HERE")
        row("DND access", yn(runCatching { ZenController.hasDndAccess(ctx) }.getOrNull()))
        row("exact alarms", yn(runCatching { Scheduler.canScheduleExact(ctx) }.getOrNull()))
        row("background", runCatching {
            if (BackgroundLimit.isRestricted(ctx)) "RESTRICTED - alarms park" else "not restricted"
        }.getOrDefault("?"))
        row("screen effects", runCatching {
            if (ScreenEffects.applied(ctx)) "applied" else "not applied on this phone"
        }.getOrDefault("?"))
        row("ambient keys", yn(runCatching { AmbientCapability.isSupported(ctx) }.getOrNull()))
        row("launch manager", yn(runCatching { Doors.hasLaunchManager(ctx) }.getOrNull()))
        row("system bedtime", yn(runCatching { Doors.hasSystemBedtime(ctx) }.getOrNull()))

        // What the SYSTEM says. Asked of NotificationManager every time, never
        // recalled from prefs - "what we last wrote" is the belief that has
        // already cost this app a whole night of Do Not Disturb.
        head("WHAT THE SYSTEM REPORTS")
        row("zen_mode", runCatching {
            Settings.Global.getInt(ctx.contentResolver, "zen_mode", -1).toString()
        }.getOrDefault("?"))
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
        row("running now", yn(runCatching { Bedtime.runningNow(ctx, p) }.getOrNull()))
        row("window", Clock.hhmm(ctx, p.startTime) + " - " + Clock.hhmm(ctx, p.endTime))
        row("days", p.days.sortedBy { it.value }.joinToString(",") { it.name.take(2) }
            .ifEmpty { "once" })
        row("end at alarm", yn(p.exitAtAlarm))
        row("next alarm", runCatching {
            Scheduler.nextAlarm(ctx)?.toString() ?: "none set"
        }.getOrDefault("?"))
        row("active day", if (p.activeDay == Prefs.NO_DAY) "-" else p.activeDay.toString())
        row("wants", listOfNotNull(
            "dnd".takeIf { p.fxDnd },
            "grayscale".takeIf { p.fxGrayscale },
            "dim".takeIf { p.fxDimWallpaper },
            "dark".takeIf { p.fxDarkTheme },
            "hideAmbient".takeIf { p.fxHideAmbient }
        ).joinToString(" ").ifEmpty { "nothing" })
        row("allows", "calls=" + p.allowCalls + " messages=" + p.allowMessages +
            " convs=" + p.allowConversations + " repeat=" + p.allowRepeatCallers +
            " alarms=" + p.allowAlarms + " media=" + p.allowMedia +
            " reminders=" + p.allowReminders + " events=" + p.allowEvents)

        head("OVERNIGHT CHECKS")
        row("END alarm", runCatching {
            if (AlarmWatch.missed(p)) "MISSED - did not arrive on time" else "no miss recorded"
        }.getOrDefault("?"))
        row("delivery probe", runCatching {
            when {
                BackgroundProbe.blocked(p) -> "LATE - this phone holds alarms"
                BackgroundProbe.answered(p) -> "alarms arrive on time"
                else -> "not answered yet"
            }
        }.getOrDefault("?"))
        row("restart", runCatching {
            if (BootWatch.missed(p)) "MISSED - no boot broadcast" else "none missed"
        }.getOrDefault("?"))

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

    private fun stamp(ms: Long): String =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneId.systemDefault()).format(STAMP)

    /** Null means the call could not be made, which is not the same as "no". */
    private fun yn(b: Boolean?): String = when (b) {
        true -> "yes"
        false -> "no"
        null -> "?"
    }

    private fun filterName(f: Int?): String = when (f) {
        NotificationManager.INTERRUPTION_FILTER_ALL -> "ALL (nothing filtered)"
        NotificationManager.INTERRUPTION_FILTER_PRIORITY -> "PRIORITY"
        NotificationManager.INTERRUPTION_FILTER_ALARMS -> "ALARMS"
        NotificationManager.INTERRUPTION_FILTER_NONE -> "NONE"
        null -> "?"
        else -> "unknown(" + f + ")"
    }

    private fun stateName(s: Int?): String = when (s) {
        Condition.STATE_TRUE -> "TRUE (active)"
        Condition.STATE_FALSE -> "FALSE"
        Condition.STATE_ERROR -> "ERROR"
        Condition.STATE_UNKNOWN -> "UNKNOWN"
        null -> "?"
        else -> "unknown(" + s + ")"
    }

    private fun StringBuilder.head(s: String) {
        if (isNotEmpty()) appendLine()
        appendLine(s)
    }

    private fun StringBuilder.row(label: String, value: String) {
        appendLine("  " + label.padEnd(15) + value)
    }
}
