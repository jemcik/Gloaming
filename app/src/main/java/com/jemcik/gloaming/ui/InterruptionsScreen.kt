package com.jemcik.gloaming.ui

import android.content.Intent
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.jemcik.gloaming.R
import com.jemcik.gloaming.core.Clock
import com.jemcik.gloaming.core.Interruptions
import com.jemcik.gloaming.core.Prefs
import java.time.LocalTime


/**
 * Underneath this is a DND policy editor - four enum fields and a couple of
 * bitmasks. The default rendering of that is a settings page with five section
 * headers. This asks the honest question instead: what gets through.
 *
 * Grouped people-then-everything-else, which is how both Android's own DND
 * screen and iOS Focus split it, and with repeat callers inside People, where
 * both platforms put it.
 *
 * The design shows individual starred contacts by name. That needs
 * READ_CONTACTS, and it would not change the model - the scope is global, not
 * per person. Kept permission-free until that trade is worth making.
 */
private enum class Who { Call, Msg, Conv, Repeat, Bell, Cal, Media, Alarm }

/*
 * Google's own geometry, fetched from google/material-design-icons and checked
 * in as vector drawables. Hand-drawing these from arcs and capsules produced a
 * handset that read as a letter C, then a horseshoe, then a limp hook - the
 * shapes are the work, and they are already drawn.
 */
