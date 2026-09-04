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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.jemcik.gloaming.R
import com.jemcik.gloaming.core.*
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
/* Every `val res` below is LocalResources, never ctx.resources. remember{}
   blocks are not composable so they cannot call stringResource and must read
   through Resources - and ctx.resources is not configuration-aware, so it keeps
   serving the old values after a Configuration change, which in THIS app is not
   hypothetical: it ships a per-app language picker. */

/**
 * The home screen: the bar, then the sections, in the order they are read.
 *
 * Home holds no state of its own - [HomeState] does - and each section below is
 * its own composable, so what is left here IS the page order plus the three
 * things that genuinely span it: the minute ticker, the ON_RESUME re-read, and
 * whether the two permissions are in place.
 *
 * The sections are deliberately unconditional. Each decides for itself whether
 * it draws anything, so adding one is a line here and a function below, and
 * reading the screen's shape does not mean reading its conditions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Home(
    scroll: ScrollState,
    onOpenSettings: () -> Unit,
    onOpenInterruptions: () -> Unit
) {
    val ctx = LocalContext.current
    val s = rememberHomeState()
    val g = gloam

    val dnd = ZenController.hasDndAccess(ctx)
    val exact = Scheduler.canScheduleExact(ctx)
    val ready = dnd && exact

    val now = remember(s.tick) { LocalTime.now() }

    // The countdown, the dial's hand and the crown marker all read `now`, and
    // all three sat frozen while the screen stayed open.
    LaunchedEffect(Unit) {
        while (true) { delay(60_000); s.bump() }
    }
    val insideWindow = remember(s.tick, s.enabled, s.start, s.end, s.days) {
        s.insideWindow()
    }
    val runningNow = s.enabled && insideWindow

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) s.onResume()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // ON_RESUME is not enough on its own: the tile changes the same value from
    // over the top of this screen without ever pausing it. See HomeState.watchStore.
    DisposableEffect(s) {
        val watch = s.watchStore()
        onDispose { watch.close() }
    }

    LaunchedEffect(Unit) { s.rearmIfEnabled() }

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

    Box(
        Modifier
            .fillMaxSize()
            .drawBehind { drawRect(ground) }
    ) {
        val bar = TopAppBarDefaults.pinnedScrollBehavior()
        Scaffold(
            containerColor = Color.Transparent,
            modifier = Modifier.nestedScroll(bar.nestedScrollConnection),
            topBar = {
                HomeBar(s, runningNow, ready, bar, onOpenSettings)
            }
        ) { inner ->
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll)
                    .padding(inner)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = SIDE)
                    // GROUP, not the old 8dp. The card that used to sit here was
                    // a block among blocks and took the list's own spacing; the
                    // bar replacing it is a fixed edge that content slides under,
                    // and 8dp put the BEDTIME and WAKE UP overlines 11dp from it
                    // - tighter than any deliberate gap on the screen.
                    .padding(top = GROUP, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(GROUP),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                PermissionSection(dnd, exact)
            LaunchSetupSection(s)
                BootNoticeSection(s)
            BackgroundNoticeSection(s)
            MissedAlarmSection(s)
        LaunchTipSection(s)
                WindowBlock(s, now, runningNow, scroll)
                EndsSection(s)
        DaysSection(s)
                WakeSection(s, runningNow, onOpenInterruptions)
                ScreenEffectsSection(s, runningNow)
            }
        }
    }

    TimePickerDialog(s)
}


/**
 * The two permissions the app cannot work without, as a section of list items
 * with a leading icon and a trailing action - which is what each of these rows is.
 */
@Composable
private fun PermissionSection(dnd: Boolean, exact: Boolean) {
    val ctx = LocalContext.current
    val g = gloam
    val card = g.raise
    val ready = dnd && exact

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
}

/**
 * Not a permission - nothing here can be granted, and the phone will not tell us
 * whether it was fixed. A report that a restart went unhandled.
 */
