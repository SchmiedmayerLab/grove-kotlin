//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.consent

/**
 * The consent form as the signer is filling it in: their [responses] so far, and whether the form
 * [isComplete], meaning every element that requires an answer has one and the form is signed.
 */
data class ConsentFormState(
    val responses: ConsentResponses,
    val isComplete: Boolean,
)
