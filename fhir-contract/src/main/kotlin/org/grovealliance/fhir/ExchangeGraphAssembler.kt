//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.fhir

import org.hl7.fhir.r4.model.Bundle
import org.hl7.fhir.r4.model.CodeableConcept
import org.hl7.fhir.r4.model.Coding
import org.hl7.fhir.r4.model.Device
import org.hl7.fhir.r4.model.Extension
import org.hl7.fhir.r4.model.InstantType
import org.hl7.fhir.r4.model.Observation
import org.hl7.fhir.r4.model.PlanDefinition
import org.hl7.fhir.r4.model.Provenance
import org.hl7.fhir.r4.model.Reference
import org.hl7.fhir.r4.model.ResearchStudy
import org.hl7.fhir.r4.model.ResearchSubject
import org.hl7.fhir.r4.model.Resource
import org.hl7.fhir.r4.model.Type

/**
 * Builds the adapter-neutral part of one active exchange graph from an [ExchangeEventContext].
 *
 * The assembler mints every event-scoped snapshot, bundles the subject and study context, resolves
 * the references an output carries and closes the graph with its conversion Provenance.
 */
@InternalGroveFhirApi
public class ExchangeGraphAssembler(
    public val context: ExchangeEventContext,
    private val adapterId: String,
    recordingDevice: RecordingDevice?,
) {
    private val scope = context.identityScope
    private val event = context.event
    private val ids = context.repositoryIds

    /** The bundled Patient entry, or null for a logical subject. */
    public val subjectEntry: GraphEntry? = (context.subject as? Subject.Bundled)?.let { subject ->
        GraphEntry(
            EntryNodeKey(context.entryNodeIdentifierSystem, event, StudyContextEntryNodeRole.PATIENT.code, 0).identifier,
            subject.patient,
        )
    }

    /** The host snapshot entry. */
    public val hostEntry: GraphEntry = GraphEntry(
        scope.deviceSnapshot(event, DeviceSnapshotRole.HOST, context.host.sourceDeviceToken),
        context.host.resource().withRepositoryId(ExchangeGraphNode.HOST_DEVICE),
    ).withSnapshotIdentifier()

    /** The converter application snapshot entry, linked to its host. */
    public val applicationEntry: GraphEntry = applicationSnapshot(context.application, ids[ExchangeGraphNode.APPLICATION_DEVICE])

    /** The distinct gateway application snapshot when the converter role names one; no [ExchangeGraphNode] addresses it. */
    public val gatewayApplicationEntry: GraphEntry? = (context.converterRole as? ConverterRole.GatewayApplication)
        ?.let { applicationSnapshot(it.application, repositoryId = null) }

    /** The recording Device entry when the adapter resolved a governed per-unit token. */
    public val recordingDeviceEntry: GraphEntry? = recordingDevice?.let { device ->
        val physicalUnit = scope.recordingDevice(adapterId, context.subject.identifier, device.stableUnitToken)
        val snapshot = scope.deviceSnapshot(event, DeviceSnapshotRole.RECORDING_DEVICE, device.stableUnitToken)
        GraphEntry(
            snapshot,
            device.resource().withRepositoryId(ExchangeGraphNode.RECORDING_DEVICE).apply {
                addIdentifier(physicalUnit.toFhir())
                addIdentifier(snapshot.toFhir())
            },
        )
    }

    /** The ResearchStudy, PlanDefinition and ResearchSubject entries of every known enrollment. */
    public val studyEntries: List<GraphEntry> = context.studies.flatMapIndexed { ordinal, enrollment ->
        studyContext(enrollment, ordinal.toLong())
    }

    /** The reference every output carries to the subject: literal for a bundled Patient, logical otherwise. */
    public val subjectReference: Reference
        get() = subjectEntry?.reference() ?: Reference().apply {
            type = PATIENT
            identifier = context.subject.identifier.toFhir()
        }

    /** The gateway device an output names, or null when the converter only assembled. */
    public val gatewayDeviceReference: Reference?
        get() = when (context.converterRole) {
            ConverterRole.Assembler -> null
            ConverterRole.Gateway -> applicationEntry.reference()
            is ConverterRole.GatewayApplication -> gatewayApplicationEntry?.reference()
        }

    /** The recording Device reference an output carries, or null when none was resolved. */
    public val recordingDeviceReference: Reference?
        get() = recordingDeviceEntry?.reference()

    /** One reference per bundled ResearchStudy entry. */
    public val studyReferences: List<Reference>
        get() = studyEntries.filter { it.resource is ResearchStudy }.map { it.reference() }

    /** The entry-node key of the conversion Provenance. */
    public val provenanceKey: RoledIdentifier =
        EntryNodeKey(context.entryNodeIdentifierSystem, event, CONVERSION_PROVENANCE_ROLE, 0).identifier

    /** The application snapshot identity that a retraction of this event's outputs names as assembler. */
    public val applicationSnapshot: RoledIdentifier
        get() = applicationEntry.identifier

    /** The designated primary output, carrying the repository id the context assigns [ExchangeGraphNode.PRIMARY_OUTPUT]. */
    public fun primaryOutput(entry: GraphEntry): GraphEntry = entry.withRepositoryId(ExchangeGraphNode.PRIMARY_OUTPUT)

    /** A source-artifact DocumentReference, carrying the repository id the context assigns [ExchangeGraphNode.SOURCE_ARTIFACT]. */
    public fun sourceArtifact(entry: GraphEntry): GraphEntry = entry.withRepositoryId(ExchangeGraphNode.SOURCE_ARTIFACT)

    /** Attaches the subject, recording device, gateway device and study references to one output. */
    public fun decorate(observation: Observation) {
        observation.subject = subjectReference
        recordingDeviceReference?.let { observation.device = it }
        gatewayDeviceReference?.let { observation.addExtension(Extension(ExchangeContract.GATEWAY_DEVICE_EXTENSION, it)) }
        studyReferences.forEach { observation.addExtension(Extension(ExchangeContract.RESEARCH_STUDY_EXTENSION, it)) }
    }

    /** The conversion Provenance targeting every output, before adapter-specific additions. */
    public fun conversionProvenance(
        profile: String,
        sourceRecord: RoledIdentifier,
        outputs: List<GraphEntry>,
        occurred: Type,
    ): Provenance = Provenance().apply {
        meta.addProfile(profile)
        ids[ExchangeGraphNode.PROVENANCE]?.let { id = it.value }
        this.occurred = occurred.copy() as Type
        recordedElement = InstantType(context.conversionInstant.toString())
        activity = CodeableConcept(
            Coding(ExchangeContract.RECORD_LIFECYCLE, ExchangeContract.ACTIVE_LIFECYCLE_ACTIVITY_CODE, TRANSFORM_DISPLAY),
        )
        addAgent().apply {
            type = CodeableConcept(Coding(ExchangeContract.PROVENANCE_PARTICIPANT, ASSEMBLER, "Assembler"))
            who = applicationEntry.reference()
        }
        outputs.forEach { output ->
            addTarget(
                Reference().apply {
                    reference = output.fullUrl
                    type = output.resource.fhirType()
                    identifier = output.identifier.toFhir()
                },
            )
        }
        addEntity().apply {
            role = Provenance.ProvenanceEntityRole.SOURCE
            what = Reference().setIdentifier(sourceRecord.toFhir())
        }
    }

    /** The validated active graph: context entries, outputs in the given order and the Provenance. */
    public fun activeGraph(outputs: List<GraphEntry>, provenance: Provenance): ExchangeGraph {
        val bundle = Bundle().apply {
            identifier = event.identifier.toFhir()
            meta.addProfile(ExchangeContract.MOBILE_EXCHANGE_BUNDLE_PROFILE)
            type = Bundle.BundleType.COLLECTION
            timestampElement = InstantType(context.conversionInstant.toString())
            ids[ExchangeGraphNode.BUNDLE]?.let { id = it.value }
        }
        contextEntries().forEach { it.addTo(bundle) }
        outputs.forEach { it.addTo(bundle) }
        GraphEntry(provenanceKey, provenance).addTo(bundle)
        return ExchangeGraph.of(ExchangeGraphKind.ACTIVE, event, bundle)
    }

    /** Every identity the graph carries for one source record and its outputs. */
    public fun identifiers(
        sourceRecord: RoledIdentifier,
        primaryOutput: RoledIdentifier,
        childOutputs: List<RoledIdentifier>,
        sourceArtifact: RoledIdentifier? = null,
    ): ExchangeGraphIdentifiers = ExchangeGraphIdentifiers(
        event = event.identifier,
        sourceRecord = sourceRecord,
        primaryOutput = primaryOutput,
        applicationSnapshot = applicationEntry.identifier,
        hostSnapshot = hostEntry.identifier,
        provenance = provenanceKey,
        childOutputs = childOutputs,
        sourceArtifact = sourceArtifact,
        recordingDeviceSnapshot = recordingDeviceEntry?.identifier,
        writerSnapshot = null,
        writerHostSnapshot = null,
    )

    private fun contextEntries(): List<GraphEntry> =
        listOfNotNull(subjectEntry, hostEntry, applicationEntry, gatewayApplicationEntry, recordingDeviceEntry) + studyEntries

    private fun applicationSnapshot(application: ApplicationDevice, repositoryId: RepositoryId?): GraphEntry = GraphEntry(
        scope.deviceSnapshot(event, DeviceSnapshotRole.APPLICATION, application.sourceDeviceToken),
        application.resource().apply {
            repositoryId?.let { id = it.value }
            parent = hostEntry.reference()
        },
    ).withSnapshotIdentifier()

    private fun studyContext(enrollment: StudyEnrollment, ordinal: Long): List<GraphEntry> {
        fun key(role: StudyContextEntryNodeRole) =
            EntryNodeKey(context.entryNodeIdentifierSystem, event, role.code, ordinal).identifier
        val planDefinition = GraphEntry(
            key(StudyContextEntryNodeRole.PLAN_DEFINITION),
            PlanDefinition().apply {
                url = enrollment.protocolUrl
                version = enrollment.protocolVersion
                status = org.hl7.fhir.r4.model.Enumerations.PublicationStatus.ACTIVE
            },
        )
        val study = GraphEntry(
            key(StudyContextEntryNodeRole.RESEARCH_STUDY),
            ResearchStudy().apply {
                addIdentifier(enrollment.study.toFhir())
                status = ResearchStudy.ResearchStudyStatus.ACTIVE
                addProtocol(planDefinition.reference())
            },
        )
        val subject = GraphEntry(
            key(StudyContextEntryNodeRole.RESEARCH_SUBJECT),
            ResearchSubject().apply {
                addIdentifier(enrollment.enrollment.toFhir())
                status = ResearchSubject.ResearchSubjectStatus.ONSTUDY
                this.study = study.reference()
                individual = subjectReference
            },
        )
        return listOf(study, planDefinition, subject)
    }

    private fun GraphEntry.withSnapshotIdentifier(): GraphEntry {
        val snapshot = identifier
        return GraphEntry(snapshot, (resource as Device).apply { addIdentifier(snapshot.toFhir()) })
    }

    private fun <T : Resource> T.withRepositoryId(node: ExchangeGraphNode): T = apply {
        ids[node]?.let { id = it.value }
    }

    private fun GraphEntry.withRepositoryId(node: ExchangeGraphNode): GraphEntry = GraphEntry(identifier, resource.withRepositoryId(node))

    private companion object {
        const val PATIENT = "Patient"
        const val ASSEMBLER = "assembler"
        const val CONVERSION_PROVENANCE_ROLE = "conversion-provenance"
        const val TRANSFORM_DISPLAY = "Transform/Translate Record Lifecycle Event"
    }
}