@Composable
private fun BootNoticeSection(s: HomeState) {
    val ctx = LocalContext.current
    val g = gloam
    val haptics = s.haptics
    val card = g.raise

    // Not a permission - nothing here can be granted, and the phone
    // will not tell us whether it was fixed. It is a report that a
    // restart went unhandled, which is invisible otherwise: the app
    // stays armed, shows the right times, and has no alarms behind them.
    if (s.missedBoot) {
        Section(stringResource(R.string.boot_missed_title), rule = false) {
            GroupedList(card, listOf {
                PermissionCard(
                    stringResource(R.string.boot_missed_row),
                    stringResource(R.string.boot_missed_why),
                    granted = false,
                    icon = R.drawable.ic_restart, tint = IconTint.Boot
                ) { haptics.open(); Doors.openAutoStart(ctx) }
            })
        }
    }
}

/**
 * Told BEFORE anything is lost, on phones that can stop an app running.
 *
 * The other two cards here are reports: something already went wrong. This one
 * is a warning, and it exists because waiting for the evidence costs a whole
 * night in Do Not Disturb - which is exactly what happened, once, and is not a
 * reasonable way to find out.
 *
 * It is honest about its own uncertainty. The app cannot read auto-launch, and a
 * background appop reading `allow` is indistinguishable from one never
 * configured, so this does NOT claim anything is wrong - only that this phone
 * can interfere and has not yet been seen to behave. The first END that arrives
 * settles it and the card never returns.
 */
@Composable
private fun LaunchSetupSection(s: HomeState) {
    val ctx = LocalContext.current
    val g = gloam
    val haptics = s.haptics
    val card = g.raise

    // Read it if you can; measure it only when you cannot. Where the appop is
    // readable, BackgroundNoticeSection is already saying this in better words -
    // it names a setting that exists, and it clears itself the moment that
    // setting changes, where the probe has to be re-run to believe a fix. Two
    // cards making the same accusation is worse than either alone.
    if (!s.blocked || s.restricted) return

    // Same measurement, two sets of words. Naming Honor's switches is the most
    // useful thing that can be said WHERE THEY EXIST, and a lie anywhere else -
    // so a phone without a launch manager gets the general wording, whose button
    // lands on app details rather than a screen it does not have.
    val title = if (s.hasLaunchManager) R.string.launch_setup_title else R.string.bg_blocked_title
    val row = if (s.hasLaunchManager) R.string.launch_setup_row else R.string.bg_blocked_row
    val why = if (s.hasLaunchManager) R.string.launch_setup_why else R.string.bg_blocked_why

    Section(stringResource(title), rule = false) {
        GroupedList(card, listOf {
            PermissionCard(
                stringResource(row), stringResource(why),
                granted = false,
                icon = R.drawable.ic_gloaming, tint = IconTint.Blocked
            ) { haptics.open(); s.retestBackground(); Doors.openAutoStart(ctx) }
        })
    }
}

/**
 * The one-time offer, shown when bedtime is first switched on.
 *
 * Deliberately the calmest thing on the screen, and the only card here that can
 * be answered with "no". See [TipCard] for why it looks nothing like the notices
 * above it: those report a measurement, this one cannot, and dressing a guess up
 * as a finding is what made the previous version of this card unbearable - it
 * appeared on every phone with a launch manager, said something was wrong when
 * nothing was, and could not be cleared by fixing anything.
 *
 * ONLY THE REFUSAL ANSWERS IT. "Set up" used to close the tip as well, so
 * opening the vendor screen, changing nothing and pressing back left the card
 * gone for good - reported as exactly that, and reproduced on the Honor. Nothing
 * here can tell the difference: Honor's auto-launch state is unreadable, so an
 * app that treats "you looked at the screen" as "you did it" has made the one
 * claim it has no way to check, and will never find out it was wrong. Going to
 * look is not an answer. Only "Skip" is.
 */
