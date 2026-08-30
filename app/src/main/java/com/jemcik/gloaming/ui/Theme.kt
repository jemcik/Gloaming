package com.jemcik.gloaming.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.size
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.Shapes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextGeometricTransform
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jemcik.gloaming.R

/* ── Fonts ─────────────────────────────────────────────────────────────────
   Baloo 2 carries every number; Figtree carries every word. Two weights only:
   600 for numerals, 700 for the brand lockup and button labels. Never 800 -
   at 44sp the heavier cuts read as friendly rather than calm.              */

private fun baloo(w: Int) = Font(
    R.font.baloo2,
    weight = FontWeight(w),
    variationSettings = FontVariation.Settings(FontVariation.weight(w))
)

private fun figtree(w: Int) = Font(
    R.font.figtree,
    weight = FontWeight(w),
    variationSettings = FontVariation.Settings(FontVariation.weight(w))
)

val Baloo = FontFamily(baloo(600), baloo(700))
val Figtree = FontFamily(figtree(400), figtree(600), figtree(700))

/* ── Colour ────────────────────────────────────────────────────────────────
   Two grounds. "Dusk" is the default and the one the app is designed for;
   "Dawn" is the light theme, inherited from Organic.                        */

data class GloamColors(
    val surface: Color,          // armed and waiting
    /* Three grounds, and they are a LADDER: the more the app is doing, the
       deeper the page. Dusk had it symmetric - running is (-5,-6,-7) from
       surface and off is (+5,+5,+6) - while Dawn had only the running half and
       an `off` identical to `surface`, so switching the app off in the light
       theme changed nothing at all. Dawn's off now mirrors Dawn's own running
       delta, which puts both themes on the same three rungs. */
    val surfaceRunning: Color,   // while running - deeper
    val surfaceOff: Color,       // switched off - lifted
    val bloom: Color,            // radial highlight, running only
    val raise: Color,            // cards, dial track
    /* Cards while bedtime runs. In Dawn the page deepens TOWARDS the cards, so
       leaving them still would have closed the gap to 1.03:1 and dissolved
       them - the exact failure this palette was rebuilt to fix. They deepen
       with it instead, which keeps 1.217:1, slightly BETTER than the 1.153 they
       had. In Dusk the page deepens AWAY from them, so there the same token is
       simply `raise` and the separation improves for free: 1.469 to 1.500. */
    val raiseRunning: Color,
    /* The strip at the foot of a card whose switches are not in effect. It is a
       RUNG, not an alert: `cta` at 24% over `raise`, which puts it 1.23:1 from
       the card in Dawn and 1.40:1 in Dusk - the SAME separation `raise` itself
       has from the page (1.22 / 1.40). So it reads as page -> card -> notice,
       one more step on a ladder that already exists, rather than as an error.
       Colouring the TEXT instead was measured first and fails: `cta` as ink is
       2.49:1 on the card in Dawn and `lampOff` is 3.80:1, both under the 4.5:1
       body minimum and under even the 3:1 large-text exemption. The cream
       cannot carry an accent hue as text, which is the WAKE UP finding again.
       Its ink is plain `onSurface` - 9.31:1 in Dawn, 8.17:1 in Dusk - so there
       is deliberately no `onNotice`: a token identical to one beside it is
       noise. The quiet `onSurfaceLow` was tried on it and passes at 5.30/4.55,
       but in Dusk a cool grey on a warm band reads washed out. */
    val notice: Color,
    val veil: Color,             // hairlines, ticks, off arc
    val line: Color,             // hollow handles, disabled
    val onSurface: Color,
    val onSurfaceMid: Color,
    /* Secondary text, and the ink most of the app is written in - 24 usages
       against onSurface's 13. Its worst case is not the page but a CARD, which
       is a lighter ground in Dawn and a lighter one in Dusk, and it was 4.54:1
       there in Dawn and 4.30:1 in Dusk - the second of those failing AA outright
       and the first passing it by 0.04. Both now clear 6.3:1 on a card, which is
       a margin rather than a rounding. The titles stay at 11.4:1 on the same
       ground, so the hierarchy is unchanged; what moved is the floor. */
    val onSurfaceLow: Color,
    val stateOn: Color,          // armed, active - accent and TEXT
    val onState: Color,
    /* Selection as a filled surface: day toggles, effect chips, allowlist
       avatars, the switch track. Not stateOn - that one has to stay legible
       as text on the ground, which forces it dark in Dawn, and a fill that
       dark makes every selected day read as a hole punched in the screen. */
    val selectFill: Color,
    val onSelect: Color,
    /* The switch track is not selectFill either. A chip is large and can carry
       a washed fill; a track is small, and washed track plus light thumb
       leaves the control with no definition at all. */
    val switchTrack: Color,
    val switchThumb: Color,
    /* The UNSELECTED thumb needs its own token for the same reason the track
       did. M3 builds it from `outline` on `surfaceContainerHighest`, which here
       are `line` on `veil` - the hairline colour on the hairline colour, since
       both are deliberately quiet and `line` is literally what gets drawn ON
       `veil` elsewhere. Measured 1.35:1 in Dawn and 1.34:1 in Dusk against the
       3:1 a UI part has to reach; M3's own baseline manages 3.51. Borrowing a
       darker `outline` was not an option: it also draws the segmented button's
       border, the day circles' rings and the dial's dots, where quiet is right. */
    val switchThumbOff: Color,
    /* The state light is NOT stateOn. A fill has to be dark in Dawn so onState
       reads on top of it; a lamp carries no text, so at 10dp it needs chroma
       rather than value contrast or it reads as a dark speck, not a colour. */
    val lampOn: Color,
    val lampOff: Color,
    val stateTint: Color,        // armed chip fill
    val cta: Color,
    val dark: Boolean
)

