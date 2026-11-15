//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.license.internal

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.grovealliance.license.Dependency
import org.grovealliance.ui.ActionSink
import org.grovealliance.ui.CardRow
import org.grovealliance.ui.GroveAppBar
import org.grovealliance.ui.GroveSection
import org.grovealliance.ui.StringResource
import org.grovealliance.ui.consumeAll
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LicenseInformationViewModelTest {

    private val source = FakeLicenseInformationSource()
    private val mapper = RecordingLicenseInformationMapper()
    private val withText = DependencyFixtures.create(
        id = "org.example:library",
        name = "Example Library",
        licenseText = "MIT License",
        url = "https://github.com/example/library",
    )
    private val withoutText = DependencyFixtures.create(
        id = "org.example:unlabelled",
        name = "Unlabelled Library",
        url = "https://example.org/unlabelled",
    )

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        source.setResult(result = Result.success(information(dependencies = listOf(withText, withoutText))))
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `lists the dependencies once they are read`() {
        // when
        val viewModel = viewModel()

        // then
        assertThat(viewModel.layout().dependencyRows().map { it.key }).containsExactly(
            "org.example:library",
            "org.example:unlabelled",
        ).inOrder()
        assertThat(viewModel.layout().detail.value).isNull()
    }

    @Test
    fun `shows the license text of a selected dependency`() {
        // given
        val viewModel = viewModel()

        // when
        viewModel.layout().dependencyRows().first().licenseRow.onClick?.invoke()

        // then
        assertThat(viewModel.layout().detail.value).isEqualTo(LicenseDetailLayout(text = StringResource("MIT License")))
        assertThat(mapper.appBarSelection).isEqualTo(withText)
    }

    @Test
    fun `returns to the list from a license text`() {
        // given
        val viewModel = viewModel()
        viewModel.layout().dependencyRows().first().licenseRow.onClick?.invoke()

        // when
        viewModel.layout().onBack()

        // then
        assertThat(viewModel.layout().detail.value).isNull()
        assertThat(mapper.appBarSelection).isNull()
        assertThat(viewModel.events.consumeAll()).isEmpty()
    }

    @Test
    fun `restores the selected license text after recreation`() {
        // given
        val savedStateHandle = SavedStateHandle()
        viewModel(savedStateHandle = savedStateHandle).layout().dependencyRows().first().licenseRow.onClick?.invoke()

        // when
        val restored = viewModel(savedStateHandle = SavedStateHandle(savedStateHandle.keys().associateWith { savedStateHandle[it] }))

        // then
        assertThat(restored.layout().detail.value).isEqualTo(LicenseDetailLayout(text = StringResource("MIT License")))
    }

    @Test
    fun `shows the list when the restored dependency has no license text`() {
        // given
        val savedStateHandle = SavedStateHandle(mapOf(LicenseInformationViewModel.KEY_SELECTED_ID to withoutText.id))

        // when
        val viewModel = viewModel(savedStateHandle = savedStateHandle)

        // then
        assertThat(viewModel.layout().detail.value).isNull()
        assertThat(mapper.appBarSelection).isNull()
    }

    @Test
    fun `reports opening the website of a dependency without a license text`() {
        // given
        val viewModel = viewModel()

        // when
        viewModel.layout().dependencyRows().last().licenseRow.onClick?.invoke()

        // then
        assertThat(viewModel.events.consumeAll()).containsExactly(LicenseInformationEvent.UrlOpened(url = "https://example.org/unlabelled"))
    }

    @Test
    fun `reports leaving the screen from the list`() {
        // given
        val viewModel = viewModel()

        // when
        mapper.actionSink.push(LicenseInformationAction.Close)

        // then
        assertThat(viewModel.events.consumeAll()).containsExactly(LicenseInformationEvent.Dismissed)
    }

    @Test
    fun `offers to try again when the information can't be read`() {
        // given
        source.setResult(result = Result.failure(IllegalStateException("unreadable")))

        // when
        val viewModel = viewModel()

        // then
        assertThat(viewModel.screen.state.value).isInstanceOf(LicenseInformationScreenState.Error::class.java)
    }

    @Test
    fun `reads the information again when retried`() = runTest {
        // given
        source.setResult(result = Result.failure(IllegalStateException("unreadable")))
        val viewModel = viewModel()
        val error = viewModel.screen.state.value as LicenseInformationScreenState.Error
        source.setResult(result = Result.success(information(dependencies = listOf(withText))))

        // when
        error.layout.primaryButton.action()

        // then
        assertThat(source.reads).isEqualTo(2)
        assertThat(viewModel.layout().dependencyRows().map { it.key }).containsExactly("org.example:library")
    }

    private fun viewModel(savedStateHandle: SavedStateHandle = SavedStateHandle()) = LicenseInformationViewModel(
        savedStateHandle = savedStateHandle,
        source = source,
        mapper = mapper,
    )

    private fun information(dependencies: List<Dependency>) = LicenseInformation(
        project = LicenseProjectFixtures.create(),
        dependencies = dependencies,
    )

    private fun LicenseInformationLayout.dependencyRows() =
        (list.sections.single { it.key == "dependencies" }.elements.single() as GroveSection.Element.Card).rows

    private val CardRow<*>.licenseRow: LicenseRow
        get() = content as LicenseRow

    private fun LicenseInformationViewModel.layout() =
        (screen.state.value as LicenseInformationScreenState.Content).layout

    private class RecordingLicenseInformationMapper : LicenseInformationMapper {
        private val mapper = LicenseInformationMapperImpl()
        private var currentActionSink: ActionSink<LicenseInformationAction>? = null
        val actionSink: ActionSink<LicenseInformationAction>
            get() = requireNotNull(currentActionSink)
        var appBarSelection: Dependency? = null
            private set

        override fun map(input: LicenseInformationInput): LicenseInformationLayout = mapper.map(input = input)

        override fun mapAppBar(
            selected: Dependency?,
            actionSink: ActionSink<LicenseInformationAction>,
        ): GroveAppBar {
            currentActionSink = actionSink
            appBarSelection = selected
            return mapper.mapAppBar(
                selected = selected,
                actionSink = actionSink,
            )
        }
    }
}
