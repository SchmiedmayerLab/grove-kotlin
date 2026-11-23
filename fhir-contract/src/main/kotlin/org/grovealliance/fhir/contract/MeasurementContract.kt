//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.fhir.contract

/**
 * The fixed semantics of one catalog measurement: the [profile] that claims it, its [code] with every
 * [requiredCodings] beside it, its result shape (a [quantity], [components], or a coded result from
 * [resultCodeSystem] limited to [allowedValues] or [resultCodes]), its [method], its [effective] time
 * and its [category].
 */
@InternalGroveFhirApi
data class MeasurementContract(
    val id: String,
    val profile: String,
    val code: Coding,
    val requiredCodings: List<Coding>,
    val quantity: QuantityContract?,
    val components: List<ComponentContract>,
    val resultCodeSystem: String?,
    val allowedValues: List<String>,
    val resultCodes: List<ResultCode>,
    val additionalResultBindings: List<ResultBinding>,
    val method: ResultCode?,
    val methodChoice: List<String>,
    val effective: Effective,
    val category: Coding?,
) {

    /**
     * The effective time a measurement states.
     */
    enum class Effective {
        DATE_TIME,
        PERIOD,
        DATE_TIME_OR_PERIOD,
    }

    /**
     * One coded result, [code] shown as [display].
     */
    data class ResultCode(
        val code: String,
        val display: String,
    )

    /**
     * A further code system a result may be coded in, limited to [allowedValues].
     */
    data class ResultBinding(
        val system: String,
        val allowedValues: List<String>,
    )

    /**
     * One component of a measurement: its [code] in [system], with a [quantity] or a coded result from
     * [resultCodeSystem], and how often it occurs, as its [cardinality] states.
     */
    data class ComponentContract(
        val id: String,
        val system: String,
        val code: String,
        val quantity: QuantityContract?,
        val resultCodeSystem: String?,
        val resultCodes: List<ResultCode>,
        val cardinality: String?,
    )
}
