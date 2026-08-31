package com.jemcik.gloaming.ui

import android.content.Context
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.jemcik.gloaming.R
import com.jemcik.gloaming.core.Prefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.GraphicsMode
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

/**
 * The sentence under the dial, in every branch it has.
 *
 * It exists because the dial cannot say WHICH morning, so the day words are the
 * whole point of it and are the part that can be wrong without looking wrong.
 * One of them already was: with the app off and a one-off running, it named
 * tomorrow's window instead of the one you were inside.
 *
 * These assert the day words rather than the whole string, deliberately. An
 * assertion built by formatting the same resource with the same helpers would
 * only prove the code equals itself.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WindowSentenceTest {

    @get:Rule
    val compose = createComposeRule()

    private fun ctx(): Context = ApplicationProvider.getApplicationContext()

    private fun sentence(
        enabled: Boolean,
        from: LocalTime,
        to: LocalTime,
        days: Set<DayOfWeek>
    ): String {
        val p = Prefs(ctx())
        p.enabled = enabled; p.startTime = from; p.endTime = to; p.days = days
        p.activeDay = Prefs.NO_DAY
        compose.setContent {
            GloamingTheme(dark = false) {
                Home(rememberScrollState(), onOpenSettings = {}, onOpenInterruptions = {})
            }
        }
        val opener = ctx().getString(R.string.window_span).substringBefore(" %1")
        return compose
            .onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty() }
            .map { it.text }
            .first { it.startsWith(opener) }
    }

    private val today get() = ctx().getString(R.string.day_today)
    private val tomorrow get() = ctx().getString(R.string.day_tomorrow)
    private val now: LocalTime get() = LocalTime.now()

    // ---- inside the window: it must name the window you are IN ----

    @Test
    fun `a one-off you are inside names today, even switched off`() {
        // The reported bug. A one-off counts as running only while the switch is
        // on, so asking liveWindowEnd with the real switch fell through to
        // nextStart and named tomorrow - while the dial drew the marker inside
        // the arc, and switching on would have started bedtime that second.
        val s = sentence(false, now.minusHours(2), now.plusHours(6), emptySet())
        assertTrue(s, s.contains(today))
    }

    @Test
    fun `a running one-off names today`() {
        val s = sentence(true, now.minusHours(2), now.plusHours(6), emptySet())
        assertTrue(s, s.contains(today))
    }

    @Test
    fun `a repeating schedule you are inside names today`() {
        val s = sentence(false, now.minusHours(2), now.plusHours(6), DayOfWeek.entries.toSet())
        assertTrue(s, s.contains(today))
    }

    // ---- outside the window: it must name the NEXT one, not today ----

    @Test
    fun `a window still to come names the day it actually begins`() {
        // This asserted a flat "not today" and so only passed between 21:00 and
        // midnight, which is the one window where now+3h lands on the next date.
        // It ran green for weeks and failed the first time the suite was run
        // after midnight - the assertion was about the clock, not the code.
        //
        // A one-off three hours out is TODAY at 09:00 and TOMORROW at 23:00, and
        // both are correct. What must hold at every hour is that the sentence
        // names the day the window really starts on, so that is what is checked.
        val start = now.plusHours(3)
        val wrapsPastMidnight = start.isBefore(now)
        val s = sentence(false, start, now.plusHours(9), emptySet())
        if (wrapsPastMidnight) assertFalse(s, s.contains(today))
        else assertTrue(s, s.contains(today))
    }

    // ---- shape of the sentence ----

    @Test
    fun `a window inside one day names that day once`() {
        val s = sentence(true, now.plusHours(1), now.plusHours(4), DayOfWeek.entries.toSet())
        val day = if (s.contains(today)) today else tomorrow
        assertEquals("$s names '$day' twice", 1, s.split(day).size - 1)
    }

    @Test
    fun `a window days away names weekdays, not today or tomorrow`() {
        // Past a day, "tomorrow" would be a lie by omission.
        // Three days out. The day set names the MORNING a window ends on, so
        // this one begins two days out - neither end can be today or tomorrow,
        // whatever day the test runs on. Picking "any day that is not today or
        // tomorrow" is what the first version did, and it picked tomorrow.
        val far = java.time.LocalDate.now().dayOfWeek.plus(3)
        val s = sentence(true, LocalTime.of(22, 0), LocalTime.of(7, 0), setOf(far))
        assertFalse(s, s.contains(today))
        assertFalse(s, s.contains(tomorrow))
    }

    @Test
    fun `each weekday set is declined where the language declines it`() {
        // java.time returns the NOMINATIVE in every TextStyle - both FULL and
        // FULL_STANDALONE give "среда" - so the window sentence shipped as
        // "С 22:30 среда до 8:30 четверг", which is not Russian.
        //
        // There are TWO sets because the two sentences need different grammar:
        // the span reads the day as a possessive ("from Wednesday's 22:30") and
        // the note as a point in time with its own preposition ("в среду").
        // Only English is indifferent, which is what makes it the control here.
        //
        // Asserted as PROPERTIES, never against the wording: English must MATCH
        // java.time, Russian and Ukrainian must differ from it, and their two
        // sets must differ from EACH OTHER - which is what catches one being
        // pasted over the other. A test on the literal strings would only prove
        // the translation equals itself and would block any rephrasing.
        val span = listOf(
            R.string.day_span_monday, R.string.day_span_tuesday,
            R.string.day_span_wednesday, R.string.day_span_thursday,
            R.string.day_span_friday, R.string.day_span_saturday,
            R.string.day_span_sunday
        )
        val note = listOf(
            R.string.day_note_monday, R.string.day_note_tuesday,
            R.string.day_note_wednesday, R.string.day_note_thursday,
            R.string.day_note_friday, R.string.day_note_saturday,
            R.string.day_note_sunday
        )
        for ((tag, declines) in listOf("en" to false, "ru" to true, "uk" to true)) {
            RuntimeEnvironment.setQualifiers("+$tag")
            val ctx = ApplicationProvider.getApplicationContext<Context>()
            val loc = Locale.forLanguageTag(tag)
            DayOfWeek.entries.forEach { day ->
                val i = day.ordinal
                val s = ctx.getString(span[i])
                val n = ctx.getString(note[i])
                val nominative = day.getDisplayName(TextStyle.FULL_STANDALONE, loc)
                if (declines) {
                    assertNotEquals("$tag $day span is still the nominative", nominative, s)
                    assertNotEquals("$tag $day note is still the nominative", nominative, n)
                    assertNotEquals("$tag $day span and note are the same form", s, n)
                } else {
                    assertEquals("$tag $day span should match java.time", nominative, s)
                    assertEquals("$tag $day note should match java.time", nominative, n)
                }
            }
        }
    }
}
