//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.license.internal

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import org.grovealliance.ui.ComposableContent
import org.grovealliance.ui.StringResource
import org.grovealliance.ui.theme.Colors
import org.grovealliance.ui.theme.GroveTheme
import org.grovealliance.ui.theme.Spacings
import org.grovealliance.ui.theme.TextStyles
import org.grovealliance.ui.theme.ThemePreviews

/**
 * The full, selectable [text] of a license, scrolling when it doesn't fit.
 */
internal data class LicenseDetailLayout(
    val text: StringResource,
) : ComposableContent {

    @Composable
    override fun Content(modifier: Modifier) {
        SelectionContainer(
            modifier = modifier
                .fillMaxWidth()
                .verticalScroll(state = rememberScrollState()),
        ) {
            Text(
                modifier = Modifier.padding(Spacings.medium),
                text = text.text(),
                style = TextStyles.bodyMedium,
                color = Colors.onSurface,
            )
        }
    }
}

private class LicenseDetailLayoutPreviewProvider : PreviewParameterProvider<LicenseDetailLayout> {
    private val base = LicenseDetailLayout(
        text = StringResource(
            "MIT License\n\nCopyright (c) 2026 Example Authors\n\n" +
                "Permission is hereby granted, free of charge, to any person obtaining a copy of this software " +
                "and associated documentation files (the \"Software\"), to deal in the Software without restriction.",
        ),
    )
    override val values = sequenceOf(base)
}

@ThemePreviews
@Composable
private fun Preview(@PreviewParameter(LicenseDetailLayoutPreviewProvider::class) content: LicenseDetailLayout) {
    GroveTheme { content.Content() }
}
