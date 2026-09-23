//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.fhir

/** One registered producer diagnostic: the stable rule code, its registry reason, where it applies and how severe it is. */
public data class ProducerDiagnostic(
    public val code: String,
    public val reason: String,
    public val location: String,
    public val severity: Severity,
) {
    /** Whether the diagnostic refuses a record or graph, or names what an accepted record lost. */
    public enum class Severity(public val code: String) {
        ERROR("error"),
        WARNING("warning"),
    }

    override fun toString(): String = "[$code] at $location: $reason"
}

/** The diagnostic a registered rule reports at one location, with its registry reason and severity. */
public fun ExchangeGraphRule.at(location: String): ProducerDiagnostic =
    ProducerDiagnostic(code = code, reason = reason, location = location, severity = severity)

/** Why a Bundle is not a valid exchange graph. */
public sealed interface ExchangeGraphError {
    /** The registry diagnostic this error reports. */
    public val diagnostic: ProducerDiagnostic

    /** One registered rule failed at one location; [detail] never carries a source value. */
    public data class RuleViolation(
        public val rule: ExchangeGraphRule,
        public val location: String,
        public val detail: String,
    ) : ExchangeGraphError {
        override val diagnostic: ProducerDiagnostic
            get() = rule.at(location)

        override fun toString(): String = "RuleViolation(${rule.code} at $location: $detail)"
    }

    /** The bytes are not a FHIR R4 Bundle at all. */
    public data class Malformed(public val detail: String) : ExchangeGraphError {
        override val diagnostic: ProducerDiagnostic
            get() = ExchangeGraphRule.MOBILE_EXCHANGE_UNCLASSIFIED.at("Bundle")

        override fun toString(): String = "Malformed($detail)"
    }
}

/** Carries an [ExchangeGraphError] across a graph-building boundary that has no result type. */
public class ExchangeGraphException(public val error: ExchangeGraphError) :
    IllegalArgumentException(error.toString())