@Composable
private fun LaunchTipSection(s: HomeState) {
    val ctx = LocalContext.current
    val haptics = s.haptics
    val card = gloam.raise

    if (!s.showLaunchTip()) return

    Section(stringResource(R.string.section_this_phone), rule = false) {
        GroupedList(card, listOf {
            // Two faces, and the second exists because the first cannot be
            // checked. Before you have been: an offer, refusable. After you
            // have been: the question the app has no way to answer for itself.
            // Asking is the honest move where measuring is impossible - and it
            // is what replaces the accidental dismissal that "Set up" used to
            // perform, which was wrong for treating a visit as an answer.
            if (s.tipVisited) {
                TipCard(
                    stringResource(R.string.launch_tip_title),
                    stringResource(R.string.launch_tip_check),
                    stringResource(R.string.launch_tip_not_yet),
                    stringResource(R.string.launch_tip_done),
                    // Not yet is not a refusal - it is "I have not done it",
                    // which puts them back where they were, still able to
                    // dismiss the offer outright.
                    onDismiss = { haptics.select(); s.unvisitLaunchTip() },
                    onAction = { haptics.confirm(); s.closeLaunchTip() }
                )
            } else {
                TipCard(
                    stringResource(R.string.launch_tip_title),
                    stringResource(R.string.launch_tip_why),
                    stringResource(R.string.launch_tip_dismiss),
                    stringResource(R.string.launch_tip_action),
                    onDismiss = { haptics.select(); s.closeLaunchTip() },
                    onAction = {
                        haptics.open(); s.visitLaunchTip(); Doors.openAutoStart(ctx)
                    }
                )
            }
        })
    }
}

/**
 * The phone is holding our alarms. Unlike the boot notice this is a SETTING we
 * can read, so the section appears exactly while it is wrong and disappears the
 * moment it is fixed - no guessing, no measuring a symptom.
 *
 * It sits above the window rather than below it because it invalidates
 * everything below: with this on, the times drawn on the dial are what the app
 * intends, not what the phone will do.
 */
@Composable
private fun BackgroundNoticeSection(s: HomeState) {
    val ctx = LocalContext.current
    val g = gloam
    val haptics = s.haptics
    val card = g.raise

    if (s.restricted) {
        Section(stringResource(R.string.bg_blocked_title), rule = false) {
            GroupedList(card, listOf {
                PermissionCard(
                    stringResource(R.string.bg_blocked_row),
                    stringResource(R.string.bg_blocked_why),
                    granted = false,
                    // The app's own mark, not a clock. An alarm-clock icon here
                    // says "this wakes you up", which is the exact misreading the
                    // wording of this card was rewritten to avoid.
                    icon = R.drawable.ic_gloaming, tint = IconTint.Blocked
                ) { haptics.open(); BackgroundLimit.openSettings(ctx) }
            })
        }
    }
}

/**
 * The phone ate an alarm. This is the backstop for everything BackgroundLimit
 * cannot see: measured on an Honor, a merely FROZEN app misses its END entirely
 * while isBackgroundRestricted still reports false, so a card keyed on the
 * restriction alone would have stayed silent through the exact failure it exists
 * for. This one asks a question no vendor can hide the answer to - did our own
 * alarm arrive? - and so it catches the freezer, the restriction, a process
 * killer, and whatever ships next.
 *
 * Both cards can appear together, and that is correct rather than redundant: one
 * names a switch to fix, the other reports what already went wrong.
 */
@Composable
private fun MissedAlarmSection(s: HomeState) {
    val ctx = LocalContext.current
    val g = gloam
    val haptics = s.haptics
    val card = g.raise

    if (s.missedAlarm) {
        Section(stringResource(R.string.missed_alarm_title), rule = false) {
            GroupedList(card, listOf {
                PermissionCard(
                    stringResource(R.string.missed_alarm_row),
                    stringResource(R.string.missed_alarm_why),
                    granted = false,
                    icon = R.drawable.ic_gloaming, tint = IconTint.Blocked
                ) { haptics.open(); BackgroundLimit.openSettings(ctx) }
            })
        }
    }
}

/**
 * One block: the two times, the crown that spans them, the dial that edits them
 * and the sentence that says it in words. Nothing here is separable from the rest.
 */
