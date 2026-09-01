package com.jemcik.gloaming.core

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.core.content.edit
import java.time.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The one-shot migration in [Prefs].
 *
 * `days` used to mean the evening a window STARTED on; it now means the morning
 * it ENDS on. Everyone who had the app before that change carries days in the
 * old meaning, so they are shifted forward once on first construction.
 *
 * This is the only code in the app that can corrupt data silently. It rewrites
 * the user's schedule, it runs before any screen is drawn, and if it shifted
 * twice - or shifted a schedule that never crossed midnight - the nights would
 * simply be wrong with nothing to report it. Hence: shift once, shift the right
 * ones, and never shift again.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PrefsMigrationTest {

    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    /** Prefs exactly as an older version of the app would have left them. */
    private fun seedOldFormat(start: Int, end: Int, days: Set<DayOfWeek>?) {
        ctx.getSharedPreferences("gloaming", Context.MODE_PRIVATE).edit {
            putLong("start", start.toLong())
            putLong("end", end.toLong())
            if (days != null) putStringSet("days", days.map { it.name }.toSet())
            // deliberately NOT setting daysAreMornings: that is what marks a
            // pre-migration install
        }
    }

    private val h2230 = 22 * 3600 + 30 * 60
    private val h0800 = 8 * 3600

    @Test
    fun `an overnight schedule moves forward by one day`() {
        // The case that was verified on hardware: Fri+Sat evenings were what the
        // user picked, and they meant Sat+Sun mornings all along.
        seedOldFormat(h2230, h0800, setOf(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY))
        assertEquals(setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY), Prefs(ctx).days)
    }

    @Test
    fun `Sunday wraps round to Monday, it does not fall off the end`() {
        seedOldFormat(h2230, h0800, setOf(DayOfWeek.SUNDAY))
        assertEquals(setOf(DayOfWeek.MONDAY), Prefs(ctx).days)
    }

    @Test
    fun `it runs once, however many times Prefs is constructed`() {
        // Prefs is constructed all over the app - in the receiver, on every
        // screen, in reconcile. A migration that ran per instance would walk the
        // schedule forward a day at a time until nobody could explain it.
        seedOldFormat(h2230, h0800, setOf(DayOfWeek.FRIDAY))
        repeat(5) { Prefs(ctx) }
        assertEquals(setOf(DayOfWeek.SATURDAY), Prefs(ctx).days)
    }

    @Test
    fun `a daytime window is left alone`() {
        // Nothing to fix: a window that does not cross midnight always ended on
        // the day it started, so the old meaning and the new one agree.
        seedOldFormat(13 * 3600, 14 * 3600, setOf(DayOfWeek.FRIDAY))
        assertEquals(setOf(DayOfWeek.FRIDAY), Prefs(ctx).days)
    }

    @Test
    fun `an install with no days stored is not invented into one`() {
        seedOldFormat(h2230, h0800, days = null)
        // days falls back to every day, which is the default, not a migration
        assertEquals(DayOfWeek.entries.toSet(), Prefs(ctx).days)
    }

    @Test
    fun `a fresh install is marked migrated so it never runs later`() {
        // Otherwise the flag stays false, and the first time this user picks an
        // overnight schedule the NEXT construction shifts it.
        val p = Prefs(ctx)
        p.days = setOf(DayOfWeek.FRIDAY)
        p.startTime = java.time.LocalTime.of(22, 30)
        p.endTime = java.time.LocalTime.of(8, 0)
        assertEquals(setOf(DayOfWeek.FRIDAY), Prefs(ctx).days)
        assertTrue(
            ctx.getSharedPreferences("gloaming", Context.MODE_PRIVATE)
                .getBoolean("daysAreMornings", false)
        )
    }

    // ---- the screen effects' defaults, and the install that predates them ----

    @Test
    fun `a fresh install starts with every screen effect off`() {
        // Grayscale and dim used to be on out of the box. They are the two a
        // person sees the instant bedtime starts, and the first night should not
        // change the screen in a way they did not ask for.
        val p = Prefs(ctx)
        assertEquals(false, p.fxGrayscale)
        assertEquals(false, p.fxDimWallpaper)
        assertEquals(false, p.fxDarkTheme)
        assertEquals(false, p.fxHideAmbient)
        // Do Not Disturb is the app, not a look, and stays on.
        assertEquals(true, p.fxDnd)
    }

    @Test
    fun `an install that predates the change keeps grayscale and dim`() {
        // The trap in changing a read-through default: the value lives only in
        // the code, so lowering it lowers it RETROACTIVELY, and an install that
        // had simply never touched these rows would lose grayscale overnight
        // with nothing on screen to say why.
        seedOldFormat(h2230, h0800, setOf(DayOfWeek.FRIDAY))
        val p = Prefs(ctx)
        assertEquals(true, p.fxGrayscale)
        assertEquals(true, p.fxDimWallpaper)
    }

    @Test
    fun `an existing install that had switched them OFF is not switched back on`() {
        // The other half, and the one a "write the old defaults in" migration
        // gets wrong if it only checks the flag: false is a real answer, and
        // absent is the only thing that may be filled in.
        seedOldFormat(h2230, h0800, setOf(DayOfWeek.FRIDAY))
        ctx.getSharedPreferences("gloaming", Context.MODE_PRIVATE).edit {
            putBoolean("fxGrayscale", false)
            putBoolean("fxDimWallpaper", false)
        }
        val p = Prefs(ctx)
        assertEquals(false, p.fxGrayscale)
        assertEquals(false, p.fxDimWallpaper)
    }

    @Test
    fun `a fresh install stays off however many times Prefs is constructed`() {
        // `fresh` is read from an EMPTY store, and the first construction stops
        // it being empty. If the backfill were keyed on the flag rather than on
        // freshness, the second construction would see a non-fresh install with
        // the keys absent and switch both effects on - which is the old defaults
        // coming back through the door they were shown out of.
        repeat(5) { Prefs(ctx) }
        val p = Prefs(ctx)
        assertEquals(false, p.fxGrayscale)
        assertEquals(false, p.fxDimWallpaper)
    }
}
