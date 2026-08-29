package com.jemcik.gloaming

import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.layout
import android.content.res.Resources
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.jemcik.gloaming.R
import com.jemcik.gloaming.core.*
import com.jemcik.gloaming.ui.BedtimeDial
import com.jemcik.gloaming.ui.GloamingTheme
import com.jemcik.gloaming.ui.InterruptionsScreen
import com.jemcik.gloaming.ui.Arc
import com.jemcik.gloaming.ui.SectionLabel
import com.jemcik.gloaming.ui.SettingsScreen
import com.jemcik.gloaming.ui.SectionRule
import com.jemcik.gloaming.ui.gloam
import com.jemcik.gloaming.ui.rememberHaptics
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import android.icu.text.ListFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/* Spacing rhythm: Organic's 1.10 density scale doesn't survive dp rounding, so
   the app runs a 4dp grid and keeps the ratios. One deliberate deviation.   */
private val SIDE = 24.dp        // screen side padding
/* Proximity is the only grouping this screen has, and it was not being used:
   one gap for everything meant the day row sat as close to "when bedtime is on"
   as the times sit to the dial, which belong together. Material's own order is
   whitespace, then dividers, then cards - this is the whitespace step. */
private val GROUP = 18.dp       // between functional blocks, either side of a rule
private val TIGHT = 10.dp       // within one
/* The dial's canvas is 260dp but its ring stops at a 105dp radius, so it
   carries ~24dp of empty margin all round. Left alone that margin ADDS to
   whatever gap its neighbours ask for, and the block ends up looser inside
   than the gaps between blocks. Trimmed off the layout box only; the canvas
   still draws and still takes touches at full size. */
private val DIAL_TRIM = 18.dp
private val CARD_PAD = 16.dp
private val CHIP_GUTTER = 7.dp
private val DAY_SIZE = 40.dp
private val CORNER = 28.dp      // card / master-switch corner

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Let the sky run behind the status and gesture bars. Light icons are
        // pinned regardless of system theme - the ground is always dark enough
        // in Dusk, and Dawn's cream is light enough that we flip per theme.
        enableEdgeToEdge()
        setContent {
            // The theme mode lives ABOVE GloamingTheme, because it decides which
            // scheme the theme is built from - so it cannot live inside it.
            val prefs = remember { Prefs(this) }
            var themeMode by remember { mutableIntStateOf(prefs.themeMode) }
            val dark = when (themeMode) {
                Prefs.THEME_LIGHT -> false
                Prefs.THEME_DARK -> true
                else -> isSystemInDarkTheme()
            }
            LaunchedEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = if (dark) SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                    else SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
                    navigationBarStyle = if (dark) SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                    else SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                )
            }
            GloamingTheme(dark) {
                Root(themeMode) { themeMode = it; prefs.themeMode = it }
            }
        }
    }
}

@Composable
private fun Root(themeMode: Int, onThemeMode: (Int) -> Unit) {
    val ctx = LocalContext.current
    var screen by remember { mutableIntStateOf(HOME) }
    // Held here, not in Home: Root swaps the two screens, so Home is disposed
    // while the allowlist is up and would otherwise come back scrolled to the
    // top. Its other state is re-read from prefs on return, which is correct.
    val homeScroll = rememberScrollState()

    if (screen == ALLOWLIST) {
        BackHandler { screen = HOME }
        InterruptionsScreen(
            onBack = { screen = HOME },
            onChanged = {
                val p = Prefs(ctx)
                // Not syncRule alone: rewriting the rule clears its condition,
                // so a running window has to be re-armed, not just re-described.
                if (p.enabled) Scheduler.rescheduleAll(ctx, p)
            }
        )
    } else if (screen == SETTINGS) {
        BackHandler { screen = HOME }
        SettingsScreen(themeMode, onThemeMode) { screen = HOME }
    } else {
        Home(homeScroll, onOpenSettings = { screen = SETTINGS }) { screen = ALLOWLIST }
    }
}

private const val HOME = 0
private const val ALLOWLIST = 1
private const val SETTINGS = 2

/**
 * The only way into Settings, at the foot of the page. The offset cancels the
 * row's own padding so the gear's box lines up with the content margin, the same
 * trick BackRow uses for its arrow.
 */
