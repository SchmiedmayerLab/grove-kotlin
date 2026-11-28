//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.health.fhir

import org.grovealliance.fhir.contract.ExchangeRecord

/**
 * What [HealthConnectFhirExporter] made of one [deletion]: its retraction record, nothing when the
 * exporter can't have converted the Record, or why it was refused.
 */
data class HealthConnectRetraction(
    val deletion: HealthConnectDeletion,
    val outcome: Outcome,
) {

    /**
     * The retraction record, or `null` when there is none.
     */
    val record: ExchangeRecord? get() = (outcome as? Outcome.Retracted)?.record

    /**
     * Whether the deletion retracts a record.
     */
    sealed interface Outcome {

        /**
         * The deletion retracts the source record as [record] states.
         */
        data class Retracted(val record: ExchangeRecord) : Outcome

        /**
         * The exporter converts no Record of the deletion's type, so nothing was ever emitted for it.
         */
        data object NothingToRetract : Outcome

        /**
         * The deletion was refused for [error].
         */
        data class Refused(val error: HealthConnectConversionError) : Outcome
    }
}