@Composable
private fun WindowBlock(
    s: HomeState,
    now: LocalTime,
    runningNow: Boolean,
    scroll: ScrollState
) {
    val ctx = LocalContext.current
    val res = LocalResources.current
    val g = gloam
    val haptics = s.haptics
    val prefs = s.prefs
    val card = g.raise
    val ground = g.surface
    val locale = LocalLocale.current.platformLocale

    // WHAT TONIGHT ACTUALLY ENDS AT, which is not always the wake handle.
    //
    // With "at your alarm" on and an alarm inside the window, the night ends
    // at the alarm, and every reading on this screen has to say so - the
    // numeral, the arc, the handle, the countdown, the sentence. Shipping
    // the alarm to some of them and not others produced one screen giving
    // two answers to when tonight ends, which is what was reported, twice.
    //
    // Derived ONCE, as an instant, because the readings below need different
    // things from it - the numeral and the arc want a clock time, the countdown
    // wants a duration - and computing it twice is how the two halves of the
    // dial centre came to disagree in the first place.
    val endsTonight = remember(s.tick, s.start, s.end, s.days, s.endAtAlarm, s.enabled) {
        val dur = Scheduler.duration(s.start, s.end)
        val scheduled = Scheduler.liveWindowEnd(prefs, s.start, s.end, s.days)
            ?: Scheduler.nextStart(s.start, s.end, s.days)?.plus(dur)
        scheduled?.let {
            Scheduler.endAt(
                it.minus(dur), it,
                Scheduler.endingAlarm(ctx, s.endAtAlarm), s.endAtAlarm
            )
        }
    }

    // Null when nothing overrides, so the dial can tell "tonight is the
    // schedule" from "tonight is shorter" without asking again.
    val endTonight = endsTonight?.toLocalTime()?.takeIf { it != s.end }

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
                        ) { haptics.open(); s.picking = "start" }
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
                        // s.tick, so a change to the phone's 12/24 setting
                        // reaches the numerals. Nothing else here would move.
                        remember(s.tick, s.start) { Clock.reading(ctx, s.start) },
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
                        ) { haptics.open(); s.picking = "end" },
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
                    // Tonight's end, not the setting behind it. "I see 8:30 in
                    // the top right, how can this be correct" - it could not:
                    // every other reading on the screen said 7:30.
                    WindowTime(
                        remember(s.tick, endTonight, s.end) {
                            Clock.reading(ctx, endTonight ?: s.end)
                        },
                        color = g.onSurfaceMid
                    )

                }
            }
        }

        // Everything the centre could truthfully say right now, most useful
        // first. Tapping it walks the list; states with only one true thing
        // to say are not tappable and show no dots.
        val readings = remember(s.tick, s.enabled, runningNow, s.start, s.end, s.days, s.endAtAlarm) {
            // endTonight here too. This is the "sleep window" reading, and it
            // read 13h 30m - the full schedule - while the numeral above it read
            // the alarm's 7:30. Every number that describes tonight takes the
            // same end; the list of them is longer than it looks.
            val shownEnd = endTonight ?: s.end
            val secs = ((shownEnd.toSecondOfDay() - s.start.toSecondOfDay() + 86400) % 86400)
            val total = span(res, secs / 60L) to res.getString(R.string.dial_sleep_window)
            buildList {
                if (runningNow) {
                    // The same instant the numeral and the arc are drawn from.
                    // Both halves of this reading had been wrong together - the
                    // countdown ran to the wake handle and the caption named it
                    // - so they agreed with each other and with nothing else on
                    // the screen.
                    val left = endsTonight
                        ?.let { Duration.between(LocalDateTime.now(), it).toMinutes() }
                        ?: 0L
                    val endHour = endsTonight ?: LocalDateTime.now().with(s.end)
                    add(
                        span(res, left) to
                            res.getString(
                                R.string.dial_until,
                                hhmm(ctx, endHour.hour, endHour.minute)
                            )
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
                    if (s.enabled) Scheduler.nextStart(s.start, s.end, s.days)?.let { n ->
                        add(
                            span(res, Duration.between(LocalDateTime.now(), n).toMinutes())
                                to res.getString(R.string.dial_until_bedtime)
                        )
                    }
                }
            }
        }
        val centreIndex = s.centreMode.coerceIn(0, readings.size - 1)

        BedtimeDial(
            modifier = Modifier.layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                val trim = DIAL_TRIM.roundToPx()
                layout(placeable.width, placeable.height - trim * 2) {
                    placeable.place(0, -trim)
                }
            },
            scrollInProgress = { scroll.isScrollInProgress },
            start = s.start, end = s.end, endTonight = endTonight, now = now,
            running = runningNow,
            track = card, enabled = s.enabled,
            centreValue = readings[centreIndex].first,
            centreLabel = readings[centreIndex].second,
            centreIndex = centreIndex, centreCount = readings.size,
            onCentreCycle = { dir ->
                haptics.select()
                val n = readings.size
                s.centreMode = ((centreIndex + dir) % n + n) % n
            },
            onStartChange = { s.start = it },
            onEndChange = { s.end = it },
            onDragFinished = { endMoved -> if (endMoved) s.commitWake() else s.commit() }
        )

        // The window in words, under the dial. The dial says this
        // spatially and the centre as a duration; neither answers which
        // morning. See windowSentence.
        windowSentence(ctx, prefs, s.start, s.end, s.days)?.let { line ->
            Spacer(Modifier.height(TIGHT))
            Text(
                line,
                // BALANCED, so the last line is not one orphaned word. Centred
                // text wrapping greedily puts as much as it can on line one and
                // the remainder on line two, which at a large system font left
                // «С 11:00 PM сегодня до 8:30 AM» over a lone «завтра». Balanced
                // shares the words out instead - the same thing CSS calls
                // text-wrap: balance. It costs nothing when the sentence fits on
                // one line, which is the usual case.
                style = MaterialTheme.typography.bodyLarge.copy(
                    lineBreak = LineBreak.Paragraph.copy(
                        strategy = LineBreak.Strategy.Balanced
                    )
                ),
                color = g.onSurfaceLow,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Whether the morning alarm may end the night early.
 *
 * AOSP's own schedule rules carry this as `exitAtAlarm` and Settings calls it
 * "Alarm can override end time"; Google's Bedtime mode calls it "Turn off at
 * next alarm". Both apply it only when the alarm falls INSIDE the window, and
 * so does this - see [Scheduler.endAt].
 *
 * THE SECTION ONLY EXISTS WHEN AN ALARM DOES. With none set there is nothing
 * for the night to end at, so a heading called "when it ends" has no subject and
 * the switch under it no object; what it drew instead was the wake time, which
 * the dial, the countdown, the span sentence and the app bar are all already
 * showing, phrased - "No alarm; ends 8:30 AM" - as though something were wrong.
 * Reported from the phone as an inconsistent state, and it was.
 *
 * That the switch is a STANDING preference is true and does not save it:
 * `BedtimeReceiver` returns early on NEXT_ALARM_CLOCK_CHANGED unless it is set,
 * so leaving it on really does arm the app for an alarm that appears later. But
 * it costs nothing to leave on, changes nothing while no alarm exists, and the
 * section comes back by itself the moment one does - through that same
 * broadcast. So there is nothing to reach it FOR in the meantime, and the same
 * rule the screen-effects section already follows applies: a control that cannot
 * act is worse than an absent one.
 *
 * The subtitle names the fallback hour rather than describing the rule in the
 * abstract, and appears only while the switch is ON, because the sentence it
 * carries is a claim about tonight.
 */
@Composable
private fun EndsSection(s: HomeState) {
    val ctx = LocalContext.current
    val card = gloam.raise
    val res = LocalResources.current

    val alarm = remember(s.tick) { Scheduler.nextAlarm(ctx) }
    // No alarm, no section: nothing for the night to end AT, so a heading called
    // "when it ends" has no subject and the switch under it no object.
    val alarmAt = alarm?.let { hhmm(ctx, it.hour, it.minute) } ?: return

    // The HEADING carries the purpose - "end bedtime at your alarm" - because a
    // heading that only categorises left the section looking like a stray switch
    // beside a time. "When it ends" was worse than vague, it was wrong: with the
    // switch off the night ends at the wake handle, not the alarm, so the
    // heading asked a question the section answered incorrectly half the time.
    Section(stringResource(R.string.section_end_at_alarm)) {
        GroupedList(card, listOf {
            SwitchRow(
                // With the purpose in the heading, the row is the hour and
                // nothing else - anything more repeated the line above it.
                headline = alarmAt,
                // Which leaves TalkBack with "7:30 AM, switch, off" and no idea
                // what it switches: a heading is not read as part of the row.
                // So the row SPEAKS the whole sentence even though it shows one
                // hour. If state is visible it must be in the semantics, and
                // this is the same rule from the other side.
                modifier = Modifier.semantics {
                    contentDescription =
                        res.getString(R.string.row_end_at_alarm, alarmAt)
                },
                checked = s.endAtAlarm,
                leading = { RowIcon(R.drawable.ic_alarm, IconTint.Alarm) }
            ) { s.followAlarm(it) }
        })
    }
}

/**
 * Which MORNINGS the window ends on, plus the presets. Always present - hiding
 * it while running read as a bug - and it may be emptied, which is the one-off.
 */
@Composable
private fun DaysSection(s: HomeState) {
    val g = gloam
    val haptics = s.haptics

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
        val crossesMidnight = s.end.toSecondOfDay() <= s.start.toSecondOfDay()
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
        val repeats = s.days.isNotEmpty()
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
                        if (on) s.days = savedDays.ifEmpty { everyNight }
                        else { savedDays = s.days; s.days = emptySet() }
                        s.commit()
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
                        selected = s.days == set,
                        onClick = { haptics.select(); s.days = set; s.commit() },
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
                        // M3's default is 16dp either side. At font scale 1.3
                        // "Weekends" wants 81.5px of the 81 a third of the row
                        // leaves - a 0.6% shortfall, and because the overflow is
                        // Clip rather than Ellipsis, Compose drops the whole last
                        // GLYPH that will not fit. Half a pixel costs the entire
                        // "s". Eight either side gives the label back more than
                        // it was short, and costs nothing at any size: the
                        // segments are 104dp wide and the text is centred.
                        contentPadding = PaddingValues(horizontal = 8.dp),
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
                            // AUTO-SIZED, WITH A FLOOR. At font scale 1.3 this
                            // row truncated in English and Russian - "Weekdays"
                            // read "Weekday", "Weekends" read "Weekend",
                            // «Выходные» read «Выходн» - and there is no
                            // ellipsis, so the cut was INVISIBLE: the English
                            // ones are still valid words meaning something else.
                            // WCAG 1.4.4 asks for 200% with no loss of content
                            // and this lost it at 130%. `RowFitTest` pins it now.
                            //
                            // Wrapping would have been the better answer and is
                            // not available: SegmentedButton is an extension on
                            // SingleChoiceSegmentedButtonRowScope and itemShape
                            // computes start/middle/end corners for ONE row, so
                            // a FlowRow can carry neither the receiver nor the
                            // shapes.
                            //
                            // The floor is in DP, not sp, and that is the whole
                            // point: it means "never smaller than a default user
                            // sees". The label may give back the growth the font
                            // scale gave it, never more - shrinking text below
                            // default to answer a request for larger text would
                            // trade one accessibility failure for another.
                            val floor = with(LocalDensity.current) { 14.dp.toSp() }
                            BasicText(
                                label,
                                style = MaterialTheme.typography.labelLarge
                                    .copy(color = LocalContentColor.current),
                                maxLines = 1,
                                autoSize = TextAutoSize.StepBased(
                                    minFontSize = floor,
                                    maxFontSize = MaterialTheme.typography.labelLarge.fontSize
                                )
                            )
                        }
                    )
                }
            }
            DayRow(s.days) { d ->
                haptics.toggle(d !in s.days)
                s.days = if (d in s.days) s.days - d else s.days + d
                s.commit()
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
}

