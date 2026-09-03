package com.jemcik.gloaming.core

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The caption is the schedule line the phone's OWN Do Not Disturb screen shows
 * for our rule, and it is the one thing [ZenController.syncRule] deliberately
 * refuses to push while a window is running: a rewrite blinks Do Not Disturb
 * off and on, which is far worse than a stale label for one night.
 *
 * So the question is not whether the skip happens - it should - but whether the
 * skipped change is remembered as still OWED. It was not: the signature was
 * stored as though the push had gone out, every later sync compared equal, and
 * the label stayed wrong for good. Measured on a OnePlus on 3 Sep 2026, where
 * the app read 18:45 to 08:00 beside a rule still captioned "6:20 pm - 8:00 am".
 *
 * The live branch cannot be driven from here - Robolectric's shadow implements
 * no `getAutomaticZenRuleState`, so the rule never reads STATE_TRUE - which is
 * why the decision is a function of its own, the same way `looksStuck` is.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RuleCaptionTest {

    private fun ctx(): Context = ApplicationProvider.getApplicationContext()

    private fun allowed(): NotificationManager {
        val nm = ctx().getSystemService(NotificationManager::class.java)
        shadowOf(nm).setNotificationPolicyAccessGranted(true)
        return nm
    }

    private val live = "live-part"
    private val was = live + "‖" + "22:30-08:00"
    private val sig = live + "‖" + "18:45-08:00"

    @Test
    fun `a caption we chose not to push is still owed`() {
        // The bug, in one line. Storing `sig` here claims the new times went
        // out; nothing had. The next sync then finds nothing to do, and neither
        // does the one after it.
        assertEquals(was, ZenController.carriedSignature(pushed = false, was = was, sig = sig))
    }

    @Test
    fun `a caption we did push is what the rule carries`() {
        assertEquals(sig, ZenController.carriedSignature(pushed = true, was = was, sig = sig))
    }

    @Test
    fun `a rule built from scratch owes nothing`() {
        // No previous signature means nothing was skipped: boot and upgrade
        // clear it so the rule is compared against itself and repaired. The
        // rule carries exactly what it was just built from.
        assertEquals(sig, ZenController.carriedSignature(pushed = false, was = null, sig = sig))
    }

    @Test
    fun `the debt is paid by the first sync where the rule is not live`() {
        // End to end, through the branch Robolectric CAN reach: not live, so the
        // whole signature is compared and a changed caption is pushed. This is
        // the sync that was never reaching the rule while the stored signature
        // claimed to be current.
        val nm = allowed()
        val p = Prefs(ctx())
        p.startTime = LocalTime.of(22, 30)
        p.endTime = LocalTime.of(8, 0)
        val id = ZenController.syncRule(ctx(), p)!!
        val before = nm.getAutomaticZenRule(id)!!.triggerDescription

        p.startTime = LocalTime.of(18, 45)
        ZenController.syncRule(ctx(), p)

        val after = nm.getAutomaticZenRule(id)!!.triggerDescription
        assertNotEquals("the caption never followed the window", before, after)
        assertEquals(
            Clock.hhmm(ctx(), LocalTime.of(18, 45)) + " – " +
                Clock.hhmm(ctx(), LocalTime.of(8, 0)),
            after
        )
    }
}
