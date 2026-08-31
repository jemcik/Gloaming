package com.jemcik.gloaming.ui

/**
 * The home screen. Lifted out of MainActivity.kt, which had grown to 1,639
 * lines - about a third of the whole app - with `Home` alone accounting for 930
 * of them. It sits in `ui` beside SettingsScreen and InterruptionsScreen, which
 * is where the app's other two screens already lived; MainActivity keeps only
 * the Activity, the theme decision and `Root`, which is all an entry point
 * should be.
 */

import android.app.NotificationManager
import android.content.Intent
import android.content.res.Resources
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.*
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.jemcik.gloaming.R
import com.jemcik.gloaming.core.*
import com.jemcik.gloaming.core.Prefs
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.coroutines.delay
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
    // they read through Resources instead. `LocalResources`, not
    // ctx.resources: the latter is not configuration-aware and keeps serving
    // the old values after a Configuration change, which in THIS app is not
    // hypothetical - it ships a per-app language picker.
    val res = LocalResources.current
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
    // cannot drift apart.
    //
    // Switching off MID-WINDOW used to raise a confirm dialog, and it is gone.
    // A confirmation is for an action that is destructive or hard to undo, and
    // this is neither: measured on the phone, off gives zen_mode 0 with
    // activeDay cleared, and one tap back on gives zen_mode 1 with activeDay
    // re-derived, the END alarm restored to the same minute and the next START
    // re-queued. Nothing is spent and nothing is lost.
    // Its real job was not consent but EXPLANATION - "the next one runs as
    // scheduled" - and the screen behind it already answers that the instant
    // the switch moves: the status line goes to "Off", the dial still draws the
    // window, the days stay filled, and the plan note appears on both cards
    // saying these settings take effect once you turn it on. A modal charges a
    // decision every single time to deliver a fact you need once.
    // It also fired at the worst possible moment - a dark room, a grayscale
    // screen, someone who wants the night over NOW - and it was the only switch
    // in the app that argued back.
    fun setBedtime(on: Boolean) {
        haptics.toggle(on)
        enabled = on
        if (!on) {
            Scheduler.cancelAll(ctx)
            ZenController.setActive(ctx, prefs, false)
        }
        commit()
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

    // The page does not move with the state, and neither do the cards.
    // There used to be a three-rung ground ladder crossfading over a second,
    // with a radial bloom on top while running. Both are gone. Two things are
    // worth keeping from how that ended: the animation was added because the
    // instant version read as a glitch mid-drag ("the background changes, what
    // is that?"), so if a moving ground ever comes back it has to be animated;
    // and `raiseRunning` was never a design choice, only compensation for the
    // page deepening towards the cards in Dawn, so it left with the thing it
    // was compensating for.
    val ground = g.surface
    val card = g.raise

    Box(
        Modifier
            .fillMaxSize()
            .drawBehind { drawRect(ground) }
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
                                   else R.drawable.ic_check,
                            contentDescription = stringResource(R.string.bedtime_mode)
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
            // A SECTION and a GROUPED LIST, like every other list in the app.
            // It used to be one Surface of padded prose with a titleLarge
            // heading inside it and rows carrying no leading icon - the only
            // block on Home built its own way, which is what "correspond to our
            // style" was asking about. M3 has no permission component; what it
            // does have is a list item with a leading icon and a trailing
            // action, which is exactly what each of these rows is.
            if (!ready) {
                val missing = listOf(dnd, exact).count { !it }
                Section(
                    pluralStringResource(R.plurals.permissions_to_go, missing, missing),
                    rule = false
                ) {
                    GroupedList(card, buildList<@Composable () -> Unit> {
                        add {
                            PermissionCard(
                                stringResource(R.string.perm_dnd_title),
                                stringResource(R.string.perm_dnd_why), dnd,
                                R.drawable.ic_dnd, IconTint.Dnd
                            ) {
                                ctx.startActivity(
                                    Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                                )
                            }
                        }
                        add {
                            PermissionCard(
                                stringResource(R.string.perm_alarms_title),
                                stringResource(R.string.perm_alarms_why), exact,
                                R.drawable.ic_alarm, IconTint.Alarm
                            ) {
                                ctx.startActivity(
                                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                )
                            }
                        }
                    })
                }
            }

            // Not a permission - nothing here can be granted, and the phone
            // will not tell us whether it was fixed. It is a report that a
            // restart went unhandled, which is invisible otherwise: the app
            // stays armed, shows the right times, and has no alarms behind them.
            if (missedBoot) {
                Section(stringResource(R.string.boot_missed_title), rule = false) {
                    GroupedList(card, listOf {
                        PermissionCard(
                            stringResource(R.string.boot_missed_row),
                            stringResource(R.string.boot_missed_why),
                            granted = false,
                            icon = R.drawable.ic_restart, tint = IconTint.Boot
                        ) { haptics.open(); BootWatch.openAutoStart(ctx) }
                    })
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
                                    activeBorderColor = g.selectBorder,
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
            // CONTAINERS, not bare text. Both actions already met Android's
            // 48dp touch minimum - measured 69x48 and 58x48 - but a TextButton
            // draws no container, so only the words read as a button and they
            // were reported as too small. Filled for the confirm and outlined
            // for the dismiss keeps M3's emphasis order while giving each one a
            // shape to aim at, and the filled one matches the permission card's
            // button, which is now the app's only other action button.
            //
            // Neither names a colour: the scheme's primary is `stateOn`, so
            // they follow the accent and flip correctly per theme.
            confirmButton = {
                Button(
                    onClick = {
                        haptics.confirm()
                        val t = LocalTime.of(state.hour, state.minute)
                        if (picking == "start") start = t else end = t
                        picking = null; commit()
                    },
                    shape = CircleShape
                ) { Text(stringResource(R.string.action_set)) }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { picking = null },
                    shape = CircleShape,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = g.onSurfaceLow),
                    border = BorderStroke(1.dp, g.outline)
                ) { Text(stringResource(R.string.action_cancel)) }
            },
            text = {
                TimePicker(state = state, colors = pickerColors(accent, onAccent))
            }
        )
    }
}
