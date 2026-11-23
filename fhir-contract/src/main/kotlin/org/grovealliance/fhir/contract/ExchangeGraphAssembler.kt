//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.fhir.contract

import org.grovealliance.fhir.contract.internal.DeviceSnapshots
import org.grovealliance.fhir.contract.internal.RecordDevices
import org.grovealliance.fhir.contract.internal.StudyContext
import java.time.Instant
import java.time.ZoneOffset

/**
 * Builds the one record shape every adapter emits: its outputs decorated with the record's
 * identities and links, the study context, the Device snapshots and the conversion Provenance, in a
 * fixed entry order.
 *
 * Every identity is minted once, each shared node after its body is final and after the nodes it
 * references. The adapter supplies content; the assembler supplies everything the record says about
 * the producer and its source.
 */
@InternalGroveFhirApi
class ExchangeGraphAssembler(private val envelope: ExchangeEnvelope) {
    private val instant = ExchangeInstant()

    /**
     * The record [draft] states, with the identities it minted.
     */
    fun assemble(draft: ExchangeRecordDraft): Result<AssembledExchangeRecord> {
        if (draft.outputs.isEmpty()) return Result.failure(ExchangeAssemblyError.NoOutputs)
        return runCatching { assembled(draft = draft) }
    }

    private fun assembled(draft: ExchangeRecordDraft): AssembledExchangeRecord {
        val studyContext = StudyContext.create(
            subject = envelope.subject,
            studies = envelope.facts.studies,
            identityScope = envelope.identityScope,
        ).getOrThrow()
        val devices = RecordDevices.resolve(draft = draft, envelope = envelope).getOrThrow()
        val outputs = decoratedOutputs(
            draft = draft,
            studyContext = studyContext,
            devices = devices,
        )
        val provenance = conversionProvenance(
            draft = draft,
            targetUrls = outputs.map { it.second.fullUrl },
            devices = devices,
        )
        val entries = outputs.map { (identity, entry) -> Bundle.Entry.keyed(identifier = identity, resource = entry.resource) } +
            studyContext.allEntries +
            devices.entries.map { it.entry }
        val record = RecordBuilder().conversionRecord(
            provenance = provenance,
            sourceRecord = draft.sourceRecord.identifier,
            entries = entries,
            subject = envelope.subject.identifier,
            identityScope = envelope.identityScope,
        ).getOrThrow()
        return AssembledExchangeRecord(
            record = record,
            identifiers = ExchangeGraphIdentifiers(
                sourceRecord = draft.sourceRecord.identifier,
                primaryOutput = outputs.first().first,
                applicationSnapshot = devices.application.identity,
                hostSnapshot = devices.host.identity,
                provenance = record.version.identifier,
                childOutputs = outputs.drop(1).map { it.first },
                recordingDeviceSnapshot = devices.recording?.identity,
                writerSnapshot = devices.writer?.identity,
                writerHostSnapshot = devices.writerHost?.identity?.takeIf { devices.writerEntries.isNotEmpty() },
            ),
        )
    }

    /**
     * The retraction of [sourceRecord], assembled by this envelope's converting application and
     * recorded at [instant].
     */
    fun retraction(
        sourceRecord: RoledIdentifier,
        targets: List<RetractionRecord.Target>,
        occurred: RetractionRecord.Occurrence,
        instant: Instant,
    ): Result<RetractionRecord> {
        val snapshots = DeviceSnapshots(
            identityScope = envelope.identityScope,
            subject = envelope.subject.identifier,
            adapterId = envelope.adapter.adapterId,
            applicationDeviceProfile = envelope.adapter.applicationDeviceProfile,
        )
        val host = snapshots.host(host = envelope.facts.host).getOrElse { return Result.failure(it) }
        val application = snapshots.application(
            application = envelope.facts.application,
            parentUrl = host.url,
            identifiers = envelope.adapter.applicationIdentifiers(envelope.facts.application),
        ).getOrElse { return Result.failure(it) }
        return RetractionRecord.create(
            instant = instant,
            identityScope = envelope.identityScope,
            subject = envelope.subject.identifier,
            application = application.entry,
            host = host.entry,
            sourceRecord = sourceRecord,
            targets = targets,
            occurred = occurred,
        )
    }

