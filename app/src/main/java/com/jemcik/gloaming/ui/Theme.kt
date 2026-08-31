package com.jemcik.gloaming.ui

import androidx.annotation.DrawableRes
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
    /* ONE page colour, in every state.
       There used to be three, a LADDER - the more the app was doing, the deeper
       the page - crossfading over a second, plus a radial `bloom` while
       running. It is gone, asked for directly, and the reason is worth keeping:
       the page is the one surface a person is not looking at, so moving it says
       nothing they were trying to read and it drags every container on top of
       it along. Dawn now sits at what used to be the OFF rung, tone 99.3,
       which is the value that was picked by looking at all three.
       What went with it: `surfaceRunning`, `surfaceOff`, `bloom`,
       `raiseRunning`, the two colour animations and the float that faded the
       bloom. `raiseRunning` existed ONLY to compensate - in Dawn the running
       page deepened TOWARDS the cards and would have swallowed them - so with
       a still page it has nothing to do.
       Cards keep their own separation from the page: 1.26:1 in Dusk, and in
       Dawn 1.12:1, which is where Google Health's own cards measure on this
       phone. */
    val surface: Color,
    val raise: Color,            // cards, dial track
    val veil: Color,             // hairlines, ticks, off arc
    val line: Color,             // section rules, dividers, dial ticks and dots
    /* The border of a selection control: the unselected day's ring AND the
       preset row's segments. One token for both, and that is the load-bearing
       part - they sit 60dp apart doing the same job, and when the ring was
       split out to fix its contrast the preset row kept naming `line` at its
       own call site. 29 tones apart on one screen, reported on sight as one
       being black next to the other's calm grey. The numbers were checked; the
       two controls were never looked at together.
       Distinct from `line`, which draws section rules, card dividers and the
       dial's ticks - those separate blocks rather than bounding a control, and
       quiet is right there. `line` is tone 34 and 79, which IS Material's
       `outlineVariant`; the scheme used to alias `outline` to it, so the strong
       role and the quiet one were the same colour.
       Tone 70 in Dawn and 43 in Dusk: 2.19:1 and 2.70:1 against the page, and
       9 tones from `line` in both - close enough that the borders and the rules
       read as one family without the rules gaining any weight. That gap was
       closed from the BORDER side deliberately: moving the DIVIDERS to this
       token instead was built and looked at, and it lifts every section rule
       from 1.66:1 to 2.49:1 in Dawn and 1.91 to 3.23 in Dusk - roughly half
       again as heavy - which is the rules starting to box the content, the one
       thing they were tuned not to do. Material splits them for that reason:
       `outlineVariant` (80/30) is for dividers, `outline` (50/60) for control
       borders, and `line` at 79/34 IS outlineVariant.
       Dawn is UNDER the 3:1 a UI part is meant to hold, deliberately and after
       looking. The state here is carried by fill-versus-no-fill and the day
       letter sits at 8.28:1, so the ring reinforces rather than reports - and
       the tone that cleared the guideline (50, 4.28:1) was built, installed and
       rejected on sight as black. If it ever needs to go back inside the
       guideline, tone 58 is the lightest that does. */    val outline: Color,

    /* The border of a SELECTED control: the checked switch track, the selected
       day, the active preset segment and the "in effect" pill. One token, for
       the same reason `outline` is one - they are the same job in four places,
       and the day ring and the preset segment were 29 tones apart the last time
       that was left to call sites.
       M3 leaves a checked track bare and outlines only the unchecked one, and
       this app followed that. It stops here, deliberately: the checked track
       measures 1.26:1 against its card in Dawn and 2.06:1 in Dusk, both under
       the 3:1 a component needs to be identifiable against its background, and
       nothing else on the track supplies it. The rim roughly doubles both.
       The asymmetry M3's rule protects survives anyway - thumb POSITION, thumb
       SIZE (16dp against 24dp) and the thumb's GLYPH are three shape cues that
       do not depend on a border. The one lost measured 1.87:1, which is under
       what it needed to be read at all. */
    val selectBorder: Color,
    /* The border of an UNSELECTED control on a `veil` ground: the unchecked
       switch track and the "off" pill. Separate from `outline` because the two
       grounds are 6 tones apart and one value cannot serve both - `outline`
       manages only 1.87:1 on veil in BOTH themes, while a value that clears 3:1
       there lands near tone 50 on the page, which is the "the border around
       days is black" report. So: `outline` on the page, this on veil. */
    val veilOutline: Color,
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
    /* The checked switch's TRACK.
       It is the SAME VALUE as `selectFill` again in both themes, and the round
       trip is the note. The two were originally one token so a selected day,
       the preset pill and the switch could not drift. They were split apart
       when the accent was a pale washed sage, because a large light fill on
       every one of those controls put too much of one colour on the screen.
       Then the accent went DARK - tone 46, white text - and that inverted the
       problem: a dark fill reads as one system rather than as four competing
       areas, and the split it had needed was gone. The token stays separate so
       the two CAN diverge; today they do not.
       The history below is kept because the reasoning still holds for a pale
       accent, and this is the second time this app has been pale.
       The two were deliberately collapsed into one token so a selected day, the
       preset pill and the switch could not drift - and that held while the
       accent was a dark fern. With the accent a WASHED SAGE the screen carried
       green on the days, the presets, the notice strip and every switch, which
       was reported as too much of it. Splitting the switch back out is the
       smallest cut that fixes it: one control changes, the rest of the
       selection language stays one token.
       Dawn's is Arc.dusk VERBATIM. The dial already contained a colour built
       for this job - tone 41.7, where a track wants ~42 - so nothing had to be
       invented, and it gives the switch back a link to the arc that the accent
       gave up when it went green. Dusk's own value is within two tones and two
       degrees of it already, so both themes now read the same way: a dark track
       under a light thumb.
       It also RESTORES an M3 asymmetry this file argued for and then lost. A
       dark track bounds itself - 4.80:1 against the card - so the checked side
       needs no rim, leaving unchecked outlined and checked bare, which is
       Material's own rule and one more shape cue back. */
    val switchTrack: Color,
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
    /* The one FAILURE state in the app: bedtime is running, Do Not Disturb was
       asked for, and the phone reports that everything is getting through.
       Material's error container tones (30 dark / 90 light). It is now the
       ONLY red in the app: the state lamp used to carry a second one for
       merely being switched off, which is not a failure, and it is gone.
       So a red on screen means exactly one thing.
       It is a container pair rather than an ink because the readout that uses
       it is a status PILL now - fill plus same-hue ink, the grammar Google
       Health uses for "In range" and "Out of range". It used to be `cta` as
       TEXT, which was borrowing the accent to mean alarm and said nothing to
       anyone reading shape rather than colour. */
    val alert: Color,
    val onAlert: Color,
    /* The alert pill's rim, and it exists because the ground moved under it.
       All three status pills are light fills on a light card in Dawn, so each
       needs an edge; the other two take `selectBorder` and `veilOutline`. This
       one was left bare on the argument that the failure state is already the
       loudest thing on screen - true when the card was tone 95 and the pill
       held 1.14:1 against it, false at tone 91 where it fell to 1.02:1, which
       is the same lightness. A red on a neutral card would still have read by
       HUE alone, and hue alone is exactly what this app does not rely on.
       Note what the near-miss teaches: `alert` is Material's errorContainer
       tone (90 light / 30 dark), and that tone assumes a tone-95-or-lighter
       surface. Darkening a surface silently invalidates every container tone
       borrowed from a spec that assumed the old one. */
    val alertBorder: Color,
    /* There is no `cta` token any more, and its absence is the note.
       It was the arc's WARM end used as an action colour - the Allow button,
       the picker's Set, and the scheme's secondary/tertiary. That survived the
       accent moving to the arc's COOL end, and left a chroma-46 burnt orange
       154 degrees from everything else on a screen whose card is chroma 3.
       Every one of those now takes the scheme's own primary, which is
       `stateOn`, so they cannot drift from the accent again. Warm still exists
       where it belongs: Arc.ember, Arc.dawn and Arc.onDawn, on the dial. */
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
    raise = Color(0xFF2F353D),            // tone 22 · 1.26:1 on the page
    veil = Color(0xFF383E46),
    line = Color(0xFF49505A),
    outline = Color(0xFF61666E),      // tone 43 · ring 2.70:1 on the page
    /* = the fill, in BOTH themes now. The rim existed to bound a selected
       control against its ground, and neither fill needs it any more: Dawn's
       is dark on a light page, Dusk's lifts off its own at 2.59:1. Kept as a
       token rather than deleted, so a future accent that DOES need bounding
       can diverge without re-plumbing five call sites.
       Looked at on the device rather than decided from the ratio: at 17dp a
       lighter ring on a dark disc reads as a HALO rather than an edge, and the
       day row came out looking outlined instead of filled. */
    selectBorder = Color(0xFF566479),
    veilOutline = Color(0xFF8A8B90),  // tone 58 · 3.17:1 on the veil track
    onSurface = Color(0xFFEAEBED),        // 10.37:1 on a card
    onSurfaceMid = Color(0xFFC7CDD7),
    onSurfaceLow = Color(0xFFBCC1CC),     //  6.86:1 on a card
    stateOn = Color(0xFFB0C3E0),          // tone 78 - Material's `primary`
    onState = Color(0xFF23354E),
    selectFill = Color(0xFF566479),       // tone 42 chroma 18.7 · 2.59:1 on the page
    onSelect = Color(0xFFDDE9FF),         //                       4.91:1 on the fill
    switchTrack = Color(0xFF566479),      // within 2 tones of Arc.dusk already
    switchThumb = Color(0xFFDDE9FF),      // light thumb on a dark track
    switchThumbOff = Color(0xFF9196A0),   //                     3.59:1 on the veil track
    alert = Color(0xFF6F362E),         // tone 30 · ink 7.24:1
    onAlert = Color(0xFFFFDAD5),
    /* Tone 54, and the same value Dawn uses - a mid-tone rim clears 3:1
       against a light card AND a dark one, 3.06 and 3.20. Note the direction:
       on a DARK card this had to go LIGHTER, and the instinct to darken a
       border makes it worse (tone 46 gives 2.39). */
    alertBorder = Color(0xFFC5665B),
    dark = true
)

