//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.health.fhir

import org.grovealliance.fhir.contract.ExchangeRecord
import org.grovealliance.fhir.contract.ProducerDiagnostic

/**
 * What [HealthConnectFhirExporter] made of one Record: its [source], the record it converted to or why
 * it was refused, and the [warnings] the conversion reported.
 */
data class HealthConnectExport(
    val source: Source,
    val outcome: Outcome,
    val warnings: List<ProducerDiagnostic>,
) {

    /**
     * The converted record, or `null` when the Record was refused.
     */
    val record: ExchangeRecord? get() = (outcome as? Outcome.Exported)?.record

    /**
     * The Record converted: its Health Connect [recordId] and its exact Record class [recordType].
     */
    data class Source(
        val recordId: String,
        val recordType: String,
    )

    /**
     * Whether the Record converted.
     */
    sealed interface Outcome {

        /**
         * The Record converted to [record].
         */
        data class Exported(val record: ExchangeRecord) : Outcome

        /**
         * The Record was refused for [error].
         */
        data class Refused(val error: HealthConnectConversionError) : Outcome
    }
}
