package com.jemcik.gloaming.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.jemcik.gloaming.R
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.Color
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

/** Every card in the app. */
val CORNER = 28.dp

/* ── Grouped lists ─────────────────────────────────────────────────────────
   Rows used to sit inside ONE container divided by hairlines. They are separate
   containers now, spaced 2dp apart, with the group's OUTER corners large and
   the corners BETWEEN items small - so the block still reads as one thing made
   of parts rather than as several unrelated cards. That distinction is the
   whole trick: a first attempt with uniform corners and a 6dp gap did read as
   unrelated cards, which is the obvious objection to separating rows at all.

   M3 states the same idea for connected button groups, and the numbers here are
   its: BetweenSpace 2dp, a large ContainerShape, a small InnerCorner. At 2dp
   the separation reads as a hairline of the PAGE showing through, which is why
   this also retires the question of what colour a divider should be - there is
   no divider left to colour.

   Cost, measured on the effects block: 165dp to 175dp for three rows. The
   vertical price was the main argument against doing this and it was wrong. */
val LIST_GAP = 2.dp
private val LIST_OUTER = CORNER   // a group IS a card made of parts
private val LIST_INNER = 6.dp

/** The shape of item [i] of [n] in a grouped list. */
fun listItemShape(i: Int, n: Int) = RoundedCornerShape(
    topStart = if (i == 0) LIST_OUTER else LIST_INNER,
    topEnd = if (i == 0) LIST_OUTER else LIST_INNER,
    bottomStart = if (i == n - 1) LIST_OUTER else LIST_INNER,
    bottomEnd = if (i == n - 1) LIST_OUTER else LIST_INNER
)

/**
 * A grouped list. Items are passed as a list rather than a trailing lambda
 * because each one's SHAPE depends on how many there are, and Compose cannot
 * count children it has not composed yet. `buildList` at the call site keeps
 * conditional rows readable.
 */
@Composable
fun GroupedList(color: Color, items: List<@Composable () -> Unit>) {
    Column(verticalArrangement = Arrangement.spacedBy(LIST_GAP)) {
        items.forEachIndexed { i, item ->
            Surface(
                color = color,
                shape = listItemShape(i, items.size),
                modifier = Modifier.fillMaxWidth()
            ) { item() }
        }
    }
}

/** Between functional blocks, either side of a rule. */
val GROUP = 18.dp

/** Within one block. */
val TIGHT = 10.dp


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

/**
 * The chrome both detail screens wear: a Scaffold, and a top app bar carrying
 * the title and Back.
 *
 * It existed twice, byte for byte apart from the title, and SettingsScreen said
 * so in a comment - "the same top app bar as the allowlist, for the same
 * reason" - which is duplication acknowledged rather than removed. The reason
 * it is worth one composable is that the bar's details are each a DECISION, and
 * a decision copied is a decision that can drift:
 *
 * Back lives in the bar rather than in the scrolling column, because it used to
 * be a row inside the content and on a nine-row screen the only visible way out
 * scrolled off the top.
 *
 * And there is no scrollBehavior, deliberately. M3's default goes transparent
 * at rest and `raise` the moment anything scrolls under it, which is a step
 * change that reads as a blink; both bars are one constant colour, for the same
 * reason Home's is.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable (PaddingValues) -> Unit
) {
    val g = gloam
    Scaffold(
        containerColor = g.surface,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleLarge, color = g.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painterResource(R.drawable.ic_back),
                            contentDescription = stringResource(R.string.action_back),
                            tint = g.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = g.raise,
                    scrolledContainerColor = g.raise,
                    titleContentColor = g.onSurface,
                    navigationIconContentColor = g.onSurface
                )
            )
        },
        content = content
    )
}
