package com.jemcik.gloaming.core

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.time.DayOfWeek
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager

/**
 * The rule can be deleted or switched off from the phone's own Do Not Disturb
 * screen, and neither reaches us any other way than a hint to go and look.
 * Measured on the Honor: deleting leaves the app armed with no rule at all,
 * and the toggle leaves the rule enabled but inactive, so bedtime silently
 * does nothing for the rest of the night. This is what looking does.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReconcileTest {

    private fun ctx(): Context = ApplicationProvider.getApplicationContext()

    /** The granted state, and the manager to arrange the phone's side with. */
    private fun nm(): NotificationManager =
        ctx().getSystemService(NotificationManager::class.java).also {
            shadowOf(it).setNotificationPolicyAccessGranted(true)
            ShadowAlarmManager.setCanScheduleExactAlarms(true)
        }

    private fun prefs(): Prefs = Prefs(ctx()).apply {
        enabled = true
        startTime = LocalTime.of(22, 30)
        endTime = LocalTime.of(8, 0)
        days = DayOfWeek.entries.toSet()
    }

    @Test
    fun `a rule deleted from the phone's own screen is rebuilt`() {
        val nm = nm()
        val p = prefs()
        val id = ZenController.syncRule(ctx(), p)!!
        nm.removeAutomaticZenRule(id)

        ZenController.reconcile(ctx(), p)

        val fresh = p.ruleId
        assertNotNull("no rule was rebuilt", fresh)
        assertNotEquals("the old id cannot be reused - the system forgot it", id, fresh)
        assertNotNull("the rule is back on the phone", nm.getAutomaticZenRule(fresh!!))
        assertTrue(Journal.read(ctx()).any { it.contains("rule gone - rebuilding") })
    }

    @Test
    fun `a rule switched off from the phone's own screen switches bedtime off with it`() {
        // On, doing nothing, is the state this exists to avoid: the phone
        // will never activate a disabled rule, so the app's own switch says so.
        val nm = nm()
        val p = prefs()
        val id = ZenController.syncRule(ctx(), p)!!
        val rule = nm.getAutomaticZenRule(id)!!
        rule.isEnabled = false
        nm.updateAutomaticZenRule(id, rule)

        ZenController.reconcile(ctx(), p)

        assertFalse(p.enabled)
        assertTrue(Journal.read(ctx()).any { it.contains("switched off in Settings") })
    }

    @Test
    fun `with bedtime off, reconcile still sweeps a rule whose id was lost`() {
        // An orphan is just as visible on the phone's Do Not Disturb screen
        // while the app is switched off, so the sweep runs above the check.
        val nm = nm()
        val p = prefs()
        ZenController.syncRule(ctx(), p)
        p.ruleId = null
        p.enabled = false

        ZenController.reconcile(ctx(), p)

        assertTrue("the orphan survived: " + nm.automaticZenRules, nm.automaticZenRules.isEmpty())
    }

    @Test
    fun `nothing wrong, nothing rewritten`() {
        // Cheap when the phone agrees: the id and the signature stand, so the
        // next sync has nothing to push and a live rule is not blinked.
        nm()
        val p = prefs()
        val id = ZenController.syncRule(ctx(), p)!!
        val sig = p.ruleSignature

        ZenController.reconcile(ctx(), p)

        assertEquals(id, p.ruleId)
        assertEquals(sig, p.ruleSignature)
    }
}
