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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.runtime.*
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import com.jemcik.gloaming.ui.GroupedList
import com.jemcik.gloaming.ui.IconTint
import com.jemcik.gloaming.ui.RowAvatar
import com.jemcik.gloaming.ui.InterruptionsScreen
import com.jemcik.gloaming.ui.Arc
import com.jemcik.gloaming.ui.CARD_PAD
import com.jemcik.gloaming.ui.CORNER
import com.jemcik.gloaming.ui.GROUP
import com.jemcik.gloaming.ui.Section
import com.jemcik.gloaming.ui.TIGHT
import com.jemcik.gloaming.ui.SectionLabel
import com.jemcik.gloaming.ui.SettingsScreen
import com.jemcik.gloaming.ui.SectionRule
import com.jemcik.gloaming.ui.gloam
import android.app.NotificationManager
import androidx.annotation.DrawableRes
import com.jemcik.gloaming.ui.GloamSwitch
import com.jemcik.gloaming.ui.ActionRow
import com.jemcik.gloaming.ui.LinkRow
import com.jemcik.gloaming.ui.SwitchRow
import com.jemcik.gloaming.ui.rememberHaptics
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.Locale
import java.time.temporal.ChronoUnit
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
/* The dial's canvas is 260dp but its ring stops at a 105dp radius, so it
   carries ~24dp of empty margin all round. Left alone that margin ADDS to
   whatever gap its neighbours ask for, and the block ends up looser inside
   than the gaps between blocks. Trimmed off the layout box only; the canvas
   still draws and still takes touches at full size. */
private val DIAL_TRIM = 18.dp
private val CHIP_HEIGHT = 38.dp   // the effect chips' own height, kept from the Surface they were
/* A second. The page changing state is not a control responding to a tap - it
   is the room the app is describing, and it was reported as a blink at 0ms and
   still read as quick at 450. */