// Dusk
private val Dusk = GloamColors(
    surface = Color(0xFF12161B),
    surfaceRunning = Color(0xFF0A0D11),
    surfaceOff = Color(0xFF171B21),
    bloom = Color(0xFF1E2530),
    raise = Color(0xFF29323D),
    raiseRunning = Color(0xFF29323D),
    notice = Color(0xFF534440),
    veil = Color(0xFF2B333D),
    line = Color(0xFF3C4551),
    onSurface = Color(0xFFEEF1F5),
    onSurfaceMid = Color(0xFFC3CAD3),
    onSurfaceLow = Color(0xFFACB7C3),
    stateOn = Color(0xFFAEBF92),
    onState = Color(0xFF1B2114),
    selectFill = Color(0xFFAEBF92),
    onSelect = Color(0xFF1B2114),
    switchTrack = Color(0xFFAEBF92),
    switchThumb = Color(0xFF1B2114),
    switchThumbOff = Color(0xFF7E8B9B),
    lampOn = Color(0xFFA9C98C),
    lampOff = Color(0xFFC98079),
    stateTint = Color(0xFF2B333D),
    cta = Color(0xFFD67F48),
    dark = true
)

// Dawn
private val Dawn = GloamColors(
    surface = Color(0xFFF5EAD8),
    surfaceRunning = Color(0xFFE6DCCB),
    surfaceOff = Color(0xFFFAF0E1),
    bloom = Color(0xFFF7EDDD),
    raise = Color(0xFFE2D5BD),
    raiseRunning = Color(0xFFD4C8B2),
    notice = Color(0xFFDBBD9D),
    veil = Color(0xFFDCD3C4),
    line = Color(0xFFC0B6A5),
    onSurface = Color(0xFF201E1D),
    onSurfaceMid = Color(0xFF4C453B),
    onSurfaceLow = Color(0xFF4C453B),
    stateOn = Color(0xFF56633F),
    onState = Color(0xFFF0FAE1),
    selectFill = Color(0xFFBDCB9F),
    onSelect = Color(0xFF2E3720),
    switchTrack = Color(0xFF7F945A),
    switchThumb = Color(0xFFFBFDF6),
    switchThumbOff = Color(0xFF7C7060),
    lampOn = Color(0xFF4E8B3C),
    lampOff = Color(0xFFB24632),
    stateTint = Color(0xFFE1EECC),
    cta = Color(0xFFC67139),
    dark = false
)

/* The arc is shared by both themes: a four-stop sweep from night to dawn.
   Shipped as a SweepGradient remapped onto the arc's angular span, so the
   gradient tracks the handles when dragged - never a fixed screen gradient. */
object Arc {
    val night = Color(0xFF2F4260)   // 0.00 · bedtime handle fill
    val dusk = Color(0xFF4E6480)    // 0.42 · deepest hour
    val ember = Color(0xFFB2622D)   // 0.76
    val dawn = Color(0xFFF6A06B)    // 1.00 · wake handle
    val stops = listOf(0f to night, 0.42f to dusk, 0.76f to ember, 1f to dawn)

