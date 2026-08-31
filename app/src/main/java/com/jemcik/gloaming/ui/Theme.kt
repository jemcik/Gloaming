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
   "Dawn" is the light theme. Both were rebuilt 31 Aug 2026 - see the note
   above the palettes for what moved and why.                                */

data class GloamColors(
    val surface: Color,          // armed and waiting
    /* Three grounds, and they are a LADDER: the more the app is doing, the
       deeper the page. Both themes sit on the same three rungs, stated in tone
       so the two are comparable: Dusk runs 16.5 / 14 / 11 and Dawn 99 / 98 / 95.
       Dusk's rungs are wider than Dawn's because a dark ground has more room
       below it than a near-white one has above it - Dawn's `off` cannot climb
       past 100, so the ladder there is necessarily shallower. */
    val surfaceRunning: Color,   // while running - deeper
    val surfaceOff: Color,       // switched off - lifted
    val bloom: Color,            // radial highlight, running only
    val raise: Color,            // cards, dial track
    /* Cards while bedtime runs. In Dawn the page deepens TOWARDS the cards, so
       leaving them still would close the gap to nothing - `raise` and
       `surfaceRunning` are both tone 95. They deepen with it instead, to tone
       90, which holds 1.14:1. In Dusk the page deepens AWAY from them, so there
       the token is simply `raise` and the separation improves for free: 1.26:1
       resting, 1.35:1 running.
       Both are near Google's own: Health's cards measure 1.10:1 in light and
       1.12:1 in dark against their pages. That is much flatter than the 1.40:1
       Dusk used to hold, and deliberately so - the old number was bought with a
       tone-7 page, and lifting the ground is what the whole rebuild was for. */
    val raiseRunning: Color,
    val veil: Color,             // hairlines, ticks, off arc
    val line: Color,             // hollow handles, disabled
    val onSurface: Color,
    val onSurfaceMid: Color,
    /* Secondary text, and the ink most of the app is written in - 24 usages
       against onSurface's 13. Its worst case is not the page but a CARD: 6.86:1
       in Dusk and 7.68:1 in Dawn, against the 4.5:1 body minimum. Both were
       under 4.6 on the old palette and one of them failed outright, which is
       what forced the ink darker; the rebuilt grounds carry it for free.
       Titles sit at 10.4:1 and 15.1:1 on the same ground, so the hierarchy is
       intact - what moved is the floor, and it moved up. */
    val onSurfaceLow: Color,
    val stateOn: Color,          // armed, active - accent and TEXT
    val onState: Color,
    /* Selection as a filled surface: the day toggles, the preset pill, the
       effect fills - AND the checked switch track, which used to have its own
       pair. Material would separate them: a switch track is `primary` (tone 80
       dark / 40 light) because it is small, a chip is `secondaryContainer`
       (tone 30 / 90) because it is large. Gloaming deliberately does not, on
       the grounds that one accent used identically for every selection control
       is what makes a palette read as authored. The two tokens are COLLAPSED
       rather than merely set equal, so the requirement cannot drift.
       What that costs is measured in the switch's own note below.
       Not stateOn - that one has to stay legible as TEXT on the ground. */
    val selectFill: Color,
    val onSelect: Color,
    /* The checked thumb. It was `onSelect` for one release and that was wrong
       in Dawn: the ink is TEXT and has to clear 4.5:1 on the fill, so at ink
       strength the thumb arrived as a near-black dot at 6.80:1 - reported as
       too hard on the eye. A thumb is a UI part and needs 3:1, so it gets its
       own tone, and the softening comes as much from CHROMA as from tone: a
       desaturated dark dot reads as black, a chromatic one reads as coloured. */
    val switchThumb: Color,
    /* The UNSELECTED thumb has its own token, and now carries more than it used
       to: with the checked track no longer bright, the THUMB is what separates
       on from off. M3 would build it from `outline` on `surfaceContainerHighest`
       - here `line` on `veil` - and that measures 1.35:1 in Dawn and 1.34:1 in
       Dusk against the 3:1 a UI part must reach. This token holds 3.42:1 and
       3.64:1 instead. Borrowing a darker `outline` was not an option: it also
       draws the segmented button's border, the day circles' rings and the
       dial's dots, where quiet is right. */
    val switchThumbOff: Color,
    /* The state light is NOT stateOn. A lamp carries no text, so at 10dp it
       needs chroma rather than value contrast or it reads as a speck rather
       than a colour: 6.47:1 and 4.92:1 on the app bar. It follows the accent
       hue rather than staying green - with a slate accent a green lamp would be
       a fifth hue on a screen that already carries the arc's two, the accent
       and the red. Armed-versus-off is filled-versus-hollow as well as colour,
       so the red/green convention is not load-bearing here. */
    val lampOn: Color,
    val lampOff: Color,
    val cta: Color,
    val dark: Boolean
)

