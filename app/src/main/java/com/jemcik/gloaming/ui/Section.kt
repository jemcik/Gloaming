package com.jemcik.gloaming.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/* Both screens group by the same two devices, so they live together. */

/** Names a block. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        // uppercase() with no locale uses the default one, which is not always
        // the one the text is in.
        text.uppercase(LocalLocale.current.platformLocale),
        style = MaterialTheme.typography.labelSmall,
        color = gloam.onSurfaceLow,
        modifier = modifier.fillMaxWidth()
    )
}

/**
 * The boundary between two blocks. `line`, not `veil` - veil is invisible at
 * 1dp. Full content width, which is what Material reserves for separating
 * unrelated sections; inset rules divide items within one.
 */
@Composable
fun SectionRule() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(gloam.line)
    )
}

/** Between functional blocks, either side of a rule. */
val GROUP = 18.dp

/** Within one block. */
val TIGHT = 10.dp

/** Every card in the app. */
val CORNER = 28.dp

/** The content margin, on every screen. */
val SCREEN_PAD = 24.dp

/**
 * Inside a card, and inside a list row. It is M3's own ListItemStartPadding, so
 * a divider drawn at this inset lines up with the row content above it.
 */
val CARD_PAD = 16.dp

/**
 * A rule, a name, and the block they introduce - in one place, because four
 * hand-assembled copies had drifted into four different rhythms.
 *
 * Measured before this existed: the gap between a section's LABEL and its
 * content was `TIGHT` under "which days", 18dp under "what can wake you" and
 * ZERO under "how the screen looks" - three sections of one screen, each
 * built by hand and each different. The allowlist had a fourth value again.
 * Nothing chose those numbers; they were whatever the enclosing Column's
 * `Arrangement` happened to be, which differed per section because some wrapped
 * their content in an inner Column and some did not.
 *
 * So the rhythm is stated once here and the call sites cannot disagree: GROUP
 * throughout - above the rule, below it, and between the name and what it names.
 * That is "what can wake you"'s spacing, chosen by eye against the other three.
 * TIGHT is kept for gaps WITHIN a block, between one control and the next. The
 * The gap ABOVE the rule is the parent's, because a Column's arrangement will
 * add it whether this wants it or not - adding one here too simply doubled it.
 * So both screens' columns are spacedBy(GROUP) and every section on either one
 * has the same three gaps.
 */
@Composable
fun Section(
    title: String,
    modifier: Modifier = Modifier,
    // False for the first section on a screen, where there is nothing above to
    // divide it FROM and the app bar's own edge already separates it.
    rule: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier.fillMaxWidth()) {
        if (rule) {
            SectionRule()
            Spacer(Modifier.height(GROUP))
        }
        SectionLabel(title)
        Spacer(Modifier.height(GROUP))
        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(TIGHT),
            content = content
        )
    }
}
