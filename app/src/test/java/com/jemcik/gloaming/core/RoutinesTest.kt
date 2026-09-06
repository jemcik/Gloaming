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

    /** A Galaxy with our two routines saved: grayscale is 10, the dark theme 11. */
    private fun phone(): FakeRoutines = FakeRoutines.install(ctx).apply {
        rows += FakeRoutines.Row(10, "Gloaming: grayscale")
        rows += FakeRoutines.Row(11, "Gloaming: dark theme")
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
}
