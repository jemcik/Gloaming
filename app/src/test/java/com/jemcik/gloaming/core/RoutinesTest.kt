package com.jemcik.gloaming.core

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Samsung's routines, run for the night - one per screen effect.
 *
 * The questions are about OWNERSHIP, because that is where this can go wrong
 * without anyone noticing: a reconcile at noon that ends a routine the user
 * started by hand, a morning whose end was refused and then forgotten, a
 * switch flipped mid-window that leaves the old routine running. None of them
 * needs a Galaxy - they need the provider's contract, which [FakeRoutines]
 * carries.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RoutinesTest {

    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    /**
     * A Galaxy with our two routines saved: grayscale is 10, the dark theme 11.
     * A GALAXY, because the routines run only where the zen effects are thrown
     * away, and that is the one manufacturer prior in the app.
     */
    private fun phone(): FakeRoutines {
        org.robolectric.util.ReflectionHelpers.setStaticField(android.os.Build::class.java, "MANUFACTURER", "samsung")
        return FakeRoutines.install(ctx).apply {
        rows += FakeRoutines.Row(10, "Gloaming: grayscale")
        rows += FakeRoutines.Row(11, "Gloaming: dark theme")
        }
    }

    private fun prefs(gray: Boolean = true, dark: Boolean = true): Prefs = Prefs(ctx).apply {
        setRoutineUuid(RoutineEffect.GRAYSCALE, 10)
        setRoutineUuid(RoutineEffect.DARK, 11)
        fxGrayscale = gray
        fxDarkTheme = dark
    }

    @Test
    fun `the switched-on routines run exactly while the window does`() {
        val f = phone()
        val p = prefs()
        Routines.sync(ctx, p, windowActive = true)
        assertEquals(setOf("start 10", "start 11"), f.calls.toSet())
        assertEquals("what we started is on the books", setOf(10L, 11L), p.routinesStarted)
        // Every path lands here - reconcile on resume, the boot repair, a
        // dragged handle - so the second time through must ask nothing more.
        Routines.sync(ctx, p, windowActive = true)
        assertEquals(2, f.calls.size)
        Routines.sync(ctx, p, windowActive = false)
        assertEquals(setOf("end 10", "end 11"), f.calls.drop(2).toSet())
        assertTrue(p.routinesStarted.isEmpty())
        assertTrue(f.running.isEmpty())
    }

    @Test
    fun `a switch that is off keeps its routine idle`() {
        val f = phone()
        val p = prefs(gray = true, dark = false)
        Routines.sync(ctx, p, windowActive = true)
        assertEquals(listOf("start 10"), f.calls)
    }

    @Test
    fun `a switch flipped mid-window is honoured now, not at the next window`() {
        val f = phone()
        val p = prefs(gray = true, dark = false)
        Routines.sync(ctx, p, windowActive = true)
        p.fxDarkTheme = true
        Routines.sync(ctx, p, windowActive = true)
        assertEquals(listOf("start 10", "start 11"), f.calls)
        p.fxGrayscale = false
        Routines.sync(ctx, p, windowActive = true)
        assertEquals(listOf("start 10", "start 11", "end 10"), f.calls)
        assertEquals(setOf(11L), p.routinesStarted)
    }

    @Test
    fun `nothing adopted, nothing asked of the phone`() {
        val f = phone()
        val p = Prefs(ctx).apply { fxGrayscale = true; fxDarkTheme = true }
        Routines.sync(ctx, p, windowActive = true)
        Routines.sync(ctx, p, windowActive = false)
        assertTrue(f.calls.isEmpty())
    }

    @Test
    fun `a daytime reconcile does not end a run the user began by hand`() {
        val f = phone()
        f.running += 10
        val p = prefs()
        // Running now - but not by us. The window is shut and reconcile comes
        // through, as it does on every resume.
        Routines.sync(ctx, p, windowActive = false)
        assertTrue("it is theirs to end: " + f.calls, f.calls.isEmpty())
        assertTrue(10L in f.running)
    }

    @Test
    fun `a refused end is retried, not forgotten`() {
        val f = phone()
        val p = prefs(gray = true, dark = false)
        Routines.sync(ctx, p, windowActive = true)
        f.refuse = "Failed to end routine"
        Routines.sync(ctx, p, windowActive = false)
        assertEquals(listOf("start 10", "end 10"), f.calls)
        assertEquals("still ours, still owed", setOf(10L), p.routinesStarted)
        f.refuse = null
        Routines.sync(ctx, p, windowActive = false)
        assertEquals(listOf("start 10", "end 10", "end 10"), f.calls)
        assertTrue(p.routinesStarted.isEmpty())
    }

    @Test
    fun `a refused start stays unrecorded, so the next sync asks again`() {
        val f = phone()
        val p = prefs(gray = true, dark = false)
        f.refuse = "Routine is disabled"
        Routines.sync(ctx, p, windowActive = true)
        assertTrue(p.routinesStarted.isEmpty())
        f.refuse = null
        Routines.sync(ctx, p, windowActive = true)
        assertEquals(listOf("start 10", "start 10"), f.calls)
        assertEquals(setOf(10L), p.routinesStarted)
    }

    @Test
    fun `the list is what Samsung reports, running state and all`() {
        val f = phone()
        f.rows += FakeRoutines.Row(12, "Focus", enabled = false)
        f.running += 10
        val list = Routines.list(ctx)
        assertEquals(listOf("Gloaming: grayscale", "Gloaming: dark theme", "Focus"), list.map { it.name })
        assertTrue(list[0].running && list[0].enabled)
        assertFalse(list[2].enabled)
    }

    @Test
    fun `a phone without the door answers nothing and throws nothing`() {
        // Every phone but a Galaxy. Nothing is registered, nothing is granted.
        assertFalse(Routines.available(ctx))
        assertTrue(Routines.list(ctx).isEmpty())
        val p = prefs()
        Routines.sync(ctx, p, windowActive = true)
        assertTrue("nothing started, so nothing is owed", p.routinesStarted.isEmpty())
    }

    @Test
    fun `the door is a probe - the provider and the permission together`() {
        phone()
        assertTrue(Routines.available(ctx))
    }

    @Test
    fun `an offered routine is adopted on evidence, never on the offer`() {
        val f = FakeRoutines.install(ctx)
        val p = Prefs(ctx)
        p.routineOffered = RoutineEffect.GRAYSCALE.key
        p.routineOfferedName = "Gloaming: grayscale"
        // The user was sent to Samsung's editor and came back. Nothing was
        // saved there - so nothing is taken, and the offer stays open.
        assertNull(Routines.adopt(ctx, p))
        assertEquals(0L, p.routineUuid(RoutineEffect.GRAYSCALE))
        assertEquals(RoutineEffect.GRAYSCALE.key, p.routineOffered)
        // Now it exists, under the exact name the file carried. THAT is the
        // evidence - and the switch it backs goes on with it.
        f.rows += FakeRoutines.Row(42, "Gloaming: grayscale")
        assertEquals(RoutineEffect.GRAYSCALE, Routines.adopt(ctx, p))
        assertEquals(42L, p.routineUuid(RoutineEffect.GRAYSCALE))
        assertTrue(p.fxGrayscale)
        assertNull("the offer is answered", p.routineOffered)
        assertEquals(listOf(RoutineEffect.GRAYSCALE), Routines.adopted(p))
    }

    @Test
    fun `a routine by our name that was never offered is not taken uninvited`() {
        val f = FakeRoutines.install(ctx)
        f.rows += FakeRoutines.Row(42, "Gloaming: grayscale")
        val p = Prefs(ctx)
        assertNull(Routines.adopt(ctx, p))
        assertEquals(0L, p.routineUuid(RoutineEffect.GRAYSCALE))
    }

    @Test
    fun `a routine deleted in Samsung's app is forgotten, one switched off there is kept`() {
        val f = phone()
        val p = prefs()
        // Nothing missing: nothing forgotten.
        assertTrue(Routines.prune(ctx, p).isEmpty())
        // The dark-theme routine is gone from the phone; the grayscale one is
        // merely switched off there, which is a state, not an absence.
        f.rows.removeIf { it.uuid == 11L }
        f.rows.replaceAll { if (it.uuid == 10L) FakeRoutines.Row(10, it.name, enabled = false) else it }
        assertEquals(listOf(RoutineEffect.DARK), Routines.prune(ctx, p))
        assertEquals(0L, p.routineUuid(RoutineEffect.DARK))
        assertEquals(10L, p.routineUuid(RoutineEffect.GRAYSCALE))
    }

    @Test
    fun `where the zen effects are applied, the routines stay silent`() {
        val f = phone()
        val p = prefs()
        // The day this Galaxy is seen applying the rule's effects, ScreenEffects
        // records it for good - and from then on the rule does the work.
        p.effectsSeen = true
        Routines.sync(ctx, p, windowActive = true)
        assertTrue("the rule does this job, not the routines: " + f.calls, f.calls.isEmpty())
    }

    @Test
    fun `resume reads Samsung's app once, and not at all where there is nothing to ask`() {
        val f = FakeRoutines.install(ctx)
        val p = Prefs(ctx)
        // A Galaxy before any set-up, and every other phone: no read.
        assertNull(Routines.refresh(ctx, p))
        assertEquals(0, f.queries)
        // An offer open and a routine adopted: forget and adopt off ONE read.
        f.rows += FakeRoutines.Row(42, "Gloaming: grayscale")
        p.setRoutineUuid(RoutineEffect.DARK, 11)
        p.routineOffered = RoutineEffect.GRAYSCALE.key
        p.routineOfferedName = "Gloaming: grayscale"
        assertEquals(RoutineEffect.GRAYSCALE, Routines.refresh(ctx, p))
        assertEquals(1, f.queries)
        assertEquals("the deleted one is forgotten off the same read", 0L, p.routineUuid(RoutineEffect.DARK))
        assertEquals(42L, p.routineUuid(RoutineEffect.GRAYSCALE))
    }

    @Test
    fun `a provider that throws is a refusal, not a crash`() {
        val f = phone()
        f.throwOnCall = true
        val p = prefs(gray = true, dark = false)
        Routines.sync(ctx, p, windowActive = true)
        assertEquals(listOf("start 10"), f.calls)
        assertTrue("nothing recorded as started", p.routinesStarted.isEmpty())
    }

    @Test
    fun `a provider whose columns moved lists nothing, and nothing is adopted off it`() {
        val f = FakeRoutines.install(ctx)
        f.bareColumns = true
        f.rows += FakeRoutines.Row(42, "Gloaming: grayscale")
        assertTrue(Routines.list(ctx).isEmpty())
        val p = Prefs(ctx)
        p.routineOffered = RoutineEffect.GRAYSCALE.key
        p.routineOfferedName = "Gloaming: grayscale"
        assertNull(Routines.refresh(ctx, p))
    }

    @Test
    fun `the switch behind each effect is one key, read and written through the same door`() {
        val p = Prefs(ctx)
        for (e in RoutineEffect.entries) {
            p.setFxWants(e, true)
            assertTrue(p.fxWants(e))
            p.setFxWants(e, false)
            assertFalse(p.fxWants(e))
        }
        p.setFxWants(RoutineEffect.GRAYSCALE, true)
        assertTrue("it is the grayscale switch the rule reads too", p.fxGrayscale)
        assertFalse(p.fxDarkTheme)
    }

    private fun phoneDims(on: Boolean) =
        android.provider.Settings.System.putInt(ctx.contentResolver, Routines.DIM_KEY, if (on) 1 else 0)

    @Test
    fun `on a phone that dims by itself, only NOT dimming needs a routine`() {
        val f = phone()
        f.rows += FakeRoutines.Row(12, "Gloaming: dim wallpaper")
        f.rows += FakeRoutines.Row(13, "Gloaming: undimmed wallpaper")
        phoneDims(true)
        val p = prefs(gray = false, dark = true).apply {
            setRoutineUuid(RoutineEffect.DIM, 12)
            setRoutineUuid(RoutineEffect.DIM_OFF, 13)
        }
        // Dim on: the phone does that already, so nothing but the dark theme runs.
        p.fxDimWallpaper = true
        Routines.sync(ctx, p, windowActive = true)
        assertEquals(listOf("start 11"), f.calls)
        // Dim off: the night must differ from the day - the OFF routine runs.
        p.fxDimWallpaper = false
        Routines.sync(ctx, p, windowActive = true)
        assertEquals(listOf("start 11", "start 13"), f.calls)
        assertNull(Routines.dimRoutineFor(ctx, p, true))
        assertEquals(RoutineEffect.DIM_OFF, Routines.dimRoutineFor(ctx, p, false))
    }

    @Test
    fun `on a phone that does not dim, only dimming needs a routine`() {
        val f = phone()
        f.rows += FakeRoutines.Row(12, "Gloaming: dim wallpaper")
        f.rows += FakeRoutines.Row(13, "Gloaming: undimmed wallpaper")
        phoneDims(false)
        val p = prefs(gray = false, dark = true).apply {
            setRoutineUuid(RoutineEffect.DIM, 12)
            setRoutineUuid(RoutineEffect.DIM_OFF, 13)
        }
        p.fxDimWallpaper = false
        Routines.sync(ctx, p, windowActive = true)
        assertEquals(listOf("start 11"), f.calls)
        p.fxDimWallpaper = true
        Routines.sync(ctx, p, windowActive = true)
        assertEquals(listOf("start 11", "start 12"), f.calls)
        assertEquals(RoutineEffect.DIM, Routines.dimRoutineFor(ctx, p, true))
        assertNull(Routines.dimRoutineFor(ctx, p, false))
    }

    @Test
    fun `the wallpaper is never touched without the dark theme`() {
        val f = phone()
        f.rows += FakeRoutines.Row(13, "Gloaming: undimmed wallpaper")
        phoneDims(true)
        val p = prefs(gray = false, dark = false).apply {
            setRoutineUuid(RoutineEffect.DIM_OFF, 13)
            fxDimWallpaper = false
        }
        // One UI dims the wallpaper only in dark mode: with the dark theme off
        // the routine would run and change nothing, so it is not asked.
        Routines.sync(ctx, p, windowActive = true)
        assertTrue("nothing to dim with: " + f.calls, f.calls.isEmpty())
    }

    @Test
    fun `the wallpaper switch opens telling the truth about the phone`() {
        val f = FakeRoutines.install(ctx)
        f.rows += FakeRoutines.Row(11, "Gloaming: dark theme")
        phoneDims(true)
        val p = Prefs(ctx)
        p.fxDimWallpaper = false
        p.routineOffered = RoutineEffect.DARK.key
        p.routineOfferedName = "Gloaming: dark theme"
        assertEquals(RoutineEffect.DARK, Routines.adopt(ctx, p))
        assertTrue("the phone dims in dark mode, so the switch says so", p.fxDimWallpaper)
    }

    @Test
    fun `our own routine's work is not mistaken for the phone's setting`() {
        val f = phone()
        f.rows += FakeRoutines.Row(13, "Gloaming: undimmed wallpaper")
        phoneDims(true)
        val p = prefs(gray = false, dark = true).apply {
            setRoutineUuid(RoutineEffect.DIM_OFF, 13)
            fxDimWallpaper = false
        }
        Routines.sync(ctx, p, windowActive = true)
        assertEquals(listOf("start 11", "start 13"), f.calls)
        // Samsung ran it: the key now reads what WE made it. The next sync -
        // and there is always a next sync - must not read that as "the phone
        // does not dim, so nothing is needed" and end the routine it just
        // started. Measured: start, end, start, within a second.
        phoneDims(false)
        Routines.sync(ctx, p, windowActive = true)
        assertEquals("nothing ended, nothing restarted", listOf("start 11", "start 13"), f.calls)
        assertTrue("the setting under our routine is still ON", Routines.phoneDimsByItself(ctx, p))
        // And the window's end puts it back, as the routine's own revert does.
        Routines.sync(ctx, p, windowActive = false)
        assertEquals(setOf("end 11", "end 13"), f.calls.drop(2).toSet())
    }

    @Test
    fun `a flip is one call, even while Samsung's revert is still landing`() {
        val f = phone()
        f.rows += FakeRoutines.Row(12, "Gloaming: dim wallpaper")
        f.rows += FakeRoutines.Row(13, "Gloaming: undimmed wallpaper")
        phoneDims(true)
        val p = prefs(gray = false, dark = true).apply {
            setRoutineUuid(RoutineEffect.DIM, 12)
            setRoutineUuid(RoutineEffect.DIM_OFF, 13)
            fxDimWallpaper = false
        }
        Routines.sync(ctx, p, windowActive = true)
        assertEquals(listOf("start 11", "start 13"), f.calls)
        phoneDims(false) // Samsung ran the OFF routine
        // The switch goes back on. The OFF routine ends - and its revert has
        // not landed yet, so the key still reads 0 at the next sync. That must
        // not start the ON routine: the phone dims by itself, as recorded when
        // the window opened. Measured: end, then a needless start, in one second.
        p.fxDimWallpaper = true
        Routines.sync(ctx, p, windowActive = true)
        Routines.sync(ctx, p, windowActive = true)
        assertEquals(listOf("start 11", "start 13", "end 13"), f.calls)
        // And the record is let go with the window, so tomorrow reads the phone afresh.
        Routines.sync(ctx, p, windowActive = false)
        assertEquals(-1, p.dimBaseline)
    }

    @Test
    fun `a record made while our routine already holds the key reads what was under it`() {
        val f = phone()
        f.rows += FakeRoutines.Row(13, "Gloaming: undimmed wallpaper")
        // An upgrade mid-window: the OFF routine is on the books and the key
        // reads what it made it, and no record exists yet.
        phoneDims(false)
        val p = prefs(gray = false, dark = true).apply {
            setRoutineUuid(RoutineEffect.DIM_OFF, 13)
            fxDimWallpaper = false
            routinesStarted = setOf(11L, 13L)
        }
        Routines.sync(ctx, p, windowActive = true)
        assertEquals("the phone dims by itself; our routine is what says 0", 1, p.dimBaseline)
        assertTrue("nothing ended: " + f.calls, f.calls.isEmpty())
    }
}
