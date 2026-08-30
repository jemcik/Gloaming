package com.jemcik.gloaming.core

import android.content.Context
import android.content.res.Resources
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The sentences the two screens are built from, in all three languages.
 *
 * This is the one piece of the app where translation and code meet: the items
 * come from resources, the conjunction comes from ICU's ListFormatter, the
 * "and N more" tail is a plural, and the whole thing is then capitalised. Every
 * one of those four can be right on its own and wrong together - which is how
 * «Будильники, звонки и и еще 6» reached the screen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class InterruptionsTest {

    private val res: Resources
        get() = ApplicationProvider.getApplicationContext<Context>().resources

    private fun locale(tag: String) = RuntimeEnvironment.setQualifiers(tag)

    /** Everything on, which is the longest the list can get. */
    private fun everything(short: Boolean = false) = Interruptions.allowed(
        res,
        calls = Interruptions.PEOPLE_STARRED,
        messages = Interruptions.PEOPLE_STARRED,
        conversations = Interruptions.CONV_IMPORTANT,
        repeatCallers = true, reminders = true, events = true, media = true,
        short = short
    )

    // ---------- what is in the list ----------

    @Test
    fun `alarms lead, because they are never silenced`() {
        val list = Interruptions.allowed(
            res, Interruptions.PEOPLE_NONE, Interruptions.PEOPLE_NONE,
            Interruptions.CONV_NONE, false, false, false, false
        )
        assertEquals(listOf("alarms"), list)
    }

    @Test
    fun `calls carry their scope in the long form and drop it in the short one`() {
        val long = Interruptions.allowed(
            res, Interruptions.PEOPLE_STARRED, Interruptions.PEOPLE_NONE,
            Interruptions.CONV_NONE, false, false, false, false
        )
        assertEquals(listOf("alarms", "calls from starred contacts"), long)

        val short = Interruptions.allowed(
            res, Interruptions.PEOPLE_STARRED, Interruptions.PEOPLE_NONE,
            Interruptions.CONV_NONE, false, false, false, false, short = true
        )
        assertEquals(listOf("alarms", "calls"), short)
    }

    @Test
    fun `anyone calling needs no scope at all`() {
        val list = Interruptions.allowed(
            res, Interruptions.PEOPLE_ANYONE, Interruptions.PEOPLE_NONE,
            Interruptions.CONV_NONE, false, false, false, false
        )
        assertEquals(listOf("alarms", "calls"), list)
    }

    // ---------- how the list becomes a sentence ----------

    @Test
    fun `one thing allowed reads as only`() {
        val one = Interruptions.allowed(
            res, Interruptions.PEOPLE_NONE, Interruptions.PEOPLE_NONE,
            Interruptions.CONV_NONE, false, false, false, false
        )
        assertEquals("Alarms only", Interruptions.shortSummary(res, one))
    }

    @Test
    fun `a long list is cut short with a counted tail`() {
        val summary = Interruptions.shortSummary(res, everything(short = true))
        assertTrue(summary, summary.startsWith("Alarms, calls"))
        assertTrue(summary, summary.contains("6 more"))
    }

    @Test
    fun `the detail screen keeps three and counts the rest`() {
        val sentence = Interruptions.sentence(res, everything())
        assertTrue(sentence, sentence.endsWith("are allowed."))
        assertTrue(sentence, sentence.contains("5 more"))
        assertTrue(sentence, sentence.first().isUpperCase())
    }

    // ---------- the locales ----------

    @Test
    fun `the conjunction is the language's own, not the JVM default`() {
        // ListFormatter.getInstance() with no locale uses Locale.getDefault(),
        // which is the SYSTEM language - not the app's. With the per-app
        // language picker those differ, and the app produced
        // «Будильники, дзвінки, and ще 6».
        locale("ru")
        val ru = Interruptions.shortSummary(res, everything(short = true))
        assertFalse(ru, ru.contains(" and "))
        assertTrue(ru, ru.contains(" и "))

        locale("uk")
        val uk = Interruptions.shortSummary(res, everything(short = true))
        assertFalse(uk, uk.contains(" and "))
        assertTrue(uk, uk.contains(" і "))
    }

    @Test
    fun `Russian does not say и twice`() {
        // ListFormatter supplies the conjunction; the "and N more" plural must
        // not carry one of its own. It did, and «и и еще 6» reached the screen.
        locale("ru")
        val summary = Interruptions.shortSummary(res, everything(short = true))
        assertFalse(summary, summary.contains(" и и "))
        assertTrue(summary, summary.contains("еще"))
    }

    @Test
    fun `Ukrainian does not say і twice`() {
        locale("uk")
        val summary = Interruptions.shortSummary(res, everything(short = true))
        assertFalse(summary, summary.contains(" і і "))
        assertTrue(summary, summary.contains("ще"))
    }

    @Test
    fun `each language builds the sentence in its own words`() {
        locale("ru")
        val ru = Interruptions.sentence(res, everything())
        locale("uk")
        val uk = Interruptions.sentence(res, everything())
        locale("en")
        val en = Interruptions.sentence(res, everything())
        assertTrue(en, en.contains("are allowed"))
        assertFalse("ru should not be English: $ru", ru.contains("are allowed"))
        assertFalse("uk should not be Russian: $uk", uk == ru)
    }

    @Test
    fun `the sentence is capitalised after formatting, not before`() {
        // Capitalising the list first puts a capital mid-sentence in any language
        // that does not open with it. A translation candidate wrote exactly that.
        locale("ru")
        val sentence = Interruptions.sentence(res, everything())
        val inner = sentence.drop(1)
        assertFalse(sentence, inner.any { it.isUpperCase() })
    }

    // ---------- the labels the rows show ----------

    @Test
    fun `people labels cover every ZenPolicy value`() {
        locale("en")
        assertEquals("Anyone", Interruptions.peopleLabel(res, Interruptions.PEOPLE_ANYONE))
        assertEquals("Your contacts", Interruptions.peopleLabel(res, Interruptions.PEOPLE_CONTACTS))
        assertEquals("Starred contacts", Interruptions.peopleLabel(res, Interruptions.PEOPLE_STARRED))
        assertEquals("No one", Interruptions.peopleLabel(res, Interruptions.PEOPLE_NONE))
    }
}
