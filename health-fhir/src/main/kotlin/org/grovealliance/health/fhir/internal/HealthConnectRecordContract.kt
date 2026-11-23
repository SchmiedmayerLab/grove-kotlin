//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.health.fhir.internal

import org.grovealliance.fhir.contract.Coding
import org.grovealliance.fhir.contract.InternalGroveFhirApi
import org.grovealliance.fhir.contract.QuantityContract

/**
 * What the adapter contract states for one Record class [token]: its [status] and the [outputs] a
 * Record of it converts to.
 */
internal data class HealthConnectRecordContract(
    val token: String,
    val status: Status,
    val outputs: List<Output>,
) {

    /**
     * Whether the adapter converts the Record class, and why not when it doesn't.
     */
    enum class Status {
        SUPPORTED,
        MAPPED_STANDARD,
        PLATFORM_EXCLUSIVE,
        UNMODELED,
        DEFERRED,
        INTENTIONALLY_UNSUPPORTED,
    }

    /**
     * How many outputs of one measurement a Record converts to.
     */
    enum class CountRule {
        EXACTLY_ONE,
        ZERO_OR_ONE,
        ONE_PER_SAMPLE,
        ONE_PER_STAGE,
        ONE_PER_PRESENT_FIELD,
        GRAPH_SPECIFIC,
    }

    /**
     * One output of the [measurement], counted by [countRule] or the named [graphRule], admitted only
     * under its source [condition] when it states one.
     */
    data class Output(
        val measurement: String,
        val countRule: CountRule,
        val graphRule: String?,
        val condition: String?,
    )
}

/**
 * A glucose measurement only this adapter states: its [profile], [code] and fixed [quantity].
 */
@OptIn(InternalGroveFhirApi::class)
internal data class HealthConnectAdapterMeasurement(
    val id: String,
    val profile: String,
    val code: Coding,
    val quantity: QuantityContract,
)

/**
 * An admitted glucose specimen source [token]: the glucose [measurement] it selects and the SNOMED CT
 * [type] of the Specimen it synthesizes.
 */
@OptIn(InternalGroveFhirApi::class)
internal data class HealthConnectSpecimenContract(
    val token: String,
    val measurement: String,
    val type: Coding,
)

/**
 * A source constant's exact [source] coding and the [shared] code it projects onto.
 */
@OptIn(InternalGroveFhirApi::class)
internal data class HealthConnectCodedValue(
    val source: Coding,
    val shared: String,
)
