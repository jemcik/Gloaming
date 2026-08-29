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
}