@Composable
private fun WhoIcon(kind: Who) {
    RowAvatar(
        id = when (kind) {
            Who.Call -> R.drawable.ic_call
            Who.Msg -> R.drawable.ic_message
            Who.Conv -> R.drawable.ic_forum
            Who.Repeat -> R.drawable.ic_repeat
            Who.Bell -> R.drawable.ic_reminder
            Who.Cal -> R.drawable.ic_event
            Who.Media -> R.drawable.ic_media
            Who.Alarm -> R.drawable.ic_alarm
        },
        tint = when (kind) {
            Who.Call -> IconTint.Call
            Who.Msg -> IconTint.Msg
            Who.Conv -> IconTint.Conv
            Who.Repeat -> IconTint.Repeat
            Who.Bell -> IconTint.Bell
            Who.Cal -> IconTint.Cal
            Who.Media -> IconTint.Media
            Who.Alarm -> IconTint.Alarm
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InterruptionsScreen(onBack: () -> Unit, onChanged: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    val g = gloam
    val haptics = rememberHaptics()

    var calls by remember { mutableIntStateOf(prefs.allowCalls) }
    var messages by remember { mutableIntStateOf(prefs.allowMessages) }
    var conversations by remember { mutableIntStateOf(prefs.allowConversations) }
    var repeatCallers by remember { mutableStateOf(prefs.allowRepeatCallers) }
    var reminders by remember { mutableStateOf(prefs.allowReminders) }
    var events by remember { mutableStateOf(prefs.allowEvents) }
    var media by remember { mutableStateOf(prefs.allowMedia) }
    var editing by remember { mutableStateOf<String?>(null) }

    // Each rule rewrite costs a visible blink of Do Not Disturb, so flipping six
    // switches should cost one rewrite and not six. The preference is written on
    // every tap; the rule is pushed once, on the way out.
    var dirty by remember { mutableStateOf(false) }
    fun flush() {
        if (dirty) { dirty = false; onChanged() }
    }

    // The readout at the foot of the screen is a live reading, not a belief, so
    // it has to be re-taken when we come back - Do Not Disturb can be switched
    // from the quick settings tile while this screen sits in the background.
    var tick by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_RESUME -> tick++
                // Leaving the app counts as leaving the screen: the change has
                // to reach the rule without waiting for a trip back to Home.
                Lifecycle.Event.ON_PAUSE -> flush()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            flush()
        }
    }

    fun save() {
        prefs.allowCalls = calls; prefs.allowMessages = messages
        prefs.allowConversations = conversations
        prefs.allowRepeatCallers = repeatCallers
        prefs.allowReminders = reminders; prefs.allowEvents = events
        prefs.allowMedia = media
        dirty = true
    }

    val wake: LocalTime = prefs.endTime
    val res = ctx.resources
    val allowed = Interruptions.allowed(
        res, calls, messages, conversations, repeatCallers, reminders, events, media
    )

    // A real top app bar, so Back stays put. It used to be a row INSIDE the
    // scrolling column, which meant the only visible way out left the screen as
    // soon as you scrolled - and this screen is nine rows long. The title moves
    // up with it, which is where M3 puts a detail screen's title anyway.
    //
    // No scrollBehavior on purpose. The bar is one constant colour, for the same
    // reason Home's is: M3 defaults to transparent at rest and `raise` once
    // anything scrolls under, which is a step change that reads as a blink. A
    // scroll behaviour here would have nothing left to drive.
    Scaffold(
        containerColor = g.surface,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.what_is_allowed),
                        style = MaterialTheme.typography.titleLarge, color = g.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painterResource(R.drawable.ic_back),
                            contentDescription = stringResource(R.string.action_back),
                            tint = g.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = g.raise,
                    scrolledContainerColor = g.raise,
                    titleContentColor = g.onSurface,
                    navigationIconContentColor = g.onSurface
                )
            )
        }
    ) { inner ->
    Column(
        Modifier
            .fillMaxSize()
            .padding(inner)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = SCREEN_PAD)
            .padding(top = 8.dp, bottom = 40.dp),
        // The same arrangement as Home's column, so Section gets the same gap
        // above its rule on both screens.
        verticalArrangement = Arrangement.spacedBy(GROUP)
    ) {
        Text(
            stringResource(
                R.string.allow_lead,
                Interruptions.sentence(res, allowed),
                Clock.hhmm(ctx, wake)
            ),
            style = MaterialTheme.typography.bodyLarge, color = g.onSurfaceLow
        )

        Section(stringResource(R.string.section_people)) {
        GroupedList(gloam.raise, listOf(
            {
                AllowRow(
                    Who.Call, stringResource(R.string.row_calls),
                    Interruptions.peopleLabel(res, calls)
                ) { haptics.open(); editing = "calls" }
            },
            {
                AllowRow(
                    Who.Msg, stringResource(R.string.row_messages),
                    Interruptions.peopleLabel(res, messages)
                ) { haptics.open(); editing = "messages" }
            },
            {
                AllowRow(
                    Who.Conv, stringResource(R.string.row_conversations),
                    Interruptions.convLabel(res, conversations)
                ) { haptics.open(); editing = "conv" }
            },
            {
                AllowRow(
                    Who.Repeat, stringResource(R.string.row_repeat_callers),
                    stringResource(
                        if (repeatCallers) R.string.row_repeat_callers_on
                        else R.string.state_blocked
                    ),
                    checked = repeatCallers,
                    onCheckedChange = { haptics.toggle(it); repeatCallers = it; save() }
                )
            }
        ))

        }

        Section(stringResource(R.string.section_everything_else)) {
        GroupedList(gloam.raise, listOf(
            {
                AllowRow(
                    Who.Bell, stringResource(R.string.row_reminders),
                    stringResource(if (reminders) R.string.state_allowed else R.string.state_blocked),
                    checked = reminders,
                    onCheckedChange = { haptics.toggle(it); reminders = it; save() }
                )
            },
            {
                AllowRow(
                    Who.Cal, stringResource(R.string.row_events),
                    stringResource(if (events) R.string.state_allowed else R.string.state_blocked),
                    checked = events,
                    onCheckedChange = { haptics.toggle(it); events = it; save() }
                )
            },
            {
                AllowRow(
                    Who.Media, stringResource(R.string.row_media),
                    stringResource(if (media) R.string.row_media_on else R.string.state_blocked),
                    checked = media,
                    onCheckedChange = { haptics.toggle(it); media = it; save() }
                )
            },
            {
            // Android will silence alarms if asked. We never ask: an app that
            // can mute your morning alarm is a footgun, and the old copy blamed
            // the platform for a decision that was ours.
            // No checked and no onClick, so this falls to StaticRow: the row
            // that states something and offers nothing to press.
            AllowRow(
                Who.Alarm, stringResource(R.string.row_alarms),
                stringResource(R.string.row_alarms_why)
            )
            }
        ))
        }
    }
    }

    when (editing) {
        "calls" -> PeopleSheet(stringResource(R.string.sheet_who_can_call), calls) {
            calls = it; save(); editing = null
        }
        "messages" -> PeopleSheet(stringResource(R.string.sheet_who_can_message), messages) {
            messages = it; save(); editing = null
        }
        "conv" -> ChoiceSheet(
            title = stringResource(R.string.row_conversations),
            why = stringResource(R.string.conv_sheet_why),
            options = listOf(
                stringResource(R.string.conv_sheet_blocked) to Interruptions.CONV_NONE,
                stringResource(R.string.conv_sheet_priority) to Interruptions.CONV_IMPORTANT,
                stringResource(R.string.conv_sheet_all) to Interruptions.CONV_ANYONE
            ),
            selected = conversations,
            onPick = { conversations = it; save(); editing = null },
            onDismiss = { editing = null }
        )
    }
}

/**
 * The contacts app's own starred screen. Launching an activity needs no
 * permission, which is the whole point: this app does not read contacts, and
 * the system's list is authoritative and always current where a copy of ours
 * would go stale the moment a star was added elsewhere.
 *
 * ContactsContract.Intents.UI.LIST_STARRED_ACTION by another name - the constant
 * is deprecated, the action is still handled. Resolved rather than assumed, and
 * the row is not drawn where it does not resolve: a door that opens onto nothing
 * is the always-on row's mistake.
 */