    /* The same ramp, started from dusk on a dark ground. Measured: night is
       8.4:1 against Dawn's cream but only 1.8:1 against Dusk's surface, so a
       hairline appears to begin halfway along. The ring itself keeps night -
       at 17dp it carries its own weight; this is for thin strokes on the
       ground, where the low end has nothing to sit on. */
    /* Night as a filled block on a dark ground measures 1.6:1 against the card
       it sits on - visible, but it stops reading as a fill. Dusk is the same
       ramp one stop along and takes it to 2.6:1. */
    fun nightOn(dark: Boolean): Color = if (dark) dusk else night

    fun stopsOn(dark: Boolean): List<Pair<Float, Color>> =
        if (dark) listOf(0f to dusk, 0.42f to dusk, 0.76f to ember, 1f to dawn) else stops
}

val LocalGloam = staticCompositionLocalOf { Dusk }

val gloam: GloamColors
    @Composable @ReadOnlyComposable get() = LocalGloam.current

// FOUR sizes are visible in this app: 36 numerals, 19 titles, 16 row titles,
// 14 for everything at reading size, 11 overlines. There is no 13 - the comment
// here described one for a while after it had gone.
//
// 14 carries prose, subtitles, chips and buttons alike, split by WEIGHT rather
// than by size: Figtree 400 for prose, 600 for chips and buttons. What was wrong
// before was 36 against 34 in the same family and weight inches apart on one
// screen, and body 14 against subtitles 13 - two sizes of the same 400-weight
// prose, a distinction nobody can see and everybody has to maintain.
//
// Every role is named, including the ones this app never writes itself.
// An unset TextStyle falls back to Material's baseline - Roboto - exactly the
// way an unset ColorScheme role falls back to baseline violet, and for the same
// reason it is invisible until some component reaches for it. TimePicker reaches
// for displayLarge, which is how Roboto got into the time picker's numerals.
private val GloamType = Typography(
    // Reached by components, not by us: TimePicker's hour and minute fields.
    displayLarge = TextStyle(
        fontFamily = Baloo, fontWeight = FontWeight(600),
        fontSize = 44.sp, lineHeight = 48.sp, letterSpacing = (-0.5).sp
    ,
        // Tabular figures. Without them "20:05" measures 86.9dp and "13:50"
        // 79.7dp - the same five characters - so every minute shoves the
        // countdown sideways. Both Baloo 2 and Figtree carry tnum.
        fontFeatureSettings = "tnum"
    ),
    displayMedium = TextStyle(
        fontFamily = Baloo, fontWeight = FontWeight(600),
        fontSize = 40.sp, lineHeight = 44.sp, letterSpacing = (-0.5).sp
    ,
        // Tabular figures. Without them "20:05" measures 86.9dp and "13:50"
        // 79.7dp - the same five characters - so every minute shoves the
        // countdown sideways. Both Baloo 2 and Figtree carry tnum.
        fontFeatureSettings = "tnum"
    ),
    // THE numeral role: the dial centre and the two window times alike. They sit
    // inches apart on one screen, so they are one size, not two near-identical ones.
    displaySmall = TextStyle(
        fontFamily = Baloo, fontWeight = FontWeight(600),
        fontSize = 36.sp, lineHeight = 40.sp, letterSpacing = (-0.5).sp
    ,
        // Tabular figures. Without them "20:05" measures 86.9dp and "13:50"
        // 79.7dp - the same five characters - so every minute shoves the
        // countdown sideways. Both Baloo 2 and Figtree carry tnum.
        fontFeatureSettings = "tnum"
    ),
    // Nothing here writes a headline; the ladder exists so a component cannot
    // fall through to Roboto, and is spaced clear of the numerals above.
    headlineLarge = TextStyle(
        fontFamily = Baloo, fontWeight = FontWeight(600),
        fontSize = 30.sp, lineHeight = 34.sp, letterSpacing = (-0.4).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = Baloo, fontWeight = FontWeight(600),
        fontSize = 26.sp, lineHeight = 30.sp, letterSpacing = (-0.3).sp
    ),
    // AlertDialog's title, when a call site does not name its own style.
    headlineSmall = TextStyle(
        fontFamily = Baloo, fontWeight = FontWeight(600),
        fontSize = 22.sp, lineHeight = 26.sp, letterSpacing = (-0.2).sp
    ),
    // Screen title / button label
    titleLarge = TextStyle(
        fontFamily = Baloo, fontWeight = FontWeight(700),
        fontSize = 19.sp, lineHeight = 24.sp, letterSpacing = (-0.2).sp
    ),
    // Row title. 16 against body's 14, because a row title sits directly above
    // its own subtitle and 15 against 14 is not a step anyone can see.
    titleMedium = TextStyle(
        fontFamily = Figtree, fontWeight = FontWeight(600),
        fontSize = 16.sp, lineHeight = 22.sp
    ),
    titleSmall = TextStyle(
        fontFamily = Figtree, fontWeight = FontWeight(600),
        fontSize = 14.sp, lineHeight = 20.sp
    ),
    // One body size, for prose and subtitles alike. Secondary text is separated
    // by colour (onSurfaceLow), which the screen already does everywhere, rather
    // than by a 1sp difference nobody can see.
    //
    // Deliberately NOT tabular. Tabular figures pad every digit to the width of
    // the widest one, and Figtree's "1" is far narrower than the rest, so it
    // ends up floating in an oversized slot - "Starts in 12 hr" read as
    // "1 2 hr". That padding buys alignment, and alignment is worth having in a
    // centred countdown that rewrites itself every minute. It is worth nothing
    // in left-aligned prose, where a width change moves no other pixel. Numerals
    // keep tnum; sentences do not.
    bodyLarge = TextStyle(
        fontFamily = Figtree, fontWeight = FontWeight(400),
        fontSize = 14.sp, lineHeight = 20.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = Figtree, fontWeight = FontWeight(400),
        fontSize = 14.sp, lineHeight = 20.sp
    ),
    // 18, not 20. bodySmall is the supporting line under a row title, the two
    // captions beneath the day row, and the right-now readout - short labels,
    // never a paragraph. At 20sp the ratio was 1.43, looser than M3's own
    // bodySmall (12/16, 1.33), and a subtitle that wrapped read as two separate
    // sentences with a gap between them rather than one that ran on. Prose is
    // bodyLarge and keeps 20.
    bodySmall = TextStyle(
        fontFamily = Figtree, fontWeight = FontWeight(400),
        fontSize = 14.sp, lineHeight = 18.sp
    ),
    // Effect chip · day toggle
    labelLarge = TextStyle(
        fontFamily = Figtree, fontWeight = FontWeight(600),
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = Figtree, fontWeight = FontWeight(600),
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp
    ),
    // Overline - centre sublabel, time labels
    labelSmall = TextStyle(
        fontFamily = Figtree, fontWeight = FontWeight(700),
        fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 1.2.sp
    )
)