private const val GROUND_FADE = 1000
private val CHIP_GUTTER = 7.dp
private val DAY_SIZE = 40.dp
// Where a day chip's corners land while held. Round is DAY_SIZE / 2 = 20dp.
private val DAY_PRESSED_CORNER = 12.dp

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
    val loc = LocalLocale.current.platformLocale
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
    val scope = rememberCoroutineScope()

    // Three states, not two. Either the zen effect works, or we can write the
    // vendor's own always-on keys, or nothing the app can reach moves that
    // display at all - and then the row is not drawn, because a control that
    // neither toggles anything nor opens the right screen is only noise. Keyed
    // on tick so an adb grant is picked up on the next resume.
    // Not keyed on tick: this reads prefs and, on first run, writes them.
    // Re-answering it every minute would be pointless and impure both.
    var missedBoot by remember { mutableStateOf(BootWatch.missed(prefs)) }
    val ambientZen = remember(tick) { AmbientCapability.isSupported(ctx) }
    val ambientRow = remember(tick) { ambientZen || AmbientControl.canControl(ctx) }
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
                // Re-asked here so the notice clears itself the moment a boot is
                // handled properly - which is the only confirmation available,
                // since the vendor's own setting cannot be read.
                missedBoot = BootWatch.missed(prefs)
                tick++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        if (prefs.enabled) { Scheduler.rescheduleAll(ctx, prefs); tick++ }
    }

    // Animated, not swapped. The page has three grounds and Dawn DEEPENS while
    // bedtime runs rather than lifting, which is the right instinct - it makes
    // the running state felt rather than read. But it was painted straight from
    // the state, so dragging the dial across the "now" boundary repainted the
    // whole screen and grew a bloom in ONE frame, mid-drag. A state change that
    // takes 0ms does not read as a state change, it reads as a glitch, and it
    // was reported as exactly that: "the background changes, what is that?".
    //
    // 450ms, which is slow for a control and about right for a room. It was
    // also the only transition on this screen that was not animated - the day
    // pickers collapse, the switch thumb grows, the bar reacts to scroll.
    val groundTarget = when {
        runningNow -> g.surfaceRunning
        !enabled -> g.surfaceOff
        else -> g.surface
    }
    val ground by animateColorAsState(
        groundTarget,
        animationSpec = tween(durationMillis = GROUND_FADE),
        label = "ground"
    )
    // The cards travel with the page. In Dawn the running ground deepens
    // TOWARDS them, so a page that moved alone would have swallowed them.
    val card by animateColorAsState(
        if (runningNow) g.raiseRunning else g.raise,
        animationSpec = tween(durationMillis = GROUND_FADE),
        label = "card"
    )
    // The bloom appears only while running, and it has to fade with the ground
    // or the highlight would still arrive in a single frame on top of a colour
    // that took 450ms. Its alpha carries that.
    val bloomAlpha by animateFloatAsState(
        if (runningNow) 1f else 0f,
        animationSpec = tween(durationMillis = GROUND_FADE),
        label = "bloom"
    )

    Box(
        Modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(ground)
                if (bloomAlpha > 0f) {
                    drawRect(
                        Brush.radialGradient(
                            colors = listOf(g.bloom, ground),
                            center = Offset(size.width * 0.78f, size.height * 0.06f),
                            radius = size.height * 0.62f
                        ),
                        alpha = bloomAlpha
                    )
                }
            }
    ) {
        // The master control lives in the bar, not in a card in the flow. It
        // governs everything on the screen and the screen is 1.9 viewports tall
        // - measured - so for about half the scroll range the one control that
        // turns it all off was somewhere above.
        //
        // PINNED, and two lines, after trying MediumTopAppBar with
        // exitUntilCollapsed. That version collapsed to 64dp when scrolled,
        // which sounded like the thrifty choice and was not: a medium bar puts
        // its actions on the top row and its title on the bottom one, so at
        // rest it stood 136dp tall with a void above the words - 60dp MORE than
        // this, in the state the screen is in when you open it. It bought that
        // back only while scrolled, 12dp, and paid for it by dropping the
        // status line at the halfway point of the collapse. Both were reported,
        // and they were the same mistake seen from two sides.
        //
        // So: one row of content, always the same, always there. 76dp rather
        // than the small bar's 64 because the title is two lines - the name and
        // the state - which is the subtitle-shaped hole MediumFlexibleTopAppBar
        // would fill if it were public in material3 1.4.0.
        val bar = TopAppBarDefaults.pinnedScrollBehavior()
        val status = remember(tick, enabled, runningNow, start, end, days) {
            if (!enabled) res.getString(R.string.bedtime_off)
            else if (runningNow) Scheduler.liveWindowEnd(prefs, start, end, days)
                ?.let {
                    res.getString(R.string.state_on_until, hhmm(ctx, it.hour, it.minute))
                }
                ?: res.getString(R.string.state_on_now)
            else Scheduler.nextStart(start, end, days)?.let { n ->
                val m = Duration.between(LocalDateTime.now(), n).toMinutes()
                // The SAME formatter the dial centre uses, on the
                // same quantity: its "until bedtime" reading is
                // this duration too. They were "12h 47m" there and
                // "12 hr" here, which reads as two numbers rather
                // than one fact. Minutes were dropped past four
                // hours on the argument that they are noise most of
                // a day out - but the circle was showing them the
                // whole time, so the argument only ever cost the
                // agreement between the two.
                res.getString(R.string.state_starts_in, span(res, m))
            } ?: res.getString(R.string.state_nothing_scheduled)
        }
        Scaffold(
            containerColor = Color.Transparent,
            modifier = Modifier.nestedScroll(bar.nestedScrollConnection),
            topBar = {
                TopAppBar(
                    scrollBehavior = bar,
                    // 76dp rather than the small bar's 64, because the title is
                    // two lines: the name and the state.
                    expandedHeight = 76.dp,
                    // The same colour whether scrolled or not, and that colour
                    // is the page's own ground. M3's default is transparent at
                    // rest and `raise` once anything has scrolled under it, which
                    // is a step function: the band went #F2E9D9 to #E0D5BF - 21
                    // units, full width - the instant a drag began, and read as a
                    // blink. Reported as exactly that.
                    //
                    // The bar has to be OPAQUE, or content slides visibly under
                    // the title. It does not have to CHANGE - and the two are
                    // separate requirements that M3's default conflates.
                    //
                    // `raise`, the same fill every card on the screen uses, so
                    // the bar reads as one of the app's containers rather than
                    // as a piece of page that happens to be stuck. It also puts
                    // the status line on the ground its ink was measured
                    // against: onSurfaceLow was chosen at 6.52:1 on raise in
                    // Dawn and 6.38:1 in Dusk, and both are theme tokens, so
                    // this follows the theme without a second decision.
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = card,
                        scrolledContainerColor = card,
                        titleContentColor = g.onSurface
                    ),
                    title = {
                        // No lamp here any more. It was the THIRD telling of one
                        // fact: the status line below names all three states in
                        // words, the switch beside it shows on-versus-off, and
                        // the dot then said it again in colour alone. What it
                        // uniquely carried - armed versus running - moved into
                        // the switch's own thumb, which is where a person looks
                        // for this control's state anyway.
                        //
                        // Its OFF state was also the loudest thing on the app's
                        // calmest surface: lampOff was a red, and a red on a
                        // surface reads as a fault. Being switched off is a
                        // choice. lampOn/lampOff are deleted with it.
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.bedtime_mode))
                                if (Scheduler.isOneOff(days)) {
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        stringResource(R.string.badge_once),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = g.onSurfaceLow
                                    )
                                }
                            }
                            Text(
                                status,
                                style = MaterialTheme.typography.bodyLarge,
                                color = g.onSurfaceLow,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    actions = {
                        // No row to carry the gesture up here, so the switch is
                        // its own target for the first time in this app.
                        GloamSwitch(
                            checked = enabled, enabled = ready,
                            onCheckedChange = { setBedtime(it) },
                            // The one switch in the app that has three things
                            // to say. Checked is armed; checked with the moon
                            // is a window actually running.
                            icon = if (runningNow) R.drawable.ic_bedtime
                                   else R.drawable.ic_check
                        )
                        IconButton(onClick = { haptics.open(); onOpenSettings() }) {
                            Icon(
                                painterResource(R.drawable.ic_settings),
                                contentDescription = stringResource(R.string.settings_title),
                                tint = g.onSurfaceLow
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                    }
                )
            }
        ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(inner)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = SIDE)
                // GROUP, not the old 8dp. The card that used to sit here was a
                // block among blocks and took the list's own spacing; the bar
                // replacing it is a fixed edge that content slides under, and
                // 8dp put the BEDTIME and WAKE UP overlines 11dp from it -
                // tighter than any deliberate gap on the screen.
                .padding(top = GROUP, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(GROUP),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!ready) {
                val missing = listOf(dnd, exact).count { !it }
                Surface(
                    color = card, shape = RoundedCornerShape(CORNER),
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

            // Not a permission - nothing here can be granted, and the phone
            // will not tell us whether it was fixed. It is a report that a
            // restart went unhandled, which is invisible otherwise: the app
            // stays armed, shows the right times, and has no alarms behind them.
            if (missedBoot) {
                Surface(
                    color = card, shape = RoundedCornerShape(CORNER),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        Modifier.padding(CARD_PAD),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            stringResource(R.string.boot_missed_title),
                            style = MaterialTheme.typography.titleLarge, color = g.onSurface
                        )
                        PermissionRow(
                            stringResource(R.string.boot_missed_row),
                            stringResource(R.string.boot_missed_why),
                            granted = false
                        ) { haptics.open(); BootWatch.openAutoStart(ctx) }
                    }
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
                            WindowTime(
                                ctx, start,
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
                                    // Same ink as BEDTIME. In Arc.dawn this word sat
                                    // at 1.7:1 on the cream - measured, and against
                                    // BEDTIME's 14:1 in the same row - which is not
                                    // a shade, it is a tint. 11sp does not reach the
                                    // large-text exemption, so nothing warm enough to
                                    // still read as dawn could clear 4.5:1: the best
                                    // was #9E5426, which arrives as rust. The warmth
                                    // moves to the SUN instead, which needs only 3:1
                                    // and is the mark the ring already uses. That is
                                    // the reasoning the glyphs were added under -
                                    // colour alone made the wake side something you
                                    // had to learn - so this is that decision
                                    // finished, not reversed. 5.5:1 now.
                                    style = MaterialTheme.typography.labelSmall,
                                    color = g.onSurfaceLow
                                )
                                Spacer(Modifier.width(7.dp))
                                PhaseGlyph(moon = false, tint = Arc.dawn, ground = ground)
                            }
                            Spacer(Modifier.height(4.dp))
                            WindowTime(ctx, end, color = g.onSurfaceMid)

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
                            //
                            // Only while armed, and it stays that way: "34m until
                            // bedtime" under a switch reading Off is a countdown to
                            // something that is not going to happen. Offering it either
                            // way was tried and rejected for exactly that.
                            //
                            // Switching off therefore does drop this reading. What was
                            // reported as the time JUMPING was not that - it was the
                            // dots below it appearing, which lengthened the centre
                            // column and moved the numeral 6dp. BedtimeDial reserves
                            // their space now, so the value can change without the
                            // numeral moving.
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
                    running = runningNow,
                    track = card, enabled = enabled,
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

                // The window in words, under the dial. The dial says this
                // spatially and the centre as a duration; neither answers which
                // morning. See windowSentence.
                windowSentence(ctx, prefs, start, end, days)?.let { line ->
                    Spacer(Modifier.height(TIGHT))
                    Text(
                        line,
                        style = MaterialTheme.typography.bodyLarge,
                        color = g.onSurfaceLow,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Always present. Hiding it while running read as a bug, and the
            // reason it was hidden - that an edit could cut the night short -
            // is fixed at the source now. Emptying it is allowed too: it means
            // the window runs once and the app switches itself off, which the
            // "Just once" badge on the card names.
            Section(stringResource(R.string.section_which_days)) {
                // "which days", not "which nights". The window is a span on a
                // 24-hour dial and can sit anywhere in the day - an afternoon
                // nap is a legitimate use - so a label naming the time of day
                // was describing one case of it. Neither platform names one
                // either: Android's own schedule editor calls this row Repeat,
                // and Apple's Sleep Schedule - which would have every excuse -
                // heads its day circles DAYS ACTIVE.
                // The Russian translation had already quietly said «в какие дни»
                // rather than «ночи», so this brings the English into line with a
                // correction a native speaker had made on their own, and fixes
                // the Ukrainian, which had copied the English mistake.
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
                // A segmented button, not four chips. The presets are
                // single-choice and they RULE the day row beneath them, but as
                // loose pills they wore the same fill, the same round shape and
                // the same selected treatment as the seven day circles - which
                // says "these are peers" about two controls where one governs
                // the other. M3 connects a segmented button into one container,
                // which reads as "pick one of these" against seven separate
                // things that read as "toggle any of these". Four options is
                // inside the 2-5 the component is specified for.
                // The switch, and beneath it what the switch governs - the
                // same shape as the Do Not Disturb card, because it is the same
                // relationship. "Does this repeat" is a different question from
                // "which nights", it is answered FIRST, and it was previously a
                // TextButton underneath both controls that answer the second -
                // the lowest-emphasis component M3 has, carrying half the
                // schedule's meaning, positioned after the thing it decides.
                //
                // It is a switch rather than a button because its label has to
                // name a STATE. As a button it flipped to "Repeat every night"
                // exactly when the schedule had stopped repeating, and between a
                // segmented control and seven circles that both show state by
                // fill, a bare word is read as state too. So it said the
                // opposite of the truth in the one state it existed for.
                //
                // repeats is derived from days rather than stored: a one-off IS
                // the empty set, and deriving it means deselecting the last day
                // flips the switch by itself instead of leaving two sources of
                // truth to disagree.
                val repeats = days.isNotEmpty()
                var savedDays by remember { mutableStateOf(DayOfWeek.entries.toSet()) }
                val press = remember { MutableInteractionSource() }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = repeats,
                            interactionSource = press,
                            // No ripple on this row. It is the one switch row
                            // with no card behind it, so the ripple had nothing
                            // to be clipped by and arrived as a bare rectangle
                            // across the content width; clipping it to the
                            // container corner only traded that for a floating
                            // lozenge. The press is not unacknowledged, though -
                            // the interaction source above still reaches the
                            // switch, so the thumb grows to 28dp under the
                            // finger, which is where the feedback belongs.
                            indication = null,
                            role = Role.Switch,
                            onValueChange = { on ->
                                haptics.toggle(on)
                                if (on) days = savedDays.ifEmpty { everyNight }
                                else { savedDays = days; days = emptySet() }
                                commit()
                            }
                        )
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            stringResource(R.string.repeat_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = g.onSurface
                        )
                        Text(
                            stringResource(
                                if (repeats) R.string.repeat_sub_on
                                else R.string.days_runs_once
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = g.onSurfaceLow
                        )
                    }
                    GloamSwitch(checked = repeats, interactionSource = press)
                }
                // Gone entirely when the schedule does not repeat, not dimmed.
                // Dimming was the first answer, on the model of the allowlist row
                // under the Do Not Disturb switch - but that row stays because it
                // still HAS a value to show, greyed. These do not: a one-off IS
                // the empty set, so the presets came up all unselected and the
                // circles all hollow, which is not a dimmed state, it is a dimmed
                // absence. The subtitle above already says the whole of it.
                //
                // Note this is not the case CLAUDE.md warns about. The day row
                // vanishing once bedtime STARTED read as a bug because the
                // schedule still existed and the screen had stopped admitting it.
                // Here there is genuinely nothing to show, and the section is
                // asking "which nights" of a schedule that has none.
                //
                // expandVertically rather than a bare if, or 110dp leaves in one
                // frame and the whole page below it jumps.
                AnimatedVisibility(
                    visible = repeats,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                Column(
                    Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(TIGHT)
                ) {
                    val presets = listOf(
                        stringResource(R.string.preset_every_night) to everyNight,
                        stringResource(R.string.preset_weekdays) to weekdays,
                        stringResource(R.string.preset_weekends) to weekends
                    )
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        presets.forEachIndexed { i, (label, set) ->
                            SegmentedButton(
                                selected = days == set,
                                onClick = { haptics.select(); days = set; commit() },
                                shape = SegmentedButtonDefaults.itemShape(i, presets.size),
                            // M3 sizes a segmented button 40dp tall through
                            // defaultMinSize and, unlike Surface, never calls
                            // minimumInteractiveComponentSize - so Material's own
                            // component ships under Android's 48dp touch minimum.
                            // Measured at 40.3dp here before this line. The day
                            // row already pays 48dp for the same reason, and a
                            // row of presets directly above it should not be the
                            // one control on the screen you have to aim at.
                            modifier = Modifier.height(48.dp),
                                colors = SegmentedButtonDefaults.colors(
                                    activeContainerColor = g.selectFill,
                                    activeContentColor = g.onSelect,
                                    // `outline`, like the day rings directly
                                    // below - these are two borders doing the
                                    // same job 60dp apart, and they were 29
                                    // tones apart until someone looked at them
                                    // together instead of at their numbers.
                                    activeBorderColor = g.outline,
                                    inactiveContainerColor = Color.Transparent,
                                    inactiveContentColor = g.onSurfaceMid,
                                    inactiveBorderColor = g.outline
                                ),
                                // No check mark. M3 puts one on the active segment,
                                // but it costs about 26dp and "Weekdays" already
                                // wants 66 of the 79 a third of the row leaves. The
                                // fill says which one is chosen, and unlike the
                                // check it says so from across the room.
                                icon = {},
                                label = {
                                    Text(
                                        label,
                                        style = MaterialTheme.typography.labelLarge,
                                        maxLines = 1
                                    )
                                }
                            )
                        }
                    }
                    DayRow(days) { d ->
                        haptics.toggle(d !in days)
                        days = if (d in days) days - d else days + d
                        commit()
                    }
                }
                }
                // The chips are mornings; a window reaching Saturday morning
                // starts on Friday. Only worth saying when the two fall on
                // different days.
                //
                // "the day before", not "the evening before". Crossing midnight
                // does not mean starting in the evening: 12:30 to 08:30 is a
                // twenty-hour window that crosses it and starts at midday, and
                // 17:00 to 02:00 starts in the afternoon. Both translations
                // already used a word that means the day before on its own -
                // «накануне», «напередодні» - and had "evening" bolted on
                // because the English said so, so both got shorter as well as
                // truer.
                if (crossesMidnight && repeats) Text(
                    stringResource(R.string.days_start_day_before),
                    style = MaterialTheme.typography.bodySmall,
                    color = g.onSurfaceLow
                )
            }

            Section(stringResource(R.string.section_what_can_wake_you)) {
            // Do Not Disturb is not a screen effect, it is a subsystem, and
            // this is its configuration. As a chip in the effects row it sat
            // apart from the screen that configures it - and turning it off
            // left that screen fully live while doing nothing, because the
            // filter had become INTERRUPTION_FILTER_ALL. One card: the switch,
            // and beneath it what the switch governs.
            GroupedList(card, buildList<@Composable () -> Unit> {
                // At the TOP of the group, not its foot - a caveat you meet
                // after reading what it qualifies has already failed.
                if (!runningNow) add {
                    NoticeStrip(planNote(ctx, enabled, start, end, days, loc))
                }
                add {
                    SwitchRow(
                        headline = stringResource(R.string.dnd_title),
                        checked = fxDnd,
                        // Every other row in the app carries a leading icon, so
                        // these two started their text 40dp from the screen edge
                        // where the card directly below started at 80 - a ragged
                        // left edge between two cards on one screen.
                        leading = { RowIcon(R.drawable.ic_dnd, IconTint.Dnd) },
                        onCheckedChange = { fxDnd = it; haptics.toggle(it); commit() },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                add {
                    val gets = remember(tick, fxDnd) {
                        Interruptions.shortSummary(res, Interruptions.allowed(res, prefs, short = true))
                    }
                    // The title matches the screen it opens: a link and its
                    // destination naming themselves differently is how you
                    // doubt you arrived.
                    LinkRow(
                        headline = stringResource(R.string.what_is_allowed),
                        supporting =
                            if (fxDnd) gets else stringResource(R.string.dnd_nothing_silenced),
                        leading = { RowIcon(R.drawable.ic_allowlist, IconTint.Allowed) },
                        onClick = { haptics.open(); onOpenInterruptions() },
                        modifier = Modifier
                            .alpha(if (fxDnd) 1f else 0.45f)
                            .fillMaxWidth(),
                        enabled = fxDnd
                    )
                }
                // The readout is its own item. It reports on the two rows above
                // rather than being one of them, and a grouped list has no way
                // to say that - so it ends the block, which is where evidence
                // belongs.
                if (runningNow) add {
                    val filter = remember(tick) { ZenController.currentFilter(ctx) }
                    val ignored = fxDnd &&
                        filter == NotificationManager.INTERRUPTION_FILTER_ALL
                    // No divider: the 2dp between items is the separation now.
                    // Verdict first, then what it means. The pill is the
                    // grammar Google Health uses for "In range" / "Out of
                    // range": a tonal fill with same-hue ink, so the state
                    // is carried by an ELEMENT rather than by the colour of
                    // a sentence. What it replaced said the failure only in
                    // `cta` ink, which is invisible to anyone reading shape
                    // - and said it in words that were wrong, see strings.
                    val (pill, line, fill, ink) = when {
                        ignored -> Quad(
                            R.string.filter_pill_ignored, R.string.filter_ignored,
                            g.alert, g.onAlert
                        )
                        filter == NotificationManager.INTERRUPTION_FILTER_ALL -> Quad(
                            R.string.filter_pill_off, R.string.filter_all,
                            g.veil, g.onSurfaceLow
                        )
                        filter == NotificationManager.INTERRUPTION_FILTER_PRIORITY -> Quad(
                            R.string.filter_pill_on, R.string.filter_priority,
                            g.selectFill, g.onSelect
                        )
                        filter == NotificationManager.INTERRUPTION_FILTER_ALARMS -> Quad(
                            R.string.filter_pill_alarms, R.string.filter_alarms,
                            g.selectFill, g.onSelect
                        )
                        filter == NotificationManager.INTERRUPTION_FILTER_NONE -> Quad(
                            R.string.filter_pill_none, R.string.filter_none,
                            g.selectFill, g.onSelect
                        )
                        else -> Quad(
                            R.string.filter_pill_unknown, R.string.filter_unknown,
                            g.veil, g.onSurfaceLow
                        )
                    }
                    Column(
                        Modifier.padding(
                            horizontal = CARD_PAD, vertical = 14.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatusPill(stringResource(pill), fill, ink)
                        Text(
                            stringResource(line),
                            style = MaterialTheme.typography.bodySmall,
                            color = g.onSurfaceLow
                        )
                    }
                }
            })
            }

            // A card of rows, the same shape as "What can wake you" above it,
            // because it is the same kind of thing: a list of switches with
            // something to say about each.
            //
            // These were chips, and the chips were already one per row - no two
            // Russian labels fit a 311dp line, the closest pair missing by 9dp -
            // so they were full-width elements shaped like pills with dead space
            // to the right of every one. The explanations had nowhere to go and
            // collected at the foot of the section, two sentences away from the
            // controls they described. Each row carries its own now, the dead
            // space holds the switch, and the footnotes are gone.
            // "how the screen LOOKS" is a claim about now, and it is false
            // whenever the window is not running - which is most of the time
            // anyone is reading it. The label follows the tense of the thing it
            // names; the rows below are a plan until they are not.
            Section(
                stringResource(
                    if (runningNow) R.string.section_how_the_screen_looks
                    else R.string.section_how_the_screen_will_look
                )
            ) {
                GroupedList(card, buildList<@Composable () -> Unit> {
                    // The notice is an ITEM of the group, so the top outer
                    // corner belongs to whichever row is actually first.
                    if (!runningNow) add {
                        NoticeStrip(planNote(ctx, enabled, start, end, days, loc))
                    }
                    add {
                        EffectRow(
                            Fx.Grayscale, stringResource(R.string.fx_grayscale),
                            stringResource(R.string.fx_grayscale_sub), fxGray
                        ) { fxGray = !fxGray; haptics.toggle(fxGray); commit() }
                    }
                    add {
                        EffectRow(
                            Fx.Dim, stringResource(R.string.fx_dim),
                            stringResource(R.string.fx_dim_sub), fxDim
                        ) { fxDim = !fxDim; haptics.toggle(fxDim); commit() }
                    }
                    add {
                        // The one subtitle that is load-bearing rather than
                        // descriptive: the platform defers the theme change
                        // until the screen goes off, so tapping this while
                        // watching it does nothing and looks broken without it.
                        EffectRow(
                            Fx.Dark, stringResource(R.string.fx_dark),
                            stringResource(R.string.fx_dark_sub), fxDark
                        ) { fxDark = !fxDark; haptics.toggle(fxDark); commit() }
                    }
                    // No divider to remove with it: a grouped list just has one
                    // item fewer, and the corners re-form around what is left.
                    if (ambientRow) add {
                        EffectRow(
                            Fx.Ambient, stringResource(R.string.fx_ambient),
                            stringResource(R.string.fx_ambient_sub), fxAmbient
                        ) { fxAmbient = !fxAmbient; haptics.toggle(fxAmbient); commit() }
                    }
                })
            }

        }
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
                    // A one-off has no next occurrence, so the reassuring half
                    // of this sentence would be a promise the app cannot keep.
                    stringResource(
                        if (Scheduler.isOneOff(days)) R.string.end_bedtime_body_once
                        else R.string.end_bedtime_body
                    ),
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
/**
 * The window in plain language: "From 11:05 PM today to 7:15 AM tomorrow".
 *
 * The dial says this spatially and the centre says it as a duration. Neither
 * answers the question a circle is worst at - WHICH morning - and a window that
 * crosses midnight is only obvious on a clock face to someone who already reads
 * clock faces. This is the same fact in the register the screen was missing, and
 * it is the only part of the block a screen reader can make sense of at all.
 *
 * Built from the ACTUAL next or current window rather than from the two handles,
 * so the day words cannot drift from what is scheduled: a one-off, a window five
 * days out and a window running right now each name their own days.
 */
@Composable
private fun windowSentence(
    ctx: Context,
    prefs: Prefs,
    start: LocalTime,
    end: LocalTime,
    days: Set<DayOfWeek>
): String? {
    val res = ctx.resources
    val locale = LocalLocale.current.platformLocale
    val now = LocalDateTime.now()
    // The window you are IN, or else the next one - and "in" is asked with
    // enabled = true regardless of the switch, deliberately.
    //
    // Asking with the real switch was wrong in a way that only shows at night.
    // Off, at 23:34, inside a 6:55 PM window, it fell through to nextStart and
    // said "from 6:55 PM TOMORROW" - while the dial above it drew the marker
    // inside the arc. Reported as exactly that. Worse, it was a wrong
    // prediction rather than merely an odd one: switch on at 23:34 and bedtime
    // begins immediately, because liveWindowEnd treats a one-off as running the
    // moment the switch is on. The sentence would have promised tomorrow and
    // the app would have started that second.
    val endsAt = Scheduler.liveWindowEnd(
        enabled = true, activeDay = prefs.activeDay,
        start = start, end = end, days = days, from = now
    )
    val from = (endsAt?.minus(Scheduler.duration(start, end))
        ?: Scheduler.nextStart(start, end, days, now)) ?: return null
    val to = from.plus(Scheduler.duration(start, end))

    fun day(at: LocalDateTime): String = dayWord(ctx, at, now, DaySlot.SPAN)
    // One day word when both ends fall on it. "From 2:40 AM tomorrow to 8:40 AM
    // tomorrow" is correct and says it twice.
    return if (from.toLocalDate() == to.toLocalDate()) res.getString(
        R.string.window_span_same_day,
        hhmm(ctx, from.hour, from.minute), hhmm(ctx, to.hour, to.minute), day(to)
    ) else res.getString(
        R.string.window_span,
        hhmm(ctx, from.hour, from.minute), day(from),
        hhmm(ctx, to.hour, to.minute), day(to)
    )
}

/**
 * The last element of a card whose switches are not in effect: a warm strip
 * carrying one sentence about the rows above it. Both cards on Home use it, so
 * the two cannot drift.
 */
/** Four things travel together per filter state; a data class beats four whens. */
private data class Quad(val pill: Int, val line: Int, val fill: Color, val ink: Color)

/**
 * A status pill: tonal fill, same-hue ink, capsule.
 *
 * Borrowed deliberately from Google Health, which labels every metric this way
 * ("In range", "Goal not met"). The point is that STATE becomes an element you
 * can see and a screen reader can reach, instead of the colour of a sentence -
 * the same fault the effect chips and the choice sheets both had before they
 * were given real semantics.
 */
@Composable
private fun StatusPill(text: String, fill: Color, ink: Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = ink,
        modifier = Modifier
            .clip(CircleShape)
            .background(fill)
            .padding(horizontal = 12.dp, vertical = 5.dp)
    )
}

@Composable
private fun NoticeStrip(text: String) {
    val g = gloam
    // Full width and no inset, so it is a STRIP rather than a padded paragraph.
    // Area is what does the work: this is a warning about controls that look
    // live and are not, and it has to be seen before they are read.
    //
    // It is `selectFill` behind `onSelect` - the SAME pair as a selected day,
    // the preset pill and a checked switch - and it carries no token of its own.
    // That is the fourth shape this band has had and the first that cannot
    // drift: there is nothing to keep in sync, because it is not a copy of the
    // accent, it IS the accent.
    //
    // What the three earlier shapes cost is worth knowing before adding a
    // `notice` token back. A themed pair drifted 34 tones apart between the two
    // schemes. A single shared pair taken from Arc.dawn could not be tuned per
    // theme at all. A re-themed pair could, and then needed a dead-band worked
    // around (between tone 48 and 58 on that hue, NEITHER ink clears 4.5:1).
    // Reusing the accent has none of those problems by construction.
    //
    // The honest cost: the band is now the colour of the controls it is warning
    // about, and green conventionally reads "on". It earns that back by being
    // unmistakably part of this app rather than a fourth hue on the screen, and
    // the words do the semantic work. 4.90:1 in Dusk, 6.79:1 in Dawn; 2.07:1
    // and 1.26:1 against the card behind it.
    //
    // No rule below it: the colour step IS the boundary, and a rule plus a
    // colour change is two edges drawn for one. The TOP corners come free -
    // the card is a Surface with a shape, and a Surface clips its content.
    Box(Modifier.fillMaxWidth().background(g.selectFill)) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = g.onSelect,
            modifier = Modifier.padding(horizontal = CARD_PAD, vertical = 14.dp)
        )
    }
}

