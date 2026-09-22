//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.health.fhir

import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import org.grovealliance.fhir.ExchangeContract
import org.grovealliance.fhir.GroveIdentifierRole
import org.grovealliance.fhir.RoledIdentifier
import org.hl7.fhir.r4.model.Coding
import org.hl7.fhir.r4.model.Observation
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeParseException

internal class HealthConnectProjectionRefusalException(val refusal: HealthConnectProjectionRefusal) : RuntimeException(refusal.toString())

internal fun refuseProjection(refusal: HealthConnectProjectionRefusal): Nothing = throw HealthConnectProjectionRefusalException(refusal)

internal fun Observation.sourceRecordIdentity(): RoledIdentifier? =
    identifier.mapNotNull(RoledIdentifier::from).firstOrNull { it.role == GroveIdentifierRole.SOURCE_RECORD }

internal fun Observation.sourceOutputIdentity(): RoledIdentifier? =
    identifier.mapNotNull(RoledIdentifier::from).firstOrNull { it.role == GroveIdentifierRole.SOURCE_OUTPUT }

/** The Health Connect record-type token, or a refusal when the Observation is not an adapter output. */
internal fun Observation.recordTypeToken(): String =
    getExtensionsByUrl(HealthConnectContract.HEALTH_CONNECT_RECORD_TYPE_EXTENSION).singleOrNull()?.value?.primitiveValue()
        ?: refuseProjection(HealthConnectProjectionRefusal.NotHealthConnectOutput("Observation.extension"))

internal fun Observation.measurementId(): String? =
    meta.profile.map { it.value }.firstNotNullOfOrNull { profile -> HealthConnectMeasurements.byProfile[profile]?.id }

/** The metadata the projected record carries: recording method, writer identity and no device. */
internal fun Observation.projectedMetadata(clientRecordId: String?): Metadata {
    val id = clientRecordId ?: sourceOutputIdentity()?.identifier?.value
        ?: refuseProjection(HealthConnectProjectionRefusal.MissingElement("Observation.identifier"))
    val version = getExtensionByUrl(ExchangeContract.WRITER_RECORD_VERSION_EXTENSION)?.value?.primitiveValue()?.toLongOrNull() ?: 0L
    val method = (getExtensionByUrl(HealthConnectContract.RECORDING_METHOD_EXTENSION)?.value as? Coding)?.code
    // A recorded method requires a device in AndroidX; the graph never carries one the record can name.
    val device = Device(type = Device.TYPE_UNKNOWN)
    return when (method) {
        "actively-recorded" -> Metadata.activelyRecorded(device = device, clientRecordId = id, clientRecordVersion = version)
        "automatically-recorded" -> Metadata.autoRecorded(device = device, clientRecordId = id, clientRecordVersion = version)
        "manual-entry" -> Metadata.manualEntry(clientRecordId = id, clientRecordVersion = version)
        else -> Metadata.unknownRecordingMethod(clientRecordId = id, clientRecordVersion = version)
    }
}

internal data class ProjectedMoment(val time: Instant, val offset: ZoneOffset)

internal data class ProjectedInterval(val start: ProjectedMoment, val end: ProjectedMoment)

internal fun Observation.moment(): ProjectedMoment =
    effectiveDateTimeType?.valueAsString?.let(::parseMoment) ?: missing("Observation.effectiveDateTime")

internal fun Observation.interval(): ProjectedInterval {
    val period = effectivePeriod?.takeIf { it.hasStart() && it.hasEnd() } ?: missing("Observation.effectivePeriod")
    return ProjectedInterval(
        parseMoment(period.startElement.valueAsString) ?: missing("Observation.effectivePeriod.start"),
        parseMoment(period.endElement.valueAsString) ?: missing("Observation.effectivePeriod.end"),
    )
}

private fun missing(path: String): Nothing = refuseProjection(HealthConnectProjectionRefusal.MissingElement(path))

private fun parseMoment(text: String?): ProjectedMoment? = text?.let {
    try {
        OffsetDateTime.parse(it).let { parsed -> ProjectedMoment(parsed.toInstant(), parsed.offset) }
    } catch (_: DateTimeParseException) {
        null
    }
}

/** The Quantity value of an Observation, required to carry the measurement's catalog-fixed unit. */
internal fun Observation.quantityValue(spec: QuantitySpec, path: String = "Observation.valueQuantity"): Double {
    val quantity = valueQuantity?.takeIf { it.hasValue() } ?: refuseProjection(HealthConnectProjectionRefusal.MissingElement(path))
    if (quantity.system != spec.system || quantity.code != spec.code) refuseProjection(HealthConnectProjectionRefusal.UnitMismatch(path))
    return quantity.value.toDouble()
}
