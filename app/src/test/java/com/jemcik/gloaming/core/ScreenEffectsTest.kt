package com.jemcik.gloaming.core

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

/**
 * Does this phone apply the rule's device effects - answered from a TRANSITION,
 * and only from one the rule could have made.
 *
 * The case that matters: on a Galaxy the dark theme is driven by a routine of
 * ours, so a theme that follows the window is what we asked Samsung's app
 * for, not the rule at work. Taken as evidence, it redrew the section with the
 * rule's switches and ended the routine that had made it, within a second.
 * Measured on the S23 on the owner's first run.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ScreenEffectsTest {

    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    private fun galaxy(): Prefs {
        ReflectionHelpers.setStaticField(Build::class.java, "MANUFACTURER", "samsung")
        return Prefs(ctx)
    }

    @Test
    fun `night mode following the rule, with no routine in play, is evidence`() {
        val p = galaxy()
        assertFalse("the prior: a Galaxy throws the effects away", ScreenEffects.applied(ctx))
        RuntimeEnvironment.setQualifiers("+notnight")
        ScreenEffects.observeApplied(ctx, wantsNight = true, ruleActive = false)
        RuntimeEnvironment.setQualifiers("+night")
        ScreenEffects.observeApplied(ctx, wantsNight = true, ruleActive = true)
        assertTrue("the edge the rule made overrules the prior", p.effectsSeen)
        assertTrue(ScreenEffects.applied(ctx))
    }

    @Test
    fun `a dark theme a routine of ours can move is not evidence`() {
        val p = galaxy()
        p.setRoutineUuid(RoutineEffect.DARK, 42)
        RuntimeEnvironment.setQualifiers("+notnight")
        ScreenEffects.observeApplied(ctx, wantsNight = true, ruleActive = false)
        RuntimeEnvironment.setQualifiers("+night")
        ScreenEffects.observeApplied(ctx, wantsNight = true, ruleActive = true)
        assertFalse("the routine did that, not the rule", p.effectsSeen)
        assertFalse(ScreenEffects.applied(ctx))
    }

    @Test
    fun `evidence recorded while a routine drove the theme is withdrawn`() {
        val p = galaxy()
        p.setRoutineUuid(RoutineEffect.DARK, 42)
        p.effectsSeen = true
        assertTrue("the false record makes the switches lie", ScreenEffects.applied(ctx))
        ScreenEffects.observeApplied(ctx, wantsNight = true, ruleActive = false)
        assertFalse(p.effectsSeen)
        assertFalse("back to the routines", ScreenEffects.applied(ctx))
    }
}
