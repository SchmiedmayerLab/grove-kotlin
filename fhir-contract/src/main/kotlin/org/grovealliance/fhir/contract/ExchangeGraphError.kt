//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.fhir.contract

/**
 * Why a Bundle is not a valid exchange graph, with the registered [diagnostic] it reports.
 */
sealed class ExchangeGraphError(message: String) : Exception(message) {

    /**
     * The registered diagnostic of this failure; one the registry doesn't name reports the
     * unclassified diagnostic rather than borrowing another rule's code.
     */
    abstract val diagnostic: ProducerDiagnostic

    /**
     * The graph breaks [rule] at [location].
     */
    data class RuleViolation(
        val rule: ExchangeGraphRule,
        val location: String,
    ) : ExchangeGraphError("${rule.code} at $location") {
        override val diagnostic: ProducerDiagnostic get() = rule.diagnostic(location = location)
    }

    /**
     * The graph is malformed in a way no registered rule names, as [reason] describes.
     */
    data class InvalidEntries(val reason: String) : ExchangeGraphError(reason) {
        override val diagnostic: ProducerDiagnostic
            get() = ExchangeGraphRule.MOBILE_EXCHANGE_UNCLASSIFIED.diagnostic(location = "Bundle")
    }
}
