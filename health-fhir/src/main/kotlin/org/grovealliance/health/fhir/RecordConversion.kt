//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

@file:OptIn(InternalGroveFhirApi::class)

package org.grovealliance.health.fhir

import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import org.grovealliance.fhir.BusinessIdentifier
import org.grovealliance.fhir.ExchangeContract
import org.grovealliance.fhir.ExchangeGraphAssembler
import org.grovealliance.fhir.ExchangeGraphNode
import org.grovealliance.fhir.GovernedSourceIdentifierDisclosurePolicy
import org.grovealliance.fhir.GraphEntry
import org.grovealliance.fhir.IdentifierSystem
import org.grovealliance.fhir.InternalGroveFhirApi
import org.grovealliance.fhir.RoledIdentifier
import org.grovealliance.fhir.SourceRecordIdentity
import org.hl7.fhir.r4.model.CodeType
import org.hl7.fhir.r4.model.CodeableConcept
import org.hl7.fhir.r4.model.Coding
import org.hl7.fhir.r4.model.DateTimeType
import org.hl7.fhir.r4.model.Extension
import org.hl7.fhir.r4.model.Identifier
import org.hl7.fhir.r4.model.InstantType
import org.hl7.fhir.r4.model.Observation
import org.hl7.fhir.r4.model.Period
import org.hl7.fhir.r4.model.Reference
import org.hl7.fhir.r4.model.StringType
import org.hl7.fhir.r4.model.Type
import java.time.Instant

/**
 * The state of one record's conversion: its identities, the assembler and everything the outputs share.
 *
 * Per-type conversions add outputs and call [finish]; every refusal is thrown as a
 * [HealthConnectRecordRefusal] and becomes a result at the converter boundary.
 */
internal class RecordConversion(
    val record: Record,
    val type: HealthConnectSourceType,
    val context: HealthConnectConversionContext,
) {
    val metadata: Metadata = record.metadata
    private val event = context.event
    private val scope = event.identityScope
    private val warnings = linkedSetOf<HealthConnectConversionWarning>()
    val source: HealthConnectSourceRecord
    val sourceRecord: SourceRecordIdentity
    private val writerIdentity: RoledIdentifier?
    private val assembler: ExchangeGraphAssembler

    init {
        validateMetadata(metadata, event.conversionInstant)
        source = HealthConnectSourceRecord(metadata.id, type)
        sourceRecord = scope.sourceRecord(HealthConnectContract.ADAPTER_ID, type.token, event.repositoryScope, metadata.id)
        writerIdentity = metadata.clientRecordId?.let { clientRecordId ->
            val writer = BusinessIdentifier(
                IdentifierSystem(HealthConnectContract.WRITER_PACKAGE_SYSTEM),
                metadata.dataOrigin.packageName,
            )
            scope.writerRecord(writer, clientRecordId)
        }
        assembler = ExchangeGraphAssembler(event, HealthConnectContract.ADAPTER_ID, metadata.device?.let(::resolveRecorder))
    }

    /** The node a repository id names that this record's graph does not carry, or null when every id has its node. */
    val repositoryIdWithoutNode: ExchangeGraphNode?
        get() = ExchangeGraphNode.RECORDING_DEVICE.takeIf { it in event.repositoryIds && assembler.recordingDeviceEntry == null }

    fun warn(warning: HealthConnectConversionWarning) {
        warnings += warning
    }

    /** The exactly-one output of this source type, whose discriminator is its measurement id. */
    fun singleOutput(measurement: String): RoledIdentifier = output(SINGLE_ROLE, measurement)

    fun output(role: String, discriminator: String): RoledIdentifier = sourceRecord.output(role, discriminator)

    fun spec(measurement: String): MeasurementSpec = HealthConnectMeasurements.spec(measurement)

    /** The subject reference every output and companion resource carries. */
    fun subjectReference(): Reference = assembler.subjectReference

    /** One output Observation with every element the contract fixes for the adapter, before its own value. */
    fun observation(
        spec: MeasurementSpec,
        output: RoledIdentifier,
        disclosesNativeIdentifier: Boolean = false,
        configure: Observation.() -> Unit,
    ): GraphEntry {
        val observation = Observation().apply {
            meta.addProfile(spec.profile)
            if (!spec.adapterSpecific) meta.addProfile(HealthConnectContract.HEALTH_CONNECT_OBSERVATION_PROFILE)
            addIdentifier(sourceRecord.identifier.toFhir())
            addIdentifier(output.toFhir())
            if (disclosesNativeIdentifier) nativeIdentifier()?.let(::addIdentifier)
            writerIdentity?.let { writer ->
                addIdentifier(writer.toFhir())
                val version = StringType(metadata.clientRecordVersion.toString())
                addExtension(Extension(ExchangeContract.WRITER_RECORD_VERSION_EXTENSION, version))
            }
            addExtension(Extension(HealthConnectContract.HEALTH_CONNECT_RECORD_TYPE_EXTENSION, CodeType(type.token)))
            recordingMethod(metadata)?.let(::addExtension)
            status = Observation.ObservationStatus.FINAL
            spec.category?.let { addCategory(it.concept()) }
            code = spec.code.concept().apply { spec.requiredCodings.forEach { addCoding(it.coding()) } }
            issuedElement = InstantType(metadata.lastModifiedTime.toString())
            configure()
        }
        assembler.decorate(observation)
        return GraphEntry(output, observation)
    }

    /** Closes the graph: no outputs is an admitted zero-output result, otherwise one validated active graph. */
    fun finish(
        primary: GraphEntry?,
        children: List<GraphEntry> = emptyList(),
        companions: List<GraphEntry> = emptyList(),
    ): HealthConnectConversionResult {
        if (primary == null) return HealthConnectConversionResult.NoOutput(source, warnings.toList())
        val outputs = listOf(assembler.primaryOutput(primary)) + children + companions
        val provenance = assembler.conversionProvenance(
            HealthConnectContract.HEALTH_CONNECT_PROVENANCE_PROFILE,
            sourceRecord.identifier,
            outputs,
            sourceActivityTime(outputs),
        )
        provenance.entity.single().addAgent().apply {
            type = CodeableConcept(Coding(ExchangeContract.PROVENANCE_PARTICIPANT, ENTERER, "Enterer"))
            who = Reference().apply {
                type = DEVICE
                identifier = Identifier()
                    .setSystem(HealthConnectContract.WRITER_PACKAGE_SYSTEM)
                    .setValue(metadata.dataOrigin.packageName)
            }
        }
        val graph = assembler.activeGraph(outputs, provenance)
        val identifiers = assembler.identifiers(sourceRecord.identifier, primary.identifier, children.map { it.identifier })
        return HealthConnectConversionResult.Converted(HealthConnectConversion(source, identifiers, graph, warnings.toList()))
    }

    private fun resolveRecorder(device: Device) = context.options.recordingDevice.resolve(device).also { resolved ->
        if (resolved == null) {
            val name = listOfNotNull(device.manufacturer, device.model).filter(String::isNotBlank).joinToString(" ")
            warn(HealthConnectConversionWarning.RecordingDeviceOmitted(name.ifEmpty { null }))
        }
    }

    private fun nativeIdentifier(): Identifier? =
        (context.options.nativeIdentifierDisclosure as? GovernedSourceIdentifierDisclosurePolicy.Authorized)
            ?.identifier(metadata.id)

    private companion object {
        const val SINGLE_ROLE = "single"
        const val ENTERER = "enterer"
        const val DEVICE = "Device"
    }
}