// Dawn
private val Dawn = GloamColors(
    // Tone 99.3. This used to be the OFF rung of a three-rung ladder and is now
    // the only page colour, which is what was asked for by name - and it is the
    // rung that makes every container on top of it read: cards go from 1.08:1
    // against the old tone-98 page to 1.12:1, and the checked switch track,
    // which sits on a CARD, keeps its own separation instead of having the card
    // walk towards it. Deepening the card was tried first and does the opposite:
    // it buys card-versus-page and spends switch-versus-card.
    surface = Color(0xFFFDFDFD),
    // Hue 130 at low chroma - a green-grey paper, not a cream. The creamy
    // beige it replaced was hue 76, and the accent is hue 258: nearly opposite,
    // which is what "inappropriate" meant when it was reported.
    // Tone 91, not 95, and the ladder is the whole trade: card-versus-page goes
    // 1.12 to 1.24 while everything sitting ON the card loses the same amount,
    // because all of it is lighter than the card. That is affordable now only
    // because the controls gained RIMS - the checked track's raw fill drops to
    // 1.13:1 and its rim carries it at 1.81:1.
    // Note hue 130 will not take Material's chroma-8 stepping for the outline
    // family: at that weight it reads olive rather than grey. It passes as
    // neutral paper only while its chroma stays near 3, which is the cost of
    // choosing a hue that is not the accent's.
    raise = Color(0xFFE4E5E2),            // tone 91 · 1.24:1 on the page
    veil = Color(0xFFDCDEDA),
    line = Color(0xFFC5C4BD),
    outline = Color(0xFFACACA5),      // tone 70 · ring 2.24:1 on the page
    selectBorder = Color(0xFF596F8B),     // = the fill; a dark fill bounds itself
    veilOutline = Color(0xFF7C7C76),  // tone 52 · 3.10:1 on the veil track
    onSurface = Color(0xFF1E1B18),        // 15.11:1 on a card
    onSurfaceMid = Color(0xFF514A43),
    onSurfaceLow = Color(0xFF514A43),     //  7.68:1 on a card
    stateOn = Color(0xFF435B7A),          // tone 38 - TEXT, so darker than the fill: 5.51:1 on a card
    onState = Color(0xFFFFFFFF),          
    /* Tone 88, chroma 13.7, hue 150 - a washed sage.
       Two reports moved it here and both are worth keeping. It was the arc's
       night stop at chroma 11, and GREY CONVENTIONALLY READS DISABLED, so a
       selected day and a checked switch looked unavailable; M3 treats chroma 20
       as a tint and the sage this replaced carried 24, so 11 was half of what
       Material considers a colour at all. Then chroma 24-26 read as too bright,
       which put the usable band at 16-20 - and "washed out" turned out to mean
       TONE rather than chroma: the fill moves toward its ground instead of
       losing its colour. Tone 88 is the ceiling. Past it the notice strip and
       the switch track dissolve into the card at tone 90.8, and one token
       serves those as well as the day discs, which sit on the far lighter page.
       Note what this gives up, deliberately: the accent no longer comes off the
       dial, which is what the palette rebuild was named for. Chosen anyway,
       with that on the table. */
    selectFill = Color(0xFF596F8B),       // tone 46 · white on it 5.16:1
    // Tone 23, not the container role's 29. The fill is the arc's own night
    // stop as it is drawn when bedtime is OFF, and at that chroma it is near
    // the floor of what still reads as a hue - so the label on it was the first
    // thing to suffer. 8.38:1 here against 6.76:1 at tone 29, and the ink still
    // holds chroma 13.3, so it stays a blue ink rather than going near-black.
    onSelect = Color(0xFFFFFFFF),         // white; tone 50 would fail it at 4.47:1
    switchTrack = Color(0xFF596F8B),      // the SAME token again - see the note above
    switchThumb = Color(0xFFFFFFFF),      // 5.16:1 on the track
    switchThumbOff = Color(0xFF837B72),   //                     3.39:1 on the veil track
    alert = Color(0xFFFFDAD5),         // tone 90 · ink 7.27:1
    onAlert = Color(0xFF6B3831),
    /* Tone 54, not 58. The pill's FILL is 1.02:1 against the card - the rim is
       the only thing separating it - so unlike the day ring, which merely
       reinforces a letter at 8:1, this one genuinely has to reach 3:1. It was
       set at tone 58 against a different card and drifted to 2.69 when the card
       moved; 3.06 now. */
    alertBorder = Color(0xFFC5665B),
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
    checkedTrackColor = gloam.switchTrack,
    checkedThumbColor = gloam.switchThumb,
    // Bare, which is M3's own rule: a dark track bounds itself.
    checkedBorderColor = gloam.switchTrack,
    uncheckedTrackColor = gloam.veil,
    uncheckedThumbColor = gloam.switchThumbOff,
    uncheckedBorderColor = gloam.veilOutline,
    // the check reads as the track's own green on the pale thumb
    // the check reads as the track's colour on the pale thumb
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
    onCheckedChange: ((Boolean) -> Unit)? = null,
    // What the thumb wears when checked. Every switch in the app leaves this
    // alone; the master switch passes a moon while a window is actually
    // running, so armed and running are told apart by the control itself
    // rather than by a lamp beside it saying the same thing a third time.
    @DrawableRes icon: Int = R.drawable.ic_check,
    // Every switch in the app but one sits in a ROW that carries the label and
    // the gesture, so the switch is an unnamed indicator and correctly so. The
    // app bar's has no row: read off the device it came back with no text and
    // no content description at all, which TalkBack announces as a bare
    // "on, switch". Naming it is the fix, and it is the same string the title
    // beside it shows.
    contentDescription: String? = null
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        interactionSource = interactionSource,
        modifier = if (contentDescription == null) Modifier
                   else Modifier.semantics { this.contentDescription = contentDescription },
        colors = gloamSwitchColors(),
        // M3 offers the thumb an icon and it is worth taking: it states the ON
        // state a second way, so the control does not rest on thumb POSITION
        // alone - which is the one cue that survives neither a glance nor
        // colour blindness. Only on the checked side; an icon on both is noise.
        //
        // Note the sizing this rests on, before anyone reaches for an icon on
        // the unchecked side too: M3 grows the thumb to 24dp whenever
        // thumbContent is non-null, so an icon there would silently flatten
        // the 16/24dp asymmetry that CLAUDE.md records as load-bearing.
        // Passing null while unchecked is what keeps it.
        thumbContent = if (checked) {
            {
                Icon(
                    painterResource(icon),
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
        secondary = g.stateOn, onSecondary = g.onState,
        background = g.surface, onBackground = g.onSurface,
        surface = g.surface, onSurface = g.onSurface,
        surfaceVariant = g.raise, onSurfaceVariant = g.onSurfaceLow,
        outline = g.outline, outlineVariant = g.veil,
        secondaryContainer = g.selectFill, onSecondaryContainer = g.onSelect,
        primaryContainer = g.selectFill, onPrimaryContainer = g.onSelect,
        tertiary = g.stateOn, onTertiary = g.onState,
        tertiaryContainer = g.raise, onTertiaryContainer = g.onSurface,
        errorContainer = g.alert, onErrorContainer = g.onAlert,
        surfaceContainerHighest = g.veil, surfaceContainerHigh = g.raise,
        surfaceContainer = g.raise, surfaceContainerLow = g.surface,
        surfaceContainerLowest = g.surface
    ) else lightColorScheme(
        primary = g.stateOn, onPrimary = g.onState,
        secondary = g.stateOn, onSecondary = g.onState,
        background = g.surface, onBackground = g.onSurface,
        surface = g.surface, onSurface = g.onSurface,
        surfaceVariant = g.raise, onSurfaceVariant = g.onSurfaceLow,
        outline = g.outline, outlineVariant = g.veil,
        secondaryContainer = g.selectFill, onSecondaryContainer = g.onSelect,
        primaryContainer = g.selectFill, onPrimaryContainer = g.onSelect,
        tertiary = g.stateOn, onTertiary = g.onState,
        tertiaryContainer = g.raise, onTertiaryContainer = g.onSurface,
        errorContainer = g.alert, onErrorContainer = g.onAlert,
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
