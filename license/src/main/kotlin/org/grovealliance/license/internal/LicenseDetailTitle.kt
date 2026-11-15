//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.license.internal

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import org.grovealliance.ui.ComposableContent
import org.grovealliance.ui.StringResource
import org.grovealliance.ui.theme.Colors
import org.grovealliance.ui.theme.GroveTheme
import org.grovealliance.ui.theme.TextStyles
import org.grovealliance.ui.theme.ThemePreviews

/**
 * An app bar title naming the software whose license is shown, with an optional [subtitle] such as
 * where it is published. Both are cut to one line.
 */
internal data class LicenseDetailTitle(
    val title: StringResource,
    val subtitle: StringResource?,
) : ComposableContent {

    @Composable
    override fun Content(modifier: Modifier) {
        Column(
            modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title.text(),
                style = TextStyles.titleMedium,
                color = Colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            subtitle?.let {
                Text(
                    text = it.text(),
                    style = TextStyles.bodySmall,
                    color = Colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private class LicenseDetailTitlePreviewProvider : PreviewParameterProvider<LicenseDetailTitle> {
    private val base = LicenseDetailTitle(
        title = StringResource("Example Library"),
        subtitle = StringResource("github.com/example/library"),
    )
    override val values = sequenceOf(
        base,
        base.copy(subtitle = null),
    )
}

@ThemePreviews
@Composable
private fun Preview(@PreviewParameter(LicenseDetailTitlePreviewProvider::class) content: LicenseDetailTitle) {
    GroveTheme { content.Content() }
}
