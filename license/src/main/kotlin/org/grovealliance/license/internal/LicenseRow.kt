//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.license.internal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import org.grovealliance.ui.ComposableContent
import org.grovealliance.ui.StringResource
import org.grovealliance.ui.noRippleClickable
import org.grovealliance.ui.theme.Colors
import org.grovealliance.ui.theme.GroveShapes
import org.grovealliance.ui.theme.GroveTheme
import org.grovealliance.ui.theme.Sizes
import org.grovealliance.ui.theme.Spacings
import org.grovealliance.ui.theme.TextStyles
import org.grovealliance.ui.theme.ThemePreviews
import org.grovealliance.ui.theme.medium

/**
 * A piece of software: its [name] with its [license] label, and its [version] below. A row with an
 * [onClick] shows a trailing chevron and can be tapped.
 *
 * @param name the software's name
 * @param license the label of the license it is used under, if known
 * @param version the version in use, already formatted, if known
 * @param onClick called when the row is tapped
 */
internal data class LicenseRow(
    val name: StringResource,
    val license: StringResource?,
    val version: StringResource?,
    val onClick: (() -> Unit)?,
) : ComposableContent {

    @Composable
    override fun Content(modifier: Modifier) {
        val onClick = onClick
        Row(
            modifier = modifier
                .fillMaxWidth()
                .then(if (onClick == null) Modifier else Modifier.noRippleClickable(onClick = onClick))
                .padding(Spacings.medium),
            horizontalArrangement = Arrangement.spacedBy(Spacings.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacings.tiny),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacings.small),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        modifier = Modifier.weight(weight = 1f, fill = false),
                        text = name.text(),
                        style = TextStyles.bodyLarge.medium(),
                        color = Colors.onSurface,
                    )
                    license?.let {
                        Text(
                            modifier = Modifier
                                .clip(GroveShapes.small)
                                .background(color = Colors.surfaceContainerHigh)
                                .padding(
                                    horizontal = Spacings.extraSmall,
                                    vertical = Spacings.tiny,
                                ),
                            text = it.text(),
                            style = TextStyles.labelMedium,
                            color = Colors.onSurface,
                            maxLines = 1,
                        )
                    }
                }
                version?.let {
                    Text(
                        text = it.text(),
                        style = TextStyles.bodyMedium,
                        color = Colors.onSurface,
                    )
                }
            }
            if (onClick != null) {
                Icon(
                    modifier = Modifier.size(Sizes.Icon.extraSmall),
                    imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                    tint = Colors.onSurfaceVariant,
                    contentDescription = null,
                )
            }
        }
    }
}

private class LicenseRowPreviewProvider : PreviewParameterProvider<LicenseRow> {
    private val base = LicenseRow(
        name = StringResource("Example Library"),
        license = StringResource("Apache-2.0"),
        version = StringResource("Version: 1.2.3"),
        onClick = {},
    )
    override val values = sequenceOf(
        base,
        base.copy(name = StringResource("An example library with a name too long to fit on one line")),
        base.copy(
            license = null,
            version = null,
            onClick = null,
        ),
    )
}

@ThemePreviews
@Composable
private fun Preview(@PreviewParameter(LicenseRowPreviewProvider::class) content: LicenseRow) {
    GroveTheme { content.Content() }
}
