package com.jemcik.gloaming.core

/**
 * Does a background alarm actually arrive on this phone?
 *
 * Asked by trying it. One throwaway alarm is armed a few minutes out and
 * nothing is shown; if it lands on time the background path works here, and if
 * it never lands the phone is holding us. The user does nothing and is told
 * nothing unless the answer is bad.
 *
 * WHY MEASURE RATHER THAN READ. The setting cannot be read - toggling Honor's
 * two switches off and on changes nothing in any settings table, in appops, or
 * in the package dump, measured either side on 1 Sep 2026. Honor does document
 * a real API for it, `getDisallowCloseBootCompletedApps`, and it is gated behind
 * MDM_APP_MANAGEMENT: signature|privileged, applied for, under commercial
 * contract. So the state is genuinely unreadable and behaviour is all there is.
 *
 * WHY EVERY PHONE, not just the ones with a vendor launch manager. Because the
 * assumption that vendors misbehave and AOSP does not was tested on the same day
 * and failed in both directions: the reboot bug that silently lost a whole
 * window was on the OnePlus running LineageOS, and ABSENT on the Honor. A vendor
 * gate would have blinded this to the worse of the two. It costs one alarm, once,
 * and on a healthy phone nothing is ever shown.
 *
 * WHY IT IS ASKED ONCE, not forever. A pass can go stale - `adb install -r`
 * reset exactly this switch on the Honor and cost a whole window - so the probe
 * is not the ongoing guarantee and is not meant to be. [AlarmWatch] is: it
 * watches the REAL END alarm every night, which is a better instrument anyway
 * because it measures the alarm that actually matters. The probe exists only to
 * answer the question EARLY, before the first bedtime rather than after the
 * first one is lost. Repeating it nightly would spend alarms re-asking what the
 * window is already answering.
 *
 * ITS ONE BLIND SPOT: the probe cannot fail while the app is in the foreground,
 * because the process is alive and alarms reach a live process regardless. That
 * is what [DELAY_MS] is for - long enough that the user has almost certainly put
 * the phone down - and a false pass is corrected by the first real window.
 *
 * WHAT IT CANNOT ANSWER: auto-launch. That governs whether BOOT_COMPLETED
 * reaches us, so the only test is a real reboot - see [BootWatch], which catches
 * it after the fact because nothing can catch it before.
 */
object BackgroundProbe {

    /**
     * Far enough out that the app is likely backgrounded and possibly frozen by
     * the time it fires - a probe answered while the user is still looking at
     * the screen proves nothing, because the process is alive either way.
     */
    const val DELAY_MS = 11 * 60_000L

    /** Generous, for the same reason the missed-alarm tolerance is. */
    private const val TOLERANCE_MS = 120_000L

    /**
     * The verdict, latched. NOT derived live from probeDue: arming the next
     * probe overwrites the very instant that proves the last one was missed, so
     * a computed answer erases itself the moment it is acted on. That exact bug
     * was already found and fixed once in AlarmWatch; this is the same shape.
     */
    fun blocked(p: Prefs): Boolean = p.probeFailed

    /** True once any probe has ever arrived - the path is known to work here. */
    fun answered(p: Prefs): Boolean = p.probeSeen != Prefs.NO_DUE

    /** Read the outstanding probe before anything overwrites it. */
    fun check(p: Prefs, now: Long = System.currentTimeMillis()) {
        val due = p.probeDue
        if (due == Prefs.NO_DUE || p.probeSeen == due) return
        if (now > due + TOLERANCE_MS) p.probeFailed = true
    }

    /** Nothing has been asked yet, so ask. */
    fun needsArming(p: Prefs): Boolean = p.probeDue == Prefs.NO_DUE

    fun arming(p: Prefs, dueMs: Long) { p.probeDue = dueMs }

    /**
     * It arrived - which is NOT the same as arriving on time.
     *
     * A parked alarm is released the moment the app is foregrounded, so the
     * blocked case delivers too, just late and only because the user opened the
     * app. Measured on the Honor 1 Sep 2026 with RUN_ANY_IN_BACKGROUND at
     * `ignore`: the PROBE sat in "Pending user blocked background alarms" with
     * `origWhen=15:17:09`, then arrived at 15:21:14 - 244 seconds late - as the
     * app came up, and an earlier version of this function scored it a PASS.
     * That is precisely the failure this exists to catch, recorded as its
     * opposite.
     *
     * It also closes a race. Opening the app both releases the alarm and runs
     * [check], and whichever lands first used to decide the verdict: if delivery
     * won, `probeSeen == probeDue` and [check] returned early, so nothing was
     * ever latched. Judging by LATENESS rather than by arrival makes both orders
     * agree.
     */
    fun handled(p: Prefs, now: Long = System.currentTimeMillis()) {
        p.probeSeen = p.probeDue
        p.probeFailed = now > p.probeDue + TOLERANCE_MS
    }
}
