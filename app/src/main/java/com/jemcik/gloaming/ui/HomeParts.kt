package com.jemcik.gloaming.ui

/**
 * The pieces Home is built from: its status pill, its notice strip, its day
 * row, its numerals and its two glyphs.
 *
 * Separate from HomeScreen.kt so that file holds the SCREEN - the state, the
 * layout and the order of the sections - and not also the drawing of every part
 * it is assembled from. What lives here is what only Home uses; anything shared
 * with the allowlist or Settings belongs in Rows.kt or Section.kt instead, and
 * that boundary is the reason to keep the two files apart.
 */

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jemcik.gloaming.R
import com.jemcik.gloaming.core.*
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val DAY_SIZE = 40.dp
// Where a day chip's corners land while held. Round is DAY_SIZE / 2 = 20dp.
private val DAY_PRESSED_CORNER = 12.dp

/** Four things travel together per filter state; a data class beats four whens. */
internal data class Quad(val pill: Int, val line: Int, val fill: Color, val ink: Color)

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
internal fun StatusPill(text: String, fill: Color, ink: Color) {
    val g = gloam
    // The pill wears the same border language as the control it reports on:
    // a filled pill is an "on" state and takes the selected border, a veil pill
    // is an "off" state and takes the unselected one - on the same ground the
    // unchecked switch track sits on, which is why offBorder is asked for the
    // veil variant here.
    val border = when (fill) {
        g.selectFill -> g.selectBorder
        g.veil -> g.veilOutline
        else -> g.alertBorder
    }
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = ink,
        modifier = Modifier
            .clip(CircleShape)
            .background(fill)
            .border(1.dp, border, CircleShape)
            .padding(horizontal = 12.dp, vertical = 5.dp)
    )
}

/**
 * The last element of a card whose switches are not in effect: a warm strip
 * carrying one sentence about the rows above it. Both cards on Home use it, so
 * the two cannot drift.
 */
@Composable
internal fun NoticeStrip(text: String) {
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
            // `labelLarge`, not `bodySmall`: this is a WARNING about controls
            // that look live and are not, and at Figtree 400 it read like the
            // supporting line under a row - the same weight as the text it is
            // qualifying. labelLarge is the same 14sp at weight 600, so this
            // costs no height and adds no fontWeight override outside Theme.kt,
            // which is a rule this app holds to.
            style = MaterialTheme.typography.labelLarge,
            color = g.onSelect,
            modifier = Modifier.padding(horizontal = CARD_PAD, vertical = 14.dp)
        )
    }
}




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
 *
 * TAKES THE READING, does not fetch it. It used to take (ctx, time) and call
 * Clock.reading itself, and that is a fourth way for this screen to go stale:
 * 12-or-24-hour is a SYSTEM setting, not Compose state, so when it changed
 * under an open screen none of this composable's parameters moved and Compose
 * correctly skipped it. The sentence below the dial updated - it is built
 * inside a remember(s.tick) - and the numerals above it did not, so the screen
 * read "20:40" over "From 8:40 PM". Reported exactly that way. Hoisting the
 * read to the caller puts it on the same ticker as everything else here.
 */
