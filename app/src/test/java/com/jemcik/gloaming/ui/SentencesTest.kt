package com.jemcik.gloaming.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.jemcik.gloaming.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

/**
 * The sentence builders, driven DIRECTLY.
 *
 * Everything about these was previously checked by rendering the whole Home
 * screen and reading the answer back out of the semantics tree, because they
 * were file-private inside MainActivity.kt. Those tests still exist and still
 * earn their place - they check a sentence REACHES the screen, which is a
 * different question - but they can only reach the branches Home can be put
 * into. These reach the rest, and they run in milliseconds instead of
 * inflating a screen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SentencesTest {

    private fun ctx(): Context = ApplicationProvider.getApplicationContext()

    // ---------- span: the dial centre and the app bar's status line ----------

    @Test
    fun `under an hour, the duration drops the hour instead of saying 0h`() {
        // "0h 20m" said nothing and cost a third of the width of the longest
        // thing this string has to fit - the status line, which truncates.
        val s = span(ctx().resources, 20)
        assertEquals("20m", s)
        assertTrue("an hourless duration must not print an hour", !s.contains("h"))
    }

    @Test
    fun `with an hour, the minutes are zero-padded so the countdown holds its width`() {
        // The countdown ticks once a minute in a centred readout. Unpadded, it
        // would change width every ten minutes and shift on the spot.
        assertEquals("5h 05m", span(ctx().resources, 5 * 60 + 5))
        assertEquals(
            "a padded duration must not change width as the minutes fall",
            span(ctx().resources, 5 * 60 + 55).length,
            span(ctx().resources, 5 * 60 + 5).length
        )
    }

    @Test
    fun `exactly one hour is an hour, not fifty-nine minutes of rounding`() {
        assertEquals("1h 00m", span(ctx().resources, 60))
        assertEquals("59m", span(ctx().resources, 59))
    }

    // ---------- dayWord ----------

    @Test
    fun `the same date is today and the next is tomorrow`() {
        val now = LocalDateTime.of(2026, 8, 31, 22, 0)
        assertEquals(
            ctx().getString(R.string.day_today),
            dayWord(ctx(), now.plusHours(1), now, DaySlot.SPAN)
        )
        assertEquals(
            ctx().getString(R.string.day_tomorrow),
            dayWord(ctx(), now.plusDays(1), now, DaySlot.SPAN)
        )
    }

    @Test
    fun `today and tomorrow are adverbs, so both sentences get the same word`() {
        // They decline for nothing, which is why one pair serves both sets -
        // and why the weekday bug only ever showed for a window over a day out.
        val now = LocalDateTime.of(2026, 8, 31, 22, 0)
        for (at in listOf(now, now.plusDays(1))) {
            assertEquals(
                dayWord(ctx(), at, now, DaySlot.SPAN),
                dayWord(ctx(), at, now, DaySlot.NOTE)
            )
        }
    }

    @Test
    fun `every weekday maps to its own day, in both sets`() {
        // DAY_SPAN and DAY_NOTE are indexed by DayOfWeek.ordinal. If either
        // array were ever reordered, Monday would quietly print Tuesday - no
        // crash, no failing format, just a schedule that lies. English does not
        // decline, so there the resource must equal java.time's own name.
        val now = LocalDateTime.of(2026, 8, 31, 12, 0)   // a Monday
        for (d in DayOfWeek.entries) {
            val at = now.plusDays(((d.value - now.dayOfWeek.value + 7) % 7 + 7).toLong())
            val expected = d.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
            assertEquals(
                "day_span_ is indexed by ordinal and must line up with it",
                expected, dayWord(ctx(), at, now, DaySlot.SPAN)
            )
            assertEquals(
                "day_note_ is indexed by the same ordinal",
                expected, dayWord(ctx(), at, now, DaySlot.NOTE)
            )
        }
    }

    @Test
    fun `the two day sets are separate resources, not one aliased twice`() {
        // They are the same word in English and MUST differ in Russian and
        // Ukrainian, where the window sentence wants a genitive and the plan
        // note an accusative with its own preposition. Asserting they are
        // distinct RESOURCES is what catches one set pasted over the other.
        assertEquals(DAY_SPAN.size, DAY_NOTE.size)
        assertEquals(DayOfWeek.entries.size, DAY_SPAN.size)
        for (i in DAY_SPAN.indices) {
            assertNotEquals(
                "the two sets must be different resources",
                DAY_SPAN[i], DAY_NOTE[i]
            )
        }
    }

    // ---------- planNote ----------

    @Test
    fun `switched off, the note says so rather than naming a time`() {
        val note = planNote(
            ctx(), enabled = false,
            start = LocalTime.of(22, 30), end = LocalTime.of(7, 0),
            days = DayOfWeek.entries.toSet(), locale = Locale.ENGLISH
        )
        assertEquals(ctx().getString(R.string.note_off), note)
    }

    @Test
    fun `no days chosen is a one-off, and a one-off still names its next start`() {
        // Written first as "armed with nothing to run says note_unscheduled",
        // which failed - and the test was wrong about the app, not the other
        // way round. An empty day set IS the one-off, and Scheduler.nextStart
        // answers it with today or tomorrow rather than null.
        //
        // Which makes note_unscheduled UNREACHABLE, and that is worth writing
        // down so nobody else spends an afternoon trying to reach it:
        // isOneOff(days) == days.isEmpty(), so nextStart's empty branch never
        // reaches nextOccurrence's own empty guard, and for a non-empty set its
        // eight-day search always matches within a week. nextStart cannot
        // return null. The fallback stays because the signature is nullable and
        // something must handle it; it just cannot be observed.
        val note = planNote(
            ctx(), enabled = true,
            start = LocalTime.of(22, 30), end = LocalTime.of(7, 0),
            days = emptySet(), locale = Locale.ENGLISH
        )
        assertNotEquals(ctx().getString(R.string.note_unscheduled), note)
        assertNotEquals(ctx().getString(R.string.note_off), note)
        assertTrue("a one-off still says when it starts: $note", note.contains("10:30"))
    }

    @Test
    fun `armed and repeating, the note names when it starts`() {
        val note = planNote(
            ctx(), enabled = true,
            start = LocalTime.of(22, 30), end = LocalTime.of(7, 0),
            days = DayOfWeek.entries.toSet(), locale = Locale.ENGLISH
        )
        assertNotEquals(ctx().getString(R.string.note_off), note)
        assertNotEquals(ctx().getString(R.string.note_unscheduled), note)
        // and it must carry a day word, because a clock time alone reads as
        // TODAY - the flaw that has now cost two separate fixes.
        assertTrue(
            "the plan note must name a day, not just a time: $note",
            DayOfWeek.entries.any {
                note.contains(it.getDisplayName(TextStyle.FULL, Locale.ENGLISH))
            } || note.contains(ctx().getString(R.string.day_today))
                || note.contains(ctx().getString(R.string.day_tomorrow))
        )
    }
}