/* Both palettes are stated as HCT - Material's own space, where TONE is CIE L*
   and every number below is comparable to m3.material.io's tone table. They
   were rebuilt 31 Aug 2026 against that table and against Google Health,
   Dialer, Calendar, Tasks and Messages sampled on the Honor; the spec and
   Google's shipping apps agree exactly, which is worth knowing before treating
   any of it as an ideal nobody follows.

   Dusk is "soft charcoal": page tone 14, running 11, cards 22. Nothing on
   screen is near black. That is a deliberate walk-back of the old ground, whose
   running page was tone 3.5 - darker than surfaceContainerLowest, the darkest
   surface Material defines - while carrying a tone-75 selection fill. Both ends
   of the range sat past the edge in opposite directions, and the total swing
   from ground to selection was 71 tones. It is 28 now. The argument for lifting
   rather than deepening is that a near-black field with a bright disc on it is
   the harsher thing for a dark-adapted eye: the pupil opens for the ground and
   then takes the accent full force.

   The accent is green, but the two themes are NOT the same green, which is a
   deliberate asymmetry: Dusk is fern at hue 165 chroma 20, Dawn is sage at hue
   165's warmer neighbour 140, chroma 24. A cool green sits badly on a warm
   near-white ground - it is the one place the two grounds pull in different
   directions - and this phone's panel makes it worse, running colour mode 0
   (the vendor profile, not sRGB) with a colour-temperature correction that
   attenuates blue about 2.4%. Judged on the panel, not on a screenshot, for
   the reason recorded under Gotchas.
   It arrived by elimination rather than by taste: the old sage (hue 130) was
   rejected as too bright, a slate blue was reported as reading almost greyscale
   in Dusk and too cool in Dawn, and a lilac was disliked outright. Hue 165 is a
   green with a cool lean, which is why the chroma can stay this low and still
   read as a colour. Dusk carries more of it than Dawn because a dark ground
   swallows chroma: at 14 the theme looked greyscale, measured and reported.

   Dawn is "warm white": page tone 98 at chroma 2, cards 95. The old cream sat
   at tone 93 with chroma 7.8 at hue 89 - yellow-green, the direction the eye
   reads as tint most readily - so nothing in the theme reached above tone 95
   and it had no clean high end to anchor against. The warmth survives at hue
   ~76 and a third of the chroma, plus the arc, the CTA and the notice strip,
   which is where it was doing work.                                          */

// Dusk
private val Dusk = GloamColors(
    surface = Color(0xFF20242A),          // tone 14
    surfaceRunning = Color(0xFF1A1E23),   // tone 11
    surfaceOff = Color(0xFF25292E),       // tone 16.5
    bloom = Color(0xFF282E36),
    raise = Color(0xFF2F353D),            // tone 22 · 1.26:1 on the page
    raiseRunning = Color(0xFF2F353D),     //            1.35:1 while running
    veil = Color(0xFF383E46),
    line = Color(0xFF49505A),
    onSurface = Color(0xFFEAEBED),        // 10.37:1 on a card
    onSurfaceMid = Color(0xFFC7CDD7),
    onSurfaceLow = Color(0xFFBCC1CC),     //  6.86:1 on a card
    stateOn = Color(0xFFA1CBB4),          // tone 78 - Material's `primary`
    onState = Color(0xFF203A2E),
    selectFill = Color(0xFF4C6A5A),       // tone 42 chroma 20 · 2.61:1 on the page
    onSelect = Color(0xFFD2F0DD),         //                     4.90:1 on the fill
    switchThumb = Color(0xFFD2F0DD),      // = onSelect here; Dawn's differs, see below
    switchThumbOff = Color(0xFF9196A0),   //                     3.59:1 on the veil track
    lampOn = Color(0xFF88CAA8),
    lampOff = Color(0xFFE7968A),
    cta = Color(0xFFF49B66),
    dark = true
)

