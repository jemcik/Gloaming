package com.jemcik.gloaming.ui

import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
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
 * is happening now". So the switch's own vocabulary comes with it: a tick while
 * bedtime is ARMED and waiting for its hour, the moon while a window is
 * actually RUNNING. The subtitle carries the hour either way, so tapping is
 * never a guess.
 *
 * It deliberately does NOT start a window early. Tapping when the schedule is
 * hours away arms it, exactly as the switch does; making the tile jump the
 * schedule would need a per-night start pin in the scheduling core, which is
 * the most scar-tissued code here - `activeDay` alone was got wrong twice, in
 * opposite directions. That is a separate change with its own tests, not
 * something to smuggle into a tile.
 *
 * UNAVAILABLE rather than broken when the two permissions are missing: the
 * switch in the app is disabled in that state, and a tile that accepted a tap
 * it could not honour would be worse, because there is no card behind it
 * explaining why.
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
        if (!ready()) return
        Bedtime.set(this, p, !p.enabled)
        render()
    }

    private fun ready(): Boolean =
        ZenController.hasDndAccess(this) && Scheduler.canScheduleExact(this)

    private fun render() {
        val tile = qsTile ?: return
        val p = Prefs(this)
        val running = Bedtime.runningNow(this, p)

        tile.state = when {
            !ready() -> Tile.STATE_UNAVAILABLE
            p.enabled -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
        // The switch's own two faces, and it matters that they stay the same
        // two: an hourglass while the window is only scheduled, a tick once it
        // is in effect.
        tile.icon = Icon.createWithResource(
            this,
            if (running) R.drawable.ic_check else R.drawable.ic_hourglass
        )
        tile.label = getString(R.string.bedtime_mode)
        tile.subtitle = statusLine(
            this, resources, p.enabled, p.activeDay,
            p.startTime, p.endTime, p.days,
            alarm = Scheduler.endingAlarm(this, p.exitAtAlarm),
            exitAtAlarm = p.exitAtAlarm
        )
        tile.updateTile()
    }
}
