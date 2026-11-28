//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.fhir.contract

/**
 * An unsigned base-10 integer in its canonical wire form: `0`, or a nonzero digit followed by digits.
 *
 * The protocol sets no machine-integer ceiling, so the validated text is kept.
 */
@InternalGroveFhirApi
@ConsistentCopyVisibility
data class CanonicalNonnegativeDecimal private constructor(val rawValue: String) {

    override fun toString(): String = rawValue

    companion object {

        /**
         * The decimal [rawValue] states, or [ExchangeIdentityError.InvalidNonnegativeDecimal] unless it
         * is canonical.
         */
        fun parse(rawValue: String): Result<CanonicalNonnegativeDecimal> =
            if (isCanonical(value = rawValue)) {
                Result.success(CanonicalNonnegativeDecimal(rawValue = rawValue))
            } else {
                Result.failure(ExchangeIdentityError.InvalidNonnegativeDecimal(value = rawValue))
            }

        /**
         * The decimal of [value], or [ExchangeIdentityError.InvalidNonnegativeDecimal] when it is negative.
         */
        fun of(value: Long): Result<CanonicalNonnegativeDecimal> = parse(rawValue = value.toString())

        /**
         * Whether [value] is `0` or a nonzero digit followed by digits, with no sign or whitespace.
         */
        fun isCanonical(value: String): Boolean = when {
            value.isEmpty() -> false
            value[0] == '0' -> value.length == 1
            else -> value[0] in '1'..'9' && value.all { it in '0'..'9' }
        }
    }
}
