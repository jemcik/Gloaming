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
}