/* ── Shape ─────────────────────────────────────────────────────────────────
   Left unset, every M3 component that resolves a shape through the theme takes
   Material's defaults - and they are square next to this app. The TimePicker is
   what shows it: its hour and minute fields and its AM/PM selector read
   ShapeKeyTokens.CornerSmall, 8dp by default, so distinctly boxy numerals sat
   inside a 32dp dialog in an app whose containers are 28dp and whose chips are
   circles. The same trap as the Typography and ColorScheme roles that were
   never set, and recorded twice already: invisible until a component reaches
   for a token nobody wrote, then wrong in a way that looks like another app.

   The house scale is Material's shifted one step rounder, and every value is a
   real M3 token rather than a number picked to taste:

       role         ours   M3 default   what it reaches here
       extraSmall     8         4        menus
       small         16         8        the time picker's fields and AM/PM
       medium        20        12        cards
       large         28        16        - our own container corner
       extraLarge    32        28        dialogs, sheets

   16 for small was chosen on the phone against 8, 12 and 20: 8 and 12 keep
   square shoulders, and 20 bends the AM/PM stack into a lozenge - very round
   at top and bottom with a straight divider across its middle.                */
private val GloamShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

/**
 * Every switch in the app, in one place.
 *
 * The geometry was already M3's - measured on the phone at 52x32dp with a 24dp
 * thumb, exactly the spec. What was not M3's was the CONTRAST between the parts.
 * The unselected thumb sat at 1.35:1 on its own track and the selected one at
 * 2.69:1 on its own, where M3's baseline reaches 3.51 and 6.44 and a UI part
 * that must be identified needs 3. A switch whose thumb you cannot find is
 * telling you nothing that its position alone does not.
 *
 * Dawn's track darkens from #8FA36C to #7F945A to buy that, which is a
 * deliberate walk-back of the note in CLAUDE.md about a track needing less
 * weight than a chip: it still does - #7F945A is nowhere near the chips' fill -
 * but not at the price of an invisible thumb.
 */
