package com.jemcik.gloaming.core

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The report a user sends when a night went wrong.
 *
 * The questions worth asking of it are not "does it contain a heading". They
 * are: does it still speak when the phone answers nothing, and does it keep the
 * system's account separate from ours - because every bug this exists for lives
 * in the gap between the two, and a report that merged them would hide exactly
 * what it was sent to show.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DiagnosticsTest {

    private fun ctx(): Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `it reports on a phone that answers nothing, rather than throwing`() {
        // The whole point is that it runs where something is already wrong.
        // Robolectric grants no notification policy access and holds no rule,
        // which is a fair imitation of a phone that has just refused us.
        val text = Diagnostics.report(ctx())
        assertTrue("report was empty", text.isNotEmpty())
        assertTrue("no journal section", text.contains("JOURNAL"))
    }

    @Test
    fun `a missing rule is named as missing, not left blank`() {
        val p = Prefs(ctx())
        p.ruleId = null
        val text = Diagnostics.report(ctx())
        // "the app holds no rule id" is a finding. An empty value would read as
        // a report that failed to look.
        assertTrue("a null ruleId was not called out: $text", text.contains("NONE"))
    }

    @Test
    fun `a rule id we hold but the system does not is reported as GONE`() {
        // The exact case reconcile exists for - deleted from the phone's own Do
        // Not Disturb screen - and the one a user cannot see or describe.
        //
        // Policy access has to be granted first, or the lookup throws and the
        // report says "unreadable" instead. That is the RIGHT answer without
        // access - we genuinely cannot tell a deleted rule from one we are not
        // allowed to see - so the two must not be conflated, and this test
        // would be asserting the wrong branch if it did not grant it.
        val nm = ctx().getSystemService(NotificationManager::class.java)
        shadowOf(nm).setNotificationPolicyAccessGranted(true)
        val p = Prefs(ctx())
        p.ruleId = "a-rule-that-was-deleted"
        val text = Diagnostics.report(ctx())
        assertTrue("a stranded rule id was not reported: $text", text.contains("GONE"))
    }

    @Test
    fun `without policy access it says unreadable rather than guessing GONE`() {
        val p = Prefs(ctx())
        p.ruleId = "a-rule-we-may-not-look-at"
        val text = Diagnostics.report(ctx())
        assertFalse("a refused lookup was reported as a deleted rule: $text",
            text.contains("GONE"))
    }

    @Test
    fun `the system's account and ours are kept apart`() {
        val text = Diagnostics.report(ctx())
        val system = text.indexOf("WHAT THE SYSTEM REPORTS")
        val ours = text.indexOf("WHAT THE APP INTENDED")
        assertTrue("the system's section is missing", system >= 0)
        assertTrue("our own section is missing", ours >= 0)
        assertTrue("the system must be read BEFORE our own belief", system < ours)
    }

    @Test
    fun `the schedule is described in the phone's own clock format`() {
        val p = Prefs(ctx())
        p.startTime = java.time.LocalTime.of(22, 30)
        p.endTime = java.time.LocalTime.of(8, 0)
        val text = Diagnostics.report(ctx())
        assertTrue(
            "the window is missing from the report: $text",
            text.contains(Clock.hhmm(ctx(), java.time.LocalTime.of(22, 30)))
        )
    }

    @Test
    fun `the allowlist is reported in words, not ZenPolicy's numbering`() {
        // The report is read by someone trying to work out why a night went
        // wrong. "calls=3" makes them hold an enum in their head to do it, and
        // an enum printed wrong is worse than one not printed at all - it is a
        // confident, legible, incorrect answer.
        val p = Prefs(ctx())
        val words = mapOf(
            Interruptions.PEOPLE_ANYONE to "anyone",
            Interruptions.PEOPLE_CONTACTS to "contacts",
            Interruptions.PEOPLE_STARRED to "starred contacts",
            Interruptions.PEOPLE_NONE to "no one"
        )
        for ((value, word) in words) {
            p.allowCalls = value
            val line = Diagnostics.report(ctx()).lines().first { it.contains("allows calls") }
            assertTrue("calls=$value printed as: $line", line.endsWith(word))
        }
        val convs = mapOf(
            Interruptions.CONV_ANYONE to "all",
            Interruptions.CONV_IMPORTANT to "important",
            Interruptions.CONV_NONE to "none"
        )
        for ((value, word) in convs) {
            p.allowConversations = value
            val line = Diagnostics.report(ctx()).lines()
                .first { it.contains("allows conversations") }
            assertTrue("convs=$value printed as: $line", line.endsWith(word))
        }
    }

    @Test
    fun `a value the enum does not cover is named, not silently mapped`() {
        // ZenPolicy could gain a sender type. Falling through to "no one" would
        // report a policy the phone is not applying; naming the number says
        // which line of the report to distrust.
        Prefs(ctx()).allowCalls = 99
        assertTrue(
            "an unknown sender type was quietly mapped to a real one",
            Diagnostics.report(ctx()).contains("unknown(99)")
        )
    }

    @Test
    fun `the overnight verdicts each have their own words`() {
        // Three checks, three distinguishable answers. They are the first thing
        // read in a report, and "no miss recorded" and "MISSED" being one line
        // apart is the whole point of printing them.
        val p = Prefs(ctx())
        p.alarmMissed = true
        p.probeFailed = true
        assertTrue("a missed END was not called out", Diagnostics.report(ctx()).contains("MISSED"))
        assertTrue(
            "a phone that holds alarms was not called out",
            Diagnostics.report(ctx()).contains("LATE - this phone holds alarms")
        )

        p.alarmMissed = false
        p.probeFailed = false
        p.probeSeen = System.currentTimeMillis()
        val clean = Diagnostics.report(ctx())
        assertTrue("a clean END read as a miss", clean.contains("no miss recorded"))
        assertTrue("a working phone read as blocked", clean.contains("alarms arrive on time"))
    }

    @Test
    fun `the filter is named rather than numbered`() {
        // currentInterruptionFilter is an int, and ALL is the one that matters:
        // it means nothing is being filtered, whatever our rule believes. That
        // reading is what catches a rule the platform has quietly overridden.
        val nm = ctx().getSystemService(NotificationManager::class.java)
        shadowOf(nm).setNotificationPolicyAccessGranted(true)
        assertTrue(
            "the interruption filter was left as a number",
            Diagnostics.report(ctx()).contains("ALL (nothing filtered)")
        )
    }

    @Test
    fun `it carries no mailbox or account, only what the app can defend`() {
        // It leaves the device, so what it may contain is a decision, not an
        // accident. The app never reads accounts and this pins that it stays so.
        val text = Diagnostics.report(ctx())
        assertFalse("an email address reached the report", text.contains("@"))
    }
}
