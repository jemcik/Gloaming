package com.jemcik.gloaming.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * One colour per row icon.
 *
 * There is no published standard for this: Apple does not document the colours
 * in its own Settings app and Material says nothing about it, so these are the
 * associations people already carry from the apps themselves - a phone is
 * green, a calendar orange, alarms and Do Not Disturb red. Grayscale is
 * neutral, which is the only joke, because that is what it does.
 *
 * Hues are spaced within the SECTION each row appears in, since that is where
 * they have to be told apart. Do Not Disturb and Alarms share a red on purpose:
 * different screens, and both mean "this one matters".
 *
 * TONE AND CHROMA ARE ONE PAIR FOR ALL OF THEM - Dawn tone 48 chroma 58, Dusk
 * tone 78 chroma 46 - so the icons differ by hue and agree on weight. That is
 * what keeps fourteen colours from reading as a rainbow. Three exceptions, each
 * of them a perceptual correction rather than a break:
 *
 * TONE 30 WAS THE FIRST VERSION AND IT FAILED. Every hue read as the same dark
 * blob; a hue does not read at all that deep, whatever chroma it carries.
 * Reported on sight, and it is the reason the pair sits at 48.
 *
 * RED NEEDS MORE CHROMA. Dnd and Alarm carry 92 against everything else's 58,
 * because red is where sRGB has least room and at equal numbers it reads duller
 * than green or blue. Every red people recognise sits high: M3's error is 76,
 * Material Red 700 is 83, iOS systemRed is 95. Matching perceived intensity
 * means giving red a bigger number, not the same one.
 *
 * YELLOW HAS THE MIRROR PROBLEM AND LOST. Reminders was amber, and amber only
 * reads as amber high up - measured, the crossover at hue 80 is tone 58, which
 * gives exactly 3.00:1 on a card. Below it turns olive, above it drops under
 * the 3:1 a non-text graphic holds. There is no third option in that hue, so
 * the bell left yellow entirely and took hue 285, which sits 35 degrees from
 * anything else on the screen - the widest gap of the fourteen.
 *
 * Dusk is a separate pair and was never the complaint; the light half is what
 * moved. Note red cannot follow there - at tone 78 hue 25 clamps at chroma 33.6,
 * the gamut ceiling, so Dusk's red is as red as Dusk's red gets.
 */
enum class IconTint(private val dark: Color, private val light: Color) {
    /** Do Not Disturb - red */
    Dnd(Color(0xFFFFABA2), Color(0xFFDD2524)),
    /** the allowlist - green */
    Allowed(Color(0xFF97D07F), Color(0xFF3A8123)),
    /** grayscale - neutral, which is what it does */
    Grayscale(Color(0xFFBFC1C7), Color(0xFF787068)),
    /** dim wallpaper - indigo */
    Dim(Color(0xFFA3C1FF), Color(0xFF2D70D3)),
    /** dark theme - night blue */
    Dark(Color(0xFF84C8FF), Color(0xFF0079B4)),
    /** always-on - teal */
    Ambient(Color(0xFF51D3D1), Color(0xFF00807E)),
    /** calls - green, universally a phone */
    Call(Color(0xFF97D07F), Color(0xFF3A8123)),
    /** messages - blue */
    Msg(Color(0xFF8DC6FF), Color(0xFF0077BC)),
    /** conversations - cyan */
    Conv(Color(0xFF55D0E6), Color(0xFF007E8E)),
    /** repeat callers - green-teal */
    Repeat(Color(0xFF65D5B1), Color(0xFF008265)),
    /** reminders - amber */
    Bell(Color(0xFFBABBFF), Color(0xFF6E70DE)),
    /** calendar - orange */
    Cal(Color(0xFFFFB075), Color(0xFFB15B00)),
    /** media - pink */
    Media(Color(0xFFF2A8F1), Color(0xFFA551AB)),
    /** alarms - red, the one thing never silenced */
    Alarm(Color(0xFFFFABA2), Color(0xFFDD2524));

    /** On the card behind it: Dusk takes the pale member, Dawn the deep one. */
    val ink: Color @Composable get() = if (gloam.dark) dark else light
}