/**
 * Do Not Disturb and what it lets through, in one card: the switch, and
 * beneath it what the switch governs.
 */
@Composable
private fun WakeSection(s: HomeState, runningNow: Boolean, onOpenInterruptions: () -> Unit) {
    val ctx = LocalContext.current
    val res = LocalResources.current
    val g = gloam
    val haptics = s.haptics
    val prefs = s.prefs
    val card = g.raise
    val loc = LocalLocale.current.platformLocale

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
            NoticeStrip(planNote(ctx, s.enabled, s.start, s.end, s.days, loc))
        }
        add {
            SwitchRow(
                headline = stringResource(R.string.dnd_title),
                checked = s.fxDnd,
                // Every other row in the app carries a leading icon, so
                // these two started their text 40dp from the screen edge
                // where the card directly below started at 80 - a ragged
                // left edge between two cards on one screen.
                leading = { RowIcon(R.drawable.ic_dnd, IconTint.Dnd) },
                onCheckedChange = { s.fxDnd = it; haptics.toggle(it); s.commit() },
                modifier = Modifier.fillMaxWidth()
            )
        }
        add {
            val gets = remember(s.tick, s.fxDnd) {
                Interruptions.shortSummary(res, Interruptions.allowed(res, prefs, short = true))
            }
            // The title matches the screen it opens: a link and its
            // destination naming themselves differently is how you
            // doubt you arrived.
            LinkRow(
                headline = stringResource(R.string.what_is_allowed),
                supporting =
                    if (s.fxDnd) gets else stringResource(R.string.dnd_nothing_silenced),
                leading = { RowIcon(R.drawable.ic_allowlist, IconTint.Allowed) },
                onClick = { haptics.open(); onOpenInterruptions() },
                modifier = Modifier
                    .alpha(if (s.fxDnd) 1f else 0.45f)
                    .fillMaxWidth(),
                enabled = s.fxDnd
            )
        }
        // The readout is its own item. It reports on the two rows above
        // rather than being one of them, and a grouped list has no way
        // to say that - so it ends the block, which is where evidence
        // belongs.
        if (runningNow) add {
            val filter = remember(s.tick) { ZenController.currentFilter(ctx) }
            val ignored = s.fxDnd &&
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
}

