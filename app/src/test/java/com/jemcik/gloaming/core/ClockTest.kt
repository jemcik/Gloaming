package com.jemcik.gloaming.core

import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalTime

/**
 * Splitting a clock time from its day period, per locale.
 *
 * The bug this exists for was visible and still not obvious: with the phone in
 * 12-hour mode, "11:30 PM" rendered as "11:30 P" on one line and "M" on the
 * next. It broke mid-token because CLDR joins the time to the day period with
 * U+202F, a NARROW NO-BREAK space - so the string has no legal break point, and
 * a text one hair too wide for its column breaks between characters instead.
 *
 * These cases are therefore about the SEPARATOR and the ORDER as much as the
 * words: that the separator never survives into either half, that a 24-hour
 * phone gets no period at all, and that ja - which leads with the period - is
 * not silently reversed. ja is not a language this app ships; it is here
 * because it is the cheapest available proof that the order is read from the
 * pattern rather than assumed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ClockTest {

    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    private fun locale(tag: String) = RuntimeEnvironment.setQualifiers(tag)

    private fun hourFormat(twentyFour: Boolean) = Settings.System.putString(
        ctx.contentResolver, Settings.System.TIME_12_24, if (twentyFour) "24" else "12"
    )

    private fun reading(h: Int, m: Int) = Clock.reading(ctx, LocalTime.of(h, m))

    // ---------- 24-hour: there is no period to split ----------

    @Test
    fun `24-hour mode yields no day period at all`() {
        locale("en"); hourFormat(true)
        val r = reading(23, 30)
        assertNull("24-hour time has no day period", r.period)
        assertTrue("expected a 23:xx reading, got ${r.time}", r.time.contains("23"))
    }

    // ---------- 12-hour: the split itself ----------

    @Test
    fun `English splits the numerals from PM`() {
        locale("en"); hourFormat(false)
        val r = reading(23, 30)
        assertEquals("11:30", r.time)
        assertEquals("PM", r.period)
        assertFalse("English trails the period", r.periodFirst)
    }

    @Test
    fun `midnight is 12 AM, the case a single-digit hour hides`() {
        locale("en"); hourFormat(false)
        val r = reading(0, 30)
        assertEquals("12:30", r.time)
        assertEquals("AM", r.period)
    }

    /**
     * Ukrainian is a 24-hour locale, so this is only reachable when the user has
     * set the PHONE to 12-hour. CLDR still has a day period for it, and it is
     * Cyrillic - unlike Russian, which falls back to Latin AM/PM.
     */
    @Test
    fun `Ukrainian uses its own Cyrillic day period`() {
        locale("uk"); hourFormat(false)
        val evening = reading(23, 30)
        val morning = reading(8, 30)
        assertEquals("11:30", evening.time)
        assertNotNull(evening.period)
        assertTrue(
            "expected a Cyrillic day period, got ${evening.period}",
            evening.period!!.any { it in 'Ѐ'..'ӿ' }
        )
        assertTrue(
            "morning and evening must differ: ${morning.period} vs ${evening.period}",
            morning.period != evening.period
        )
    }

    @Test
    fun `Russian splits cleanly even though its period is Latin`() {
        locale("ru"); hourFormat(false)
        val r = reading(23, 30)
        assertEquals("11:30", r.time)
        assertEquals("PM", r.period)
    }

    // ---------- the separator, which is the actual bug ----------

    @Test
    fun `no no-break space survives into either half, in any language`() {
        hourFormat(false)
        for (tag in listOf("en", "ru", "uk")) {
            locale(tag)
            val r = reading(23, 30)
            val halves = listOf(r.time, r.period.orEmpty())
            for (half in halves) {
                assertFalse(
                    "$tag left a U+202F in \"$half\" - that is the character that " +
                        "made the line break mid-token",
                    half.contains(' ')
                )
                assertEquals(
                    "$tag left an untrimmed edge in \"$half\"",
                    half.trim { it.isWhitespace() || Character.isSpaceChar(it) }, half
                )
            }
        }
    }

    // ---------- order is read, not assumed ----------

    @Test
    fun `a locale that leads with the day period is not reversed`() {
        locale("ja"); hourFormat(false)
        val r = reading(23, 30)
        assertNotNull("ja has a day period", r.period)
        assertTrue(
            "ja writes the period first (午後11:30); got periodFirst=${r.periodFirst}",
            r.periodFirst
        )
    }

    // ---------- the whole reading still says the same thing ----------

    @Test
    fun `the two halves together carry the same time as hhmm`() {
        hourFormat(false)
        for (tag in listOf("en", "ru", "uk")) {
            locale(tag)
            val t = LocalTime.of(23, 30)
            val r = Clock.reading(ctx, t)
            val whole = Clock.hhmm(ctx, t)
            assertTrue(
                "$tag: \"$whole\" should contain the numerals \"${r.time}\"",
                whole.contains(r.time)
            )
            assertTrue(
                "$tag: \"$whole\" should contain the period \"${r.period}\"",
                whole.contains(r.period!!)
            )
        }
    }
}
