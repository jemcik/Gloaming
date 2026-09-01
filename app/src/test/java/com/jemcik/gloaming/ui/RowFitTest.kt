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

    private fun rowsFitIn(locale: String) {
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
                Home(rememberScrollState(), onOpenSettings = {}, onOpenInterruptions = {})
            }
        }

        val tall = titles.mapNotNull { title ->
            val match = hasText(title, substring = true) and isToggleable()
            if (compose.onAllNodes(match).fetchSemanticsNodes().isEmpty()) return@mapNotNull null
            val b = compose.onNode(match).getUnclippedBoundsInRoot()
            val h = b.bottom - b.top
            if (h >= threeLine) "$title is ${h.value}dp" else null
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
    private fun settingsRowsFitIn(locale: String) {
        RuntimeEnvironment.setQualifiers("+$locale-w360dp-h800dp")
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val titles = listOf(
            R.string.settings_language, R.string.launch_setup_row,
            R.string.bedtime_settings_row
        )
            .map { ctx.getString(it) }

        compose.setContent {
            GloamingTheme(dark = false) {
                SettingsScreen(themeMode = 0, onThemeMode = {}, onBack = {})
            }
        }

        val tall = titles.mapNotNull { title ->
            val match = hasText(title, substring = true)
            if (compose.onAllNodes(match).fetchSemanticsNodes().isEmpty()) return@mapNotNull null
            val b = compose.onNode(match).getUnclippedBoundsInRoot()
            val h = b.bottom - b.top
            if (h >= threeLine) "$title is ${h.value}dp" else null
        }
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
    private fun allowlistFitsIn(locale: String) {
        RuntimeEnvironment.setQualifiers("+$locale-w360dp-h800dp")
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val titles = listOf(
            R.string.row_calls, R.string.row_messages, R.string.row_conversations,
            R.string.row_repeat_callers, R.string.row_reminders, R.string.row_events,
            R.string.row_media
        ).map { ctx.getString(it) }

        compose.setContent {
            GloamingTheme(dark = false) {
                InterruptionsScreen(onBack = {}, onChanged = {})
            }
        }

        val tall = titles.mapNotNull { title ->
            val match = hasText(title, substring = true) and hasClickAction()
            if (compose.onAllNodes(match).fetchSemanticsNodes().isEmpty()) return@mapNotNull null
            val b = compose.onAllNodes(match).onFirst().getUnclippedBoundsInRoot()
            val h = b.bottom - b.top
            if (h >= threeLine) "$title is ${h.value}dp" else null
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
    private fun endsSectionFitsIn(locale: String) {
        RuntimeEnvironment.setQualifiers("+$locale-w360dp-h800dp")
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        // The widest time a 12-hour locale can produce, on BOTH lines - the
        // headline names the alarm and the subtitle the wake time, so each has
        // to hold one of these on its own.
        val headline = ctx.getString(R.string.row_end_at_alarm, "12:30 AM")
        val sub = ctx.getString(R.string.row_end_at_alarm_instead, "12:30 AM")

        compose.setContent {
            GloamingTheme(dark = false) {
                Box(Modifier.padding(horizontal = 24.dp)) {
                GroupedList(gloam.raise, listOf {
                    SwitchRow(
                        headline = headline,
                        supporting = sub,
                        checked = false,
                        leading = { RowIcon(R.drawable.ic_alarm, IconTint.Alarm) }
                    ) {}
                })
                }
            }
        }

        val b = compose.onNode(hasText(headline, substring = true))
            .getUnclippedBoundsInRoot()
        val h = (b.bottom - b.top).value
        assertTrue(
            "in '$locale' the ends row is ${h}dp: its subtitle wrapped, and M3 " +
                "then top-aligns the switch instead of centring it on the row",
            h < twoLineCeiling.value
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
}
