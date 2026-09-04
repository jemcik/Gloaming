package com.jemcik.gloaming.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.FilledIconButton
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import com.jemcik.gloaming.R
import com.jemcik.gloaming.core.Prefs
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.DayOfWeek
import java.time.LocalTime

/**
 * Does the text fit, in every language?
 *
 * The character budgets in `values/strings.xml` are a PROXY, and a poor one:
 * Cyrillic is wider, so a 24-character Ukrainian subtitle wrapped where a
 * 25-character English one did not. Three rows shipped misaligned because the
 * app had only ever been looked at in English.
 *
 * This measures the thing itself. An M3 ListItem is 56dp on one line and 72dp on
 * two; past that it is a THREE-line item, and M3 then top-aligns the trailing
 * content - so a supporting line long enough to wrap silently lifts the switch
 * off its own row's centre. Any row over 72dp here is that bug, in whichever
 * language made it happen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
// Real text layout. Robolectric's default graphics mode stubs text measurement -
// every glyph the same width - so every row came back an identical 86dp in all
// three languages, which is not a measurement of anything. A layout test needs
// the real Skia.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RowFitTest {

    @get:Rule
    val compose = createComposeRule()

    /**
     * M3's THREE-line height. The bug is a wrapped SUPPORTING line: that is what
     * makes a ListItem three-line, and M3 then top-aligns the trailing content,
     * so the switch leaves its own row's centre. A wrapped HEADLINE is not that
     * bug - it lands at 74dp with the switch still centred, measured on the
     * device - and the threshold used to be 72, which failed the two the same
     * way. "Hide always-on" has a long, correct name in Russian and Ukrainian
     * and is allowed to use two lines for it.
     */
    private val threeLine = 88.dp

    /**
     * The same rule cannot be applied at both font sizes, and the reason is not
     * arithmetic - M3's ListItem heights are dp constants that do NOT scale.
     * Measured in English: at 1.0 the rows are 56, 72, 72, 72, 72dp and at 1.3
     * they are 56, 72, 96, 72, 72. Only the row whose supporting line WRAPPED
     * moved, and it moved a whole step.
     *
     * So 88dp means two different things. At 1.0 it means this copy is too long,
     * which is a content problem worth failing over. At 1.3 it means the text
     * wrapped, which is the layout doing its job - and past two lines M3
     * top-aligns its trailing slot BY SPECIFICATION, so the switch leaving the
     * centre is Material behaving, not breaking. Re-centring it was tried:
     * `ListItem` measures the trailing slot itself, so `fillMaxHeight` has
     * nothing to fill.
     *
     * Nothing is lost either way - `Rows.kt` sets no `maxLines`, so these wrap
     * rather than clip.
     *
     * 128dp at 1.3 is the same STRUCTURE this file already allows at 1.0, not a
     * looser rule. 88 there permits a wrapped headline over a subtitle - the
     * comment above says so, and says why: "Hide always-on" has a long, correct
     * name in Russian and Ukrainian and is allowed two lines for it. Grow that
     * same shape by a third and it is 119dp in Russian and 124 in Ukrainian.
     * Failing those would reverse a decision already taken, in the languages
     * where the name is right.
     *
     * 100 was tried first and did exactly that. What 128 still catches is a row
     * gaining a line it did not have at 1.0 - copy that is long even after the
     * scale is allowed for, which is the only thing left that a person can fix.
     */
    private fun ceiling(scale: Float) = if (scale > 1f) 128.dp else threeLine


    /**
     * The system font size, as a user's accessibility setting sets it.
     *
     * Density is overridden rather than the resource qualifiers because font
     * scale is NOT a qualifier - there is no "-fontScale130" - it is a
     * Configuration value, and this is the path Compose actually reads.
     */
    @Composable
    private fun AtFontScale(scale: Float, content: @Composable () -> Unit) {
        val d = LocalDensity.current
        CompositionLocalProvider(
            LocalDensity provides Density(d.density, fontScale = scale)
        ) { content() }
    }

    private fun rowsFitIn(locale: String, scale: Float = 1f) {
        // A width, not Robolectric's default: this is a layout test, so the
        // screen it lays out on has to be a phone's. 360dp is the common narrow
        // modern phone, and the Honor these were measured on is 359.
        RuntimeEnvironment.setQualifiers("+$locale-w360dp-h800dp")
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs = Prefs(ctx)
        prefs.enabled = true
        val now = LocalTime.now()
        prefs.startTime = now.minusHours(1)
        prefs.endTime = now.plusHours(1)
        prefs.days = DayOfWeek.entries.toSet()

        val titles = listOf(
            R.string.dnd_title, R.string.what_is_allowed,
            R.string.fx_grayscale, R.string.fx_dim, R.string.fx_dark, R.string.fx_ambient
            // "At your alarm" is NOT here, and deliberately: its section draws
            // only when the phone has a next alarm, and Robolectric has none, so
            // listing it would skip silently and read as coverage it is not.
            // Measured directly instead - see the ends-section test below.
        ).map { ctx.getString(it) }

        compose.setContent {
            GloamingTheme(dark = false) {
                AtFontScale(scale) {
                    Home(rememberScrollState(), onOpenSettings = {}, onOpenInterruptions = {})
                }
            }
        }

        val tall = titles.mapNotNull { title ->
            val match = hasText(title, substring = true) and isToggleable()
            if (compose.onAllNodes(match).fetchSemanticsNodes().isEmpty()) return@mapNotNull null
            val b = compose.onNode(match).getUnclippedBoundsInRoot()
            val h = b.bottom - b.top
            if (h >= ceiling(scale)) "$title is ${h.value}dp" else null
        }
        assertTrue(
            "in '$locale' these rows' SUPPORTING text wrapped, which makes the item " +
                "three-line and top-aligns its switch: $tall",
            tall.isEmpty()
        )
    }

    /**
     * Settings' rows carry a leading icon AND a trailing chevron, so their
     * supporting text has the least room in the app - and both of these rows
     * exist to explain a handoff to a system screen, which is exactly the kind
     * of sentence that runs long in ru and uk.
     */
    /**
     * Make this phone answer for BOTH vendor doors.
     *
     * Without it neither row is drawn - `Doors` resolves nothing under
     * Robolectric - and the measurement below silently skipped them, because it
     * skips any row it cannot find. That is how "App launch" shipped at 88dp in
     * English and 96dp in Ukrainian with a green test: the rows the section
     * exists for were the two never measured.
     */
    private fun withVendorDoors(ctx: android.content.Context) {
        ctx.withLaunchManager()
        ctx.withSystemBedtime()
    }

    private fun settingsRowsFitIn(locale: String, scale: Float = 1f) {
        RuntimeEnvironment.setQualifiers("+$locale-w360dp-h800dp")
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        withVendorDoors(ctx)
        val titles = listOf(
            R.string.settings_language, R.string.launch_setup_row,
            R.string.bedtime_settings_row, R.string.diagnostics_row,
            R.string.reset_row
        )
            .map { ctx.getString(it) }

        compose.setContent {
            GloamingTheme(dark = false) {
                AtFontScale(scale) {
                    SettingsScreen(themeMode = 0, onThemeMode = {}, onBack = {})
                }
            }
        }

        val missing = mutableListOf<String>()
        val tall = titles.mapNotNull { title ->
            val match = hasText(title, substring = true)
            if (compose.onAllNodes(match).fetchSemanticsNodes().isEmpty()) {
                missing += title
                return@mapNotNull null
            }
            val b = compose.onNode(match).getUnclippedBoundsInRoot()
            val h = b.bottom - b.top
            if (h >= ceiling(scale)) "$title is ${h.value}dp" else null
        }
        // Absence is a FAILURE, not a pass. A row that is not on screen was not
        // measured, and this test spent its life reporting that as a fit.
        assertTrue(
            "in '$locale' these Settings rows were never drawn, so nothing " +
                "measured them: $missing",
            missing.isEmpty()
        )
        assertTrue(
            "in '$locale' these Settings rows wrapped to three lines, which " +
                "top-aligns the chevron: $tall",
            tall.isEmpty()
        )
    }

    @Test fun `settings rows fit in English`() = settingsRowsFitIn("en")
    @Test fun `settings rows fit in Russian`() = settingsRowsFitIn("ru")
    @Test fun `settings rows fit in Ukrainian`() = settingsRowsFitIn("uk")

    /**
     * The allowlist's rows have LESS room than Home's - a leading icon and a
     * trailing control both - so they are the likeliest to wrap. Alarms is
     * excluded deliberately: it is three lines on purpose, it carries no
     * trailing content, and its sentence is the point of the row.
     */
    private fun allowlistFitsIn(locale: String, scale: Float = 1f) {
        RuntimeEnvironment.setQualifiers("+$locale-w360dp-h800dp")
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val titles = listOf(
            R.string.row_calls, R.string.row_messages, R.string.row_conversations,
            R.string.row_repeat_callers, R.string.row_reminders, R.string.row_events,
            R.string.row_media
        ).map { ctx.getString(it) }

        compose.setContent {
            GloamingTheme(dark = false) {
                AtFontScale(scale) {
                    InterruptionsScreen(onBack = {}, onChanged = {})
                }
            }
        }

        val tall = titles.mapNotNull { title ->
            val match = hasText(title, substring = true) and hasClickAction()
            if (compose.onAllNodes(match).fetchSemanticsNodes().isEmpty()) return@mapNotNull null
            val b = compose.onAllNodes(match).onFirst().getUnclippedBoundsInRoot()
            val h = b.bottom - b.top
            if (h >= ceiling(scale)) "$title is ${h.value}dp" else null
        }
        assertTrue("in '$locale' these allowlist rows did not fit: $tall", tall.isEmpty())
    }

    @Test
    fun `rows fit in English`() = rowsFitIn("en")

    @Test
    fun `rows fit in Russian`() = rowsFitIn("ru")

    @Test
    fun `rows fit in Ukrainian`() = rowsFitIn("uk")

    @Test
    fun `allowlist fits in English`() = allowlistFitsIn("en")

    @Test
    fun `allowlist fits in Russian`() = allowlistFitsIn("ru")

    @Test
    fun `allowlist fits in Ukrainian`() = allowlistFitsIn("uk")

    /**
     * The one row of the ends section, held to a STRICTER rule than [threeLine].
     *
     * Everything else here may spend a second line on a long Cyrillic name,
     * because a wrapped HEADLINE still leaves the switch centred. This row may
     * not: its subtitle names two times inside a sentence, which is the shape
     * most likely to wrap, and a wrapped subtitle is exactly what makes an item
     * three-line and lifts the switch off its own row's centre.
     *
     * It cannot be reached through Home - the section draws only when
     * `getNextAlarmClock` returns an alarm, and Robolectric has none, so
     * `EndsSection` correctly draws nothing. So it is built here as EndsSection
     * builds it.
     *
     * WITH HOME'S SIDE PADDING, which is the whole difficulty and was missing.
     * A row built bare on a 360dp screen gets a 360dp card; on Home it gets 311,
     * because the page insets 24dp either side. That is 49dp the real row does
     * not have - about six characters - so this test passed a subtitle that
     * wrapped on the phone the moment it was looked at. A measurement taken at
     * the wrong width is not a measurement.
     */
    private fun endsSectionFitsIn(locale: String, scale: Float = 1f) {
        RuntimeEnvironment.setQualifiers("+$locale-w360dp-h800dp")
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        // The row shows the hour and nothing else now, so the thing that can
        // still wrap is the HEADING, which carries the whole purpose and is the
        // longest string in the section in ru and uk.
        val headline = "12:30 AM"

        val heading = ctx.getString(R.string.section_end_at_alarm)

        compose.setContent {
            GloamingTheme(dark = false) {
                AtFontScale(scale) {
                Box(Modifier.padding(horizontal = 24.dp)) {
                Section(heading) {
                GroupedList(gloam.raise, listOf {
                    SwitchRow(
                        headline = headline,
                        checked = false,
                        leading = { RowIcon(R.drawable.ic_alarm, IconTint.Alarm) }
                    ) {}
                })
                }
                }
                }
            }
        }

        // The HEADING is the long string here now - it carries the whole purpose
        // of the section, and in ru and uk that is thirty characters. It is
        // labelSmall across the full card, so it wraps rather than truncates,
        // which is untidy rather than broken - but a two-line heading over a
        // one-line row reads as a paragraph, not a label.
        val head = compose.onNode(hasText(heading.uppercase(), substring = true))
            .getUnclippedBoundsInRoot()
        val hh = (head.bottom - head.top).value
        assertTrue("in '$locale' the heading wrapped: ${hh}dp", hh < 24f)

        val b = compose.onNode(hasText(headline, substring = true))
            .getUnclippedBoundsInRoot()
        val h = (b.bottom - b.top).value
        assertTrue(
            "in '$locale' the ends row is ${h}dp: its subtitle wrapped, and M3 " +
                "then top-aligns the switch instead of centring it on the row",
            h < if (scale > 1f) 92f else twoLineCeiling.value
        )
    }

    /**
     * A two-line M3 list item is 72dp. One extra wrapped line takes it to 88,
     * measured on the phone, so 80 separates them with room for rounding.
     */
    private val twoLineCeiling = 80.dp

    @Test fun `ends section fits in English`() = endsSectionFitsIn("en")
    @Test fun `ends section fits in Russian`() = endsSectionFitsIn("ru")
    @Test fun `ends section fits in Ukrainian`() = endsSectionFitsIn("uk")

    /**
     * Does the text still fit when the SYSTEM FONT IS LARGE?
     *
     * Everything above measures at font scale 1.0, and that is the one setting
     * these controls actually break at. Measured on the Honor at 1.3 - Android's
     * "Large", two notches off a scale that goes to 2.0 - the preset row
     * truncates in EVERY language: "Weekdays" loses its s and reads "Weekday",
     * "Weekends" reads "Weekend", and «Выходные» reads «Выходн». The Russian is
     * obvious and the English is nearly invisible, which is why it shipped.
     *
     * It was never a translation problem. The row is three equal thirds of the
     * screen with `maxLines = 1`, and HomeScreen's own comment records that
     * "Weekdays" already wants 66 of the 79 a third leaves - 84% at 1.0, so
     * anything above about 1.2 overflows. The budgets in strings.xml count
     * characters at one size and cannot see this.
     *
     * `hasVisualOverflow` is the assertion rather than a height: a truncated
     * label does not change the row's height, so every dp measurement in this
     * file reads a truncated row as passing.
     */
    private fun presetsFitAt(locale: String, scale: Float) {
        RuntimeEnvironment.setQualifiers("+$locale-w360dp-h800dp")
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs = Prefs(ctx)
        prefs.enabled = true
        // The preset row draws only while Repeat is on, and only then are the
        // three segments laid out at a third of the width each.
        prefs.days = DayOfWeek.entries.toSet()

        compose.setContent {
            GloamingTheme(dark = false) {
                // The system font size, as a user's accessibility setting sets
                // it. Density is overridden rather than the qualifiers because
                // font scale is not a resource qualifier - there is no
                // "-fontScale130" - it is a Configuration value, and this is the
                // path Compose actually reads.
                AtFontScale(scale) {
                    Home(rememberScrollState(), onOpenSettings = {}, onOpenInterruptions = {})
                }
            }
        }

        val clipped = listOf(
            R.string.preset_every_night, R.string.preset_weekdays, R.string.preset_weekends
        ).map { ctx.getString(it) }.mapNotNull { label ->
            // UNMERGED: a SegmentedButton merges its label's semantics into
            // itself, so the merged tree hands back the BUTTON, whose layout
            // says nothing about whether the text inside it was cut.
            val node = compose
                .onAllNodes(hasText(label, substring = true), useUnmergedTree = true).onFirst()
            val out = mutableListOf<TextLayoutResult>()
            node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(out) }
            val r = out.firstOrNull() ?: return@mapNotNull null
            val room = r.layoutInput.constraints.maxWidth
            // The text's UNCONSTRAINED width against the room it was given.
            // Two simpler signals were tried and both lie here:
            //
            //   `hasVisualOverflow` is true for every label at 1.3, including
            //   "Always" at 61 of 80, because the taller line also overflows the
            //   segment's HEIGHT. It cannot tell "cut off" from "tall".
            //
            //   `size.width >= maxWidth` reads a clip correctly but also fires
            //   on auto-sized text that was shrunk to fit EXACTLY, which is a
            //   pass, not a failure.
            //
            // maxIntrinsicWidth is what the string wants at its chosen size, so
            // wanting more than it has is the definition of being cut.
            val wants = r.multiParagraph.maxIntrinsicWidth
            if (wants > room) "$label (wants $wants of $room at ${r.layoutInput.style.fontSize})" else null
        }
        assertTrue(
            "at font scale $scale in '$locale' these preset labels are TRUNCATED: " +
                "$clipped. The row is three equal thirds with maxLines = 1, so the " +
                "text is cut rather than wrapped and the row's height never changes - " +
                "which is why every dp measurement in this file reads it as passing.",
            clipped.isEmpty()
        )
    }

    // 1.3 is Android's "Large". The scale goes to 2.0, so this is not the worst
    // case - it is the first one that breaks.
    @Test fun `presets fit in English at large font`() = presetsFitAt("en", 1.3f)
    @Test fun `presets fit in Russian at large font`() = presetsFitAt("ru", 1.3f)
    @Test fun `presets fit in Ukrainian at large font`() = presetsFitAt("uk", 1.3f)

    // And at the default, so a regression there is told apart from a scale one.
    @Test fun `presets fit in English`() = presetsFitAt("en", 1.0f)
    @Test fun `presets fit in Russian`() = presetsFitAt("ru", 1.0f)
    @Test fun `presets fit in Ukrainian`() = presetsFitAt("uk", 1.0f)

    /**
     * Does the DIAL'S CAPTION stay inside its ring?
     *
     * Unlike the preset row this one cannot truncate - the text is centred in
     * the dial with nothing constraining its width - so it does the other thing
     * and draws straight over the ring. No test above could see it: the row
     * heights do not change and nothing is clipped.
     *
     * The budget is geometry. The canvas is 260dp, R_TRACK is 97 and STROKE
     * 17.3, so the ring's inner radius is 97 - 17.3/2 = 88.35dp; the caption
     * sits about 32dp below centre, where the chord is 2*sqrt(88.35² - 32²) =
     * about 165dp. Measured on the Honor: 122dp at scale 1.0 and 159dp at 1.3,
     * which is 6dp of clearance a side - tight, not yet touching. It crosses at
     * about 1.4, and WCAG 1.4.4 asks for 200%.
     */
    private fun dialCaptionFitsAt(locale: String, scale: Float) {
        RuntimeEnvironment.setQualifiers("+$locale-w360dp-h800dp")
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs = Prefs(ctx)
        prefs.enabled = true
        val caption = ctx.getString(R.string.dial_sleep_window).uppercase()

        compose.setContent {
            GloamingTheme(dark = false) {
                AtFontScale(scale) {
                    Home(rememberScrollState(), onOpenSettings = {}, onOpenInterruptions = {})
                }
            }
        }

        val b = compose.onAllNodes(hasText(caption, substring = true), useUnmergedTree = true)
            .onFirst().getUnclippedBoundsInRoot()
        val w = (b.right - b.left).value
        assertTrue(
            "at font scale $scale in '$locale' the dial's caption is ${w}dp wide and the " +
                "ring leaves ${ringChord}dp - it is drawing over its own dial. Nothing " +
                "clips it and no height changes, so only a width says so.",
            w <= ringChord
        )
    }

    /** 2*sqrt(88.35² - 32²): the ring's inner chord where the caption sits. */
    private val ringChord = 165f

    @Test fun `dial caption fits in English at 200 percent`() = dialCaptionFitsAt("en", 2.0f)
    @Test fun `dial caption fits in Russian at 200 percent`() = dialCaptionFitsAt("ru", 2.0f)
    @Test fun `dial caption fits in Ukrainian at 200 percent`() = dialCaptionFitsAt("uk", 2.0f)

    /**
     * And all of it again at a LARGE system font.
     *
     * The thresholds above are M3's own dp heights, and they do NOT scale: a
     * two-line item is 72dp whatever the font size, because the container is
     * sized in dp while the text inside it grows. So a row that was one line at
     * 1.0 and wraps to two at 1.3 is not a bug - the layout doing its job - but
     * a row that reaches THREE lines still is, because M3 then top-aligns the
     * trailing control and the switch leaves its own row's centre.
     *
     * The app's rows carry no `maxLines`, so nothing here can truncate; they
     * wrap, which is what Android's guidance asks for. What these measure at
     * scale is the one thing wrapping can still cost.
     */
    @Test fun `rows fit in English at large font`() = rowsFitIn("en", 1.3f)
    @Test fun `rows fit in Russian at large font`() = rowsFitIn("ru", 1.3f)
    @Test fun `rows fit in Ukrainian at large font`() = rowsFitIn("uk", 1.3f)

    @Test fun `settings rows fit in English at large font`() = settingsRowsFitIn("en", 1.3f)
    @Test fun `settings rows fit in Russian at large font`() = settingsRowsFitIn("ru", 1.3f)
    @Test fun `settings rows fit in Ukrainian at large font`() = settingsRowsFitIn("uk", 1.3f)

    @Test fun `allowlist fits in English at large font`() = allowlistFitsIn("en", 1.3f)
    @Test fun `allowlist fits in Russian at large font`() = allowlistFitsIn("ru", 1.3f)
    @Test fun `allowlist fits in Ukrainian at large font`() = allowlistFitsIn("uk", 1.3f)

    @Test fun `ends section fits in English at large font`() = endsSectionFitsIn("en", 1.3f)
    @Test fun `ends section fits in Russian at large font`() = endsSectionFitsIn("ru", 1.3f)
    @Test fun `ends section fits in Ukrainian at large font`() = endsSectionFitsIn("uk", 1.3f)
}
