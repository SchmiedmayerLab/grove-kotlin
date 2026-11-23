//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.fhir.contract

import java.math.BigDecimal

/**
 * The fixed unit of a measurement: its UCUM [code] in [system], the human-readable [unit], and the
 * [valueDomain] its values fall in.
 */
@InternalGroveFhirApi
data class QuantityContract(
    val system: String,
    val code: String,
    val unit: String,
    val valueDomain: QuantityValueDomain?,
)

/**
 * The closed numeric range of a quantity, from [minimum] to an optional [maximum].
 */
@InternalGroveFhirApi
data class QuantityValueDomain(
    val minimum: QuantityBoundary,
    val maximum: QuantityBoundary?,
    val integerOnly: Boolean,
) {

    /**
     * Whether [value] lies in the domain.
     */
    fun contains(value: BigDecimal): Boolean {
        val isInteger = value.stripTrailingZeros().scale() <= 0
        val aboveMinimum = minimum.compare(value = value).let { if (minimum.inclusive) it <= 0 else it < 0 }
        val belowMaximum = maximum?.compare(value = value)?.let { if (maximum.inclusive) it >= 0 else it > 0 } ?: true
        return (!integerOnly || isInteger) && aboveMinimum && belowMaximum
    }
}

/**
 * One end of a [QuantityValueDomain], at the decimal [value].
 */
@InternalGroveFhirApi
data class QuantityBoundary(
    val value: String,
    val inclusive: Boolean,
) {

    /**
     * How this boundary compares with [value].
     */
    fun compare(value: BigDecimal): Int = BigDecimal(this.value).compareTo(value)
}

/**
 * The fixed [quantity] of the measurement [measurementId].
 */
@InternalGroveFhirApi
data class FixedMeasurementQuantityClaim(
    val measurementId: String,
    val quantity: QuantityContract,
)