/**
 * What the screen does: grayscale, wallpaper dimming, dark theme, and - only
 * where something the app can reach actually moves it - always-on display.
 */
@Composable
private fun ScreenEffectsSection(s: HomeState, runningNow: Boolean) {
    val ctx = LocalContext.current
    val g = gloam
    val haptics = s.haptics
    val card = g.raise
    val loc = LocalLocale.current.platformLocale
    // Three states, not two. Either the zen effect works, or we can write the
    // vendor's own always-on keys, or nothing the app can reach moves that
    // display at all - and then the row is not drawn, because a control that
    // neither toggles anything nor opens the right screen is only noise. Keyed
    // on tick so an adb grant is picked up on the next resume.
    //
    // `zenEffects` is the fourth state this grew: a phone that stores the rule's
    // device effects and applies NONE of them. Grayscale, dimming and the dark
    // theme have no other route - measured, every readback and every writable
    // key checked - so those three rows go, because a switch that lies is worse
    // than an absent one. The always-on row survives on its own, because the
    // vendor route reaches it even there.
    val zenEffects = remember(s.tick) { ScreenEffects.applied(ctx) }
    val ambientZen = remember(s.tick) { zenEffects && AmbientCapability.isSupported(ctx) }
    val ambientGrant = remember(s.tick) { AmbientControl.needsGrant(ctx) }
    val ambientRow = remember(s.tick) {
        ambientZen || AmbientControl.canControl(ctx) || ambientGrant
    }
    if (!zenEffects && !ambientRow) return

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
                NoticeStrip(planNote(ctx, s.enabled, s.start, s.end, s.days, loc))
            }
            if (zenEffects) add {
                EffectRow(
                    Fx.Grayscale, stringResource(R.string.fx_grayscale),
                    stringResource(R.string.fx_grayscale_sub), s.fxGray
                ) { s.fxGray = !s.fxGray; haptics.toggle(s.fxGray); s.commit() }
            }
            if (zenEffects) add {
                EffectRow(
                    Fx.Dim, stringResource(R.string.fx_dim),
                    stringResource(R.string.fx_dim_sub), s.fxDim
                ) { s.fxDim = !s.fxDim; haptics.toggle(s.fxDim); s.commit() }
            }
            if (zenEffects) add {
                // The one subtitle that is load-bearing rather than
                // descriptive: the platform defers the theme change
                // until the screen goes off, so tapping this while
                // watching it does nothing and looks broken without it.
                EffectRow(
                    Fx.Dark, stringResource(R.string.fx_dark),
                    stringResource(R.string.fx_dark_sub), s.fxDark
                ) { s.fxDark = !s.fxDark; haptics.toggle(s.fxDark); s.commit() }
            }
            // No divider to remove with it: a grouped list just has one
            // item fewer, and the corners re-form around what is left.
            if (ambientRow) add {
                EffectRow(
                    Fx.Ambient, stringResource(R.string.fx_ambient),
                    // The subtitle carries the ask where the permission is
                    // missing, so the row explains itself rather than failing
                    // silently when tapped.
                    if (ambientGrant) stringResource(R.string.fx_ambient_grant)
                    else stringResource(R.string.fx_ambient_sub),
                    s.fxAmbient && !ambientGrant
                ) {
                    if (ambientGrant) {
                        // Nothing to toggle yet - it cannot be honoured. Send
                        // them to the one screen that can change that.
                        haptics.open(); AmbientControl.requestGrant(ctx)
                    } else {
                        s.fxAmbient = !s.fxAmbient; haptics.toggle(s.fxAmbient); s.commit()
                    }
                }
            }
        })
    }
}

