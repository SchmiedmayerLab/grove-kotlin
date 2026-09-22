//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.health.fhir

import org.grovealliance.fhir.ExchangeProtocol
import org.hl7.fhir.r4.model.DateTimeType
import org.hl7.fhir.r4.model.Extension
import org.hl7.fhir.r4.model.Observation
import org.hl7.fhir.r4.model.Period
import org.hl7.fhir.r4.model.Quantity
import org.hl7.fhir.r4.model.StringType
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneOffset

/** One source number with the field it came from; [exact] carries an integer source without a binary detour. */
internal data class SourceValue(val value: Double, val field: String, val exact: BigDecimal? = null)

/** One source instant with the offset the source stated for it. */
internal data class SourceMoment(val time: Instant, val offset: ZoneOffset?)

/** One source interval with the offsets the source stated for its ends. */
internal data class SourceInterval(val start: SourceMoment, val end: SourceMoment)

/** One source enumeration value with the field it came from. */
internal data class SourceEnum(val value: Int, val field: String)

/** A coded source enumeration: the mapping it lives in and the exact source token. */
internal data class SourceCode(val mapping: SourceCodedMapping, val token: String)

internal fun Instant.at(offset: ZoneOffset?): SourceMoment = SourceMoment(this, offset)

/** The one offset a record states for its whole span, or null when its ends disagree and members fall back to UTC. */
internal fun spanOffset(start: ZoneOffset?, end: ZoneOffset?): ZoneOffset? = start?.takeIf { it == end }

internal infix fun SourceMoment.until(end: SourceMoment): SourceInterval = SourceInterval(this, end)

internal fun Int.at(field: String): SourceEnum = SourceEnum(this, field)

internal fun Double.at(field: String): SourceValue = SourceValue(this, field)

internal fun Long.at(field: String): SourceValue = SourceValue(toDouble(), field, toBigDecimal())

internal fun Map<Int, String>.token(value: SourceEnum): String = token(value.value, value.field)

internal fun RecordConversion.dateTime(moment: SourceMoment, field: String): DateTimeType {
    if (moment.offset == null) warn(HealthConnectConversionWarning.SourceOffsetUnavailable(field))
    return DateTimeType(HealthConnectTime.fhirDateTime(moment.time, moment.offset, field))
}

internal fun RecordConversion.period(interval: SourceInterval, field: String): Period {
    if (!interval.start.time.isBefore(interval.end.time)) refuse(HealthConnectValueFailure.EffectivePeriodInvalid(field))
    return Period().apply {
        startElement = dateTime(interval.start, "$field.start")
        endElement = dateTime(interval.end, "$field.end")
    }
}

internal fun quantity(spec: QuantitySpec, value: BigDecimal): Quantity =
    Quantity().setValue(value).setSystem(spec.system).setCode(spec.code).setUnit(spec.unit)

/** A finite source number inside the measurement's catalog domain, as an exact decimal. */
internal fun RecordConversion.decimal(value: SourceValue, spec: QuantitySpec?): BigDecimal {
    val field = "${type.token}.${value.field}"
    if (!value.value.isFinite()) refuse(HealthConnectValueFailure.ValueOutsideDomain(field))
    val decimal = value.exact ?: BigDecimal.valueOf(value.value)
    spec?.domain?.violation(decimal)?.let { refuse(HealthConnectValueFailure.ValueOutsideDomain(field)) }
    return decimal
}

internal fun scalarText(value: String, field: String): String {
    if (!ExchangeProtocol.isScalarText(value)) refuse(HealthConnectValueFailure.TextNotUnicodeScalar(field))
    return value
}

/** Keeps user-authored title and notes only under the retaining policy; the omission is chosen and never warns. */
internal fun RecordConversion.retainSessionText(observation: Observation, title: String?, notes: String?) {
    if (context.options.userAuthoredText != UserAuthoredTextPolicy.RETAIN) return
    title?.takeIf(String::isNotBlank)?.let {
        val text = StringType(scalarText(it, "${type.token}.title"))
        observation.addExtension(Extension(HealthConnectContract.HEALTH_CONNECT_SESSION_TITLE, text))
    }
    notes?.takeIf(String::isNotBlank)?.let { observation.addNote().text = scalarText(it, "${type.token}.notes") }
}