@Composable
private fun SettingsRow(onClick: () -> Unit) {
    val g = gloam
    Row(
        Modifier
            .fillMaxWidth()
            .offset(x = (-8).dp)
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painterResource(R.drawable.ic_settings),
            contentDescription = null,
            tint = g.onSurfaceLow,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            stringResource(R.string.settings_title),
            style = MaterialTheme.typography.titleMedium, color = g.onSurfaceLow
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Home(
    scroll: ScrollState,
    onOpenSettings: () -> Unit,
    onOpenInterruptions: () -> Unit
) {
    val ctx = LocalContext.current
    // remember{} blocks are not composable, so they cannot call stringResource;
    // they read through Resources instead. The locale is for uppercase().
    val res = ctx.resources
    val locale = LocalLocale.current.platformLocale
    val prefs = remember { Prefs(ctx) }
    val g = gloam
    val haptics = rememberHaptics()

    var enabled by remember { mutableStateOf(prefs.enabled) }
    var start by remember { mutableStateOf(prefs.startTime) }
    var end by remember { mutableStateOf(prefs.endTime) }
    var days by remember { mutableStateOf(prefs.days) }
    var picking by remember { mutableStateOf<String?>(null) }
    var confirmEnd by remember { mutableStateOf(false) }
    // which of the centre's readings is showing; deliberately not persisted, so
    // every visit opens on the most useful one for the current state
    var centreMode by remember { mutableIntStateOf(0) }
    var tick by remember { mutableIntStateOf(0) }

    var fxDnd by remember { mutableStateOf(prefs.fxDnd) }
    var fxGray by remember { mutableStateOf(prefs.fxGrayscale) }
    var fxDim by remember { mutableStateOf(prefs.fxDimWallpaper) }
    var fxDark by remember { mutableStateOf(prefs.fxDarkTheme) }
    var fxAmbient by remember { mutableStateOf(prefs.fxHideAmbient) }
    var darkSupport by remember { mutableIntStateOf(prefs.darkThemeSupport) }
    var darkChecking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val ambientSupported = remember { AmbientCapability.isSupported(ctx) }
    val dnd = ZenController.hasDndAccess(ctx)
    val exact = Scheduler.canScheduleExact(ctx)
    val ready = dnd && exact

    fun commit() {
        prefs.enabled = enabled; prefs.startTime = start
        prefs.endTime = end; prefs.days = days
        prefs.fxDnd = fxDnd; prefs.fxGrayscale = fxGray
        prefs.fxDimWallpaper = fxDim; prefs.fxDarkTheme = fxDark
        prefs.fxHideAmbient = fxAmbient
        // rescheduleAll -> setActive -> syncRule, so syncing here as well
        // pushed the rule twice for every tap.
        Scheduler.rescheduleAll(ctx, prefs)
        tick++
    }

    fun checkDarkSupport() {
        if (darkChecking) return
        darkChecking = true
        scope.launch {
            val v = DarkCapability.probe(ctx)
            darkChecking = false
            if (v != DarkCapability.UNKNOWN) {
                prefs.darkThemeSupport = v
                prefs.darkProbeFingerprint = SystemTheme.buildId()
                darkSupport = v
            }
        }
    }

    val now = remember(tick) { LocalTime.now() }

    // The countdown, the dial's hand and the crown marker all read `now`, and
    // all three sat frozen while the screen stayed open.
    LaunchedEffect(Unit) {
        while (true) { delay(60_000); tick++ }
    }
    // "are we inside the window" is a different question from "is it armed"
    val insideWindow = remember(tick, enabled, start, end, days) {
        Scheduler.liveWindowEnd(prefs, start, end, days) != null
    }
    val runningNow = enabled && insideWindow

    // One handler for the master switch, so the row and the switch inside it
    // cannot drift apart - including the confirm dialog when it is running.
    fun setBedtime(on: Boolean) {
        haptics.toggle(on)
        if (!on && runningNow) confirmEnd = true
        else {
            enabled = on
            if (!on) {
                Scheduler.cancelAll(ctx)
                ZenController.setActive(ctx, prefs, false)
            }
            commit()
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                // The rule can be deleted or switched off from the phone's own
                // Do Not Disturb screen while we are away, and neither reaches
                // us any other way. Cheap when nothing is wrong: it rewrites
                // nothing and re-asserts nothing.
                ZenController.reconcile(ctx, prefs)
                enabled = prefs.enabled; start = prefs.startTime
                end = prefs.endTime; days = prefs.days
                fxDnd = prefs.fxDnd; fxGray = prefs.fxGrayscale
                fxDim = prefs.fxDimWallpaper; fxDark = prefs.fxDarkTheme
                fxAmbient = prefs.fxHideAmbient
                darkSupport = prefs.darkThemeSupport
                tick++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        if (prefs.darkThemeSupport != 0 &&
            prefs.darkProbeFingerprint != SystemTheme.buildId()
        ) {
            prefs.darkThemeSupport = 0; prefs.darkProbeFingerprint = null; darkSupport = 0
        }
        if (prefs.enabled) { Scheduler.rescheduleAll(ctx, prefs); tick++ }
        if (prefs.darkThemeSupport == 0 && !Scheduler.isActiveNow(prefs)) checkDarkSupport()
    }

    val ground = when {
        runningNow -> g.surfaceRunning
        !enabled -> g.surfaceOff
        else -> g.surface
    }

    Box(
        Modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(ground)
                if (runningNow) {
                    drawRect(
                        Brush.radialGradient(
                            colors = listOf(g.bloom, ground),
                            center = Offset(size.width * 0.78f, size.height * 0.06f),
                            radius = size.height * 0.62f
                        )
                    )
                }
            }
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = SIDE)
                .padding(top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(GROUP),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!ready) {
                val missing = listOf(dnd, exact).count { !it }
                Surface(
                    color = g.raise, shape = RoundedCornerShape(CORNER),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        Modifier.padding(CARD_PAD),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            pluralStringResource(R.plurals.permissions_to_go, missing, missing),
                            style = MaterialTheme.typography.titleLarge, color = g.onSurface
                        )
                        PermissionRow(
                            stringResource(R.string.perm_dnd_title),
                            stringResource(R.string.perm_dnd_why), dnd
                        ) {
                            ctx.startActivity(
                                Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                            )
                        }
                        PermissionRow(
                            stringResource(R.string.perm_alarms_title),
                            stringResource(R.string.perm_alarms_why), exact
                        ) { ctx.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)) }
                    }
                }
            }

            Surface(
                color = g.raise, shape = RoundedCornerShape(CORNER),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier
                        // the row is the switch, as everywhere else in the app
                        .toggleable(
                            value = enabled,
                            enabled = ready,
                            role = Role.Switch,
                            onValueChange = { setBedtime(it) }
                        )
                        .padding(horizontal = CARD_PAD, vertical = 15.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Three states, not two. The switch beside it already says
                    // on or off, so a lamp that repeated it earned nothing:
                    // filled green while bedtime is actually running, a hollow
                    // ring while it is armed but waiting, filled red when off.
                    // Filled-vs-hollow already means live-vs-not here - it is
                    // what the crown's marker does when the switch is off.
                    Box(
                        Modifier
                            .size(14.dp)
                            .then(
                                when {
                                    runningNow -> Modifier
                                        .clip(CircleShape).background(g.lampOn)
                                    enabled -> Modifier
                                        .border(2.dp, g.lampOn, CircleShape)
                                    else -> Modifier
                                        .clip(CircleShape).background(g.lampOff)
                                }
                            )
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f).padding(end = 12.dp)) {
                        // "Just once" belongs beside the title, not inside the
                        // status: as a prefix it pushed "starts in 22 hr 7 min"
                        // onto a second line. It shows whenever no days are
                        // chosen, on or off, because it is what an empty day
                        // row means.
                        Row {
                            Text(
                                stringResource(R.string.bedtime_mode),
                                style = MaterialTheme.typography.titleMedium,
                                color = g.onSurface,
                                modifier = Modifier.alignByBaseline()
                            )
                            if (Scheduler.isOneOff(days)) {
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    stringResource(R.string.badge_once),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = g.onSurfaceLow,
                                    modifier = Modifier.alignByBaseline()
                                )
                            }
                        }
                        val status = remember(tick, enabled, runningNow, start, end, days) {
                            if (!enabled) res.getString(R.string.bedtime_off)
                            else if (runningNow) Scheduler.liveWindowEnd(prefs, start, end, days)
                                ?.let {
                                    res.getString(R.string.state_on_until, hhmm(ctx, it.hour, it.minute))
                                }
                                ?: res.getString(R.string.state_on_now)
                            else Scheduler.nextStart(start, end, days)?.let { n ->
                                val m = Duration.between(LocalDateTime.now(), n).toMinutes()
                                // Minutes are worth having when bedtime is close
                                // and are noise when it is most of a day away -
                                // and the minute ticker would churn those digits
                                // all evening to say nothing.
                                res.getString(R.string.state_starts_in, coarse(res, m))
                            } ?: res.getString(R.string.state_nothing_scheduled)
                        }
                        Text(
                            status,
                            style = MaterialTheme.typography.bodyLarge, color = g.onSurfaceLow,
                            // One line, always. This text changes on every drag
                            // step, and a language where it wraps makes the card
                            // grow and shrink under the finger that is dragging.
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                    Switch(checked = enabled, enabled = ready, onCheckedChange = null)
                }
            }

            // One block: the two times, the crown that spans them, and the
            // dial that edits them. Nothing here is separable from the rest.
            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(TIGHT),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // No SectionLabel here on purpose: BEDTIME and WAKE UP are
                // labelSmall overlines too, so a third one above them is a
                // stutter rather than a hierarchy - and the card above ends in
                // a filled edge, which already separates this block.
                // Bedtime and Wake share a top edge: rules, labels and numerals all
                // line up. Above the dial the pair reads as one line of the plan,
                // which the old vertical step worked against.
                Column(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        Column(
                            Modifier
                                .weight(1f)
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() }
                                ) { haptics.open(); picking = "start" }
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                PhaseGlyph(moon = true, tint = g.onSurfaceMid, ground = ground)
                                Spacer(Modifier.width(7.dp))
                                Text(
                                    stringResource(
                                        if (runningNow) R.string.label_started
                                        else R.string.label_bedtime
                                    ).uppercase(locale),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = g.onSurfaceLow
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                Clock.hhmm(ctx, start),
                                style = MaterialTheme.typography.displaySmall,
                                // dimmed once it is behind you, but never near-black
                                color = if (runningNow) g.onSurfaceMid.copy(alpha = 0.62f)
                                else g.onSurfaceMid
                            )

                        }
                        Spacer(Modifier.width(18.dp))
                        Column(
                            Modifier
                                .weight(1f)
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() }
                                ) { haptics.open(); picking = "end" },
                            horizontalAlignment = Alignment.End
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    stringResource(R.string.label_wake_up).uppercase(locale),
                                    style = MaterialTheme.typography.labelSmall, color = Arc.dawn
                                )
                                Spacer(Modifier.width(7.dp))
                                PhaseGlyph(moon = false, tint = Arc.dawn, ground = ground)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                Clock.hhmm(ctx, end),
                                style = MaterialTheme.typography.displaySmall,
                                color = g.onSurfaceMid
                            )

                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    // The top of the dial, drawn again: the pair rests on the crown
                    // of the same circle it sits above. It also works - hour ticks
                    // give the window a countable length, and inside the window it
                    // splits spent from remaining with a marker at now, the same
                    // grammar the ring uses. Unarmed, the marker goes hollow rather
                    // than vanishing, so you can still see where you would be.
                    val winSecs = ((end.toSecondOfDay() - start.toSecondOfDay() + 86400) % 86400)
                    val spent = if (insideWindow && winSecs > 0) {
                        val e = ((now.toSecondOfDay() - start.toSecondOfDay() + 86400) % 86400)
                        (e.toFloat() / winSecs).coerceIn(0f, 1f)
                    } else 0f
                    Canvas(Modifier.fillMaxWidth().height(22.dp)) {
                        val cx = size.width / 2f
                        val baseY = size.height - 8.dp.toPx()   // where both ends land
                        val rise = 10.dp.toPx()                 // apex above the ends
                        val r = (cx * cx + rise * rise) / (2f * rise)
                        val cy = baseY - rise + r
                        val half = atan2(cx, r - rise) * 180f / PI.toFloat()
                        val from = 270f - half
                        val full = half * 2f
                        val brush = Brush.horizontalGradient(*Arc.stopsOn(g.dark).toTypedArray())
                        val box = Offset(cx - r, cy - r)
                        val boxSize = Size(r * 2, r * 2)
                        val stroke = Stroke(width = 1.7.dp.toPx(), cap = StrokeCap.Round)
                        val armed = if (enabled) 1f else 0.55f
                        fun on(f: Float): Offset {
                            val a = (from + full * f) * PI.toFloat() / 180f
                            return Offset(cx + r * cos(a), cy + r * sin(a))
                        }

                        drawArc(
                            brush = brush, startAngle = from, sweepAngle = full, useCenter = false,
                            topLeft = box, size = boxSize,
                            alpha = if (insideWindow) 0.30f else armed, style = stroke
                        )
                        if (insideWindow && spent < 1f) drawArc(
                            brush = brush,
                            startAngle = from + full * spent, sweepAngle = full * (1f - spent),
                            useCenter = false, topLeft = box, size = boxSize,
                            alpha = armed, style = stroke
                        )

                        // one tick per hour, every two hours once a window runs
                        // long enough that hourly marks would crowd into a smear
                        val wholeHours = winSecs / 3600
                        val tickStep = if (wholeHours > 12) 2 else 1
                        for (h in tickStep..wholeHours step tickStep) {
                            val f = (h * 3600f) / winSecs
                            if (f >= 0.99f) continue
                            val p = on(f)
                            val ux = (cx - p.x) / r
                            val uy = (cy - p.y) / r
                            drawLine(
                                color = g.onSurfaceLow.copy(alpha = 0.45f * armed),
                                start = Offset(p.x + ux * 1.2.dp.toPx(), p.y + uy * 1.2.dp.toPx()),
                                end = Offset(p.x + ux * 4.8.dp.toPx(), p.y + uy * 4.8.dp.toPx()),
                                strokeWidth = 1.4.dp.toPx(), cap = StrokeCap.Round
                            )
                        }

                        if (insideWindow) {
                            // ringed in the ground so it reads above the line, the
                            // same trick the dial plays with its handles
                            val p = on(spent)
                            drawCircle(ground, radius = 4.0.dp.toPx(), center = p)
                            if (enabled) drawCircle(g.onSurface, radius = 2.6.dp.toPx(), center = p)
                            // onSurfaceLow, not line: line is dim enough on Dusk
                            // that the one mark saying "here" became the faintest
                            // thing on screen. Ring vs disc already says unarmed.
                            else drawCircle(
                                g.onSurfaceLow, radius = 2.6.dp.toPx(), center = p,
                                style = Stroke(width = 1.4.dp.toPx())
                            )
                        }
                    }
                }

                // Everything the centre could truthfully say right now, most useful
                // first. Tapping it walks the list; states with only one true thing
                // to say are not tappable and show no dots.
                val readings = remember(tick, enabled, runningNow, start, end, days) {
                    val secs = ((end.toSecondOfDay() - start.toSecondOfDay() + 86400) % 86400)
                    val total = span(res, secs / 60L) to res.getString(R.string.dial_sleep_window)
                    buildList {
                        if (runningNow) {
                            val endsAt = Scheduler.liveWindowEnd(prefs, start, end, days)
                            val left = if (endsAt != null)
                                Duration.between(LocalDateTime.now(), endsAt).toMinutes() else 0L
                            add(
                                span(res, left) to
                                    res.getString(R.string.dial_until, hhmm(ctx, end.hour, end.minute))
                            )
                            add(total)
                        } else {
                            add(total)
                            // nextStart, not nextOccurrence: the latter returns null
                            // for a one-off, which silently dropped this reading and
                            // left the centre with nothing to cycle to.
                            if (enabled) Scheduler.nextStart(start, end, days)?.let { n ->
                                add(
                                    span(res, Duration.between(LocalDateTime.now(), n).toMinutes())
                                        to res.getString(R.string.dial_until_bedtime)
                                )
                            }
                        }
                    }
                }
                val centreIndex = centreMode.coerceIn(0, readings.size - 1)

                BedtimeDial(
                    modifier = Modifier.layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        val trim = DIAL_TRIM.roundToPx()
                        layout(placeable.width, placeable.height - trim * 2) {
                            placeable.place(0, -trim)
                        }
                    },
                    start = start, end = end, now = now,
                    running = runningNow, enabled = enabled,
                    centreValue = readings[centreIndex].first,
                    centreLabel = readings[centreIndex].second,
                    centreIndex = centreIndex, centreCount = readings.size,
                    onCentreCycle = { dir ->
                        haptics.select()
                        val n = readings.size
                        centreMode = ((centreIndex + dir) % n + n) % n
                    },
                    onStartChange = { start = it },
                    onEndChange = { end = it },
                    onDragFinished = { commit() }
                )
            }

            SectionRule()

            // Always present. Hiding it while running read as a bug, and the
            // reason it was hidden - that an edit could cut the night short -
            // is fixed at the source now. Emptying it is allowed too: it means
            // the window runs once and the app switches itself off, which the
            // "Just once" badge on the card names.
            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(TIGHT)
            ) {
                SectionLabel(stringResource(R.string.section_which_nights))
                // The days ARE the mornings now, so the presets are the plain
                // calendar sets and mean exactly what they say.
                val crossesMidnight = end.toSecondOfDay() <= start.toSecondOfDay()
                val everyNight = DayOfWeek.entries.toSet()
                val weekdays = setOf(
                    DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                    DayOfWeek.THURSDAY, DayOfWeek.FRIDAY
                )
                val weekends = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
                // SpaceBetween, so the outer chips sit flush with the content
                // margin like the day circles under them and the card above.
                // Packed left they ended 75px short of the right edge.
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalArrangement = Arrangement.spacedBy(CHIP_GUTTER)
                ) {
                    EffectChip(stringResource(R.string.preset_every_night), days == everyNight, compact = true) {
                        haptics.select(); days = everyNight; commit()
                    }
                    EffectChip(stringResource(R.string.preset_weekdays), days == weekdays, compact = true) {
                        haptics.select(); days = weekdays; commit()
                    }
                    EffectChip(stringResource(R.string.preset_weekends), days == weekends, compact = true) {
                        haptics.select(); days = weekends; commit()
                    }
                    // The one-off already has a name on the card; here it is
                    // also the only one-tap way to clear the row.
                    EffectChip(stringResource(R.string.preset_once), days.isEmpty(), compact = true) {
                        haptics.select(); days = emptySet(); commit()
                    }
                }
                DayRow(days) { d ->
                    haptics.toggle(d !in days)
                    days = if (d in days) days - d else days + d
                    commit()
                }
                // The chips are mornings; the window reaching a Saturday
                // morning starts on Friday evening. Only worth saying when the
                // two fall on different days.
                if (crossesMidnight && days.isNotEmpty()) Text(
                    stringResource(R.string.days_start_evening_before),
                    style = MaterialTheme.typography.bodySmall,
                    color = g.onSurfaceLow
                )
            }

            SectionRule()

            SectionLabel(stringResource(R.string.section_what_can_wake_you))
            // Do Not Disturb is not a screen effect, it is a subsystem, and
            // this is its configuration. As a chip in the effects row it sat
            // apart from the screen that configures it - and turning it off
            // left that screen fully live while doing nothing, because the
            // filter had become INTERRUPTION_FILTER_ALL. One card: the switch,
            // and beneath it what the switch governs.
            Surface(
                color = g.raise, shape = RoundedCornerShape(CORNER),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Row(
                        Modifier
                            .toggleable(
                                value = fxDnd,
                                role = Role.Switch,
                                onValueChange = { fxDnd = it; haptics.toggle(it); commit() }
                            )
                            .padding(horizontal = CARD_PAD, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                stringResource(R.string.dnd_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = g.onSurface
                            )
                            Text(
                                stringResource(R.string.dnd_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = g.onSurfaceLow
                            )
                        }
                        Switch(checked = fxDnd, onCheckedChange = null)
                    }
                    Box(
                        Modifier
                            .padding(horizontal = CARD_PAD)
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(g.veil)
                    )
                    val gets = remember(tick, fxDnd) {
                        Interruptions.shortSummary(res, Interruptions.allowed(res, prefs, short = true))
                    }
                    Row(
                        Modifier
                            .alpha(if (fxDnd) 1f else 0.45f)
                            .fillMaxWidth()
                            .clickable(enabled = fxDnd) { haptics.open(); onOpenInterruptions() }
                            .padding(horizontal = CARD_PAD, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f).padding(end = 12.dp)) {
                            // Matches the title of the screen it opens: a
                            // link and its destination naming themselves
                            // differently is how you doubt you arrived.
                            Text(
                                stringResource(R.string.what_gets_through),
                                style = MaterialTheme.typography.titleMedium,
                                color = g.onSurface
                            )
                            Text(
                                if (fxDnd) gets else stringResource(R.string.dnd_nothing_silenced),
                                style = MaterialTheme.typography.bodySmall,
                                color = g.onSurfaceLow
                            )
                        }
                        Text(
                            "\u203A",
                            style = MaterialTheme.typography.titleLarge, color = g.onSurfaceLow
                        )
                    }
                }
            }

            SectionRule()

            // Thirteen switch rows is what makes a screen read as Settings.
            // The effects are a pill row: tap to arm.
            Column(Modifier.fillMaxWidth()) {
                SectionLabel(stringResource(R.string.section_how_the_screen_looks))
                Spacer(Modifier.height(TIGHT))
                // One per row. Two-up only ever worked in English: measured on
                // the device, no two Russian labels fit a 311dp row - the closest
                // pair misses by 9dp - so a flow row put one chip on each line
                // anyway, and did it raggedly. Stacking them is the same result
                // stated deliberately, and it reads the same in every language.
                Column(verticalArrangement = Arrangement.spacedBy(CHIP_GUTTER)) {
                    EffectChip(stringResource(R.string.fx_grayscale), fxGray, icon = Fx.Grayscale) {
                        fxGray = !fxGray; haptics.toggle(fxGray); commit()
                    }
                    EffectChip(stringResource(R.string.fx_dim), fxDim, icon = Fx.Dim) {
                        fxDim = !fxDim; haptics.toggle(fxDim); commit()
                    }
                    if (darkSupport == 2) {
                        EffectChip(stringResource(R.string.fx_dark), false, muted = true, icon = Fx.Dark) {
                            ctx.startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS))
                        }
                    } else {
                        EffectChip(stringResource(R.string.fx_dark), fxDark, icon = Fx.Dark) {
                            fxDark = !fxDark; haptics.toggle(fxDark); commit()
                        }
                    }
                    if (ambientSupported) {
                        EffectChip(stringResource(R.string.fx_ambient), fxAmbient, icon = Fx.Ambient) {
                            fxAmbient = !fxAmbient; haptics.toggle(fxAmbient); commit()
                        }
                    } else {
                        EffectChip(stringResource(R.string.fx_ambient), false, muted = true, icon = Fx.Ambient) {
                            AmbientSettings.open(ctx)
                        }
                    }
                }
                val unsupported = listOfNotNull(
                    if (darkSupport == 2) stringResource(R.string.fx_dark) else null,
                    if (!ambientSupported) stringResource(R.string.fx_ambient) else null
                )
                if (unsupported.isNotEmpty()) Text(
                    pluralStringResource(
                        R.plurals.fx_handled_by_phone, unsupported.size,
                        ListFormatter.getInstance().format(unsupported)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = g.onSurfaceLow,
                    modifier = Modifier.padding(top = 10.dp)
                )
                val hint = remember { AmbientSettings.locationHint() }
                if (!ambientSupported && hint != null) Text(
                    stringResource(R.string.fx_always_on_lives_in, hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = g.onSurfaceLow,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            SectionRule()

            // Settings lives at the FOOT, not in a bar at the top. It is a place
            // you go once - to set the theme or the language - and then never
            // again, and a top bar charged 66dp of the space above the fold for
            // it on every visit. Down here it is also easier to reach one-handed
            // than the top-right corner is.
            SettingsRow(onOpenSettings)
        }
    }

    if (confirmEnd) {
        AlertDialog(
            onDismissRequest = { confirmEnd = false },
            containerColor = g.raise,
            shape = RoundedCornerShape(32.dp),
            title = {
                Text(
                    stringResource(R.string.end_bedtime_title),
                    style = MaterialTheme.typography.titleLarge, color = g.onSurface
                )
            },
            text = {
                Text(
                    stringResource(R.string.end_bedtime_body),
                    style = MaterialTheme.typography.bodyLarge, color = g.onSurfaceLow
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    haptics.confirm()
                    enabled = false
                    Scheduler.cancelAll(ctx)
                    ZenController.setActive(ctx, prefs, false)
                    commit()
                    confirmEnd = false
                }) { Text(stringResource(R.string.end_now), color = g.cta) }
            },
            dismissButton = {
                TextButton(onClick = { confirmEnd = false }) {
                    Text(stringResource(R.string.keep_going), color = g.onSurfaceLow)
                }
            }
        )
    }

    if (picking != null) {
        val init = if (picking == "start") start else end
        val state = rememberTimePickerState(init.hour, init.minute, Clock.is24Hour(ctx))
        // The picker wears the handle you are editing. The dialog has no title,
        // so colour is what tells you which of the two numbers you tapped.
        val night = picking == "start"
        val accent = if (night) Arc.nightOn(g.dark) else Arc.dawn
        // the same two inks the dial paints inside its own handles
        val onAccent = if (night) Color(0xFFEEF1F5) else Color(0xFF402310)
        AlertDialog(
            onDismissRequest = { picking = null },
            containerColor = g.raise,
            shape = RoundedCornerShape(32.dp),
            confirmButton = {
                TextButton(onClick = {
                    haptics.confirm()
                    val t = LocalTime.of(state.hour, state.minute)
                    if (picking == "start") start = t else end = t
                    picking = null; commit()
                }) { Text(stringResource(R.string.action_set), color = g.cta) }
            },
            dismissButton = {
                TextButton(onClick = { picking = null }) { Text(stringResource(R.string.action_cancel), color = g.onSurfaceLow) }
            },
            text = {
                TimePicker(state = state, colors = pickerColors(accent, onAccent))
            }
        )
    }
}