@Composable
internal fun WindowTime(r: Clock.Reading, color: Color) {
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




/*
 * Google's own geometry, like the allowlist rows. The `container` parameter is
 * gone with the hand-drawn crescent, which had to punch itself with the chip's
 * own fill to stay a crescent.
 */
internal enum class Fx { Grayscale, Dim, Dark, Ambient }

/**
 * One screen effect: what it is called, what it does, and its switch.
 *
 * The ROW carries the gesture and the Switch is only the indicator, the way
 * every other switch in this app works - the switch is not a second target
 * competing for the taps people aim most carefully. `checked = null` makes it a
 * link row with a chevron instead, for an effect this phone will not apply.
 */
@Composable
internal fun EffectRow(
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
internal fun RowIcon(@DrawableRes id: Int, tint: IconTint) {
    RowAvatar(id, tint
    )
}

@Composable
internal fun FxIcon(icon: Fx) {
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
internal fun DayRow(selected: Set<DayOfWeek>, onToggle: (DayOfWeek) -> Unit) {
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
                            // Filled AND rimmed when selected, a ring when not.
                            // The rim is `selectBorder`, the same edge the
                            // checked switch, the active preset and the "in
                            // effect" pill wear - one border language, so the
                            // day row and the preset row 60dp above it cannot
                            // drift apart again.
                            if (on) Modifier
                                .background(g.selectFill)
                                .border(1.5.dp, g.selectBorder, dayShape)
                            // `outline`, not `line`: this ring reports state,
                            // and in `line` it measured 1.66:1 in Dawn. It stays
                            // `outline` rather than the heavier `veilOutline` -
                            // this ring sits on the PAGE, and the day letter
                            // inside it carries the state at 8.28:1, so the ring
                            // reinforces rather than reports.
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

/**
 * A suggestion, and it must not be mistaken for a fault.
 *
 * Every other card on this screen reports something MEASURED - a permission
 * that is missing, an alarm that was eaten, a phone that was caught holding
 * one. This one cannot: the setting behind it is unreadable, so the honest
 * claim is "worth doing", never "something is wrong". Three deliberate
 * differences carry that distinction, because tone is the only thing that can:
 *
 * - The icon is NOT an [IconTint]. Those are the notice colours, and Blocked
 *   and Boot share a violet that means "the phone is interfering". Borrowing it
 *   here would say something untrue in the one language nobody reads
 *   consciously. Plain [onSurfaceLow] instead.
 * - Two TEXT buttons, not one filled one. A filled button is the app asking; a
 *   pair of text buttons is the app offering, which is what M3 gives a banner
 *   carrying a non-blocking message.
 * - A real way out. Dismiss sits first, is a full-weight choice, and is
 *   permanent - the offer never comes back, because a suggestion repeated is a
 *   demand, and because the measured card is already behind it if the phone
 *   turns out to be broken after all.
 */
@Composable
internal fun TipCard(
    title: String,
    why: String,
    dismissLabel: String,
    actionLabel: String,
    onDismiss: () -> Unit,
    onAction: () -> Unit
) {
    val g = gloam
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = CARD_PAD, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(TIGHT)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painterResource(R.drawable.ic_restart),
                contentDescription = null,
                tint = g.onSurfaceLow,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(16.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleMedium, color = g.onSurface
            )
        }
        Text(why, style = MaterialTheme.typography.bodySmall, color = g.onSurfaceLow)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onDismiss, shape = CircleShape) {
                Text(dismissLabel, color = g.onSurfaceLow)
            }
            Spacer(Modifier.width(4.dp))
            TextButton(onClick = onAction, shape = CircleShape) {
                Text(actionLabel, color = g.stateOn)
            }
        }
    }
}

/**
 * One thing the app needs, why it needs it, and the button that grants it.
 *
 * An M3 CARD WITH ACTIONS, not a list item, and the difference is the point.
 * A list item is for scannable, uniform entries; this is a piece of persuasion
 * with a call to action, which is what M3's card anatomy - headline, supporting
 * text, action area - is for.
 *
 * It was tried as a grouped list first, which fixed one thing and broke
 * another. The leading icon put the headline at 80dp, matching every other row
 * on Home instead of starting 40dp from the edge; but it also took 80dp out of
 * the text column, so the supporting line wrapped to three, which makes an M3
 * ListItem three-line, which TOP-ALIGNS its trailing content. The result was a
 * button floating at the top of a five-line row. That is the same alignment
 * trap this project already documents for switches, reached from a new
 * direction: there the fix is to keep rows at two lines, and here the text
 * cannot be shortened because it has to say why a permission is wanted, in
 * three languages.
 *
 * Giving the text the full width and putting the action beneath it solves both:
 * the icon stays, the left edge still lines up, and nothing has to fit beside
 * anything.
 */
@Composable
internal fun PermissionCard(
    title: String,
    why: String,
    granted: Boolean,
    @DrawableRes icon: Int,
    tint: IconTint,
    onRequest: () -> Unit
) {
    val g = gloam
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = CARD_PAD, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(TIGHT)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RowIcon(icon, tint)
            Spacer(Modifier.width(16.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleMedium, color = g.onSurface
            )
        }
        Text(why, style = MaterialTheme.typography.bodySmall, color = g.onSurfaceLow)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            if (granted) Text(
                stringResource(R.string.perm_allowed),
                style = MaterialTheme.typography.labelLarge, color = g.stateOn
            ) else Button(
                onClick = onRequest,
                shape = CircleShape
                // NO colour override. M3's own buttonColors resolve to the
                // scheme's primary/onPrimary, which this app wires to
                // stateOn/onState - so the button is the accent and flips the
                // right way per theme for free: dark fill with white text in
                // Dawn, light fill with dark text in Dusk.
                //
                // It used to name `cta`, the arc's WARM end. That survived the
                // accent moving to the arc's COOL end and left a chroma-46
                // burnt orange 154 degrees from everything else, on a screen
                // whose card is chroma 3. Reported, fairly, as ugly. The fix is
                // less code than the bug was.
            ) {
                Text(
                    stringResource(R.string.perm_allow),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

/* The handles' own marks, shrunk. Colour alone made the wake side learnable;
   the moon and the sun are already on the ring, and they survive a dimmed
   screen and a colourblind reader in a way an orange label does not.
   The crescent is punched with the ground colour, so it has to be passed in -
   the ground changes between off, scheduled and running.                     */
@Composable
internal fun PhaseGlyph(moon: Boolean, tint: Color, ground: Color) {
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
internal fun pickerColors(accent: Color, onAccent: Color) = TimePickerDefaults.colors(
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
