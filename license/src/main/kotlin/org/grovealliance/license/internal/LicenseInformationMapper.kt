//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.license.internal

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.grovealliance.license.Dependency
import org.grovealliance.license.LicenseProject
import org.grovealliance.resources.Strings
import org.grovealliance.ui.ActionSink
import org.grovealliance.ui.GroveAppBar
import org.grovealliance.ui.ImageResource
import org.grovealliance.ui.StringResource
import org.grovealliance.ui.groveAppBar
import org.grovealliance.ui.groveSectionList

/**
 * Builds the license information screen: the project and its dependencies, the license text of the
 * selected dependency, and the app bar. Interactions are dispatched as [LicenseInformationAction]s.
 */
internal interface LicenseInformationMapper {

    /**
     * The project and dependencies of [LicenseInformationInput.information], showing the license
     * text of [LicenseInformationInput.selected] while one is selected.
     */
    fun map(input: LicenseInformationInput): LicenseInformationLayout

    /**
     * The app bar of the list, or of the license text of [selected] while one is selected.
     */
    fun mapAppBar(
        selected: Dependency?,
        actionSink: ActionSink<LicenseInformationAction>,
    ): GroveAppBar
}

/**
 * What the license information screen is built from.
 *
 * @param information the project and its dependencies, in display order
 * @param selected the dependency whose license text is shown, if any
 * @param scope the scope the selection is followed in
 * @param actionSink receives the screen's interactions
 */
internal data class LicenseInformationInput(
    val information: LicenseInformation,
    val selected: StateFlow<Dependency?>,
    val scope: CoroutineScope,
    val actionSink: ActionSink<LicenseInformationAction>,
)

/**
 * Default [LicenseInformationMapper] implementation.
 */
internal class LicenseInformationMapperImpl : LicenseInformationMapper {

    override fun map(input: LicenseInformationInput) = LicenseInformationLayout(
        list = groveSectionList {
            section(key = PROJECT_KEY) {
                card(key = PROJECT_KEY) {
                    row(
                        key = PROJECT_KEY,
                        content = projectRow(
                            project = input.information.project,
                            actionSink = input.actionSink,
                        ),
                    )
                }
            }
            section(key = DEPENDENCIES_KEY) {
                title(
                    StringResource(
                        Strings.license_information_dependencies_header,
                        input.information.project.name,
                    ),
                )
                card(key = DEPENDENCIES_KEY) {
                    input.information.dependencies.forEach { dependency ->
                        row(
                            key = dependency.id,
                            content = dependencyRow(
                                dependency = dependency,
                                actionSink = input.actionSink,
                            ),
                        )
                    }
                }
                footer(Strings.license_information_footer)
            }
        },
        detail = input.selected
            .map { detail(dependency = it) }
            .stateIn(
                scope = input.scope,
                started = SharingStarted.Eagerly,
                initialValue = detail(dependency = input.selected.value),
            ),
        onBack = { input.actionSink.push(LicenseInformationAction.Back) },
    )

    override fun mapAppBar(
        selected: Dependency?,
        actionSink: ActionSink<LicenseInformationAction>,
    ) = groveAppBar {
        if (selected == null) {
            back { actionSink.push(LicenseInformationAction.Close) }
            title(Strings.license_information_title)
        } else {
            back { actionSink.push(LicenseInformationAction.Back) }
            title(
                content = LicenseDetailTitle(
                    title = StringResource(selected.name),
                    subtitle = selected.url?.let { StringResource(it.substringAfter("://").trimEnd('/')) },
                ),
            )
            selected.url?.let { url ->
                action(
                    imageResource = ImageResource(
                        image = Icons.AutoMirrored.Outlined.OpenInNew,
                        contentDescription = StringResource(Strings.license_information_open_in_browser),
                    ),
                ) {
                    actionSink.push(LicenseInformationAction.UrlSelected(url = url))
                }
            }
        }
    }

    private fun projectRow(
        project: LicenseProject,
        actionSink: ActionSink<LicenseInformationAction>,
    ) = LicenseRow(
        name = project.name,
        license = project.licenseIdentifier?.let { StringResource(it) },
        version = project.version?.let { version(version = it) },
        onClick = project.url?.let { url -> { actionSink.push(LicenseInformationAction.UrlSelected(url = url)) } },
    )

    private fun dependencyRow(
        dependency: Dependency,
        actionSink: ActionSink<LicenseInformationAction>,
    ) = LicenseRow(
        name = StringResource(dependency.name),
        license = dependency.licenseIdentifier?.let { StringResource(it) },
        version = dependency.version?.let { version(version = it) },
        onClick = when {
            dependency.licenseText != null -> {
                { actionSink.push(LicenseInformationAction.DependencySelected(id = dependency.id)) }
            }
            dependency.url != null -> {
                { actionSink.push(LicenseInformationAction.UrlSelected(url = dependency.url)) }
            }
            else -> null
        },
    )

    private fun detail(dependency: Dependency?) =
        dependency?.licenseText?.let { LicenseDetailLayout(text = StringResource(it)) }

    private fun version(version: String) = StringResource(Strings.license_information_version, version)

    private companion object {
        const val PROJECT_KEY = "project"
        const val DEPENDENCIES_KEY = "dependencies"
    }
}
