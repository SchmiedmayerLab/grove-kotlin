//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.health.fhir

import androidx.health.connect.client.records.Record
import org.grovealliance.fhir.BusinessIdentifier
import org.grovealliance.fhir.ConversionBatch
import org.grovealliance.fhir.ExchangeGraph
import org.grovealliance.fhir.ExchangeGraphDiagnostic
import org.grovealliance.fhir.ExchangeGraphRule
import org.grovealliance.fhir.at
import org.hl7.fhir.r4.model.Observation
import org.hl7.fhir.r4.model.Specimen

/** Why an Observation could not be read back as a Health Connect record; each refusal carries a registry diagnostic. */
public sealed interface HealthConnectProjectionRefusal {
    /** The registry diagnostic this refusal reports. */
    public val diagnostic: ExchangeGraphDiagnostic

    /** The Observation carries no Health Connect record-type lineage. */
    public data class NotHealthConnectOutput(public val location: String) : HealthConnectProjectionRefusal {
        override val diagnostic: ExchangeGraphDiagnostic
            get() = ExchangeGraphRule.MOBILE_INPUT_UNSUPPORTED_SOURCE_TYPE.at(location)
    }

    /** The record-type token has no projection back to an AndroidX record. */
    public data class UnsupportedSourceType(public val token: String) : HealthConnectProjectionRefusal {
        override val diagnostic: ExchangeGraphDiagnostic
            get() = ExchangeGraphRule.MOBILE_INPUT_UNSUPPORTED_SOURCE_TYPE.at(token)
    }

    /** A member output cannot stand alone as a record; project its whole graph instead. */
    public data class ChildOutput(public val measurement: String) : HealthConnectProjectionRefusal {
        override val diagnostic: ExchangeGraphDiagnostic
            get() = ExchangeGraphRule.MOBILE_INPUT_REQUIRED_COMPONENT_MISSING.at(measurement)
    }

    /** An element the record requires is absent. */
    public data class MissingElement(public val path: String) : HealthConnectProjectionRefusal {
        override val diagnostic: ExchangeGraphDiagnostic
            get() = ExchangeGraphRule.MOBILE_INPUT_REQUIRED_METADATA_MISSING.at(path)
    }

    /** A Quantity does not carry the catalog-fixed system and code. */
    public data class UnitMismatch(public val path: String) : HealthConnectProjectionRefusal {
        override val diagnostic: ExchangeGraphDiagnostic
            get() = ExchangeGraphRule.MOBILE_INPUT_VALUE_SHAPE_INVALID.at(path)
    }

    /** A coding is outside the published source vocabulary. */
    public data class UnsupportedCode(public val path: String) : HealthConnectProjectionRefusal {
        override val diagnostic: ExchangeGraphDiagnostic
            get() = ExchangeGraphRule.MOBILE_INPUT_UNSUPPORTED_SOURCE_VALUE.at(path)
    }
}

/** The outcome of reading one Observation back as a Health Connect record. */
public sealed interface HealthConnectProjectionResult {
    public data class Projected(public val record: Record) : HealthConnectProjectionResult

    public data class Refused(public val refusal: HealthConnectProjectionRefusal) : HealthConnectProjectionResult
}

/** One source record of a graph that could not be read back; [sourceRecord] is null when it carried no identity. */
public data class HealthConnectProjectionFailure(
    public val sourceRecord: BusinessIdentifier?,
    public val refusal: HealthConnectProjectionRefusal,
)

/**
 * Reads one Health Connect output back as its AndroidX record.
 *
 * The record's `clientRecordId` is [clientRecordId], or the Observation's source-output identity when
 * omitted, so a re-import supersedes rather than duplicates. Member outputs and multi-output
 * records project from their whole graph through [ExchangeGraph.toHealthConnectRecords].
 */
public fun Observation.toHealthConnectRecord(clientRecordId: String? = null): HealthConnectProjectionResult = try {
    HealthConnectProjectionResult.Projected(HealthConnectProjection.project(listOf(this), emptyList(), clientRecordId))
} catch (refusal: HealthConnectProjectionRefusalException) {
    HealthConnectProjectionResult.Refused(refusal.refusal)
}

/** Reads every source record of an active graph back as one AndroidX record, in graph order. */
public fun ExchangeGraph.toHealthConnectRecords(): ConversionBatch<Record, HealthConnectProjectionFailure> {
    val resources = toBundle().entry.map { it.resource }
    val groups = linkedMapOf<String?, MutableList<Observation>>()
    resources.filterIsInstance<Observation>()
        .filter { it.hasExtension(HealthConnectContract.HEALTH_CONNECT_RECORD_TYPE_EXTENSION) }
        .forEach { groups.getOrPut(it.sourceRecordIdentity()?.identifier?.value) { mutableListOf() }.add(it) }
    val specimens = resources.filterIsInstance<Specimen>()
    val records = mutableListOf<Record>()
    val failures = mutableListOf<HealthConnectProjectionFailure>()
    groups.forEach { (sourceValue, observations) ->
        val companions = specimens.filter { it.identifier.any { identifier -> identifier.value == sourceValue } }
        try {
            records += HealthConnectProjection.project(observations, companions, null)
        } catch (refusal: HealthConnectProjectionRefusalException) {
            failures += HealthConnectProjectionFailure(observations.first().sourceRecordIdentity()?.identifier, refusal.refusal)
        }
    }
    return ConversionBatch(records, failures)
}
