//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import org.grovealliance.ui.theme.Colors
import org.grovealliance.ui.theme.GroveTheme
import org.grovealliance.ui.theme.Spacings
import org.grovealliance.ui.theme.TextStyles
import org.grovealliance.ui.theme.ThemePreviews

/**
 * A dialog stating a [title] and [message] above the buttons answering it: the [confirmButton]
 * and, when the answer is a choice, the [dismissButton] declining it.
 *
 * Pressing either button closes the dialog first and then runs the button's [Button.action].
 */
data class GroveAlertDialog(
    val title: StringResource,
    val message: StringResource,
    val confirmButton: Button,
    val dismissButton: Button? = null,
    override val onDismiss: OnActionVoid = {},
) : DialogComposableContent {

    @Composable
    override fun Content(modifier: Modifier) {
        Column(
            modifier = modifier.padding(Spacings.large),
            verticalArrangement = Arrangement.spacedBy(Spacings.medium),
        ) {
            Text(
                text = title.text(),
                style = TextStyles.headlineSmall,
                color = Colors.onSurface,
            )
            Text(
                text = message.text(),
                style = TextStyles.bodyMedium,
                color = Colors.onSurfaceVariant,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacings.small),
                horizontalArrangement = Arrangement.spacedBy(
                    space = Spacings.small,
                    alignment = Alignment.End,
                ),
            ) {
                dismissButton?.let { DialogButton(button = it) }
                DialogButton(button = confirmButton)
            }
        }
    }

    @Composable
    private fun DialogButton(button: Button) {
        val dismiss = LocalDialogDismiss.current ?: onDismiss
        AsyncTextButton(
            text = button.title.text(),
            containerColor = Colors.transparent,
            textColor = when (button.style) {
                Button.Style.Default -> Colors.primary
                Button.Style.Destructive -> Colors.error
            },
            contentPadding = ButtonDefaults.TextButtonContentPadding,
            onClick = {
                dismiss()
                button.action()
            },
        )
    }

    /**
     * A button of the dialog, titled [title], running [action] once pressed.
     */
    data class Button(
        val title: StringResource,
        val style: Style = Style.Default,
        val action: OnActionVoid,
    ) {

        /**
         * How a [Button] reads.
         */
        enum class Style {

            /**
             * An ordinary answer.
             */
            Default,

            /**
             * An answer that removes or ends something, drawn in the error color.
             */
            Destructive,
        }
    }
}

private class GroveAlertDialogPreviewProvider : PreviewParameterProvider<GroveAlertDialog> {
    private val base = GroveAlertDialog(
        title = StringResource("Delete Entry?"),
        message = StringResource("The entry is removed from all of your devices."),
        confirmButton = GroveAlertDialog.Button(
            title = StringResource("OK"),
            action = {},
        ),
    )
    override val values = sequenceOf(
        base,
        base.copy(
            confirmButton = GroveAlertDialog.Button(
                title = StringResource("Delete"),
                style = GroveAlertDialog.Button.Style.Destructive,
                action = {},
            ),
            dismissButton = GroveAlertDialog.Button(
                title = StringResource("Cancel"),
                action = {},
            ),
        ),
    )
}

@ThemePreviews
@Composable
private fun GroveAlertDialogPreview(
    @PreviewParameter(GroveAlertDialogPreviewProvider::class) dialog: GroveAlertDialog,
) {
    GroveTheme { dialog.Dialog(Modifier) }
}
