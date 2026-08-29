package com.jemcik.gloaming.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import android.app.NotificationManager
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
import com.jemcik.gloaming.core.ZenController
import java.time.LocalTime

private val ROW_CORNER = 28.dp
private val AVATAR = 44.dp

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
private fun WhoIcon(kind: Who, tint: Color) {
    Icon(
        painter = painterResource(
            when (kind) {
                Who.Call -> R.drawable.ic_call
                Who.Msg -> R.drawable.ic_message
                Who.Conv -> R.drawable.ic_forum
                Who.Repeat -> R.drawable.ic_repeat
                Who.Bell -> R.drawable.ic_reminder
                Who.Cal -> R.drawable.ic_event
                Who.Media -> R.drawable.ic_media
                Who.Alarm -> R.drawable.ic_alarm
            }
        ),
        contentDescription = null,
        tint = tint,
        modifier = Modifier.size(22.dp)
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

    Column(
        Modifier
            .fillMaxSize()
            .background(g.surface)
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(horizontal = 24.dp)
            .padding(top = 8.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        BackRow(onBack)

        Text(
            stringResource(R.string.what_gets_through),
            style = MaterialTheme.typography.titleLarge, color = g.onSurface
        )

        Text(
            stringResource(
                R.string.allow_lead,
                Interruptions.sentence(res, allowed),
                Clock.hhmm(ctx, wake)
            ),
            style = MaterialTheme.typography.bodyLarge, color = g.onSurfaceLow
        )

        Spacer(Modifier.height(8.dp))
        SectionLabel(stringResource(R.string.section_people))

        AllowRow(Who.Call, stringResource(R.string.row_calls), Interruptions.peopleLabel(res, calls)) {
            haptics.open(); editing = "calls"
        }
        AllowRow(Who.Msg, stringResource(R.string.row_messages), Interruptions.peopleLabel(res, messages)) {
            haptics.open(); editing = "messages"
        }
        AllowRow(
            Who.Conv, stringResource(R.string.row_conversations),
            Interruptions.convLabel(res, conversations)
        ) {
            haptics.open(); editing = "conv"
        }
        AllowRow(
            Who.Repeat, stringResource(R.string.row_repeat_callers),
            stringResource(
                if (repeatCallers) R.string.row_repeat_callers_on else R.string.blocked_tonight
            ),
            checked = repeatCallers,
            onCheckedChange = { haptics.toggle(it); repeatCallers = it; save() }
        )

        Spacer(Modifier.height(18.dp))
        SectionLabel(stringResource(R.string.section_everything_else))

        AllowRow(
            Who.Bell, stringResource(R.string.row_reminders),
            stringResource(if (reminders) R.string.allowed_tonight else R.string.blocked_tonight),
            checked = reminders,
            onCheckedChange = { haptics.toggle(it); reminders = it; save() }
        )
        AllowRow(
            Who.Cal, stringResource(R.string.row_events),
            stringResource(if (events) R.string.allowed_tonight else R.string.blocked_tonight),
            checked = events,
            onCheckedChange = { haptics.toggle(it); events = it; save() }
        )
        AllowRow(
            Who.Media, stringResource(R.string.row_media),
            stringResource(if (media) R.string.row_media_on else R.string.blocked_tonight),
            checked = media,
            onCheckedChange = { haptics.toggle(it); media = it; save() }
        )
        // Android will silence alarms if asked. We never ask: an app that can
        // mute your morning alarm is a footgun, and the old copy blamed the
        // platform for a decision that was ours.
        AllowRow(
            Who.Alarm, stringResource(R.string.row_alarms),
            stringResource(R.string.row_alarms_why),
            locked = true
        )

        Spacer(Modifier.height(18.dp))
        SectionLabel(stringResource(R.string.section_right_now))
        // Asked of the system on arrival and on every return, rather than
        // reported from what we last set: this app exists because the platform
        // does not always do what it is asked.
        val filter = remember(tick) { ZenController.currentFilter(ctx) }
        Text(
            when (filter) {
                NotificationManager.INTERRUPTION_FILTER_ALL -> R.string.filter_all
                NotificationManager.INTERRUPTION_FILTER_PRIORITY -> R.string.filter_priority
                NotificationManager.INTERRUPTION_FILTER_ALARMS -> R.string.filter_alarms
                NotificationManager.INTERRUPTION_FILTER_NONE -> R.string.filter_none
                else -> R.string.filter_unknown
            }.let { stringResource(it) },
            style = MaterialTheme.typography.bodyLarge, color = g.onSurfaceLow
        )
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

@Composable
private fun PeopleSheet(title: String, selected: Int, onPick: (Int) -> Unit) {
    ChoiceSheet(
        title = title,
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

@Composable
private fun AllowRow(
    kind: Who,
    title: String,
    subtitle: String,
    locked: Boolean = false,
    // A switch row takes checked/onCheckedChange rather than a trailing slot:
    // the whole row is the target and the Switch is only the indicator, which
    // is both what people expect and one semantics node instead of two, so
    // TalkBack says "Reminders, switch, on" once.
    checked: Boolean? = null,
    onCheckedChange: ((Boolean) -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    val g = gloam
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ROW_CORNER))
            .background(g.raise)
            .then(
                when {
                    checked != null && onCheckedChange != null -> Modifier.toggleable(
                        value = checked,
                        role = Role.Switch,
                        onValueChange = onCheckedChange
                    )
                    onClick != null -> Modifier.clickable(onClick = onClick)
                    else -> Modifier
                }
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(AVATAR)
                .clip(CircleShape)
                .background(if (locked) g.veil else g.selectFill),
            contentAlignment = Alignment.Center
        ) {
            WhoIcon(kind, if (locked) g.onSurfaceLow else g.onSelect)
        }
        Spacer(Modifier.width(14.dp))
        // weight(1f) so a long subtitle wraps instead of running under the switch
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = g.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = g.onSurfaceLow)
        }
        // onCheckedChange = null: the row already handles the input, and a
        // Switch that also handled it would swallow taps meant for the row.
        if (checked != null) Switch(checked = checked, onCheckedChange = null)
        else if (onClick != null) Text(
            // A glyph, not a title: it borrows titleLarge for its SIZE, because
            // there is no chevron drawable and at label size it disappears.
            "›", style = MaterialTheme.typography.titleLarge, color = g.onSurfaceLow
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChoiceSheet(
    title: String,
    options: List<Pair<String, Int>>,
    selected: Int,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val g = gloam
    // every sheet picks through here, so the feedback lives here too
    val haptics = rememberHaptics()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = g.raise,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
    ) {
        Column(
            Modifier.padding(horizontal = 24.dp).padding(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = g.onSurface)
            Spacer(Modifier.height(8.dp))
            options.forEach { (label, value) ->
                val on = value == selected
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(ROW_CORNER))
                        // the chosen option is filled, like every other
                        // selection in the app. It used to differ only by a
                        // hue shift at 14sp, which is no distinction at all.
                        .background(if (on) g.selectFill else Color.Transparent)
                        .clickable { haptics.select(); onPick(value) }
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (on) g.onSelect else g.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    if (on) Text(
                        "✓",
                        style = MaterialTheme.typography.titleMedium,
                        color = g.onSelect
                    )
                }
            }
        }
    }
}
