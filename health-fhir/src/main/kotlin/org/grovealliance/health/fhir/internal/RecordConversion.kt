//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

@file:Suppress("detekt:ReturnCount")

package org.grovealliance.health.fhir.internal

import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import org.grovealliance.fhir.contract.BusinessIdentifier
import org.grovealliance.fhir.contract.CodeableConcept
import org.grovealliance.fhir.contract.Coding
import org.grovealliance.fhir.contract.ExchangeAdapterContract
import org.grovealliance.fhir.contract.ExchangeEnvelope
import org.grovealliance.fhir.contract.ExchangeGraphAssembler
import org.grovealliance.fhir.contract.ExchangeOutputDraft
import org.grovealliance.fhir.contract.ExchangeProducer
import org.grovealliance.fhir.contract.ExchangeRecord
import org.grovealliance.fhir.contract.ExchangeRecordDraft
import org.grovealliance.fhir.contract.Hl7Url
import org.grovealliance.fhir.contract.Identifier
import org.grovealliance.fhir.contract.InternalGroveFhirApi
import org.grovealliance.fhir.contract.Measurements
import org.grovealliance.fhir.contract.ProducerDiagnostic
import org.grovealliance.fhir.contract.Profile
import org.grovealliance.fhir.contract.Provenance
import org.grovealliance.fhir.contract.RecordingDevice
import org.grovealliance.fhir.contract.Reference
import org.grovealliance.fhir.contract.ResourceType
import org.grovealliance.fhir.contract.RetractionRecord
import org.grovealliance.health.fhir.HealthConnectConversionError
import org.grovealliance.health.fhir.HealthConnectConversionError.ValueFailure
import org.grovealliance.health.fhir.HealthConnectDeletion
import org.grovealliance.health.fhir.HealthConnectExport
import org.grovealliance.health.fhir.HealthConnectFhirExporter
import org.grovealliance.health.fhir.HealthConnectRetraction
import java.time.Instant

/**
 * The conversion of one Record: its Record class admitted, the metadata Health Connect assigns
 * checked, its outputs mapped, and the record assembled with the recording Device it names and the
 * application that entered it.
 */