/**
 * The dialog behind either window numeral. It wears the handle being edited -
 * night for bedtime, dawn for wake - because it has no title, so colour is what
 * tells you which of the two adjacent numbers you tapped.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(s: HomeState) {
    val ctx = LocalContext.current
    val g = gloam
    val haptics = s.haptics

    if (s.picking != null) {
        val init = if (s.picking == "start") s.start else s.end
        val state = rememberTimePickerState(init.hour, init.minute, Clock.is24Hour(ctx))
        // The picker wears the handle you are editing. The dialog has no title,
        // so colour is what tells you which of the two numbers you tapped.
        val night = s.picking == "start"
        val accent = if (night) Arc.nightOn(g.dark) else Arc.dawn
        // the same two inks the dial paints inside its own handles
        val onAccent = if (night) Color(0xFFEEF1F5) else Color(0xFF402310)
        AlertDialog(
            onDismissRequest = { s.picking = null },
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
                        val wake = s.picking != "start"
                        if (wake) s.end = t else s.start = t
                        s.picking = null
                        if (wake) s.commitWake() else s.commit()
                    },
                    shape = CircleShape
                ) { Text(stringResource(R.string.action_set)) }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { s.picking = null },
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

/**
 * The app bar: the name, the state line beneath it, the master switch and the
 * way into Settings.
 *
 * The master control lives HERE, not in a card in the flow. It governs
 * everything on the screen and the screen is 1.9 viewports tall - measured - so
 * for about half the scroll range the one control that turns it all off was
 * somewhere above.
 *
 * PINNED, and two lines, after trying MediumTopAppBar with exitUntilCollapsed.
 * That version collapsed to 64dp when scrolled, which sounded like the thrifty
 * choice and was not: a medium bar puts its actions on the top row and its title
 * on the bottom one, so at rest it stood 136dp tall with a void above the words -
 * 60dp MORE than this, in the state the screen is in when you open it. It bought
 * that back only while scrolled, 12dp, and paid for it by dropping the status
 * line at the halfway point of the collapse. Both were reported, and they were
 * the same mistake seen from two sides.
 *
 * So: one row of content, always the same, always there. 76dp rather than the
 * small bar's 64 because the title is two lines - the name and the state - which
 * is the subtitle-shaped hole MediumFlexibleTopAppBar would fill if it were
 * public in material3 1.4.0.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeBar(
    s: HomeState,
    runningNow: Boolean,
    ready: Boolean,
    bar: TopAppBarScrollBehavior,
    onOpenSettings: () -> Unit
) {
    val ctx = LocalContext.current
    val res = LocalResources.current
    val g = gloam
    val haptics = s.haptics
    val prefs = s.prefs
    val card = g.raise

    val status = remember(s.tick, s.enabled, runningNow, s.start, s.end, s.days) {
        // Said in Sentences, because the Quick Settings tile has to say exactly
        // the same three things with no Compose around it.
        statusLine(
            ctx, res, s.enabled, prefs.activeDay, s.start, s.end, s.days,
            alarm = Scheduler.endingAlarm(ctx, s.endAtAlarm),
            exitAtAlarm = s.endAtAlarm,
            // The bar already knows - it is what disables the switch beside
            // this line - and saying it here stops the two disagreeing.
            ready = ready
        )
    }

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
                    if (Scheduler.isOneOff(s.days)) {
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
                checked = s.enabled, enabled = ready,
                onCheckedChange = { s.setBedtime(it) },
                // The one switch in the app that has three things
                // to say, and the pair says them in the right
                // order: an hourglass while a window is only
                // SCHEDULED, a tick once it is in effect. It used
                // to be the other way about - a tick sat on the
                // thumb all evening while bedtime was doing
                // nothing, which is what a tick should never mean.
                icon = if (runningNow) R.drawable.ic_check
                       else R.drawable.ic_hourglass,
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