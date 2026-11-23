//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.fhir.contract

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * A FHIR `decimal` in its exact wire [lexeme].
 *
 * A measured `Double` is written as the shortest decimal that reads back as the same `Double`, in
 * positional notation, as every Grove producer writes it, so the same value travels as the same
 * characters on every platform: `72.0` as `72`, `0.1` as `0.1`, `1e-7` as `0.0000001`.
 */
@ConsistentCopyVisibility
data class FhirDecimal private constructor(val lexeme: String) {

    override fun toString(): String = lexeme

    companion object {
        private const val MAXIMUM_SIGNIFICANT_DIGITS = 17
        private val LEXEME = Regex("-?(0|[1-9][0-9]*)(\\.[0-9]+)?")

        /**
         * The decimal of [value], or an [IllegalArgumentException] when it is not finite.
         */
        fun of(value: Double): Result<FhirDecimal> = when {
            !value.isFinite() -> Result.failure(IllegalArgumentException("$value is not a finite decimal."))
            value == 0.0 -> Result.success(FhirDecimal(lexeme = "0"))
            else -> Result.success(FhirDecimal(lexeme = shortest(value = value).stripTrailingZeros().toPlainString()))
        }

        /**
         * The decimal [lexeme] states exactly, such as a source's own `72.0`, or an
         * [IllegalArgumentException] when it is not a FHIR decimal without exponent.
         */
        fun parse(lexeme: String): Result<FhirDecimal> =
            if (LEXEME.matches(lexeme)) {
                Result.success(FhirDecimal(lexeme = lexeme))
            } else {
                Result.failure(IllegalArgumentException("$lexeme is not a FHIR decimal."))
            }

        /**
         * The shortest decimal that reads back as [value], the closest to it among those of that length.
         */
        private fun shortest(value: Double): BigDecimal {
            val exact = BigDecimal(value)
            for (digits in 1..MAXIMUM_SIGNIFICANT_DIGITS) {
                val candidate = listOf(RoundingMode.HALF_EVEN, RoundingMode.UP, RoundingMode.DOWN)
                    .map { exact.round(MathContext(digits, it)) }
                    .filter { it.toDouble() == value }
                    .minByOrNull { (it - exact).abs() }
                if (candidate != null) return candidate
            }
            return exact
        }
    }
}
