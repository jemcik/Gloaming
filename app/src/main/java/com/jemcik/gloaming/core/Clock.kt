package com.jemcik.gloaming.core

import android.content.Context
import android.text.format.DateFormat
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Clock times, in the format the PHONE is set to.
 *
 * Every time in this app used to be "%02d:%02d", which is 24-hour whatever the
 * user asked for in Settings. There is no app setting for this and there should
 * not be: Android already has the preference, and an app-level override on top
 * of a system one is usually a sign the app got it wrong.
 *
 * The pattern comes from getBestDateTimePattern rather than a literal, so the
 * separator and the placing of AM/PM follow the locale too - a hard-coded
 * "h:mm a" is only right in English.
 *
 * The DIAL is unaffected: it is a 24-hour face by construction, because a
 * 12-hour face cannot draw a window longer than 12 hours. Apple's Sleep ring
 * makes the same choice.
 */
object Clock {

    fun is24Hour(ctx: Context): Boolean = DateFormat.is24HourFormat(ctx)

    fun hhmm(ctx: Context, t: LocalTime): String {
        val locale = ctx.resources.configuration.locales[0]
        val skeleton = if (is24Hour(ctx)) "Hm" else "hm"
        return DateTimeFormatter
            .ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale)
            .format(t)
    }

    fun hhmm(ctx: Context, hour: Int, minute: Int): String =
        hhmm(ctx, LocalTime.of(hour, minute))

    /**
     * The time split into its numerals and its day period, so a caller can set
     * the two at different sizes. [period] is null in 24-hour mode, and in any
     * locale whose pattern has no day-period field.
     *
     * This exists because "11:30 PM" does not fit the 146.5dp the window
     * columns have at 36sp, and CLDR separates the time from the day period
     * with U+202F - a NARROW NO-BREAK space. No-break means the string has no
     * legal break point at all, so rather than wrapping to "11:30" / "PM" the
     * text broke INSIDE the token, to "11:30 P" / "M", and the block grew to
     * three lines. Splitting it here lets the numerals keep one size while the
     * period takes a smaller role, which is what Clock apps do and what makes
     * the width safe for any hour.
     *
     * [periodFirst] is not decoration: ja renders 午後11:30 with the period
     * LEADING. We ship en, ru and uk, where it trails - but reading the order
     * off the pattern costs three lines and means a fourth locale cannot
     * silently come out backwards.
     *
     * Worth knowing about ru and uk specifically: both are 24-hour locales -
     * their own short pattern is HH:mm - so this path is only ever reached
     * because the user set the PHONE to 12-hour. Asked for 12-hour anyway,
     * CLDR gives uk "дп"/"пп" and gives ru the Latin "AM"/"PM", having no
     * Cyrillic form for it. That looks unusual, and it is; the app follows the
     * system setting regardless, because overriding an explicit user choice
     * per-locale would be the worse mistake.
     */
    data class Reading(val time: String, val period: String?, val periodFirst: Boolean)

    fun reading(ctx: Context, t: LocalTime): Reading {
        if (is24Hour(ctx)) return Reading(hhmm(ctx, t), null, false)
        val locale = ctx.resources.configuration.locales[0]
        val pattern = DateFormat.getBestDateTimePattern(locale, "hm")
        val field = fieldRange(pattern, "abB")
            ?: return Reading(hhmm(ctx, t), null, false)
        // java.time understands 'a' but not the CLDR extensions 'b'/'B'. If one
        // ever turns up, leave the string whole rather than throwing - the old
        // wrap is survivable, a crash is not.
        if (pattern[field.first] != 'a') return Reading(hhmm(ctx, t), null, false)
        val hour = fieldRange(pattern, "hHKk")
        val timePattern = pattern.removeRange(field).trimSeparators()
        if (timePattern.isEmpty()) return Reading(hhmm(ctx, t), null, false)
        return Reading(
            DateTimeFormatter.ofPattern(timePattern, locale).format(t),
            DateTimeFormatter.ofPattern(pattern.substring(field), locale).format(t),
            periodFirst = hour != null && field.first < hour.first
        )
    }

    /** First run of any of [chars] that is a real field, i.e. not inside 'quotes'. */
    private fun fieldRange(pattern: String, chars: String): IntRange? {
        var i = 0
        var quoted = false
        while (i < pattern.length) {
            val c = pattern[i]
            if (c == '\'') { quoted = !quoted; i++; continue }
            if (!quoted && c in chars) {
                var j = i
                while (j + 1 < pattern.length && pattern[j + 1] == c) j++
                return i..j
            }
            i++
        }
        return null
    }

    /**
     * isWhitespace() is false for U+202F and U+00A0 - they are non-breaking, and
     * that is exactly the character sitting between the time and the day period.
     * isSpaceChar catches them.
     */
    private fun String.trimSeparators(): String =
        trim { it.isWhitespace() || Character.isSpaceChar(it) }
}