@OptIn(InternalGroveFhirApi::class)
internal class RecordConversion(
    producer: ExchangeProducer,
    repositoryScope: BusinessIdentifier,
    private val options: HealthConnectFhirExporter.Options,
) {
    private val envelope = ExchangeEnvelope.of(
        producer = producer,
        adapter = ExchangeAdapterContract(
            adapterId = HealthConnectCatalog.ADAPTER_ID,
            provenanceProfile = HealthConnectCatalog.CONVERSION_PROVENANCE_PROFILE,
            applicationDeviceProfile = Profile.Mobile.GROVE_APPLICATION_DEVICE,
            writerApplicationSystem = options.writerApplicationSystem,
        ),
        repositoryScope = repositoryScope,
    )
    private val assembler = ExchangeGraphAssembler(envelope = envelope)
    private val times = SourceTimeMapper()
    private val observations = ObservationMapper()
    private val outputs = OutputMapper(
        times = times,
        observations = observations,
        quantities = QuantityReadingMapper(times = times),
        bloodPressure = BloodPressureMapper(
            times = times,
            observations = observations,
        ),
        bloodGlucose = BloodGlucoseMapper(
            times = times,
            observations = observations,
        ),
        mindfulness = MindfulnessMapper(
            times = times,
            observations = observations,
            userText = options.userText,
        ),
        sleep = SleepMapper(
            times = times,
            observations = observations,
        ),
        exercise = ExerciseMapper(
            times = times,
            observations = observations,
            projection = ExerciseProjection(),
            userText = options.userText,
        ),
        nutrition = NutritionMapper(
            times = times,
            observations = observations,
        ),
        skinTemperature = SkinTemperatureMapper(
            times = times,
            observations = observations,
        ),
        cycle = CycleMapper(
            times = times,
            observations = observations,
        ),
    )

    fun export(record: Record, instant: Instant): HealthConnectExport {
        val recordType = HealthConnectCatalog.tokens[record::class] ?: record::class.java.simpleName
        val source = HealthConnectExport.Source(
            recordId = record.metadata.id,
            recordType = recordType,
        )
        return convert(record = record, recordType = recordType, instant = instant).fold(
            onSuccess = { (exchangeRecord, warnings) ->
                HealthConnectExport(
                    source = source,
                    outcome = HealthConnectExport.Outcome.Exported(record = exchangeRecord),
                    warnings = warnings,
                )
            },
            onFailure = { error ->
                HealthConnectExport(
                    source = source,
                    outcome = HealthConnectExport.Outcome.Refused(
                        error = error as? HealthConnectConversionError ?: HealthConnectConversionError.Exchange(cause = error),
                    ),
                    warnings = emptyList(),
                )
            },
        )
    }

    fun retract(deletion: HealthConnectDeletion, instant: Instant): HealthConnectRetraction.Outcome {
        val recordType = HealthConnectCatalog.tokens[deletion.recordType]
        if (recordType == null || !outputs.converts(recordType = deletion.recordType)) {
            return HealthConnectRetraction.Outcome.NothingToRetract
        }
        if (deletion.recordId.isBlank()) {
            return HealthConnectRetraction.Outcome.Refused(
                error = HealthConnectConversionError.InvalidValue(
                    failure = ValueFailure.REQUIRED_METADATA_MISSING,
                    location = "Record.metadata.id",
                ),
            )
        }
        val retraction = envelope.sourceRecord(sourceType = recordType, nativeRecordId = deletion.recordId).mapCatching { sourceRecord ->
            assembler.retraction(
                sourceRecord = sourceRecord.identifier,
                targets = listOf(
                    RetractionRecord.Target.sourceRecord(
                        sourceRecord = sourceRecord.identifier,
                        nativeRecordIdentifier = options.nativeIdentifierSystem?.let {
                            BusinessIdentifier.create(system = it, value = deletion.recordId).getOrThrow()
                        },
                    ).getOrThrow(),
                ),
                occurred = RetractionRecord.Occurrence.Between(
                    start = deletion.deletedAfter?.takeIf { it <= deletion.detectedAt },
                    end = deletion.detectedAt,
                ),
                instant = instant,
            ).getOrThrow()
        }
        return retraction.fold(
            onSuccess = { HealthConnectRetraction.Outcome.Retracted(record = it.record) },
            onFailure = { HealthConnectRetraction.Outcome.Refused(error = HealthConnectConversionError.Exchange(cause = it)) },
        )
    }

    private fun convert(record: Record, recordType: String, instant: Instant): Result<Pair<ExchangeRecord, List<ProducerDiagnostic>>> {
        val contract = HealthConnectCatalog.recordTypes[recordType]
        admissionError(contract = contract, recordType = recordType)?.let { return Result.failure(it) }
        val metadata = record.metadata
        metadataError(metadata = metadata)?.let { return Result.failure(it) }
        if (instant < metadata.lastModifiedTime) return Result.failure(HealthConnectConversionError.ConversionPrecedesSourceVersion)
        val mapped = outputs.mapOutputs(record = record, recordType = recordType, contract = requireNotNull(contract))
            .getOrElse { return Result.failure(it) }
        val writer = writerRecord(metadata = metadata).getOrElse { return Result.failure(it) }
        val sourceRecord = envelope.sourceRecord(sourceType = recordType, nativeRecordId = metadata.id)
            .getOrElse { return Result.failure(HealthConnectConversionError.Exchange(cause = it)) }
        val native = options.nativeIdentifierSystem?.let { Identifier(system = it.rawValue, value = metadata.id) }
        val primary = native?.let { primaryOutput(contract = contract, drafts = mapped.drafts) }
        val drafts = mapped.drafts.mapIndexed { index, draft ->
            val written = if (index == 0 || recordType == EXERCISE_SESSION) draft.copy(writerRecord = writer) else draft
            if (index == primary && native != null) written.copy(clearIdentifiers = listOf(native)) else written
        }
        return assembler.assemble(
            draft = ExchangeRecordDraft(
                instant = instant,
                sourceRecord = sourceRecord,
                outputs = drafts,
                recordingDevice = metadata.device?.let { recordingDevice(device = it) },
                sourceAgents = listOf(enterer(packageName = metadata.dataOrigin.packageName)),
            ),
        ).fold(
            onSuccess = { Result.success(it.record to mapped.warnings) },
            onFailure = { Result.failure(HealthConnectConversionError.Exchange(cause = it)) },
        )
    }

    /**
     * The index of the one output the contract designates as the Record's primary representation, which
     * alone may carry its disclosed native identifier, or `null` when no output or several qualify.
     *
     * An output qualifies when it is of an admitted resource type and claims exactly one measurement
     * whose output contract counts it once per Record.
     */
    private fun primaryOutput(contract: HealthConnectRecordContract, drafts: List<ExchangeOutputDraft>): Int? {
        val eligible = contract.outputs
            .filter {
                it.countRule in HealthConnectCatalog.nativeIdentifierCountRules ||
                    it.graphRule in HealthConnectCatalog.nativeIdentifierGraphRules
            }
            .map { it.measurement }
            .toSet()
        val candidates = drafts.indices.filter { index ->
            val observation = (drafts[index].resource as? ExchangeOutputDraft.Resource.ObservationResource)?.observation
            observation != null &&
                observation.resourceType !in HealthConnectCatalog.nativeIdentifierExcludedResourceTypes &&
                measurements(profiles = observation.meta?.profile.orEmpty()).count { it in eligible } == 1
        }
        return candidates.singleOrNull()
    }

    /**
     * The catalog measurements an output claims through its [profiles].
     */
    private fun measurements(profiles: List<String>): Set<String> =
        Measurements.byId.values.filter { it.profile in profiles }.map { it.id }.toSet() +
            HealthConnectCatalog.adapterMeasurements.values.filter { it.profile in profiles }.map { it.id }

    private fun admissionError(
        contract: HealthConnectRecordContract?,
        recordType: String,
    ): HealthConnectConversionError? = when (contract?.status) {
        HealthConnectRecordContract.Status.SUPPORTED,
        HealthConnectRecordContract.Status.MAPPED_STANDARD,
        HealthConnectRecordContract.Status.PLATFORM_EXCLUSIVE,
        -> null
        HealthConnectRecordContract.Status.INTENTIONALLY_UNSUPPORTED ->
            HealthConnectConversionError.IntentionallyUnsupported(recordType = recordType)
        else -> HealthConnectConversionError.UnsupportedRecordType(recordType = recordType)
    }

    /**
     * The first metadata field Health Connect assigns to a stored Record that [metadata] lacks.
     */
    private fun metadataError(metadata: Metadata): HealthConnectConversionError? {
        val missing = when {
            metadata.id.isBlank() -> "id"
            metadata.dataOrigin.packageName.isBlank() -> "dataOrigin.packageName"
            metadata.lastModifiedTime == Instant.EPOCH -> "lastModifiedTime"
            else -> return null
        }
        return HealthConnectConversionError.InvalidValue(
            failure = ValueFailure.REQUIRED_METADATA_MISSING,
            location = "Record.metadata.$missing",
        )
    }

    /**
     * The writer's own identity for the measurement when it assigned one: its client record id, with its version.
     */
    private fun writerRecord(metadata: Metadata): Result<ExchangeOutputDraft.WriterRecord?> {
        val id = metadata.clientRecordId ?: return Result.success(null)
        return when {
            id.isBlank() -> Result.failure(
                HealthConnectConversionError.InvalidValue(
                    failure = ValueFailure.SHAPE_INVALID,
                    location = "Record.metadata.clientRecordId",
                ),
            )
            metadata.clientRecordVersion < 0 -> Result.failure(
                HealthConnectConversionError.InvalidValue(
                    failure = ValueFailure.OUTSIDE_DOMAIN,
                    location = "Record.metadata.clientRecordVersion",
                ),
            )
            else -> Result.success(
                ExchangeOutputDraft.WriterRecord(
                    writerApplication = metadata.dataOrigin.packageName,
                    writerRecordId = id,
                    version = metadata.clientRecordVersion.toString(),
                ),
            )
        }
    }

    private fun recordingDevice(device: Device) = RecordingDevice(
        manufacturer = device.manufacturer,
        model = device.model,
        deviceType = DEVICE_TYPES[device.type],
    )

    /**
     * The application that entered the Record into Health Connect, named by its package as a logical Device.
     */
    private fun enterer(packageName: String) = Provenance.Agent(
        type = CodeableConcept(coding = listOf(Coding(system = Hl7Url.PROVENANCE_PARTICIPANT_TYPE, code = "enterer", display = "Enterer"))),
        who = Reference(
            type = ResourceType.DEVICE.value,
            identifier = Identifier(system = HealthConnectCatalog.PACKAGE_NAME_SYSTEM, value = packageName),
        ),
    )

    private companion object {
        const val EXERCISE_SESSION = "ExerciseSessionRecord"
        val DEVICE_TYPES = mapOf(
            Device.TYPE_WATCH to "Watch",
            Device.TYPE_PHONE to "Phone",
            Device.TYPE_SCALE to "Scale",
            Device.TYPE_RING to "Ring",
            Device.TYPE_HEAD_MOUNTED to "Head-mounted device",
            Device.TYPE_FITNESS_BAND to "Fitness band",
            Device.TYPE_CHEST_STRAP to "Chest strap",
            Device.TYPE_SMART_DISPLAY to "Smart display",
        )
    }
}
