//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.fhir.contract

/**
 * One structured producer diagnostic, as the Grove conformance corpus states it: the registered
 * [code] and its [reason], the [location] it concerns and its [severity].
 */
data class ProducerDiagnostic(
    val code: String,
    val reason: String,
    val location: String,
    val severity: Severity = Severity.ERROR,
) {

    /**
     * How serious a diagnostic is.
     */
    enum class Severity {
        /**
         * The record was refused or the graph is invalid.
         */
        ERROR,

        /**
         * The record was accepted and something it carried was lost.
         */
        WARNING,
    }
}
