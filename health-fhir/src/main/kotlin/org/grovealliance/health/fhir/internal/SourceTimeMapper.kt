//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.health.fhir.internal

import org.grovealliance.fhir.contract.ExchangeGraphRule
import org.grovealliance.fhir.contract.ExchangeInstant
import org.grovealliance.fhir.contract.InternalGroveFhirApi
import org.grovealliance.fhir.contract.Observation
import org.grovealliance.fhir.contract.Period
import org.grovealliance.fhir.contract.ProducerDiagnostic
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * The times of a Record as the exchange states them: effective times at the source's own offset, and
 * the instants an output discriminator names.
 */
@OptIn(InternalGroveFhirApi::class)
internal class SourceTimeMapper {
    private val exchangeInstant = ExchangeInstant()

    /**
     * [instant] at [offset], or in UTC when the source states none.
     */
    fun mapDateTime(instant: Instant, offset: ZoneOffset?): Observation.Effective =
        Observation.Effective.DateTime(value = exchangeInstant.at(instant = instant, offset = offset ?: ZoneOffset.UTC))

    /**
     * The period from [start] at [startOffset] to [end] at [endOffset].
     */
    fun mapPeriod(
        start: Instant,
        startOffset: ZoneOffset?,
        end: Instant,
        endOffset: ZoneOffset?,
    ): Observation.Effective =
        Observation.Effective.PeriodValue(
            value = Period(
                start = exchangeInstant.at(instant = start, offset = startOffset ?: ZoneOffset.UTC),
                end = exchangeInstant.at(instant = end, offset = endOffset ?: ZoneOffset.UTC),
            ),
        )

    /**
     * [instant] in UTC, as `Observation.issued` states it.
     */
    fun mapIssued(instant: Instant): String = exchangeInstant.utc(instant = instant)

    /**
     * One `mobile-omission.source-offset` warning per effective element of [effectives] stated in UTC
     * because the Record states no offset for it.
     */
    fun mapOffsetWarnings(effectives: List<Observation.Effective>): List<ProducerDiagnostic> = effectives
        .flatMap { effective ->
            when (effective) {
                is Observation.Effective.DateTime -> listOf("Observation.effectiveDateTime")
                is Observation.Effective.PeriodValue -> listOf("Observation.effectivePeriod.start", "Observation.effectivePeriod.end")
            }
        }
        .distinct()
        .map { ExchangeGraphRule.MOBILE_OMISSION_SOURCE_OFFSET.diagnostic(location = it) }

    /**
     * [instant] in UTC with nine fractional digits, as a discriminator names it.
     */
    fun mapDiscriminator(instant: Instant): String = DISCRIMINATOR.format(instant)

    private companion object {
        val DISCRIMINATOR: DateTimeFormatter = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSSSSSSS'Z'").withZone(ZoneOffset.UTC)
    }
}
