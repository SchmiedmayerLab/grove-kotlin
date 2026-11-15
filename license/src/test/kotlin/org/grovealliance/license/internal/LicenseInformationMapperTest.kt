//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.license.internal

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.grovealliance.license.Dependency
import org.grovealliance.license.LicenseProject
import org.grovealliance.resources.Strings
import org.grovealliance.ui.ActionSource
import org.grovealliance.ui.CardRow
import org.grovealliance.ui.GroveSection
import org.grovealliance.ui.StringResource
import org.grovealliance.ui.groveAppBar
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LicenseInformationMapperTest {

    private val pushedActions = mutableListOf<LicenseInformationAction>()
    private val actionSink = ActionSource<LicenseInformationAction> { pushedActions.add(it) }.sink<LicenseInformationAction>()
    private val selected = MutableStateFlow<Dependency?>(null)
    private val scope = CoroutineScope(UnconfinedTestDispatcher())
    private val mapper = LicenseInformationMapperImpl()

    @Test
    fun `lists the project first with its license and version`() {
        // given
        val project = LicenseProjectFixtures.create(
            name = StringResource("Example App"),
            version = "4.0.8",
            licenseIdentifier = "MIT",
        )

        // when
        val layout = mapper.map(input = input(project = project))
        val row = layout.cardRows(sectionKey = "project").single().licenseRow

        // then
        assertThat(layout.list.sections.map { it.key }).containsExactly("project", "dependencies").inOrder()
        assertThat(row.name).isEqualTo(StringResource("Example App"))
        assertThat(row.license).isEqualTo(StringResource("MIT"))
        assertThat(row.version).isEqualTo(StringResource(Strings.license_information_version, "4.0.8"))
    }

    @Test
    fun `names the project in the dependencies' header`() {
        // given
        val project = LicenseProjectFixtures.create(name = StringResource("Example App"))

        // when
        val section = mapper.map(input = input(project = project)).list.sections.single { it.key == "dependencies" }

        // then
        assertThat(section.footer).isEqualTo(StringResource(Strings.license_information_footer))
        assertThat(section.title).isEqualTo(
            StringResource(
                Strings.license_information_dependencies_header,
                StringResource("Example App"),
            ),
        )
    }

    @Test
    fun `opens the project's website from its row`() {
        // given
        val project = LicenseProjectFixtures.create(url = "https://github.com/example/app")
        val row = mapper.map(input = input(project = project)).cardRows(sectionKey = "project").single().licenseRow

        // when
        row.onClick?.invoke()

        // then
        assertThat(pushedActions).containsExactly(LicenseInformationAction.UrlSelected(url = "https://github.com/example/app"))
    }

    @Test
    fun `can't open a project without a website`() {
        // when
        val row = mapper.map(input = input()).cardRows(sectionKey = "project").single().licenseRow

        // then
        assertThat(row.onClick).isNull()
    }

    @Test
    fun `lists each dependency with its license and version, keyed by its identifier`() {
        // given
        val dependency = DependencyFixtures.create(
            id = "org.example:library",
            name = "Example Library",
            version = "1.2.3",
            licenseIdentifier = "Apache-2.0",
        )

        // when
        val row = mapper.map(input = input(dependencies = listOf(dependency))).cardRows(sectionKey = "dependencies").single()

        // then
        assertThat(row.key).isEqualTo("org.example:library")
        assertThat(row.licenseRow.name).isEqualTo(StringResource("Example Library"))
        assertThat(row.licenseRow.license).isEqualTo(StringResource("Apache-2.0"))
        assertThat(row.licenseRow.version).isEqualTo(StringResource(Strings.license_information_version, "1.2.3"))
    }

    @Test
    fun `leaves out a dependency's unknown license and version`() {
        // when
        val row = mapper.map(input = input(dependencies = listOf(DependencyFixtures.create())))
            .cardRows(sectionKey = "dependencies").single()

        // then
        assertThat(row.licenseRow.license).isNull()
        assertThat(row.licenseRow.version).isNull()
    }

    @Test
    fun `opens the license text of a dependency that bundles one`() {
        // given
        val dependency = DependencyFixtures.create(
            id = "org.example:library",
            licenseText = "MIT License",
            url = "https://github.com/example/library",
        )
        val row = mapper.map(input = input(dependencies = listOf(dependency))).cardRows(sectionKey = "dependencies").single()

        // when
        row.licenseRow.onClick?.invoke()

        // then
        assertThat(pushedActions).containsExactly(LicenseInformationAction.DependencySelected(id = "org.example:library"))
    }

    @Test
    fun `opens the website of a dependency without a license text`() {
        // given
        val dependency = DependencyFixtures.create(url = "https://github.com/example/library")
        val row = mapper.map(input = input(dependencies = listOf(dependency))).cardRows(sectionKey = "dependencies").single()

        // when
        row.licenseRow.onClick?.invoke()

        // then
        assertThat(pushedActions).containsExactly(LicenseInformationAction.UrlSelected(url = "https://github.com/example/library"))
    }

    @Test
    fun `can't open a dependency without a license text or website`() {
        // when
        val row = mapper.map(input = input(dependencies = listOf(DependencyFixtures.create())))
            .cardRows(sectionKey = "dependencies").single()

        // then
        assertThat(row.licenseRow.onClick).isNull()
    }

    @Test
    fun `shows the license text of the selected dependency`() {
        // given
        val layout = mapper.map(input = input())

        // when
        selected.value = DependencyFixtures.create(licenseText = "MIT License")

        // then
        assertThat(layout.detail.value).isEqualTo(LicenseDetailLayout(text = StringResource("MIT License")))
    }

    @Test
    fun `shows the list again once nothing is selected`() {
        // given
        selected.value = DependencyFixtures.create(licenseText = "MIT License")
        val layout = mapper.map(input = input())

        // when
        selected.value = null

        // then
        assertThat(layout.detail.value).isNull()
    }

    @Test
    fun `goes back from the license text`() {
        // given
        val layout = mapper.map(input = input())

        // when
        layout.onBack()

        // then
        assertThat(pushedActions).containsExactly(LicenseInformationAction.Back)
    }

    @Test
    fun `titles the list with the screen's title`() {
        // when
        val appBar = mapper.mapAppBar(
            selected = null,
            actionSink = actionSink,
        )

        // then
        assertThat(appBar.title).isEqualTo(groveAppBar { title(Strings.license_information_title) }.title)
    }

    @Test
    fun `titles the license text with the dependency and where it is published`() {
        // given
        val dependency = DependencyFixtures.create(
            name = "Example Library",
            url = "https://github.com/example/library/",
        )

        // when
        val appBar = mapper.mapAppBar(
            selected = dependency,
            actionSink = actionSink,
        )

        // then
        val expected = groveAppBar {
            title(
                content = LicenseDetailTitle(
                    title = StringResource("Example Library"),
                    subtitle = StringResource("github.com/example/library"),
                ),
            )
        }
        assertThat(appBar.title).isEqualTo(expected.title)
    }

    private fun LicenseInformationLayout.cardRows(sectionKey: String) =
        (list.sections.single { it.key == sectionKey }.elements.single() as GroveSection.Element.Card).rows

    private val CardRow<*>.licenseRow: LicenseRow
        get() = content as LicenseRow

    private fun input(
        project: LicenseProject = LicenseProjectFixtures.create(),
        dependencies: List<Dependency> = emptyList(),
    ) = LicenseInformationInput(
        information = LicenseInformation(
            project = project,
            dependencies = dependencies,
        ),
        selected = selected,
        scope = scope,
        actionSink = actionSink,
    )
}