private const val LIST_STARRED = "com.android.contacts.action.LIST_STARRED"

@Composable
private fun StarredLink() {
    val ctx = LocalContext.current
    val haptics = rememberHaptics()
    val intent = remember { Intent(LIST_STARRED) }
    val resolves = remember {
        ctx.packageManager.resolveActivity(intent, 0) != null
    }
    if (!resolves) return
    LinkRow(
        headline = stringResource(R.string.action_starred_contacts),
        leading = {
            Icon(
                painterResource(R.drawable.ic_star),
                contentDescription = null,
                tint = LocalContentColor.current,
                modifier = Modifier.size(24.dp)
            )
        },
        onClick = { haptics.open(); runCatching { ctx.startActivity(intent) } }
    )
}

@Composable
private fun PeopleSheet(title: String, selected: Int, onPick: (Int) -> Unit) {
    ChoiceSheet(
        title = title,
        why = stringResource(R.string.people_sheet_why),
        footer = { StarredLink() },
        options = listOf(
            stringResource(R.string.people_none) to Interruptions.PEOPLE_NONE,
            stringResource(R.string.people_starred) to Interruptions.PEOPLE_STARRED,
            stringResource(R.string.people_contacts) to Interruptions.PEOPLE_CONTACTS,
            stringResource(R.string.people_anyone) to Interruptions.PEOPLE_ANYONE
        ),
        selected = selected,
        onPick = onPick,
        onDismiss = { onPick(selected) }
    )
}

/**
 * One row of a group. The card is the group's, not the row's, so this only
 * fills the width - everything else is the shared [SwitchRow] / [LinkRow] /
 * [StaticRow] the rest of the app uses.
 */
@Composable
private fun AllowRow(
    kind: Who,
    title: String,
    subtitle: String,
    checked: Boolean? = null,
    onCheckedChange: ((Boolean) -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    val card = Modifier.fillMaxWidth()
    val avatar: @Composable () -> Unit = { WhoIcon(kind) }
    when {
        checked != null && onCheckedChange != null -> SwitchRow(
            headline = title, supporting = subtitle,
            checked = checked, onCheckedChange = onCheckedChange,
            modifier = card, leading = avatar
        )
        onClick != null -> LinkRow(
            headline = title, supporting = subtitle,
            onClick = onClick, modifier = card, leading = avatar
        )
        else -> StaticRow(
            headline = title, supporting = subtitle,
            modifier = card, leading = avatar
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChoiceSheet(
    title: String,
    options: List<Pair<String, Int>>,
    selected: Int,
    // Shown under the title when the thing being chosen needs explaining at all.
    // Only "Conversations" does: it is an Android 11 notion that Android itself
    // barely explains, and a decade of using the phone does not teach it.
    // Deliberately here rather than as a tooltip on the row - a tooltip is a
    // long-press, and someone who does not know what a thing IS will not
    // long-press it to find out. This sheet is where the choice is made, so it
    // is where the explanation is read.
    why: String? = null,
    // After the options. The slot sits ahead of both lambdas so a trailing
    // lambda at a call site cannot bind to it and be run as content.
    footer: (@Composable () -> Unit)? = null,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val g = gloam
    // every sheet picks through here, so the feedback lives here too
    val haptics = rememberHaptics()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        // Fully expanded, not half. M3's default opens partially and lets you
        // drag up for the rest, which suits a long sheet; these are short and
        // every line is a choice. With the explanation and the starred link
        // added, the last row fell below the partial height and could not be
        // tapped at all - found by tapping it and going nowhere.
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = g.raise
        // Shape left to M3: BottomSheetDefaults.ExpandedShape is
        // CornerExtraLargeTop, 28dp. It was overridden to 32 to match this app's
        // dialogs, which is 4dp off the spec for no reason worth keeping.
    ) {
        Column(
            Modifier
                .padding(bottom = 40.dp)
                // One radio group, so TalkBack announces "2 of 4" rather than
                // reading four unrelated rows.
                .selectableGroup()
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge, color = g.onSurface,
                // 16dp, so the title starts on ListItem's own edge below it.
                modifier = Modifier.padding(horizontal = CARD_PAD).padding(bottom = 8.dp)
            )
            if (why != null) Text(
                why,
                style = MaterialTheme.typography.bodyLarge,
                color = g.onSurfaceLow,
                modifier = Modifier.padding(horizontal = CARD_PAD).padding(bottom = CARD_PAD)
            )
            options.forEach { (label, value) ->
                val on = value == selected
                RadioRow(
                    label = label,
                    selected = on,
                    onSelect = { haptics.select(); onPick(value) }
                )
            }
            footer?.invoke()
        }
    }
}
