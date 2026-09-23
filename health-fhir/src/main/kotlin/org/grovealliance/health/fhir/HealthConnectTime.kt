//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.health.fhir

import org.grovealliance.fhir.ExchangeEventContext
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField

/** Instant handling the Mobile contract fixes: FHIR year range, millisecond effective times, exact identity instants. */
internal object HealthConnectTime {
    private val fhirOffsetDateTime: DateTimeFormatter = DateTimeFormatterBuilder()
        .appendPattern("uuuu-MM-dd'T'HH:mm:ss")
        .appendFraction(ChronoField.NANO_OF_SECOND, 0, MAX_FRACTION_DIGITS, true)
        .appendOffsetId()
        .toFormatter()
    private val utcNanosecond: DateTimeFormatter = DateTimeFormatterBuilder().appendInstant(MAX_FRACTION_DIGITS).toFormatter()

    fun isFhirInstant(value: Instant): Boolean = value in ExchangeEventContext.FHIR_INSTANT_RANGE

    fun requireFhirInstant(value: Instant, field: String) {
        if (!isFhirInstant(value)) refuse(HealthConnectValueFailure.EffectivePeriodInvalid(field))
    }

    /** The exact nanosecond UTC form every output discriminator states. */
    fun utc9(value: Instant, field: String): String {
        requireFhirInstant(value, field)
        return utcNanosecond.format(value)
    }

    /** Rounds an effective instant to the millisecond, ties to even, as the Mobile contract requires. */
    fun canonicalEffective(value: Instant): Instant {
        val epochMilliseconds = BigDecimal.valueOf(value.epochSecond)
            .multiply(MILLISECONDS_PER_SECOND)
            .add(BigDecimal.valueOf(value.nano.toLong(), NANOSECONDS_TO_MILLISECONDS_SCALE))
        return Instant.ofEpochMilli(epochMilliseconds.setScale(0, RoundingMode.HALF_EVEN).longValueExact())
    }

    /** The FHIR dateTime lexeme of an effective instant with its source offset, or in UTC when the source has none. */
    fun fhirDateTime(value: Instant, offset: ZoneOffset?, field: String): String {
        requireFhirInstant(value, field)
        val canonical = canonicalEffective(value)
        requireFhirInstant(canonical, field)
        if (offset == null) return canonical.toString()
        if (offset.totalSeconds % SECONDS_PER_MINUTE != 0 || offset.totalSeconds !in -MAX_OFFSET_SECONDS..MAX_OFFSET_SECONDS) {
            refuse(HealthConnectValueFailure.EffectivePeriodInvalid(field))
        }
        val local = canonical.atOffset(offset)
        if (local.year !in MIN_FHIR_YEAR..MAX_FHIR_YEAR) refuse(HealthConnectValueFailure.EffectivePeriodInvalid(field))
        return fhirOffsetDateTime.format(local)
    }

    private const val MAX_FRACTION_DIGITS = 9
    private const val NANOSECONDS_TO_MILLISECONDS_SCALE = 6
    private const val SECONDS_PER_MINUTE = 60
    private const val MAX_OFFSET_SECONDS = 14 * 60 * SECONDS_PER_MINUTE
    private const val MIN_FHIR_YEAR = 1
    private const val MAX_FHIR_YEAR = 9999
    private val MILLISECONDS_PER_SECOND = BigDecimal("1000")
}

/** Binds duplicate coordinates to their exact source-list slot before any output sorting. */
internal fun <T, K> assignSourceListOccurrences(values: List<T>, coordinate: (T) -> K): List<Pair<T, Int>> {
    val occurrences = mutableMapOf<K, Int>()
    return values.map { value ->
        val key = coordinate(value)
        val occurrence = occurrences.getOrDefault(key, 0)
        occurrences[key] = occurrence + 1
        value to occurrence
    }
}