/**
 * What to say at the foot of a card whose switches are not in effect.
 *
 * Every switch on Home is a PLAN. It is written to prefs the moment it is
 * tapped and it reaches the phone when the window opens - so with bedtime off,
 * turning Do Not Disturb "on" is a real and correct action that silences
 * nothing tonight, and looks identical to one that would. Reported as exactly
 * that, and it is the same fault while ARMED but not yet running, where the
 * master switch is green and still nothing is being silenced.
 *
 * Three readings, because the reason differs and a person can act on the
 * difference: switched off (turn it on), armed (wait, or it says when), and
 * armed with nothing to run - a one-off already spent, or no days chosen.
 */
private fun planNote(
    ctx: Context,
    enabled: Boolean,
    start: LocalTime,
    end: LocalTime,
    days: Set<DayOfWeek>,
    locale: Locale
): String {
    if (!enabled) return ctx.getString(R.string.note_off)
    val now = LocalDateTime.now()
    val next = Scheduler.nextStart(start, end, days, now)
        ?: return ctx.getString(R.string.note_unscheduled)
    // The day word is not decoration. A clock time alone reads as TODAY, and
    // the next window can be days out - reported as exactly that, with the bar
    // saying "Starts in 35h 20m" over a note saying "until 12:00 PM". Same lie
    // by omission the window sentence was fixed for, so it uses the same rule.
    return ctx.getString(
        R.string.note_until,
        hhmm(ctx, next.hour, next.minute), dayWord(ctx, next, now, DaySlot.NOTE)
    )
}