    /**
     * Every output with its identities and links applied; a derived child names the primary it derives
     * from, and the primary lists its members.
     */
    private fun decoratedOutputs(
        draft: ExchangeRecordDraft,
        studyContext: StudyContext,
        devices: RecordDevices,
    ): List<Pair<RoledIdentifier, Bundle.Entry>> {
        val decorator = OutputDecorator(
            envelope = envelope,
            sourceRecord = draft.sourceRecord,
            subjectReference = studyContext.subjectReference,
            studyReferences = studyContext.studyReferences,
            observationDeviceUrl = devices.observationDeviceUrl(sourceDevicesSuppliedEverySample = draft.sourceDevicesSuppliedEverySample),
            gatewayUrl = devices.gatewayUrl,
        )
        val decorated = draft.outputs.map { decorator.decorate(output = it).getOrThrow() }
        val primaryUrl = decorated.first().first.fullUrl
        val memberUrls = draft.outputs.zip(decorated).filter { it.first.memberOfPrimary }.map { it.second.first.fullUrl }
        val specimenUrl = draft.outputs.zip(decorated).firstOrNull { it.first.specimenOfPrimary }?.second?.first?.fullUrl
        return decorated.mapIndexed { index, (identity, resource) ->
            val output = draft.outputs[index]
            val linked = when {
                resource !is Observation -> resource
                index == 0 -> resource.copy(
                    hasMember = resource.hasMember + memberUrls.map { Reference(reference = it, type = ResourceType.OBSERVATION.value) },
                    specimen = specimenUrl?.let { Reference(reference = it) } ?: resource.specimen,
                )
                output.derivedFromPrimary -> resource.copy(derivedFrom = listOf(Reference(reference = primaryUrl)))
                else -> resource
            }
            identity to Bundle.Entry.keyed(identifier = identity, resource = linked)
        }
    }

    /**
     * The conversion Provenance: the transform activity, the converting application as the assembler,
     * the source record as the entity with the writer and peripherals that supplied it, and every
     * output as a target. It carries no record version yet, as that repeats a digest over it.
     */
    private fun conversionProvenance(
        draft: ExchangeRecordDraft,
        targetUrls: List<String>,
        devices: RecordDevices,
    ): Provenance {
        val agents = listOfNotNull(devices.writer?.url).map { sourceAgent(url = it, code = "author", display = "Author") } +
            devices.sources.map { sourceAgent(url = it.url, code = "performer", display = "Performer") } +
            listOfNotNull(devices.composerUrl).map { sourceAgent(url = it, code = "composer", display = "Composer") } +
            draft.sourceAgents
        return Provenance(
            meta = Meta(profile = listOf(envelope.adapter.provenanceProfile)),
            target = targetUrls.map { Reference(reference = it) },
            occurredDateTime = instant.at(instant = draft.instant, offset = ZoneOffset.UTC),
            recorded = instant.utc(instant = draft.instant),
            activity = CodeableConcept(
                coding = listOf(
                    Coding(
                        system = ExchangeContract.ACTIVE_ACTIVITY_SYSTEM,
                        code = ExchangeContract.ACTIVE_ACTIVITY_CODE,
                        display = "Transform/Translate Record Lifecycle Event",
                    ),
                ),
            ),
            agent = listOf(
                Provenance.Agent(
                    type = participantType(code = "assembler", display = "Assembler"),
                    who = Reference(reference = devices.application.url),
                ),
            ),
            entity = listOf(
                Provenance.Entity(
                    extension = draft.sourceExtensions,
                    role = "source",
                    what = Reference(identifier = draft.sourceRecord.identifier.fhirIdentifier),
                    agent = agents,
                ),
            ),
        )
    }

    private fun sourceAgent(url: String, code: String, display: String) = Provenance.Agent(
        type = participantType(code = code, display = display),
        who = Reference(reference = url),
    )

    private fun participantType(code: String, display: String) = CodeableConcept(
        coding = listOf(
            Coding(
                system = Hl7Url.PROVENANCE_PARTICIPANT_TYPE,
                code = code,
                display = display,
            ),
        ),
    )
}

/**
 * One assembled [record] and the [identifiers] it minted.
 */
data class AssembledExchangeRecord(
    val record: ExchangeRecord,
    val identifiers: ExchangeGraphIdentifiers,
)
