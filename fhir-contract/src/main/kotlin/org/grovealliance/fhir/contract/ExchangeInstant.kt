//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.fhir.contract

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.ZoneOffset
import kotlin.math.abs

/**
 * Converter clock instants as the exchange states them: millisecond precision, ASCII, trailing
 * fraction zeros dropped and no fraction on a whole second, in UTC unless stated at an offset, such
 * as `2026-10-03T10:30:00Z` or `2026-10-03T10:30:00.251-07:00`.
 *
 * Every lexeme is formatted with integer arithmetic over the proleptic Gregorian calendar, so the
 * same instant gives the same bytes on every platform and under every locale.
 */
@InternalGroveFhirApi
class ExchangeInstant {

    /**
     * [instant] as a FHIR `instant` lexeme in UTC.
     */
    fun utc(instant: Instant): String = lexeme(instant = instant, offset = ZoneOffset.UTC)

    /**
     * [instant] as a FHIR `dateTime` at [offset]; an offset's seconds are dropped, and the wall time
     * follows the stated offset, so the lexeme always names the exact instant.
     */
    fun at(instant: Instant, offset: ZoneOffset): String = lexeme(instant = instant, offset = offset)

    private fun lexeme(instant: Instant, offset: ZoneOffset): String {
        val offsetMinutes = offset.totalSeconds / SECONDS_PER_MINUTE
        val milliseconds = instant.toEpochMilliRoundedHalfEven()
        val wall = Instant.ofEpochMilli(milliseconds).atOffset(ZoneOffset.ofTotalSeconds(offsetMinutes * SECONDS_PER_MINUTE))
        val fraction = (wall.nano / NANOS_PER_MILLI).toString().padStart(length = 3, padChar = '0').trimEnd('0')
        val zone = if (offsetMinutes == 0) {
            "Z"
        } else {
            (if (offsetMinutes < 0) "-" else "+") +
                (abs(offsetMinutes) / MINUTES_PER_HOUR).pad(width = 2) + ":" + (abs(offsetMinutes) % MINUTES_PER_HOUR).pad(width = 2)
        }
        return wall.year.pad(width = 4) + "-" + wall.monthValue.pad(width = 2) + "-" + wall.dayOfMonth.pad(width = 2) +
            "T" + wall.hour.pad(width = 2) + ":" + wall.minute.pad(width = 2) + ":" + wall.second.pad(width = 2) +
            (if (fraction.isEmpty()) "" else ".$fraction") + zone
    }

    private fun Instant.toEpochMilliRoundedHalfEven(): Long =
        BigDecimal.valueOf(epochSecond)
            .multiply(BigDecimal.valueOf(MILLIS_PER_SECOND))
            .add(BigDecimal.valueOf(nano.toLong()).movePointLeft(NANO_TO_MILLI_DIGITS))
            .setScale(0, RoundingMode.HALF_EVEN)
            .longValueExact()

    private fun Int.pad(width: Int) = toString().padStart(length = width, padChar = '0')

    private companion object {
        const val SECONDS_PER_MINUTE = 60
        const val MINUTES_PER_HOUR = 60
        const val NANOS_PER_MILLI = 1_000_000
        const val MILLIS_PER_SECOND = 1000L
        const val NANO_TO_MILLI_DIGITS = 6
    }
}