/**
 * "today", "tomorrow", or the weekday - the app's one rule for naming the day
 * something falls on, shared by the window sentence and the plan note so the
 * two cannot disagree on the same screen.
 *
 * Past a day the relative words stop helping and start lying by omission -
 * "tomorrow" for something five days out. The weekday is localised by
 * java.time, so it needs no string of ours.
 */
/** Which sentence is asking; the two need different grammar, see strings.xml. */
private enum class DaySlot { SPAN, NOTE }

private val DAY_SPAN = intArrayOf(
    R.string.day_span_monday, R.string.day_span_tuesday, R.string.day_span_wednesday,
    R.string.day_span_thursday, R.string.day_span_friday, R.string.day_span_saturday,
    R.string.day_span_sunday
)
private val DAY_NOTE = intArrayOf(
    R.string.day_note_monday, R.string.day_note_tuesday, R.string.day_note_wednesday,
    R.string.day_note_thursday, R.string.day_note_friday, R.string.day_note_saturday,
    R.string.day_note_sunday
)

private fun dayWord(
    ctx: Context,
    at: LocalDateTime,
    now: LocalDateTime,
    slot: DaySlot
): String = when (ChronoUnit.DAYS.between(now.toLocalDate(), at.toLocalDate())) {
    0L -> ctx.getString(R.string.day_today)
    1L -> ctx.getString(R.string.day_tomorrow)
    // NOT java.time: it returns the nominative in every TextStyle, and the two
    // sentences that name a weekday decline it differently. See strings.xml.
    else -> ctx.getString(
        (if (slot == DaySlot.SPAN) DAY_SPAN else DAY_NOTE)[at.dayOfWeek.ordinal]
    )
}

