//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.health.fhir

import org.grovealliance.fhir.ExchangeGraph
import org.grovealliance.fhir.ExchangeGraphIdentifiers
import org.hl7.fhir.r4.model.Bundle

/** The validated exchange graph one Health Connect record produced, with every identity it carries. */
public class HealthConnectConversion(
    public val source: HealthConnectSourceRecord,
    public val identifiers: ExchangeGraphIdentifiers,
    public val graph: ExchangeGraph,
    warnings: List<HealthConnectConversionWarning>,
) {
    /** What the conversion lost without refusing the record; empty when nothing was lost. */
    public val warnings: List<HealthConnectConversionWarning> = warnings.toList()

    /** A copy of the validated Bundle. */
    public fun toBundle(): Bundle = graph.toBundle()

    override fun toString(): String = "HealthConnectConversion(source=$source, event=${graph.eventIdentifier})"
}

/** The outcome of converting one record; a refusal is a result, never an exception. */
public sealed interface HealthConnectConversionResult {
    /** The record produced a validated active graph. */
    public data class Converted(public val conversion: HealthConnectConversion) : HealthConnectConversionResult

    /** The record was admitted but had nothing to emit, such as a series without samples. */
    public data class NoOutput(
        public val source: HealthConnectSourceRecord,
        public val warnings: List<HealthConnectConversionWarning>,
    ) : HealthConnectConversionResult

    /** The record was refused; [failure] carries exactly one registry code. */
    public data class Failed(public val failure: HealthConnectConversionFailure) : HealthConnectConversionResult
}

/** One refused record of a batch; [source] is null when the Record class is outside the catalog inventory. */
public data class HealthConnectRecordFailure(
    public val source: HealthConnectSourceRecord?,
    public val failure: HealthConnectConversionFailure,
)
