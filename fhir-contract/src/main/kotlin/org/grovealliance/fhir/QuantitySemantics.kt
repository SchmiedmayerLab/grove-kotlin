//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.fhir

import java.math.BigDecimal

/** One catalog-fixed system and code pair for an Observation Quantity. */
public data class QuantitySemantics(public val system: String, public val code: String)

/** The representational limits one catalog Quantity states, independent of clinical plausibility. */
public class QuantityValueDomain(
    public val minimum: BigDecimal? = BigDecimal.ZERO,
    public val maximum: BigDecimal? = null,
    public val integerOnly: Boolean = false,
) {
    init {
        require(minimum == null || maximum == null || minimum <= maximum) {
            "A Quantity value domain cannot have a minimum above its maximum."
        }
    }

    /** Why a value falls outside this domain, or null when it is admitted. */
    public fun violation(value: BigDecimal): String? = when {
        minimum != null && value < minimum -> "must be greater than or equal to $minimum"
        maximum != null && value > maximum -> "must be less than or equal to $maximum"
        integerOnly && value.stripTrailingZeros().scale() > 0 -> "must be an integer"
        else -> null
    }

    override fun toString(): String =
        "QuantityValueDomain(minimum=$minimum, maximum=$maximum, integerOnly=$integerOnly)"

    public companion object {
        /** A domain that admits every finite decimal. */
        public val UNBOUNDED: QuantityValueDomain = QuantityValueDomain(minimum = null)
    }
}