/** Clock time. 24-hour throughout, which is what the dial is. */
private fun hhmm(ctx: Context, h: Int, m: Int): String = Clock.hhmm(ctx, h, m)

/**
 * One of the two window times, at the numeral size, with the day period set a
 * step down beside it.
 *
 * The period is a separate Text on purpose. As one string "11:30 PM" overflows
 * the 146.5dp column, and CLDR joins it with U+202F - a no-break space - so the
 * line cannot break at the space and broke mid-token instead, to "11:30 P"/"M".
 * At titleLarge the period costs about a third of what it did, which fits any
 * hour rather than just the ones with a single digit. Both halves align on the
 * BASELINE, not the box, or the small text would float.
 *
 * maxLines = 1 on the numerals is the backstop: if some locale still cannot
 * fit, it must clip rather than reflow, because this block sits directly above
 * the dial and anything that changes its height moves the whole page.
 */
@Composable
private fun WindowTime(ctx: Context, t: java.time.LocalTime, color: Color) {
    val r = Clock.reading(ctx, t)
    if (r.period == null) {
        Text(
            r.time,
            style = MaterialTheme.typography.displaySmall,
            color = color,
            maxLines = 1
        )
        return
    }
    Row(verticalAlignment = Alignment.Bottom) {
        val numerals = @Composable {
            Text(
                r.time,
                style = MaterialTheme.typography.displaySmall,
                color = color,
                maxLines = 1,
                modifier = Modifier.alignByBaseline()
            )
        }
        val period = @Composable {
            Text(
                r.period,
                style = MaterialTheme.typography.titleLarge,
                color = color,
                maxLines = 1,
                modifier = Modifier.alignByBaseline()
            )
        }
        if (r.periodFirst) { period(); Spacer(Modifier.width(5.dp)); numerals() }
        else { numerals(); Spacer(Modifier.width(5.dp)); period() }
    }
}

