package com.jemcik.gloaming.core

/**
 * The one rule two different watches apply: an alarm counts as delivered only
 * if it arrives, and arrives ON TIME.
 *
 * [AlarmWatch] asks it of the real END and [BackgroundProbe] of a throwaway
 * canary, and both were written separately with their own copy of the tolerance
 * and their own idea of what "delivered" means. That drift was not cosmetic:
 * the probe learned the hard way that a parked alarm is not dropped but HELD,
 * and released the moment the app is foregrounded - so arrival alone scores a
 * blocked phone as healthy. AlarmWatch still had the older, wrong answer, which
 * meant the missed-END notice could not fire for the very report it was built
 * from: an END due at 08:55 that landed at 09:07 when the app was opened.
 *
 * Stated once here so the two cannot disagree again.
 */
internal object Delivery {

    /**
     * Generous on purpose. A doze maintenance window can slip an alarm by a
     * minute or so without anything being wrong, and a false accusation is
     * worse than a late one: it teaches the user to ignore the notice.
     */
    const val TOLERANCE_MS = 120_000L

    /** Arrived, but so late that only being opened can explain it. */
    fun late(due: Long, now: Long): Boolean =
        due != Prefs.NO_DUE && now > due + TOLERANCE_MS

    /** Came due, nothing arrived, and the tolerance has run out. */
    fun missed(due: Long, seen: Long, now: Long): Boolean =
        seen != due && late(due, now)

    /**
     * The instant to judge the schedule by when an alarm lands a little EARLY.
     *
     * The Honor delivers exact alarms on its own five-minute grid: measured
     * 11 Sep 2026, an END due 08:30:00 landed at 08:29:20. Judged at the moment
     * it landed, the night still had forty seconds to run, so the handler
     * switched zen off, the reschedule walked straight back in and switched it
     * on, and armed a second END - which the phone held to the NEXT grid
     * point, 08:34:20. A forty-second-early alarm became a visible blink and a
     * four-minute-late end.
     *
     * Inside the tolerance the alarm IS the scheduled instant, and the schedule
     * is judged there. Outside it - an alarm ten minutes early would be a
     * different fault - now is now, which is the old behaviour: the window
     * re-opens and a fresh END is armed for the real end.
     */
    fun asOf(due: Long, now: Long): Long =
        if (due != Prefs.NO_DUE && now < due && due - now <= TOLERANCE_MS) due else now
}
