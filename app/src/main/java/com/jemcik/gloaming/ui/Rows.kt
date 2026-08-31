package com.jemcik.gloaming.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.Role
import com.jemcik.gloaming.R

/**
 * The app's one list row, on M3's [ListItem].
 *
 * There were six of these: AllowRow, EffectRow, two written inline on Home,
 * LinkRow and PermissionRow. They disagreed about everything a row can disagree
 * about - leading element, trailing element, subtitle style, container, and the
 * "go here" affordance, which existed at 22dp, at 24dp, and as a literal `›`
 * sized by borrowing titleLarge. The same conceptual row looked like three
 * different components depending on which screen you were on.
 *
 * Two things are deliberately NOT M3's defaults, and both are passed in rather
 * than inherited:
 *
 * - **Typography.** M3's ListItem uses BodyLarge for the headline and BodyMedium
 *   for the supporting line. This app's type scale assigns row titles to
 *   titleMedium and everything at reading size to bodySmall, which is a measured
 *   decision (see the four-sizes note in CLAUDE.md). Slot content is ours, so
 *   the structure comes from M3 and the type from the app.
 * - **The container.** M3's list items sit on `surface`; these sit inside the
 *   app's `raise` cards, so the container is transparent and the card behind
 *   shows through. M3 does not forbid a container, it just does not specify one.
 *
 * What DOES come from M3 now: the metrics. Rows were 64dp on two lines against
 * the spec's 72, and 84dp on three against 88.
 */
@Composable
private fun rowColors() = ListItemDefaults.colors(
    containerColor = Color.Transparent,
    headlineColor = gloam.onSurface,
    supportingColor = gloam.onSurfaceLow,
    leadingIconColor = gloam.onSurfaceLow,
    trailingIconColor = gloam.onSurfaceLow,
    disabledHeadlineColor = gloam.onSurface,
    disabledLeadingIconColor = gloam.onSurfaceLow,
    disabledTrailingIconColor = gloam.onSurfaceLow
)

@Composable
private fun Headline(text: String) =
    Text(text, style = MaterialTheme.typography.titleMedium)

@Composable
private fun Supporting(text: String) =
    Text(text, style = MaterialTheme.typography.bodySmall)

/**
 * A row whose whole width toggles. The switch is the indicator and takes no
 * input of its own, so there is one semantics node and TalkBack says
 * "Reminders, switch, on" once.
 */
@Composable
fun SwitchRow(
    headline: String,
    checked: Boolean,
    // `modifier` is the FIRST optional parameter, which is Compose's own API
    // guideline and what lint's ModifierParameter checks. Every call site here
    // passes by name, so the order is convention rather than ergonomics - but
    // the convention is the point when the next reader is an Android developer.
    modifier: Modifier = Modifier,
    supporting: String? = null,
    // The @Composable slot sits AHEAD of the action lambda, so a trailing
    // lambda at the call site binds to the action and not to the slot. Getting
    // that backwards once cost an evening: Compose ran the click handler as
    // content on every composition, which opened a sheet that swallowed every
    // touch on the screen beneath it.
    leading: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    val press = remember { MutableInteractionSource() }
    ListItem(
        headlineContent = { Headline(headline) },
        supportingContent = supporting?.let { { Supporting(it) } },
        leadingContent = leading,
        trailingContent = {
            GloamSwitch(checked = checked, enabled = enabled, interactionSource = press)
        },
        colors = rowColors(),
        modifier = modifier.toggleable(
            value = checked,
            enabled = enabled,
            interactionSource = press,
            indication = ripple(),
            role = Role.Switch,
            onValueChange = onCheckedChange
        )
    )
}

/** A row that leaves for somewhere else. The chevron is Google's own path. */
@Composable
fun LinkRow(
    headline: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    leading: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Headline(headline) },
        supportingContent = supporting?.let { { Supporting(it) } },
        leadingContent = leading,
        trailingContent = {
            Icon(
                painterResource(R.drawable.ic_chevron),
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
        },
        colors = rowColors(),
        modifier = modifier.clickable(enabled = enabled, onClick = onClick)
    )
}

/** A row that states something and offers no control. */
@Composable
fun StaticRow(
    headline: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    leading: (@Composable () -> Unit)? = null
) {
    ListItem(
        headlineContent = { Headline(headline) },
        supportingContent = supporting?.let { { Supporting(it) } },
        leadingContent = leading,
        colors = rowColors(),
        modifier = modifier
    )
}

/**
 * A row carrying an arbitrary trailing control - a button, a word. The slot is
 * ahead of any lambda in the parameter list on purpose: a trailing lambda binds
 * to the LAST parameter, and a @Composable slot that lands there gets invoked as
 * content on every composition. That cost an evening once already.
 */
@Composable
fun ActionRow(
    headline: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    // The only row here that had no leading slot, which is why the permission
    // panel's text started 40dp from the edge where every other row on the
    // screen starts at 80 - the same ragged left edge "what can wake you" had.
    leading: (@Composable () -> Unit)? = null,
    trailing: @Composable () -> Unit
) {
    ListItem(
        headlineContent = { Headline(headline) },
        supportingContent = supporting?.let { { Supporting(it) } },
        leadingContent = leading,
        trailingContent = trailing,
        colors = rowColors(),
        modifier = modifier
    )
}

/**
 * One option of a single choice.
 *
 * A Row rather than a ListItem, and that is the one place in the app where the
 * two part company. A ListItem's height comes from tokens by line count - 56dp
 * for one line - with no density parameter, and 56dp around a 20dp radio button
 * is a lot of air when three of them sit together in a card. Reported as too
 * much vertical separation, and it was: the rows were already flush, so all of
 * it was inside them.
 *
 * 48dp is Android's minimum touch target, so this is as tight as a row of this
 * kind is allowed to be. The leading box is fixed at 24dp so the label lands on
 * the same 80dp inset as every icon row elsewhere - RadioButton would otherwise
 * claim its own 48dp interactive size and push the text 24dp right of everything
 * else on the screen.
 *
 * The ROW takes the input and the button is the indicator, as everywhere else.
 * Colours are named rather than defaulted for the reason the TimePicker's nine
 * are: an unset ColorScheme role falls back to Material's baseline violet.
 */
@Composable
fun RadioRow(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onSelect: () -> Unit
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = CARD_PAD),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            RadioButton(
                selected = selected,
                onClick = null,
                colors = RadioButtonDefaults.colors(
                    selectedColor = gloam.stateOn,
                    unselectedColor = gloam.onSurfaceLow
                )
            )
        }
        Spacer(Modifier.width(16.dp))
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            color = gloam.onSurface
        )
    }
}


/**
 * A leading icon, tinted per row.
 *
 * A TONAL CONTAINER was tried first - M3's list samples use one, 40dp with a
 * 24dp icon - and `RowFitTest` rejected it: the extra width wrapped a Russian
 * subtitle onto a third line, which top-aligns a ListItem's switch. 36dp and
 * 32dp failed the same way, so the container was never worth what it cost.
 * The colour was the point anyway; the circle behind it was not.
 *
 * 24dp, M3's size for a list item's leading icon, and the tint is the ink of
 * the row's [IconTint] rather than LocalContentColor.
 */
@Composable
fun RowAvatar(id: Int, tint: IconTint) {
    Icon(
        painterResource(id),
        contentDescription = null,
        tint = tint.ink,
        modifier = Modifier.size(24.dp)
    )
}