/**
 * The dial centre's compact duration - "5h 20m", or just "20m" under an hour.
 *
 * The hours half is dropped rather than shown as "0h", which said nothing and
 * cost a third of the width of the longest thing this string has to fit: the app
 * bar's status line, where it is capped at one line and truncates rather than
 * wraps. The minutes stay zero-padded WITH an hour ("5h 05m") so the countdown
 * does not change width every ten minutes, and unpadded without one ("5m"),
 * where there is no column to hold.
 */
private fun span(res: Resources, minutes: Long): String =
    if (minutes < 60) res.getString(R.string.dur_minutes, minutes)
    else res.getString(R.string.dur_compact, minutes / 60, minutes % 60)




/*
 * Google's own geometry, like the allowlist rows. The `container` parameter is
 * gone with the hand-drawn crescent, which had to punch itself with the chip's
 * own fill to stay a crescent.
 */
private enum class Fx { Grayscale, Dim, Dark, Ambient }

/**
 * One screen effect: what it is called, what it does, and its switch.
 *
 * The ROW carries the gesture and the Switch is only the indicator, the way
 * every other switch in this app works - the switch is not a second target
 * competing for the taps people aim most carefully. `checked = null` makes it a
 * link row with a chevron instead, for an effect this phone will not apply.
 */