private fun validateMetadata(metadata: Metadata, conversionInstant: Instant) {
    if (metadata.id.isBlank()) refuse(HealthConnectValueFailure.NativeIdentifierInvalid("Metadata.id"))
    scalarText(metadata.id, "Metadata.id")
    val packageName = metadata.dataOrigin.packageName
    if (packageName.isBlank()) refuse(HealthConnectValueFailure.RequiredMetadataMissing("Metadata.dataOrigin.packageName"))
    scalarText(packageName, "Metadata.dataOrigin.packageName")
    if (!metadata.lastModifiedTime.isAfter(Instant.EPOCH)) {
        refuse(HealthConnectValueFailure.RequiredMetadataMissing("Metadata.lastModifiedTime"))
    }
    HealthConnectTime.requireFhirInstant(metadata.lastModifiedTime, "Metadata.lastModifiedTime")
    if (conversionInstant < metadata.lastModifiedTime) {
        refuse(HealthConnectValueFailure.ConversionInstantPrecedesSourceVersion("Metadata.lastModifiedTime"))
    }
    metadata.clientRecordId?.let { clientRecordId ->
        if (clientRecordId.isBlank()) refuse(HealthConnectValueFailure.NativeIdentifierInvalid("Metadata.clientRecordId"))
        scalarText(clientRecordId, "Metadata.clientRecordId")
        if (metadata.clientRecordVersion < 0) {
            refuse(HealthConnectValueFailure.NativeIdentifierInvalid("Metadata.clientRecordVersion"))
        }
    }
}

private fun recordingMethod(metadata: Metadata): Extension? {
    val code = when (metadata.recordingMethod) {
        Metadata.RECORDING_METHOD_UNKNOWN -> return null
        Metadata.RECORDING_METHOD_ACTIVELY_RECORDED -> "actively-recorded"
        Metadata.RECORDING_METHOD_AUTOMATICALLY_RECORDED -> "automatically-recorded"
        Metadata.RECORDING_METHOD_MANUAL_ENTRY -> "manual-entry"
        else -> refuse(HealthConnectValueFailure.UnsupportedSourceValue("Metadata.recordingMethod"))
    }
    val coding = Coding(HealthConnectContract.GROVE_RECORDING_METHOD, code, null)
    return Extension(HealthConnectContract.RECORDING_METHOD_EXTENSION, coding)
}

/** The source activity span of the outputs, never the administrative modification time. */
private fun sourceActivityTime(outputs: List<GraphEntry>): Type {
    val effectives = outputs.mapNotNull { (it.resource as? Observation)?.effective }
    val dateTimes = effectives.filterIsInstance<DateTimeType>()
    val periods = effectives.filterIsInstance<Period>()
    if (periods.isNotEmpty()) {
        return Period().apply {
            startElement = (periods.map { it.startElement } + dateTimes).minWith(EFFECTIVE_ORDER).copy()
            endElement = (periods.map { it.endElement } + dateTimes).maxWith(EFFECTIVE_ORDER).copy()
        }
    }
    val sorted = dateTimes.sortedWith(EFFECTIVE_ORDER)
    return if (sorted.first().valueAsString == sorted.last().valueAsString) {
        sorted.first().copy()
    } else {
        Period().apply {
            startElement = sorted.first().copy()
            endElement = sorted.last().copy()
        }
    }
}

private val EFFECTIVE_ORDER = compareBy<DateTimeType>({ it.value.time }, { it.valueAsString })