@Composable
fun gloamSwitchColors(): SwitchColors = SwitchDefaults.colors(
    checkedTrackColor = gloam.switchTrack,
    checkedThumbColor = gloam.switchThumb,
    checkedBorderColor = Color.Transparent,
    uncheckedTrackColor = gloam.veil,
    uncheckedThumbColor = gloam.switchThumbOff,
    uncheckedBorderColor = gloam.line,
    // the check reads as the track's own green on the pale thumb
    checkedIconColor = gloam.switchTrack,
    disabledUncheckedTrackColor = gloam.veil,
    disabledUncheckedThumbColor = gloam.line,
    disabledUncheckedBorderColor = gloam.line
)

/**
 * The app's switch. Wrapping M3's rather than calling it four times means the
 * icon, the colours and the interaction source cannot drift apart.
 *
 * [interactionSource] is not optional decoration. Every switch here is driven by
 * its ROW - onCheckedChange is null so the switch does not swallow taps meant
 * for the row - and M3 grows the thumb from 24dp to 28dp while pressed by
 * watching the interaction source it was given. Left unshared, the switch never
 * hears the press and the thumb never moves, so the control felt inert under the
 * finger even though it worked. Handing it the row's own source restores the
 * gesture M3 specifies.
 */
@Composable
fun GloamSwitch(
    checked: Boolean,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    // null everywhere a ROW carries the toggle, which is everywhere except the
    // app bar - a bar has no row to hand the gesture to, so there the switch
    // has to be its own target.
    onCheckedChange: ((Boolean) -> Unit)? = null
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        interactionSource = interactionSource,
        colors = gloamSwitchColors(),
        // M3 offers the thumb an icon and it is worth taking: it states the ON
        // state a second way, so the control does not rest on thumb POSITION
        // alone - which is the one cue that survives neither a glance nor
        // colour blindness. Only on the checked side; an icon on both is noise.
        thumbContent = if (checked) {
            {
                Icon(
                    painterResource(R.drawable.ic_check),
                    contentDescription = null,
                    modifier = Modifier.size(SwitchDefaults.IconSize)
                )
            }
        } else null
    )
}

@Composable
fun GloamingTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val g = if (dark) Dusk else Dawn
    val scheme = if (dark) darkColorScheme(
        primary = g.switchTrack, onPrimary = g.switchThumb,
        secondary = g.cta, onSecondary = g.onSurface,
        background = g.surface, onBackground = g.onSurface,
        surface = g.surface, onSurface = g.onSurface,
        surfaceVariant = g.raise, onSurfaceVariant = g.onSurfaceLow,
        outline = g.line, outlineVariant = g.veil,
        secondaryContainer = g.raise, onSecondaryContainer = g.onSurface,
        primaryContainer = g.stateTint, onPrimaryContainer = g.onSurface,
        tertiary = g.cta, onTertiary = g.surface,
        tertiaryContainer = g.raise, onTertiaryContainer = g.onSurface,
        surfaceContainerHighest = g.veil, surfaceContainerHigh = g.raise,
        surfaceContainer = g.raise, surfaceContainerLow = g.surface,
        surfaceContainerLowest = g.surface
    ) else lightColorScheme(
        primary = g.switchTrack, onPrimary = g.switchThumb,
        secondary = g.cta, onSecondary = g.onSurface,
        background = g.surface, onBackground = g.onSurface,
        surface = g.surface, onSurface = g.onSurface,
        surfaceVariant = g.raise, onSurfaceVariant = g.onSurfaceLow,
        outline = g.line, outlineVariant = g.veil,
        secondaryContainer = g.stateTint, onSecondaryContainer = g.onSurface,
        primaryContainer = g.stateTint, onPrimaryContainer = g.onSurface,
        tertiary = g.cta, onTertiary = g.surface,
        tertiaryContainer = g.raise, onTertiaryContainer = g.onSurface,
        surfaceContainerHighest = g.veil, surfaceContainerHigh = g.raise,
        surfaceContainer = g.raise, surfaceContainerLow = g.surface,
        surfaceContainerLowest = g.surface
    )

    CompositionLocalProvider(LocalGloam provides g) {
        MaterialTheme(
            colorScheme = scheme,
            typography = GloamType,
            shapes = GloamShapes,
            content = content
        )
    }
}