/** "3h 05m" from a whole number of minutes. */
/** Clock time. 24-hour throughout, which is what the dial is. */
private fun hhmm(ctx: Context, h: Int, m: Int): String = Clock.hhmm(ctx, h, m)

/** The dial centre's compact duration - "5h 20m". */
private fun span(res: Resources, minutes: Long): String =
    res.getString(R.string.dur_compact, minutes / 60, minutes % 60)

/**
 * A duration for the status line, coarsened: minutes are worth having when
 * bedtime is close and are noise when it is most of a day away. Hours and
 * minutes are separate plurals, because a language can inflect them apart.
 */
private fun coarse(res: Resources, m: Long): String {
    val h = (m / 60).toInt()
    val mins = (m % 60).toInt()
    val hours = res.getQuantityString(R.plurals.dur_hours, h, h)
    return when {
        m < 60 -> res.getQuantityString(R.plurals.dur_minutes, mins, mins)
        m < 240 -> res.getString(
            R.string.dur_hours_minutes, hours,
            res.getQuantityString(R.plurals.dur_minutes, mins, mins)
        )
        else -> hours
    }
}



/*
 * Google's own geometry, like the allowlist rows. The `container` parameter is
 * gone with the hand-drawn crescent, which had to punch itself with the chip's
 * own fill to stay a crescent.
 */
