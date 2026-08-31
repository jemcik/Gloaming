package com.jemcik.gloaming.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.LocalTime
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin


private const val DAY = 86400
private const val SNAP = 300
/* A full turn is 24 hours, not 12. A 12-hour face cannot draw a window longer
   than 12 hours - the arc laps itself and the wake handle ends up mid-arc - and
   the picker could set one even though the drag handler refused to. Apple's
   sleep ring is a day for the same reason. Windows are capped only by the wrap
   itself: (end - start + DAY) % DAY with 5-minute snapping tops out at 23h55m. */
private const val MAX_WINDOW = DAY

/* Canvas 260 x 260dp. All radii and strokes below are quoted in the spec's dp
   against that canvas, then scaled to whatever we are actually given.       */
private const val SPEC = 260f
private const val R_TRACK = 97f
private const val STROKE = 17.3f
private const val R_CENTRE_WELL = 88.5f
/* One boundary between the readout and the handles. The drag handler's dead
   zone and the centre's tap/swipe region are the same circle, so there is no
   band that belongs to both and none that belongs to neither. 75dp clears the
   numeral (~65dp half-width) and still leaves a 30dp annulus inside the ring
   for a loose handle grab. */
private const val R_WELL_TOUCH = 75f
private const val HANDLE = 31f
private const val HANDLE_RING = 2.6f
private const val TICK_IN = 119f
private const val TICK_IN_Q = 115f
private const val TICK_OUT = 125f

private fun LocalTime.faceDegrees(): Float =
    (toSecondOfDay() / DAY.toFloat()) * 360f

private fun norm(a: Float) = ((a % 360f) + 360f) % 360f

private fun angularDistance(a: Float, b: Float): Float {
    val d = abs(norm(a) - norm(b)) % 360f
    return if (d > 180f) 360f - d else d
}


