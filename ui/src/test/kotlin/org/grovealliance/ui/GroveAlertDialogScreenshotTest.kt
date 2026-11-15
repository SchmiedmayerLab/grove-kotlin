//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.ui

import androidx.compose.ui.Modifier
import org.grovealliance.testing.screenshot.ScreenshotTest
import org.junit.Test

class GroveAlertDialogScreenshotTest : ScreenshotTest() {
    private val base = GroveAlertDialog(
        title = StringResource("Something Went Wrong"),
        message = StringResource("The entry could not be saved. Please try again."),
        confirmButton = GroveAlertDialog.Button(
            title = StringResource("OK"),
            action = {},
        ),
    )

    @Test
    fun `GroveAlertDialog acknowledgement`() {
        // when / then
        screenshot { base.Dialog(Modifier) }
    }

    @Test
    fun `GroveAlertDialog destructive choice`() {
        // given
        val dialog = base.copy(
            title = StringResource("Delete Entry?"),
            message = StringResource("The entry is removed from all of your devices."),
            confirmButton = GroveAlertDialog.Button(
                title = StringResource("Delete"),
                style = GroveAlertDialog.Button.Style.Destructive,
                action = {},
            ),
            dismissButton = GroveAlertDialog.Button(
                title = StringResource("Cancel"),
                action = {},
            ),
        )

        // when / then
        screenshot { dialog.Dialog(Modifier) }
    }
}
