package com.jemcik.gloaming.core

/**
 * Whether the phone ate an alarm we were counting on.
 *
 * [BackgroundLimit] reads the one restriction that is readable, and it is worth
 * having because it names the exact switch to fix. It is not enough. Measured on
 * an Honor BKQ-N49 on 1 Sep 2026: with the app merely FROZEN and the appop left
 * at `allow`, an END due at 12:50:00 never arrived at all - not late, lost - and
 * `isBackgroundRestricted` reported false throughout. Zen stayed on until the
 * app was opened at 12:52:47, and the only reason it went off then was
 * `reconcile` noticing the window had already ended.
 *
 * So the restriction is one cause among several and the app cannot enumerate
 * them: a vendor freezer, a process killer, a future power feature nobody has
 * shipped yet. What it CAN do is notice its own missed work, which is the same
 * move [BootWatch] makes for a boot that never reached us. Arming an END writes
 * the instant it is due; the receiver marks that instant handled; a due time
 * that passed without ever being handled is proof the alarm was eaten, whatever
 * ate it.
 *
 * The tolerance is deliberately generous. A punctual alarm was measured at the
 * scheduled SECOND in forced light and deep idle, and the real failure ran to
 * twelve minutes, so there is a wide gap to sit in and no reason to crowd it.
 */
object AlarmWatch {

    /** Two minutes: far past any scheduling jitter, far short of a real failure. */

    /** Called when an END is armed for [dueMs]. */
    fun arming(p: Prefs, dueMs: Long) { p.endDue = dueMs }

    /** Called when that END actually reaches us: proof the path works again. */
    /**
     * It arrived - and whether that counts depends on WHEN, not that it
     * happened. See [Delivery]: an END released only by the app being opened is
     * the original report, not a success, and clearing the flag here
     * unconditionally is what made that report undetectable.
     */
    fun handled(p: Prefs, now: Long = System.currentTimeMillis()) {
        p.endSeen = p.endDue
        p.alarmMissed = Delivery.late(p.endDue, now)
    }

    /**
     * Look for a miss, and latch it. Called before anything re-arms, because
     * arming overwrites the due instant we would be judging.
     */
    fun check(p: Prefs, now: Long = System.currentTimeMillis()) {
        if (overdue(p, now)) p.alarmMissed = true
    }

    /** Nothing is armed, so nothing can be owed. */
    /**
     * Nothing is scheduled, so nothing can be late - and the verdict goes with
     * it, deliberately.
     *
     * Only the two branches that mean "there is no END pending" call this:
     * bedtime switched off, and a schedule with nothing to run. Leaving the
     * latch set there stranded the notice for good, because the only thing that
     * clears it is a punctual END and none was ever coming. The user would have
     * been told bedtime did not end on time, for ever, with bedtime off.
     */
    fun clear(p: Prefs) {
        p.endDue = Prefs.NO_DUE
        p.endSeen = Prefs.NO_DUE
        p.alarmMissed = false
    }

    /**
     * True when an armed END came and went without ever reaching us.
     *
     * Self-clearing, like BootWatch: the next END that arrives on time marks its
     * own due instant seen, and this goes quiet by itself. That is the only
     * confirmation available that whatever the user changed actually worked.
     */
    fun missed(p: Prefs): Boolean = p.alarmMissed

    /** The raw comparison, only meaningful before the next arming lands. */
    private fun overdue(p: Prefs, now: Long): Boolean =
        Delivery.missed(p.endDue, p.endSeen, now)
}