// Dawn
private val Dawn = GloamColors(
    surface = Color(0xFFFEF8F4),          // tone 98
    surfaceRunning = Color(0xFFF8EFEA),   // tone 95
    surfaceOff = Color(0xFFFDFDFD),       // tone 99.3
    bloom = Color(0xFFFFFAF7),
    raise = Color(0xFFF9EFE6),            // tone 95 · 1.08:1 on the page
    raiseRunning = Color(0xFFECE1D6),     //            1.14:1 while running
    veil = Color(0xFFF1E7DE),
    line = Color(0xFFCCC2B9),
    onSurface = Color(0xFF1E1B18),        // 15.11:1 on a card
    onSurfaceMid = Color(0xFF514A43),
    onSurfaceLow = Color(0xFF514A43),     //  7.68:1 on a card
    stateOn = Color(0xFF416134),          // tone 38 - Material's `primary`
    onState = Color(0xFFFFFFFF),
    selectFill = Color(0xFFC5E0B5),       // tone 86 chroma 24 · 1.36:1 on the page
    onSelect = Color(0xFF314B26),         //                     6.79:1 on the fill
    switchThumb = Color(0xFF426833),      // tone 40 chroma 36 · 4.51:1 on the track
    switchThumbOff = Color(0xFF837B72),   //                     3.39:1 on the veil track
    lampOn = Color(0xFF3E742B),
    lampOff = Color(0xFFAD483B),
    cta = Color(0xFF954914),
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
    /* The sun's rays, drawn on `dawn`. It was a literal inside BedtimeDial -
       the only colour in the app that did not come from a token - and naming it
       is worth keeping whatever else moves. */
    val onDawn = Color(0xFF402310)
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
 * The geometry is M3's - measured on the phone at 52x32dp with a 24dp thumb,
 * exactly the spec - and so are both tones it used to draw with. That was
 * checked rather than assumed: Google Messages' checked track measures tone 80
 * at 10.83:1 against its page and Gloaming's old one tone 75 at 9.23:1, so this
 * control was never the thing that glowed. What glowed was every SELECTED
 * CONTAINER sharing the switch's token.
 *
 * The track takes `selectFill`, so the switch, the day discs and the preset
 * pill are one colour rather than two tones of one. The thumb has its own token
 * because the ink is TEXT (4.5:1 on the fill) and a thumb is a UI PART (3:1);
 * at ink strength Dawn's arrived as a near-black dot at 6.80:1 and was reported
 * as too hard. It is tone 40 at chroma 28 now - 4.52:1 - and the softening is
 * as much the chroma as the tone: a desaturated dark dot reads as black, a
 * chromatic one reads as coloured.
 *
 * A dim checked track means the track's own on-versus-off lightness separation
 * is only 1.80:1 in Dusk and 1.19:1 in Dawn, and that was written up here as a
 * cost the thumb had to cover. **It was overstated, and the correction is worth
 * more than the worry was.** Measured off the device rather than reasoned about:
 *
 *     checked thumb    24.0dp        unchecked thumb   16.0dp
 *     checked track    no outline    unchecked track   2dp outline
 *
 * M3 grows the handle by half its diameter - 2.25x the area - and outlines only
 * the UNCHECKED track. Both are shape cues, both survive any colour vision, and
 * neither depends on the accent. With position and the check mark that is four
 * shape cues before a single colour is counted, and Gloaming's 16/24dp matches
 * Google Messages' own switch on this phone (23.7dp checked, measured).
 *
 * So the answer is that nothing needs fixing, and specifically NOT the thing
 * proposed here before: setting `checkedBorderColor` would put an outline on
 * BOTH states and destroy the asymmetry that is currently doing the work. It
 * would remove a cue while appearing to add one.
 *
 * WCAG 1.4.11 agrees and is worth quoting, because the intuition runs the other
 * way: it "does not require that changes in color that differentiate between
 * states of an individual component meet the 3:1 contrast ratio when they do
 * not appear next to each other". What it asks is that each state be
 * identifiable against ITS OWN adjacent colours. Both clear that on the thumb -
 * on 4.90:1 in Dusk and 4.52:1 in Dawn, off 3.59:1 and 3.39:1.
 *
 * One thing IS weaker than M3's baseline and is a separate call: the unchecked
 * outline is `line`, which this app keeps deliberately quiet, so it measures
 * 1.33:1 and 1.43:1 on its own track where M3's baseline manages 3.51:1. It
 * does not fail 1.4.11 - the thumb identifies the control - but if that outline
 * is ever wanted as a real cue it needs its own token rather than `line`, which
 * also draws the day rings, the section rules and the dial's dots.
 */
@Composable
fun gloamSwitchColors(): SwitchColors = SwitchDefaults.colors(
    checkedTrackColor = gloam.selectFill,
    checkedThumbColor = gloam.switchThumb,
    checkedBorderColor = Color.Transparent,
    uncheckedTrackColor = gloam.veil,
    uncheckedThumbColor = gloam.switchThumbOff,
    uncheckedBorderColor = gloam.line,
    // the check reads as the track's own green on the pale thumb
    checkedIconColor = gloam.selectFill,
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
        primary = g.stateOn, onPrimary = g.onState,
        secondary = g.cta, onSecondary = g.onSurface,
        background = g.surface, onBackground = g.onSurface,
        surface = g.surface, onSurface = g.onSurface,
        surfaceVariant = g.raise, onSurfaceVariant = g.onSurfaceLow,
        outline = g.line, outlineVariant = g.veil,
        secondaryContainer = g.selectFill, onSecondaryContainer = g.onSelect,
        primaryContainer = g.selectFill, onPrimaryContainer = g.onSelect,
        tertiary = g.cta, onTertiary = g.surface,
        tertiaryContainer = g.raise, onTertiaryContainer = g.onSurface,
        surfaceContainerHighest = g.veil, surfaceContainerHigh = g.raise,
        surfaceContainer = g.raise, surfaceContainerLow = g.surface,
        surfaceContainerLowest = g.surface
    ) else lightColorScheme(
        primary = g.stateOn, onPrimary = g.onState,
        secondary = g.cta, onSecondary = g.onSurface,
        background = g.surface, onBackground = g.onSurface,
        surface = g.surface, onSurface = g.onSurface,
        surfaceVariant = g.raise, onSurfaceVariant = g.onSurfaceLow,
        outline = g.line, outlineVariant = g.veil,
        secondaryContainer = g.selectFill, onSecondaryContainer = g.onSelect,
        primaryContainer = g.selectFill, onPrimaryContainer = g.onSelect,
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