private enum class Fx { Grayscale, Dim, Dark, Ambient }

@Composable
private fun FxIcon(icon: Fx) {
    Icon(
        painter = painterResource(
            when (icon) {
                Fx.Grayscale -> R.drawable.ic_grayscale
                Fx.Dim -> R.drawable.ic_dim
                Fx.Dark -> R.drawable.ic_dark
                Fx.Ambient -> R.drawable.ic_ambient
            }
        ),
        contentDescription = null,
        tint = LocalContentColor.current,
        modifier = Modifier.size(16.dp)
    )
}

@Composable
private fun EffectChip(
    label: String,
    on: Boolean,
    icon: Fx? = null,
    muted: Boolean = false,
    // Four presets on one line need every dp: at the effect chips' 14dp of
    // side padding they come to 355dp against 311dp of content width. 8dp
    // fits them with the full "Every night" label intact. The effect chips
    // below have room to spare and keep the roomier padding.
    compact: Boolean = false,
    onClick: () -> Unit
) {
    val g = gloam
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (on) g.selectFill else g.raise,
        contentColor = if (on) g.onSelect else if (muted) g.onSurfaceLow else g.onSurfaceMid
    ) {
        Row(
            Modifier.padding(
                horizontal = if (compact) 8.dp else 14.dp,
                vertical = 9.dp
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                FxIcon(icon)
                Spacer(Modifier.width(7.dp))
            }
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun DayRow(selected: Set<DayOfWeek>, onToggle: (DayOfWeek) -> Unit) {
    val g = gloam
    val locale = LocalLocale.current.platformLocale
    val first = WeekFields.of(locale).firstDayOfWeek
    val ordered = (0L until 7L).map { first.plus(it) }

    // SpaceBetween with slots exactly the width of the circle, so the outer
    // circles sit flush with the content margin like everything else on the
    // screen. Equal-weight slots were 44.4dp wide and centred a 40dp circle in
    // each, which inset the row by 2.3dp and left it visibly out of line with
    // the cards above. Height still carries the touch target from 40 to 48dp;
    // the width cannot grow without either uneven gaps or that same inset.
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        ordered.forEach { d ->
            val on = d in selected
            val press = remember { MutableInteractionSource() }
            Box(
                Modifier
                    .size(width = DAY_SIZE, height = 48.dp)
                    .toggleable(
                        value = on,
                        interactionSource = press,
                        // Unbounded, so the ripple is a circle around the chip
                        // rather than a grey rectangle the shape of the slot.
                        indication = ripple(bounded = false, radius = DAY_SIZE / 2),
                        role = Role.Checkbox,
                        onValueChange = { onToggle(d) }
                    )
                    .semantics {
                        contentDescription = d.getDisplayName(TextStyle.FULL, locale)
                    },
                contentAlignment = Alignment.Center
            ) {
                // Selected is filled, unselected is a hollow ring. The fills
                // sit at 1.3:1 against each other, so shape carries the state
                // as well as colour - the same filled-vs-hollow the lamp and
                // the crown marker use.
                Box(
                    Modifier
                        .size(DAY_SIZE)
                        .clip(CircleShape)
                        .then(
                            if (on) Modifier.background(g.selectFill)
                            else Modifier.border(1.5.dp, g.line, CircleShape)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        // NARROW repeats itself - M, T, W, T, F, S, S. Two
                        // letters off SHORT are unambiguous in every locale
                        // and fit the same circle.
                        d.getDisplayName(TextStyle.SHORT, locale)
                            .filter { it.isLetter() }.take(2),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (on) g.onSelect else g.onSurfaceLow,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.clearAndSetSemantics { }
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionRow(title: String, why: String, granted: Boolean, onRequest: () -> Unit) {
    val g = gloam
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = g.onSurface)
            Text(why, style = MaterialTheme.typography.bodySmall, color = g.onSurfaceLow)
        }
        if (granted) {
            Text(
                stringResource(R.string.perm_allowed),
                style = MaterialTheme.typography.labelLarge, color = g.stateOn
            )
        } else {
            Button(
                onClick = onRequest,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = g.cta, contentColor = g.surface
                )
            ) { Text(stringResource(R.string.perm_allow), style = MaterialTheme.typography.labelLarge) }
        }
    }
}

/* The handles' own marks, shrunk. Colour alone made the wake side learnable;
   the moon and the sun are already on the ring, and they survive a dimmed
   screen and a colourblind reader in a way an orange label does not.
   The crescent is punched with the ground colour, so it has to be passed in -
   the ground changes between off, scheduled and running.                     */
@Composable
private fun PhaseGlyph(moon: Boolean, tint: Color, ground: Color) {
    Canvas(Modifier.size(13.dp)) {
        val r = size.minDimension / 2f
        val c = Offset(r, r)
        if (moon) {
            drawCircle(tint, radius = r, center = c)
            drawCircle(ground, radius = r * 0.84f, center = Offset(c.x + r * 0.50f, c.y - r * 0.38f))
        } else {
            drawCircle(tint, radius = r * 0.44f, center = c)
            for (i in 0 until 8) {
                val rad = (i * 45f) * PI.toFloat() / 180f
                drawLine(
                    tint,
                    start = Offset(c.x + r * 0.66f * cos(rad), c.y + r * 0.66f * sin(rad)),
                    end = Offset(c.x + r * 0.97f * cos(rad), c.y + r * 0.97f * sin(rad)),
                    strokeWidth = r * 0.20f, cap = StrokeCap.Round
                )
            }
        }
    }
}

/* Every role the picker reads, named. Leave one out and Material's baseline
   palette supplies it - which is where the violet came from.                 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun pickerColors(accent: Color, onAccent: Color) = TimePickerDefaults.colors(
    clockDialColor = gloam.veil,
    clockDialSelectedContentColor = onAccent,
    clockDialUnselectedContentColor = gloam.onSurface,
    selectorColor = accent,
    containerColor = gloam.raise,
    timeSelectorSelectedContainerColor = accent,
    timeSelectorSelectedContentColor = onAccent,
    timeSelectorUnselectedContainerColor = gloam.veil,
    timeSelectorUnselectedContentColor = gloam.onSurface
)
