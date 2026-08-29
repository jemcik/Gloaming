package com.jemcik.gloaming.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
fun SectionLabel(text: String) {
    Text(
        // uppercase() with no locale uses the default one, which is not always
        // the one the text is in.
        text.uppercase(LocalLocale.current.platformLocale),
        style = MaterialTheme.typography.labelSmall,
        color = gloam.onSurfaceLow,
        modifier = Modifier.fillMaxWidth()
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
