package com.jemcik.gloaming.ui

import androidx.compose.foundation.isSystemInDarkTheme
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
    val surface: Color,          // off & scheduled
    val surfaceRunning: Color,   // while running
    val surfaceOff: Color,       // when switched off
    val bloom: Color,            // radial highlight, running only
    val raise: Color,            // cards, dial track
    val veil: Color,             // hairlines, ticks, off arc
    val line: Color,             // hollow handles, disabled
    val onSurface: Color,
    val onSurfaceMid: Color,
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
    surfaceRunning = Color(0xFF0D1014),
    surfaceOff = Color(0xFF171B21),
    bloom = Color(0xFF1E2530),
    raise = Color(0xFF29323D),
    veil = Color(0xFF2B333D),
    line = Color(0xFF3C4551),
    onSurface = Color(0xFFEEF1F5),
    onSurfaceMid = Color(0xFFC3CAD3),
    onSurfaceLow = Color(0xFF8996A3),
    stateOn = Color(0xFFAEBF92),
    onState = Color(0xFF1B2114),
    selectFill = Color(0xFFAEBF92),
    onSelect = Color(0xFF1B2114),
    switchTrack = Color(0xFFAEBF92),
    switchThumb = Color(0xFF1B2114),
    lampOn = Color(0xFFA9C98C),
    lampOff = Color(0xFFC98079),
    stateTint = Color(0xFF2B333D),
    cta = Color(0xFFD67F48),
    dark = true
)

// Dawn
private val Dawn = GloamColors(
    surface = Color(0xFFF5EAD8),
    surfaceRunning = Color(0xFFF0E4CF),
    surfaceOff = Color(0xFFF5EAD8),
    bloom = Color(0xFFF7EDDD),
    raise = Color(0xFFE2D5BD),
    veil = Color(0xFFDCD3C4),
    line = Color(0xFFC0B6A5),
    onSurface = Color(0xFF201E1D),
    onSurfaceMid = Color(0xFF645C50),
    onSurfaceLow = Color(0xFF645C50),
    stateOn = Color(0xFF56633F),
    onState = Color(0xFFF0FAE1),
    selectFill = Color(0xFFBDCB9F),
    onSelect = Color(0xFF2E3720),
    switchTrack = Color(0xFF8FA36C),
    switchThumb = Color(0xFFFBFDF6),
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

private fun tracking(sp: Float) = sp.sp

// FIVE sizes are visible in this app: 36 numerals, 19 titles, 16 row titles,
// 14 body, 13 labels, 11 overlines.
//
// The one tight step left is 14 against 13, and it is a WEIGHT boundary, not an
// arbitrary split inside one register: everything at 14 is Figtree 400 prose,
// everything at 13 is Figtree 600 on a chip or a button. Prose and chips are
// rarely adjacent, and where they are, the weight carries it. What was wrong
// before was 36 against 34 in the same family and weight inches apart on one
// screen, and body 14 against subtitles 13 - two sizes of the same 400-weight
// prose, which is the split this scale no longer makes.
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
    bodySmall = TextStyle(
        fontFamily = Figtree, fontWeight = FontWeight(400),
        fontSize = 14.sp, lineHeight = 20.sp
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
        MaterialTheme(colorScheme = scheme, typography = GloamType, content = content)
    }
}
