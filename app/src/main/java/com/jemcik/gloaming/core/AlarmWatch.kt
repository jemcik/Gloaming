package com.jemcik.gloaming.core

import android.app.ActivityManager

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
 *
 * WHAT A MISS RECORDS, and why. The card used to say one sentence for every
 * miss - "bedtime stayed on until you opened the app" - and on 11 Sep 2026 it
 * said it about an END the Honor had delivered by itself, four minutes late,
 * with the screen on and the app closed (see [Delivery.asOf] for the grid that
 * did it). A four-minute vendor delay and a twelve-hour park are not the same
 * report, so a miss now records when bedtime actually ended and whether the
 * app was on screen as the END arrived - which is how a parked alarm gets
 * released, and the only evidence for "until you opened the app".
 */
object AlarmWatch {

    /** One late END, as the card describes it. */
    class Miss(val due: Long, val endedAt: Long, val atOpen: Boolean)

    /** Called when an END is armed for [dueMs]. */
    fun arming(p: Prefs, dueMs: Long) { p.endDue = dueMs }

    /**
     * It arrived - and whether that counts depends on WHEN, not that it
     * happened. See [Delivery]: an END released only by the app being opened is
     * the original report, not a success, and clearing the flag here
     * unconditionally is what made that report undetectable.
     *
     * [due] is the instant THIS alarm was armed for, carried in the alarm
     * itself - not [Prefs.endDue]. When the app is opened, the resume's
     * reschedule and the parked alarm's release race, and if the reschedule
     * wins it has re-armed `endDue` to tomorrow before this runs. Judged
     * against tomorrow, a twelve-hour-late END scored as punctual, un-latched
     * the miss that [check] had just recorded, and marked tomorrow's END seen
     * before it existed.
     */
    fun handled(p: Prefs, due: Long, now: Long = System.currentTimeMillis(), atOpen: Boolean = appOpen()) {
        p.endSeen = due
        val late = Delivery.late(due, now)
        p.alarmMissed = late
        if (late) record(p, due, now, atOpen) else p.missedDue = Prefs.NO_DUE
    }

    /**
     * Look for a miss, and latch it. Called before anything re-arms, because
     * arming overwrites the due instant we would be judging.
     *
     * An END found overdue here never arrived; bedtime ends NOW, by the
     * reschedule this call precedes. [atOpen] says whether "now" is the app on
     * screen - the resume that just looked - or a receiver running with nobody
     * watching, which is the next night's START finding last night's END lost.
     */
    fun check(p: Prefs, now: Long = System.currentTimeMillis(), atOpen: Boolean = appOpen()) {
        if (overdue(p, now)) {
            p.alarmMissed = true
            record(p, p.endDue, now, atOpen)
        }
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
        p.missedDue = Prefs.NO_DUE
    }

    /**
     * True when an armed END came and went without ever reaching us.
     *
     * Self-clearing, like BootWatch: the next END that arrives on time marks its
     * own due instant seen, and this goes quiet by itself. That is the only
     * confirmation available that whatever the user changed actually worked.
     */
    fun missed(p: Prefs): Boolean = p.alarmMissed

    /** The miss as recorded, or null for a latch with no record behind it. */
    fun report(p: Prefs): Miss? =
        if (p.missedDue == Prefs.NO_DUE) null
        else Miss(p.missedDue, p.missedEndedAt, p.missedAtOpen)

    /**
     * "Got it": THIS miss has been read, so the card can go. The fact stays -
     * [missed] still answers, Diagnostics still says so - and the next late END
     * is a new miss with a new due instant, which this does not cover.
     *
     * It exists because the card had no exit at all. It cleared itself only on
     * the next punctual END, which on a weekend-only schedule is a week away
     * and, where the phone keeps delivering on its own grid, never; and a card
     * that cannot be cleared by fixing anything teaches the user to ignore the
     * ones that matter.
     */
    fun acknowledge(p: Prefs) { p.missAckedFor = p.missedDue }

    fun acknowledged(p: Prefs): Boolean = p.missAckedFor == p.missedDue

    /**
     * "Allow" has been pressed for this miss - the user has been to the vendor
     * screen. Not that anything was changed there: the switch is unreadable.
     * The card stops accusing and says when it will know instead, which is the
     * second face the launch tip already has and this card did not.
     */
    fun visit(p: Prefs) { p.missVisitedFor = p.missedDue }

    fun visited(p: Prefs): Boolean = p.missVisitedFor == p.missedDue

    /**
     * Is the app on screen right now? Asked as an END arrives and as a miss is
     * found, because a parked alarm is released by exactly this - the uid going
     * foreground - so it is the evidence for "until you opened the app". A
     * receiver started for the alarm alone reports itself as a service; an
     * activity on top, or one just behind a system dialog, as foreground or
     * visible. No Context needed: it asks about this process.
     */
    fun appOpen(): Boolean = runCatching {
        val info = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(info)
        info.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE
    }.getOrDefault(false)

    private fun record(p: Prefs, due: Long, endedAt: Long, atOpen: Boolean) {
        p.missedDue = due
        p.missedEndedAt = endedAt
        p.missedAtOpen = atOpen
    }

    /** The raw comparison, only meaningful before the next arming lands. */
    private fun overdue(p: Prefs, now: Long): Boolean =
        Delivery.missed(p.endDue, p.endSeen, now)
}
