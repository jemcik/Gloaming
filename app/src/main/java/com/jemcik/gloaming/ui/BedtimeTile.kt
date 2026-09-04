package com.jemcik.gloaming.ui

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.jemcik.gloaming.MainActivity
import com.jemcik.gloaming.R
import com.jemcik.gloaming.core.Bedtime
import com.jemcik.gloaming.core.Prefs
import com.jemcik.gloaming.core.Scheduler
import com.jemcik.gloaming.core.ZenController

/**
 * The master switch, in the notification shade.
 *
 * It is the SAME control as the one in the app bar, not a second one with its
 * own opinions: it calls [Bedtime.set] and reads [Bedtime.runningNow], which is
 * exactly what `HomeState.setBedtime` does, and it says its state with
 * [statusLine], which is exactly what the app bar says. Neither can drift.
 *
 * THREE STATES, NOT TWO, and that is the whole design. A tile that reads simply
 * "on" while the screen is still colourful and loud would be lying in the one
 * language nobody reads consciously - every other tile on the phone means "this
 * is happening now". So the switch's own vocabulary comes with it: an hourglass
 * while bedtime is ARMED and waiting for its hour, a tick once a window is
 * actually RUNNING.
 *
 * THE SUBTITLE IS ONLY SEEN AT 2x1, and this comment used to promise it
 * unconditionally - "the subtitle carries the hour either way, so tapping is
 * never a guess". Measured 4 Sep 2026 on both phones, Android 16.
 *
 * Android 16 QPR1 made tiles RESIZABLE and that is what decides it. At 1x1 the
 * tile is the ICON ALONE - no label, no subtitle, not even the app's name; the
 * platform's own answer is that tapping a small tile flashes its name in the bar
 * at the foot of the panel. At 2x1 on LineageOS 23 the whole thing appears:
 * hourglass, "Bedtime mode" and "Starts in 4h 01m" beneath it. The Honor at its
 * default size draws icon and label - «Режим сну» - and no subtitle, so MagicOS
 * omits at a labelled size what AOSP shows.
 *
 * Two consequences worth keeping. The label TRUNCATES at 2x1 - "Bedtime mod..."
 * on a 1440px panel - so the label is not a place to put anything that must be
 * read. And "the hour" was wrong even where the subtitle IS drawn: `statusLine`
 * returns a clock time only while a window is RUNNING. Armed gives a COUNTDOWN,
 * which is what the measurement above shows, and off, unscheduled and
 * permission-missing give no time at all.
 *
 * So the subtitle is worth setting and worth nothing to rely on: most people
 * never resize a tile, and the default is the size that shows none of it. The
 * three ICONS have to carry the whole message on their own, which is the
 * argument for three faces rather than two at its strongest - the
 * belt-and-braces reasoning the design was given turns out to have been the belt
 * alone for anyone who left the tile at 1x1.
 *
 * It deliberately does NOT start a window early. Tapping when the schedule is
 * hours away arms it, exactly as the switch does; making the tile jump the
 * schedule would need a per-night start pin in the scheduling core, which is
 * the most scar-tissued code here - `activeDay` alone was got wrong twice, in
 * opposite directions. That is a separate change with its own tests, not
 * something to smuggle into a tile.
 *
 * UNAVAILABLE rather than broken when the two permissions are missing - and the
 * tap OPENS THE APP rather than being swallowed. It used to return without
 * doing anything, which is the worst of both: a control that neither acts nor
 * explains, on the one surface with no room for an explanation. The app has one
 * already - the permission card and its Allow button - so the tile's job in
 * that state is to get you to it. It wears the app's own mark there too, since
 * an hourglass would claim a countdown that cannot run.
 */
class BedtimeTile : TileService() {

    companion object {
        /**
         * Ask SystemUI to let the tile speak again.
         *
         * A tile is only alive between onStartListening and onStopListening -
         * roughly, while the shade is open - so everything that changes bedtime
         * behind its back leaves it showing a stale face. That is most of the
         * interesting cases: the START alarm turning armed into running, the END
         * turning it back, a reboot re-arming. Without this the tick never
         * becomes a moon unless you happen to reopen the shade, which is exactly
         * the bug this fixes.
         */
        fun refresh(ctx: Context) {
            runCatching {
                requestListeningState(ctx, ComponentName(ctx, BedtimeTile::class.java))
            }
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        render()
    }

    override fun onClick() {
        super.onClick()
        val p = Prefs(this)
        // A tap it cannot honour OPENS THE APP rather than doing nothing. It
        // used to return here, which is the worst affordance available: a
        // control that neither acts nor explains, on the one surface with no
        // room to explain anything. The app has the answer already on screen -
        // the permission card and its Allow button - so the tile's job is to
        // get you there.
        if (!ready()) {
            startActivityAndCollapse(
                PendingIntent.getActivity(
                    this, 0,
                    Intent(this, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            return
        }
        Bedtime.set(this, p, !p.enabled)
        render()
    }

    private fun ready(): Boolean =
        ZenController.hasDndAccess(this) && Scheduler.canScheduleExact(this)

    private fun render() {
        val tile = qsTile ?: return
        val p = Prefs(this)
        val running = Bedtime.runningNow(this, p)

        // NOT Tile.STATE_UNAVAILABLE when the permissions are missing, which is
        // what this was. SystemUI does not dispatch a click to an unavailable
        // tile at all, so onClick never runs - measured: tapping it left the
        // shade open and did nothing whatever. Greying it out therefore bought
        // one signal and cost the only route to the explanation. INACTIVE is
        // also the truer word: nothing IS happening. The TAP is what carries the
        // explanation - it opens the app that can fix it. This said "the
        // subtitle carries the reason" until 4 Sep 2026: it does at 2x1 and
        // there is no subtitle at all at 1x1, which is the default, so the tap
        // has to work on its own. See the class comment.
        tile.state = when {
            p.enabled && ready() -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
        // The switch's two faces - an hourglass while the window is only
        // scheduled, a tick once it is in effect - and a THIRD the switch does
        // not need. M3 draws no thumb icon at all while a switch is off, so
        // "off" has no glyph to keep in step; a tile always shows one. Neither
        // of the two will do there: a tick off means nothing, and an hourglass
        // off announces a countdown that is not running. The app's own mark
        // says the honest thing, which is only that this is Gloaming - and it
        // is the LAUNCHER's mark, crescent and arc together, not ic_gloaming's
        // bare crescent, which on the shade sat two tiles from the system's own
        // Do Not Disturb and read as its twin.
        tile.icon = Icon.createWithResource(
            this,
            when {
                // UNAVAILABLE first. An hourglass here would claim a countdown
                // that cannot run - the same thing the app bar was doing.
                !ready() -> R.drawable.ic_gloaming_tile
                !p.enabled -> R.drawable.ic_gloaming_tile
                running -> R.drawable.ic_check
                else -> R.drawable.ic_hourglass
            }
        )
        tile.label = getString(R.string.bedtime_mode)
        tile.subtitle = statusLine(
            this, resources, p.enabled, p.activeDay,
            p.startTime, p.endTime, p.days,
            alarm = Scheduler.endingAlarm(this, p.exitAtAlarm),
            exitAtAlarm = p.exitAtAlarm,
            ready = ready()
        )
        tile.updateTile()
    }
}
