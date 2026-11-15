//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.license.internal

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.grovealliance.core.logging.groveLogger
import org.grovealliance.license.Dependency
import org.grovealliance.resources.Strings
import org.grovealliance.ui.ActionSource
import org.grovealliance.ui.AsyncTextButton
import org.grovealliance.ui.EventSink
import org.grovealliance.ui.EventSourceFlow
import org.grovealliance.ui.GroveErrorLayout
import org.grovealliance.ui.StringResource
import org.grovealliance.ui.mutableScaffoldState

/**
 * Backs the license information screen: reads the project and its dependencies once and lists
 * them, showing the license text of the selected dependency, which is kept in [savedStateHandle]
 * under [KEY_SELECTED_ID] so it survives recreation. Leaving the screen and opening links are
 * reported as [events].
 */
internal class LicenseInformationViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val source: LicenseInformationSource,
    private val mapper: LicenseInformationMapper,
) : ViewModel() {

    private val logger by groveLogger()
    private val actionSink = ActionSource(::onAction).sink<LicenseInformationAction>()
    private val eventSink = EventSink<LicenseInformationEvent>()
    private val selectedId = savedStateHandle.getStateFlow<String?>(key = KEY_SELECTED_ID, initialValue = null)
    private val scaffoldState = mutableScaffoldState(
        appBar = mapper.mapAppBar(
            selected = null,
            actionSink = actionSink,
        ),
    )
    private val state = MutableStateFlow<LicenseInformationScreenState>(LicenseInformationScreenState.Loading)
    private var loading: Job? = null

    /**
     * Reports leaving the screen and opening links.
     */
    val events: EventSourceFlow<LicenseInformationEvent> = eventSink.source()

    /**
     * The screen's content.
     */
    val screen = LicenseInformationScreenContent(
        scaffoldState = scaffoldState.asScaffoldState(),
        state = state.asStateFlow(),
    )

    init {
        load()
    }

    private fun load() {
        loading?.cancel()
        state.value = LicenseInformationScreenState.Loading
        loading = viewModelScope.launch {
            source.information()
                .onSuccess { show(information = it) }
                .onFailure {
                    logger.e(it) { "Reading the license information failed." }
                    state.value = LicenseInformationScreenState.Error(layout = errorLayout())
                }
        }
    }

    private suspend fun show(information: LicenseInformation) {
        fun withLicenseText(
            id: String?,
        ) = information.dependencies.firstOrNull { it.id == id && it.licenseText != null }
        val selected = selectedId
            .map { withLicenseText(id = it) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = withLicenseText(id = selectedId.value),
            )
        state.value = LicenseInformationScreenState.Content(
            layout = mapper.map(
                input = LicenseInformationInput(
                    information = information,
                    selected = selected,
                    scope = viewModelScope,
                    actionSink = actionSink,
                ),
            ),
        )
        selected.collect { showAppBar(selected = it) }
    }

    private fun showAppBar(selected: Dependency?) {
        scaffoldState.setAppBar(
            appBar = mapper.mapAppBar(
                selected = selected,
                actionSink = actionSink,
            ),
        )
    }

    private fun onAction(action: LicenseInformationAction) {
        when (action) {
            LicenseInformationAction.Close -> eventSink.push(LicenseInformationEvent.Dismissed)
            LicenseInformationAction.Back -> savedStateHandle[KEY_SELECTED_ID] = null
            LicenseInformationAction.Retry -> load()
            is LicenseInformationAction.DependencySelected -> savedStateHandle[KEY_SELECTED_ID] = action.id
            is LicenseInformationAction.UrlSelected -> eventSink.push(LicenseInformationEvent.UrlOpened(url = action.url))
        }
    }

    private fun errorLayout() = GroveErrorLayout(
        title = StringResource(Strings.license_information_error_title),
        message = StringResource(Strings.license_information_error_message),
        primaryButton = AsyncTextButton(
            title = StringResource(Strings.license_information_retry),
            action = { actionSink.push(LicenseInformationAction.Retry) },
        ),
    )

    companion object {
        /**
         * The key the identifier of the dependency whose license text is shown is kept under.
         */
        const val KEY_SELECTED_ID = "selectedDependencyId"
    }
}
