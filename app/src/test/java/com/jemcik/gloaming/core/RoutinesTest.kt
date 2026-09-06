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
 * Samsung's routine, run for the night.
 *
 * The questions are about OWNERSHIP, because that is where this can go wrong
 * without anyone noticing: a reconcile at noon that ends a routine the user
 * started by hand, a morning whose end was refused and then forgotten, a pick
 * changed mid-window that leaves the old routine running. None of them needs
 * a Galaxy - they need the provider's contract, which [FakeRoutines] carries.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RoutinesTest {

    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    private fun phone(vararg names: String): FakeRoutines =
        FakeRoutines.install(ctx).apply {
            names.forEachIndexed { i, n -> rows += FakeRoutines.Row(10L + i, n) }
        }

    @Test
    fun `the routine runs exactly while the window does`() {
        val f = phone("Sleep")
        val p = Prefs(ctx)
        p.routineUuid = 10
        Routines.sync(ctx, p, windowActive = true)
        assertEquals(listOf("start 10"), f.calls)
        assertEquals("what we started is on the books", 10L, p.routineStarted)
        // Every path lands here - reconcile on resume, the boot repair, a
        // dragged handle - so the second time through must ask nothing more.
        Routines.sync(ctx, p, windowActive = true)
        assertEquals(1, f.calls.size)
        Routines.sync(ctx, p, windowActive = false)
        assertEquals(listOf("start 10", "end 10"), f.calls)
        assertEquals(0L, p.routineStarted)
        assertFalse(10L in f.running)
    }

    @Test
    fun `nothing chosen, nothing asked of the phone`() {
        val f = phone("Sleep")
        val p = Prefs(ctx)
        Routines.sync(ctx, p, windowActive = true)
        Routines.sync(ctx, p, windowActive = false)
        assertTrue(f.calls.isEmpty())
    }

    @Test
    fun `a daytime reconcile does not end a run the user began by hand`() {
        val f = phone("Sleep")
        f.running += 10
        val p = Prefs(ctx)
        p.routineUuid = 10
        // Chosen for the night and running now - but not by us. The window is
        // shut and reconcile comes through, as it does on every resume.
        Routines.sync(ctx, p, windowActive = false)
        assertTrue("it is theirs to end: " + f.calls, f.calls.isEmpty())
        assertTrue(10L in f.running)
    }

    @Test
    fun `a refused end is retried, not forgotten`() {
        val f = phone("Sleep")
        val p = Prefs(ctx)
        p.routineUuid = 10
        Routines.sync(ctx, p, windowActive = true)
        f.refuse = "Failed to end routine"
        Routines.sync(ctx, p, windowActive = false)
        assertEquals(listOf("start 10", "end 10"), f.calls)
        assertEquals("still ours, still owed", 10L, p.routineStarted)
        f.refuse = null
        Routines.sync(ctx, p, windowActive = false)
        assertEquals(listOf("start 10", "end 10", "end 10"), f.calls)
        assertEquals(0L, p.routineStarted)
    }

    @Test
    fun `a refused start stays unlatched, so the next sync asks again`() {
        val f = phone("Sleep")
        val p = Prefs(ctx)
        p.routineUuid = 10
        f.refuse = "Routine is disabled"
        Routines.sync(ctx, p, windowActive = true)
        assertEquals(0L, p.routineStarted)
        f.refuse = null
        Routines.sync(ctx, p, windowActive = true)
        assertEquals(listOf("start 10", "start 10"), f.calls)
        assertEquals(10L, p.routineStarted)
    }

    @Test
    fun `re-picking mid-window ends the old one before starting the new`() {
        val f = phone("Sleep", "Focus")
        val p = Prefs(ctx)
        p.routineUuid = 10
        Routines.sync(ctx, p, windowActive = true)
        p.routineUuid = 11
        Routines.sync(ctx, p, windowActive = true)
        assertEquals(listOf("start 10", "end 10", "start 11"), f.calls)
        assertEquals(11L, p.routineStarted)
        assertEquals(setOf(11L), f.running)
    }

    @Test
    fun `choosing none mid-window ends what we started`() {
        val f = phone("Sleep")
        val p = Prefs(ctx)
        p.routineUuid = 10
        Routines.sync(ctx, p, windowActive = true)
        p.routineUuid = 0
        Routines.sync(ctx, p, windowActive = true)
        assertEquals(listOf("start 10", "end 10"), f.calls)
        assertEquals(0L, p.routineStarted)
    }

    @Test
    fun `the list is what Samsung reports, running state and all`() {
        val f = phone("Sleep")
        f.rows += FakeRoutines.Row(11, "Focus", enabled = false)
        f.running += 10
        val list = Routines.list(ctx)
        assertEquals(listOf("Sleep", "Focus"), list.map { it.name })
        assertTrue(list[0].running && list[0].enabled)
        assertFalse(list[1].running)
        assertFalse(list[1].enabled)
        val p = Prefs(ctx)
        p.routineUuid = 11
        assertEquals("Focus", Routines.chosen(ctx, p)?.name)
        p.routineUuid = 99
        assertNull("a deleted routine is gone, not the first one in the list", Routines.chosen(ctx, p))
    }

    @Test
    fun `a phone without the door answers nothing and throws nothing`() {
        // Every phone but a Galaxy. Nothing is registered, nothing is granted.
        assertFalse(Routines.available(ctx))
        assertTrue(Routines.list(ctx).isEmpty())
        val p = Prefs(ctx)
        p.routineUuid = 10
        Routines.sync(ctx, p, windowActive = true)
        assertEquals("nothing started, so nothing is owed", 0L, p.routineStarted)
    }

    @Test
    fun `the door is a probe - the provider and the permission together`() {
        phone("Sleep")
        assertTrue(Routines.available(ctx))
    }
}
