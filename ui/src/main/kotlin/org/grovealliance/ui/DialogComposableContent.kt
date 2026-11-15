//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

@file:OptIn(ExperimentalMaterial3Api::class)

package org.grovealliance.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import org.grovealliance.ui.theme.Spacings

/**
 * A [ComposableContent] presented as a modal dialog.
 *
 * Implementors define the dialog's body via [ComposableContent.Content]; the container, its shape
 * and its size come from [Dialog], which renders the content above everything else. Controls of the
 * body that close the dialog do so through [LocalDialogDismiss].
 *
 * Example:
 * ```kotlin
 * class MyDialog(override val onDismiss: () -> Unit) : DialogComposableContent {
 *
 *     @Composable
 *     override fun Content(modifier: Modifier) {
 *         Text(modifier = modifier.padding(Spacings.large), text = "Hello from the dialog")
 *     }
 * }
 * ```
 *
 * @see GroveAlertDialog for a ready-made dialog asking to confirm or acknowledge something.
 */
interface DialogComposableContent : ComposableContent {

    /**
     * Called whenever the dialog closes: on a back press, a tap outside it, or once one of its
     * own controls closes it.
     */
    val onDismiss: OnActionVoid get() = {}

    /**
     * How the dialog's window behaves, such as whether a back press or a tap outside it dismisses
     * it.
     */
    val dialogProperties: DialogProperties get() = DialogProperties()

    /**
     * Renders this content as a modal dialog.
     *
     * @param modifier Modifier applied to the dialog's container.
     */
    @Composable
    fun Dialog(modifier: Modifier) {
        // In inspection mode (Paparazzi / Layout Inspector) a dialog renders in a separate window
        // that is not captured. Render the container centred on the screen instead so screenshots
        // reflect the real dialog layout.
        if (LocalInspectionMode.current) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(Spacings.large),
                contentAlignment = Alignment.Center,
            ) {
                Container(modifier = modifier.widthIn(min = MIN_WIDTH, max = MAX_WIDTH))
            }
            return
        }
        BasicAlertDialog(
            modifier = modifier,
            onDismissRequest = onDismiss,
            properties = dialogProperties,
        ) {
            Container(modifier = Modifier)
        }
    }

    @Composable
    private fun Container(modifier: Modifier) {
        Surface(
            modifier = modifier,
            shape = AlertDialogDefaults.shape,
            color = AlertDialogDefaults.containerColor,
            tonalElevation = AlertDialogDefaults.TonalElevation,
        ) {
            CompositionLocalProvider(LocalDialogDismiss provides onDismiss) {
                Content(Modifier)
            }
        }
    }
}

/**
 * Closes the dialog the current content is rendered in, `null` outside of a
 * [DialogComposableContent.Dialog].
 */
val LocalDialogDismiss = staticCompositionLocalOf<OnActionVoid?> { null }

private val MIN_WIDTH = 280.dp
private val MAX_WIDTH = 560.dp
