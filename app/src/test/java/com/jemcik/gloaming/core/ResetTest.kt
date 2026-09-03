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
 * Starting over, and the order that makes it safe.
 *
 * Nothing here checks that a value came back as its default - SharedPreferences
 * can be trusted to forget. What is worth pinning is everything the wipe would
 * strand if it happened first: the rule the store is the only handle on, and the
 * fresh-install branch of the migrations.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ResetTest {

    private fun ctx(): Context = ApplicationProvider.getApplicationContext()

    private fun allowed(): NotificationManager {
        val nm = ctx().getSystemService(NotificationManager::class.java)
        shadowOf(nm).setNotificationPolicyAccessGranted(true)
        return nm
    }

    @Test
    fun `no rule survives, not even one we had lost the handle for`() {
        // The exact wreckage `pm clear` leaves: prefs gone, rule still live and
        // still applying its effects, and no id left to remove it by. Measured
        // on the Honor. Nulling the id here is that state, arranged.
        val nm = allowed()
        val p = Prefs(ctx())
        ZenController.syncRule(ctx(), p)
        assertTrue("nothing was created to remove", nm.automaticZenRules.isNotEmpty())
        p.ruleId = null

        Reset.toDefaults(ctx())

        assertTrue(
            "a rule outlived the reset: " + nm.automaticZenRules,
            nm.automaticZenRules.isEmpty()
        )
    }

    @Test
    fun `a reset install is a FRESH one, not an upgraded one`() {
        // Prefs' own migration writes the OLD grayscale defaults back for anyone
        // who was already here, and decides which of us that is by whether the
        // store is empty. Clearing everything is what puts a reset on the right
        // side of that line - leave one key behind and the next launch reads as
        // an upgrade and turns the screen grey on a night nobody asked for.
        val p = Prefs(ctx())
        p.fxGrayscale = true
        p.enabled = true

        Reset.toDefaults(ctx())

        val after = Prefs(ctx())
        assertFalse("grayscale came back through the upgrade branch", after.fxGrayscale)
        assertFalse("bedtime was left switched on", after.enabled)
    }

    @Test
    fun `a refusal the app can never re-derive survives the reset`() {
        // Reported: the launch tip came back after a reset, on a phone where
        // those two vendor switches were already set. The app cannot check them
        // - that is why the card is an offer rather than a notice - and they are
        // not the app's to reset either; they survive it untouched. So the flag
        // describes the PHONE, and clearing it re-offers advice for something
        // still configured, permanently unable to discover otherwise.
        val p = Prefs(ctx())
        p.launchTipSeen = true

        Reset.toDefaults(ctx())

        assertTrue("the reset re-offered a refused tip", Prefs(ctx()).launchTipSeen)
    }

    @Test
    fun `but a reset never answers the offer on the user's behalf`() {
        // The other direction, which matters just as much: false must stay
        // false, or a reset would silently dismiss a card that was never seen.
        val p = Prefs(ctx())
        p.launchTipSeen = false

        Reset.toDefaults(ctx())

        assertFalse("the reset answered an offer nobody had seen", Prefs(ctx()).launchTipSeen)
    }

    @Test
    fun `the journal survives`() {
        // Someone resetting is usually resetting because something went wrong.
        // The log is the only record of what, so the reset must not be the thing
        // that destroys the evidence.
        Journal.write(ctx(), "something went wrong here")
        Reset.toDefaults(ctx())
        assertTrue(
            "the journal was wiped with the store",
            Journal.read(ctx()).any { it.contains("something went wrong here") }
        )
    }

    @Test
    fun `it says so afterwards`() {
        // A store that looks untouched is indistinguishable from a fresh install
        // in a diagnostics report. This line is the difference.
        Reset.toDefaults(ctx())
        assertTrue(
            "a reset left no trace to read later",
            Journal.read(ctx()).any { it.contains("reset to defaults") }
        )
    }

    @Test
    fun `it still resets on a phone that refuses us the rule`() {
        // No policy access, so every zen call throws. The store must still come
        // back clean - a reset that gives up halfway is worse than none, because
        // the person running it has already decided something is broken.
        val p = Prefs(ctx())
        p.enabled = true
        p.ruleId = "a-rule-we-may-not-touch"

        Reset.toDefaults(ctx())

        val after = Prefs(ctx())
        assertFalse(after.enabled)
        assertTrue("the stale rule id was left behind", after.ruleId == null)
    }
}
