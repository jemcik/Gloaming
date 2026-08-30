package com.jemcik.gloaming.ui

import android.content.Context
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.jemcik.gloaming.Home
import com.jemcik.gloaming.R
import com.jemcik.gloaming.core.Prefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.DayOfWeek
import java.time.LocalTime

/**
 * The note at the foot of the two cards on Home, and the tense of the label
 * above one of them.
 *
 * Both exist for one reported flaw: every switch on that screen is a PLAN -
 * written to prefs on the tap, applied when the window opens - and while the
 * window is not running they look exactly like live controls. With bedtime
 * switched off, a person turns Do Not Disturb "on" and reasonably expects to be
 * left alone tonight.
 *
 * These assert the note is PRESENT when nothing is in effect and ABSENT while
 * it is, which is the pair that can go wrong. A note that never appears is a
 * silent regression of the fix; one that appears while bedtime is running
 * contradicts the phone's own readout directly above it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlanNoteTest {

    @get:Rule
    val compose = createComposeRule()

    private fun ctx(): Context = ApplicationProvider.getApplicationContext()

    /** Every string the screen is rendering, in one list. */
    private fun texts(
        enabled: Boolean,
        from: LocalTime,
        to: LocalTime,
        days: Set<DayOfWeek> = DayOfWeek.entries.toSet()
    ): List<String> {
        val p = Prefs(ctx())
        p.enabled = enabled; p.startTime = from; p.endTime = to; p.days = days
        p.activeDay = Prefs.NO_DAY
        p.fxDnd = true
        compose.setContent {
            GloamingTheme(dark = false) {
                Home(rememberScrollState(), onOpenSettings = {}, onOpenInterruptions = {})
            }
        }
        return compose
            .onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty() }
            .map { it.text }
    }

    private fun s(id: Int) = ctx().getString(id)

    /** SectionLabel uppercases in the text's own locale before drawing. */
    private fun label(id: Int) = s(id).uppercase(java.util.Locale.ENGLISH)
    private val now: LocalTime get() = LocalTime.now()

    // ---- switched off: nothing below the switch does anything ----

    @Test
    fun `switched off, the cards say so`() {
        val t = texts(false, now.plusHours(3), now.plusHours(9))
        assertTrue(t.toString(), t.contains(s(R.string.note_off)))
    }

    @Test
    fun `switched off, both cards carry it and not just the first`() {
        // The Do Not Disturb card had a slot for this already; the screen
        // effects card had none, and is just as misleading on its own.
        val t = texts(false, now.plusHours(3), now.plusHours(9))
        assertEquals(t.toString(), 2, t.count { it == s(R.string.note_off) })
    }

    // ---- armed, but the window has not opened ----

    @Test
    fun `armed and waiting, the note says until when`() {
        // The half of the flaw that was not reported: the master switch is
        // green, and still nothing is being silenced.
        val t = texts(true, now.plusHours(3), now.plusHours(9))
        val opener = s(R.string.note_until).substringBefore("%1")
        assertTrue(t.toString(), t.any { it.startsWith(opener) })
    }

    @Test
    fun `armed and waiting, it does not claim to be off`() {
        val t = texts(true, now.plusHours(3), now.plusHours(9))
        assertFalse(t.toString(), t.contains(s(R.string.note_off)))
    }

    // ---- running: the phone's own answer belongs there instead ----

    @Test
    fun `running, neither note is drawn`() {
        val t = texts(true, now.minusHours(2), now.plusHours(6))
        val opener = s(R.string.note_until).substringBefore("%1")
        assertFalse(t.toString(), t.contains(s(R.string.note_off)))
        assertFalse(t.toString(), t.any { it.startsWith(opener) })
    }

    // ---- the label is a claim about now, so it follows the tense ----

    @Test
    fun `not running, the label is in the future tense`() {
        val t = texts(false, now.plusHours(3), now.plusHours(9))
        assertTrue(t.toString(), t.contains(label(R.string.section_how_the_screen_will_look)))
        assertFalse(t.toString(), t.contains(label(R.string.section_how_the_screen_looks)))
    }

    @Test
    fun `running, the label is in the present tense`() {
        val t = texts(true, now.minusHours(2), now.plusHours(6))
        assertTrue(t.toString(), t.contains(label(R.string.section_how_the_screen_looks)))
        assertFalse(t.toString(), t.contains(label(R.string.section_how_the_screen_will_look)))
    }
}
