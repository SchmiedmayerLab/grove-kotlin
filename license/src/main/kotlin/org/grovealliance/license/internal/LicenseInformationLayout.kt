//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.license.internal

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.grovealliance.ui.CardRow
import org.grovealliance.ui.ComposableContent
import org.grovealliance.ui.GroveSectionList
import org.grovealliance.ui.StringResource
import org.grovealliance.ui.groveSectionList
import org.grovealliance.ui.horizontalSlideBackward
import org.grovealliance.ui.horizontalSlideForward
import org.grovealliance.ui.theme.GroveTheme
import org.grovealliance.ui.theme.ThemePreviews

/**
 * The [list] of the project and its dependencies, replaced by a [detail] license text while one is open,
 * which slides in over the list and back out again. Going back from the license text calls
 * [onBack]; the list keeps its scroll position meanwhile.
 */
internal data class LicenseInformationLayout(
    val list: GroveSectionList,
    val detail: StateFlow<LicenseDetailLayout?>,
    val onBack: () -> Unit,
) : ComposableContent {

    @Composable
    override fun Content(modifier: Modifier) {
        val detail by detail.collectAsStateWithLifecycle()
        val listState = rememberSaveableStateHolder()
        BackHandler(enabled = detail != null, onBack = onBack)
        AnimatedContent(
            modifier = modifier,
            targetState = detail,
            transitionSpec = {
                if (targetState != null) horizontalSlideForward else horizontalSlideBackward
            },
            label = "licenseInformationPage",
        ) { shown ->
            if (shown == null) {
                listState.SaveableStateProvider(key = LIST_STATE_KEY) {
                    list.Content(modifier = Modifier.fillMaxSize())
                }
            } else {
                shown.Content(modifier = Modifier.fillMaxSize())
            }
        }
    }

    private companion object {
        const val LIST_STATE_KEY = "list"
    }
}

private class LicenseInformationLayoutPreviewProvider : PreviewParameterProvider<LicenseInformationLayout> {
    private val row = CardRow(
        key = "org.example:library",
        content = LicenseRow(
            name = StringResource("Example Library"),
            license = StringResource("MIT"),
            version = StringResource("Version: 1.2.3"),
            onClick = {},
        ),
    )
    private val base = LicenseInformationLayout(
        list = groveSectionList {
            section(key = "project") {
                card(key = "project") { row(row.copy(key = "project")) }
            }
            section(key = "dependencies") {
                title("Dependencies used by Example App")
                card(key = "dependencies") { row(row) }
                footer("Please refer to the individual repository links for packages without license labels.")
            }
        },
        detail = MutableStateFlow(null),
        onBack = {},
    )
    override val values = sequenceOf(
        base,
        base.copy(detail = MutableStateFlow(LicenseDetailLayout(text = StringResource("MIT License\n\nPermission is hereby granted…")))),
    )
}

@ThemePreviews
@Composable
private fun Preview(
    @PreviewParameter(LicenseInformationLayoutPreviewProvider::class) content: LicenseInformationLayout,
) {
    GroveTheme { content.Content() }
}