@Composable
private fun EffectRow(
    icon: Fx,
    title: String,
    subtitle: String,
    checked: Boolean,
    onClick: () -> Unit
) {
    SwitchRow(
        headline = title, supporting = subtitle,
        checked = checked, onCheckedChange = { onClick() },
        modifier = Modifier.fillMaxWidth(),
        leading = { FxIcon(icon) }
    )
}

/** A list row's leading icon, in a tonal container. */
@Composable
private fun RowIcon(@DrawableRes id: Int, tint: IconTint) {
    RowAvatar(id, tint
    )
}

@Composable
private fun FxIcon(icon: Fx) {
    RowAvatar(
        id = (
            when (icon) {
                Fx.Grayscale -> R.drawable.ic_grayscale
                Fx.Dim -> R.drawable.ic_dim
                Fx.Dark -> R.drawable.ic_dark
                Fx.Ambient -> R.drawable.ic_ambient
            }
        ),
        tint = when (icon) {
            Fx.Grayscale -> IconTint.Grayscale
            Fx.Dim -> IconTint.Dim
            Fx.Dark -> IconTint.Dark
            Fx.Ambient -> IconTint.Ambient
        }
    )
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
            // The day toggles had no press feedback at all - the ripple is
            // unbounded and reads as a halo around the chip rather than as the
            // chip responding. M3 Expressive answers this by morphing a round
            // toggle's corners IN while held, and its connected button group
            // says so in tokens: ContainerShape CornerFull, and
            // PressedInnerCornerCornerSize CornerValueExtraSmall.
            //
            // The component that does it is not in material3 1.4.0 - only the
            // tokens ship, the composables are in material3-expressive, which
            // is not on this classpath and would be this project's first new
            // dependency. The BEHAVIOUR is four lines, so it is written out
            // here rather than taken on a dependency.
            //
            // The spring is M3's own "fast spatial" - damping 0.6, stiffness
            // 800, read out of ExpressiveMotionTokens. That object is
            // `internal`, so the numbers are copied rather than referenced; if
            // they ever drift, this is the thing to re-read.
            val pressed by press.collectIsPressedAsState()
            val corner by animateDpAsState(
                if (pressed) DAY_PRESSED_CORNER else DAY_SIZE / 2,
                spring(dampingRatio = 0.6f, stiffness = 800f),
                label = "day corner"
            )
            val dayShape = RoundedCornerShape(corner)
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
                        .clip(dayShape)
                        .then(
                            if (on) Modifier.background(g.selectFill)
                            // `outline`, not `line`: this ring reports state,
                            // and in `line` it measured 1.66:1 in Dawn.
                            else Modifier.border(1.5.dp, g.outline, dayShape)
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
    ActionRow(
        headline = title,
        supporting = why,
        trailing = {
            if (granted) Text(
                stringResource(R.string.perm_allowed),
                style = MaterialTheme.typography.labelLarge, color = g.stateOn
            ) else Button(
                onClick = onRequest,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = g.cta, contentColor = g.surface
                )
            ) {
                Text(
                    stringResource(R.string.perm_allow),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    )
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
