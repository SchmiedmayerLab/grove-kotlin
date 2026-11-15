//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.ui

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.grovealliance.core.coroutines.coroutinesLauncher
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MutableGroveScaffoldStateTest {

    private val state = MutableGroveScaffoldState(coroutinesLauncher = CoroutineScope(UnconfinedTestDispatcher()).coroutinesLauncher)
    private var dismissCount = 0
    private val dialog = GroveAlertDialog(
        title = StringResource("Title"),
        message = StringResource("Message"),
        confirmButton = GroveAlertDialog.Button(
            title = StringResource("OK"),
            action = {},
        ),
        onDismiss = { dismissCount++ },
    )

    @Test
    fun `shows no dialog at first`() {
        // when
        val shown = state.widgets.value.dialog

        // then
        assertThat(shown).isNull()
    }

    @Test
    fun `shows a dialog`() {
        // when
        state.showDialog(dialog = dialog)

        // then
        assertThat(state.widgets.value.dialog).isNotNull()
    }

    @Test
    fun `removes the dialog once it closes`() {
        // given
        state.showDialog(dialog = dialog)

        // when
        requireNotNull(state.widgets.value.dialog).onDismiss()

        // then
        assertThat(state.widgets.value.dialog).isNull()
        assertThat(dismissCount).isEqualTo(1)
    }

    @Test
    fun `closes the dialog on request`() {
        // given
        state.showDialog(dialog = dialog)

        // when
        state.dismissDialog()

        // then
        assertThat(state.widgets.value.dialog).isNull()
        assertThat(dismissCount).isEqualTo(1)
    }

    @Test
    fun `ignores a close request without a dialog`() {
        // when
        state.dismissDialog()

        // then
        assertThat(state.widgets.value.dialog).isNull()
        assertThat(dismissCount).isEqualTo(0)
    }

    @Test
    fun `keeps the dialog that replaced a closing one`() {
        // given
        state.showDialog(dialog = dialog)
        val replaced = requireNotNull(state.widgets.value.dialog)
        state.showDialog(dialog = dialog.copy(title = StringResource("Next")))

        // when
        replaced.onDismiss()

        // then
        assertThat(state.widgets.value.dialog).isNotNull()
        assertThat(state.widgets.value.dialog).isNotSameInstanceAs(replaced)
    }
}
