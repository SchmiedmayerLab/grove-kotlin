//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.fhir.contract

/**
 * Why a record draft could not become a record.
 */
sealed class ExchangeAssemblyError(message: String) : Exception(message) {

    /**
     * The draft states no output.
     */
    data object NoOutputs : ExchangeAssemblyError("A record states at least one output.")

    /**
     * A Provenance target names no entry of the record.
     */
    data class UnresolvedTarget(
        val reference: String?,
    ) : ExchangeAssemblyError("Provenance.target $reference resolves to no entry.")
}