@Composable
fun BedtimeDial(
    start: LocalTime,
    end: LocalTime,
    now: LocalTime,
    running: Boolean,
    // The track is a container like the cards, and it sits on the same page, so
    // it takes the same animated colour rather than deepening on its own clock.
    track: Color,
    enabled: Boolean,
    centreValue: String,
    centreLabel: String,
    centreIndex: Int = 0,
    centreCount: Int = 1,
    /** Direction to move through the centre's readings: +1 next, -1 previous. */
    onCentreCycle: ((Int) -> Unit)? = null,
    onStartChange: (LocalTime) -> Unit,
    onEndChange: (LocalTime) -> Unit,
    onDragFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val g = gloam
    val haptics = rememberHaptics()
    val curStart by rememberUpdatedState(start)
    val curEnd by rememberUpdatedState(end)
    // pointerInput(Unit) runs its block once and keeps it, so a lambda captured
    // there is frozen at the first composition - the callback closes over the
    // index it was built with and every tap repeats the same transition.
    val curCycle by rememberUpdatedState(onCentreCycle)
    val curCount by rememberUpdatedState(centreCount)

    // arc alpha cross-fades with state; spec: spring(.9, 380)
    val arcAlpha by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.55f,
        animationSpec = spring(dampingRatio = 0.9f, stiffness = 380f),
        label = "arcAlpha"
    )

    val windowSecs = ((end.toSecondOfDay() - start.toSecondOfDay() + DAY) % DAY)

    Box(modifier.size(260.dp), contentAlignment = Alignment.Center) {
        Canvas(
            Modifier
                .size(260.dp)
                .pointerInput(Unit) {
                    // The centre well: tap to advance, swipe left or right to
                    // move through the readings. Horizontal movement only is
                    // consumed, so a vertical drag still scrolls the screen.
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val k = min(size.width, size.height) / SPEC
                    val well = R_WELL_TOUCH * k
                    val slop = viewConfiguration.touchSlop
                    val swipe = 32.dp.toPx()

                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (hypot(down.position.x - cx, down.position.y - cy) > well)
                            return@awaitEachGesture
                        var dx = 0f
                        var dy = 0f
                        var horizontal = false
                        while (true) {
                            val change = awaitPointerEvent().changes
                                .firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            val d = change.positionChange()
                            dx += d.x; dy += d.y
                            if (!horizontal && abs(dx) > slop && abs(dx) > abs(dy)) {
                                horizontal = true
                            }
                            if (horizontal) change.consume()
                        }
                        if (curCount <= 1) return@awaitEachGesture
                        when {
                            horizontal && abs(dx) > swipe ->
                                curCycle?.invoke(if (dx < 0) 1 else -1)
                            !horizontal && abs(dx) <= slop && abs(dy) <= slop ->
                                curCycle?.invoke(1)
                        }
                    }
                }
                .pointerInput(Unit) {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val k = min(size.width, size.height) / SPEC
                    val rTrack = R_TRACK * k

                    fun angleAt(p: Offset) =
                        norm(atan2(p.y - cy, p.x - cx) * 180f / PI.toFloat() + 90f)

                    // One angle, one time. On the old 12-hour face every angle
                    // meant two times and this had to guess which, which is why
                    // a handle could not be dragged across noon in one go.
                    fun timeAt(p: Offset): LocalTime {
                        val within = ((angleAt(p) / 360f) * DAY).toInt()
                        val snapped = (((within + SNAP / 2) / SNAP) * SNAP) % DAY
                        return LocalTime.ofSecondOfDay(snapped.toLong())
                    }

                    var target = 0
                    var last: LocalTime? = null

                    detectDragGestures(
                        onDragStart = { pos ->
                            val d = hypot(pos.x - cx, pos.y - cy)
                            // grab only near the ring, never in the centre well
                            target = if (d < R_WELL_TOUCH * k) 0 else {
                                val a = angleAt(pos)
                                if (angularDistance(a, curStart.faceDegrees()) <=
                                    angularDistance(a, curEnd.faceDegrees())
                                ) 1 else 2
                            }
                            if (target != 0) haptics.grab()
                        },
                        onDrag = { change, _ ->
                            if (target == 0) return@detectDragGestures
                            val t = timeAt(change.position)
                            if (t != last) {
                                val s = if (target == 1) t else curStart
                                val e = if (target == 2) t else curEnd
                                val len = (e.toSecondOfDay() - s.toSecondOfDay() + DAY) % DAY
                                if (len in 1..MAX_WINDOW) {
                                    last = t
                                    if (t.minute == 0) haptics.hourTick() else haptics.tick()
                                    if (target == 1) onStartChange(t) else onEndChange(t)
                                }
                            }
                            change.consume()
                        },
                        onDragEnd = {
                            if (target != 0) { haptics.release(); onDragFinished() }
                            target = 0; last = null
                        },
                        onDragCancel = { target = 0; last = null }
                    )
                }
        ) {
            val k = size.minDimension / SPEC
            val centre = Offset(size.width / 2f, size.height / 2f)
            val rTrack = R_TRACK * k
            val stroke = STROKE * k

            fun at(deg: Float, r: Float): Offset {
                val rad = (deg - 90f) * PI.toFloat() / 180f
                return Offset(centre.x + r * cos(rad), centre.y + r * sin(rad))
            }

            val boxTopLeft = Offset(centre.x - rTrack, centre.y - rTrack)
            val boxSize = Size(rTrack * 2, rTrack * 2)

            // track ring - butt caps
            drawArc(
                color = track, startAngle = 0f, sweepAngle = 360f, useCenter = false,
                topLeft = boxTopLeft, size = boxSize,
                style = Stroke(width = stroke, cap = StrokeCap.Butt)
            )

            // 12 marks, now two hours apart - and the emphasised ones land on
            // midnight, 06:00, noon and 18:00 rather than nothing in particular
            for (h in 0 until 12) {
                val deg = h * 30f
                val quarter = h % 3 == 0
                drawLine(
                    color = g.veil,
                    start = at(deg, (if (quarter) TICK_IN_Q else TICK_IN) * k),
                    end = at(deg, TICK_OUT * k),
                    strokeWidth = 1.7f * k,
                    cap = StrokeCap.Round
                )
            }

            val startDeg = start.faceDegrees()
            val sweep = (windowSecs / DAY.toFloat()) * 360f

            // Sweep gradient remapped onto the arc's own angular span, so the
            // stops travel with the handles instead of sitting in screen space.
            if (sweep > 0f) {
                val frac = (sweep / 360f).coerceIn(0.0001f, 1f)
                val brush = Brush.sweepGradient(
                    colorStops = Arc.stops
                        .map { (p, c) -> (p * frac).coerceIn(0f, 1f) to c }
                        .toTypedArray(),
                    center = centre
                )
                // sweepGradient starts at 3 o'clock; rotate so 0 aligns with the
                // bedtime handle, which is (startDeg - 90) in screen terms.
                val spent = if (running)
                    ((now.toSecondOfDay() - start.toSecondOfDay() + DAY) % DAY)
                        .coerceAtMost(windowSecs) else 0
                val spentSweep = (spent / DAY.toFloat()) * 360f

                rotate(degrees = startDeg - 90f, pivot = centre) {
                    // whole window, dimmed
                    drawArc(
                        brush = brush,
                        startAngle = 0f, sweepAngle = sweep, useCenter = false,
                        topLeft = boxTopLeft, size = boxSize,
                        alpha = if (running) 0.30f else arcAlpha,
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                    // the hours still ahead of you, lit
                    if (running && sweep - spentSweep > 0.5f) {
                        drawArc(
                            brush = brush,
                            startAngle = spentSweep, sweepAngle = sweep - spentSweep,
                            useCenter = false,
                            topLeft = boxTopLeft, size = boxSize,
                            alpha = arcAlpha,
                            style = Stroke(width = stroke, cap = StrokeCap.Round)
                        )
                    }
                }
            }

            // now marker - a cursor in the lane between the track and the hour
            // ticks. It touches nothing: not the numeral, not the arc, not the
            // ticks. Earlier forms crossed all three.
            val nowDeg = now.faceDegrees()
            run {
                // 6.5dp across the base, 10.5dp long. The base is an arc chord,
                // so its width is dAng x radius - at 2.3f it came out wider than
                // the triangle was long, which has no point and reads as a
                // generic triangle rather than an arrowhead.
                val dAng = 1.6f
                val caret = Path().apply {
                    val apex = at(nowDeg, 106.5f * k)
                    val l = at(nowDeg - dAng, 117f * k)
                    val r2 = at(nowDeg + dAng, 117f * k)
                    moveTo(apex.x, apex.y); lineTo(l.x, l.y); lineTo(r2.x, r2.y); close()
                }
                // Filled, then a thin round-joined outline. The dial is drawn
                // entirely in round caps, so a hard-cornered triangle was the
                // one foreign shape on it - but the join has to stay thin or it
                // blunts the apex, which is the corner doing the pointing.
                drawPath(caret, color = g.onSurfaceMid)
                drawPath(
                    caret, color = g.onSurfaceMid,
                    style = Stroke(
                        width = 1.2f * k, cap = StrokeCap.Round, join = StrokeJoin.Round
                    )
                )
            }

            // handles - 31dp circle with a surface-coloured ring so they sit above the arc
            val hr = (HANDLE / 2f) * k
            drawHandle(at(startDeg, rTrack), hr, HANDLE_RING * k, Arc.night, g.surface, moon = true)
            drawHandle(at(end.faceDegrees(), rTrack), hr, HANDLE_RING * k, Arc.dawn, g.surface, moon = false)
        }

        // No hit area on the readout itself - the gesture lives on the canvas
        // above, as a circle, so it has no corners to push into the ring.
        val tappable = centreCount > 1 && onCentreCycle != null
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                centreValue,
                // One size in every state. The longest string this can show is
                // "11h 59m" - seven glyphs, since a window runs to twelve hours
                // - and at 44sp that left ~11dp before the centre well, a margin
                // a larger system font scale would eat.
                style = MaterialTheme.typography.displaySmall,
                color = g.onSurfaceMid,
                textAlign = TextAlign.Center
            )
            Text(
                centreLabel.uppercase(LocalLocale.current.platformLocale),
                style = MaterialTheme.typography.labelSmall,
                color = g.onSurfaceLow
            )
            // A tappable numeral with no affordance is a secret. Two dots are
            // the quietest thing that says "there is more here".
            //
            // The SPACE for them is always taken, whether they are drawn or
            // not. This column is centred in the dial, so adding 12dp of dots
            // to it moved the numeral up by 6 - measured, 1136 to 1115 - and
            // toggling the master switch made the time jump on the spot.
            // Reported as exactly that. The reading it shows may change; where
            // it sits should not.
            Spacer(Modifier.height(8.dp))
            Box(Modifier.height(4.dp), contentAlignment = Alignment.Center) {
                if (tappable) {
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        repeat(centreCount) { i ->
                            Box(
                                Modifier
                                    .size(4.dp)
                                    .clip(CircleShape)
                                    .background(if (i == centreIndex) g.onSurfaceMid else g.line)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawHandle(
    at: Offset, r: Float, ring: Float, fill: Color, ringColor: Color, moon: Boolean
) {
    drawCircle(ringColor, radius = r + ring, center = at)
    drawCircle(fill, radius = r, center = at)
    // The sun's ink is Arc.onDawn, shared with the notice strip so the two
    // cannot drift; the moon's stays local, nothing else draws it.
    val icon = if (moon) Color(0xFFEEF1F5) else Arc.onDawn
    if (moon) {
        drawCircle(icon, radius = r * 0.50f, center = at)
        drawCircle(fill, radius = r * 0.44f, center = Offset(at.x + r * 0.26f, at.y - r * 0.20f))
    } else {
        drawCircle(icon, radius = r * 0.32f, center = at)
        for (i in 0 until 8) {
            val rad = (i * 45f) * PI.toFloat() / 180f
            drawLine(
                color = icon,
                start = Offset(at.x + r * 0.50f * cos(rad), at.y + r * 0.50f * sin(rad)),
                end = Offset(at.x + r * 0.70f * cos(rad), at.y + r * 0.70f * sin(rad)),
                strokeWidth = r * 0.13f, cap = StrokeCap.Round
            )
        }
    }
}
