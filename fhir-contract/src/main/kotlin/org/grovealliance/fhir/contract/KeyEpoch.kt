//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.fhir.contract

/**
 * A positive key epoch in its canonical decimal form.
 *
 * The protocol sets no machine-integer ceiling, so the validated text is kept; epochs compare by
 * numeric value.
 */
@ConsistentCopyVisibility
data class KeyEpoch private constructor(val rawValue: String) : Comparable<KeyEpoch> {

    override fun compareTo(other: KeyEpoch): Int = compareValuesBy(this, other, { it.rawValue.length }, { it.rawValue })

    override fun toString(): String = rawValue

    companion object {

        /**
         * The epoch [value], or [ExchangeIdentityError.InvalidKeyEpoch] unless it is positive.
         */
        fun of(value: Long): Result<KeyEpoch> = parse(text = value.toString())

        /**
         * The epoch [text] states, or [ExchangeIdentityError.InvalidKeyEpoch] unless it is a positive
         * canonical decimal.
         */
        fun parse(text: String): Result<KeyEpoch> =
            if (CanonicalNonnegativeDecimal.isCanonical(value = text) && text != "0") {
                Result.success(KeyEpoch(rawValue = text))
            } else {
                Result.failure(ExchangeIdentityError.InvalidKeyEpoch(value = text))
            }
    }
}
