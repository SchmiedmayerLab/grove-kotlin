//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.fhir

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.grovealliance.fhir.ConformanceFixtures.applyPatch
import org.hl7.fhir.r4.model.Attachment
import org.hl7.fhir.r4.model.CodeableConcept
import org.hl7.fhir.r4.model.Coding
import org.hl7.fhir.r4.model.DateTimeType
import org.hl7.fhir.r4.model.Device
import org.hl7.fhir.r4.model.DocumentReference
import org.hl7.fhir.r4.model.Enumerations
import org.hl7.fhir.r4.model.Identifier
import org.hl7.fhir.r4.model.InstantType
import org.hl7.fhir.r4.model.Observation
import org.hl7.fhir.r4.model.Patient
import org.hl7.fhir.r4.model.PlanDefinition
import org.hl7.fhir.r4.model.Provenance
import org.hl7.fhir.r4.model.Quantity
import org.hl7.fhir.r4.model.ResearchStudy
import org.hl7.fhir.r4.model.ResearchSubject
import org.junit.Assert.assertThrows
import org.junit.Test
import java.security.MessageDigest
import java.time.Instant

/** Assembles one active graph and its retraction the way an adapter does, then reads both back. */
@OptIn(InternalGroveFhirApi::class)
class ExchangeGraphAssemblerTest {
    private val root = ConformanceFixtures.ROOT
    private val pseudonym = BusinessIdentifier(IdentifierSystem("$root/identifiers/participant"), "participant-001")
    private val studyA = StudyEnrollment(
        study = BusinessIdentifier(IdentifierSystem("$root/studies"), "a"),
        protocolUrl = "$root/PlanDefinition/a",
        protocolVersion = "1",
        enrollment = BusinessIdentifier(IdentifierSystem("$root/enrollments"), "enrollment-a"),
    )
    private val studyB = StudyEnrollment(
        study = BusinessIdentifier(IdentifierSystem("$root/studies"), "b"),
        protocolUrl = "$root/PlanDefinition/b",
        protocolVersion = "3",
        enrollment = BusinessIdentifier(IdentifierSystem("$root/enrollments"), "enrollment-b"),
    )

    @Test
    fun `a bundled subject two enrollments and a gateway application form a valid connected graph`() {
        val context = ConformanceFixtures.eventContext(
            sequence = 44,
            subject = Subject.Bundled(pseudonym, Patient()),
            converterRole = ConverterRole.GatewayApplication(
                ApplicationDevice(name = "Wearable Companion", packageName = "com.example.wearable", version = "4.0"),
            ),
            studies = listOf(studyA, studyB),
            repositoryIds = mapOf(ExchangeGraphNode.BUNDLE to RepositoryId("event-44")),
        )
        val assembler = ExchangeGraphAssembler(context, ADAPTER, RecordingDevice("watch-unit-token-001", name = "Study Watch"))
        val (graph, identifiers) = heartRate(assembler)
        val bundle = graph.toBundle()

        assertThat(bundle.id).isEqualTo("event-44")
        assertThat(bundle.entry.map { it.resource.fhirType() }).containsExactly(
            "Patient", "Device", "Device", "Device", "Device",
            "ResearchStudy", "PlanDefinition", "ResearchSubject",
            "ResearchStudy", "PlanDefinition", "ResearchSubject",
            "Observation", "Provenance",
        ).inOrder()
        val keys = bundle.entry.map { (it.getExtensionByUrl(ExchangeContract.ENTRY_NODE_KEY_EXTENSION).value as Identifier).value }
        assertThat(keys.filter { it.startsWith("n0:") }.map { it.substringBeforeLast(':') }).containsExactly(
            "n0:patient:0",
            "n0:research-study:0", "n0:plan-definition:0", "n0:research-subject:0",
            "n0:research-study:1", "n0:plan-definition:1", "n0:research-subject:1",
            "n0:conversion-provenance:0",
        ).inOrder()
        val observation = bundle.entry.map { it.resource }.filterIsInstance<Observation>().single()
        assertThat(observation.subject.reference).isEqualTo(assembler.subjectEntry?.fullUrl)
        assertThat(observation.device.reference).isEqualTo(assembler.recordingDeviceEntry?.fullUrl)
        val studyReferences = observation.getExtensionsByUrl(ExchangeContract.RESEARCH_STUDY_EXTENSION)
            .map { (it.value as org.hl7.fhir.r4.model.Reference).reference }
        assertThat(studyReferences)
            .containsExactlyElementsIn(bundle.entry.filter { it.resource is ResearchStudy }.map { it.fullUrl }).inOrder()
        val gateway = observation.getExtensionByUrl(ExchangeContract.GATEWAY_DEVICE_EXTENSION).value as org.hl7.fhir.r4.model.Reference
        assertThat(gateway.reference).isEqualTo(assembler.gatewayApplicationEntry?.fullUrl)
        assertThat(gateway.reference).isNotEqualTo(assembler.applicationEntry.fullUrl)
        val plans = bundle.entry.map { it.resource }.filterIsInstance<PlanDefinition>()
        assertThat(plans.map { it.url to it.version })
            .containsExactly("$root/PlanDefinition/a" to "1", "$root/PlanDefinition/b" to "3")
            .inOrder()
        val subjects = bundle.entry.map { it.resource }.filterIsInstance<ResearchSubject>()
        assertThat(subjects.map { it.individual.reference }.distinct()).containsExactly(assembler.subjectEntry?.fullUrl)
        val devices = bundle.entry.map { it.resource }.filterIsInstance<Device>()
        assertThat(devices.map { it.meta.profile.single().value }).containsExactly(
            ExchangeContract.MOBILE_HOST_DEVICE_PROFILE,
            ExchangeContract.MOBILE_APPLICATION_DEVICE_PROFILE,
            ExchangeContract.MOBILE_APPLICATION_DEVICE_PROFILE,
            ExchangeContract.MOBILE_RECORDING_DEVICE_PROFILE,
        ).inOrder()
        assertThat(devices.filter { it.hasParent() }.map { it.parent.reference }.distinct()).containsExactly(assembler.hostEntry.fullUrl)
        assertThat(graph.json).doesNotContain("watch-unit-token-001")
        assertThat(identifiers.recordingDeviceSnapshot).isEqualTo(assembler.recordingDeviceEntry?.identifier)
        assertThat(identifiers.provenance.identifier.value).startsWith("n0:conversion-provenance:0:")

        val replay = ExchangeGraph.parse(ExchangeGraphKind.ACTIVE, graph.json) as ExchangeGraphParseResult.Valid
        assertThat(replay.graph.semanticallyEquals(graph)).isTrue()
        assertThat(replay.graph.sha256).isEqualTo(graph.sha256)
    }

    @Test
    fun `a bundled study context that loses its revision or its enrollment link fails the study-context rule`() {
        val context = ConformanceFixtures.eventContext(
            sequence = 45,
            subject = Subject.Bundled(pseudonym, Patient()),
            studies = listOf(studyA, studyB),
        )
        val (graph, _) = heartRate(ExchangeGraphAssembler(context, ADAPTER, null))
        val document = Json.parseToJsonElement(graph.json)
        val entries = document.jsonObject.getValue("entry").jsonArray
        val types = entries.map { it.jsonObject.getValue("resource").jsonObject["resourceType"]?.jsonPrimitive?.content }
        val planIndex = types.indexOf("PlanDefinition")
        val subjectIndices = types.withIndex().filter { it.value == "ResearchSubject" }.map { it.index }
        val secondStudy = entries[subjectIndices[1]].jsonObject.getValue("resource").jsonObject.getValue("study")
        val mutations = listOf(
            """{"op": "remove", "path": "/entry/$planIndex/resource/version"}""",
            """{"op": "replace", "path": "/entry/${subjectIndices[0]}/resource/study", "value": $secondStudy}""",
        )
        mutations.forEach { mutation ->
            val mutated = document.applyPatch(Json.parseToJsonElement(mutation).jsonObject)
            val result = ExchangeGraph.parse(ExchangeGraphKind.ACTIVE, mutated.toString())
            val error = (result as ExchangeGraphParseResult.Invalid).error as ExchangeGraphError.RuleViolation
            assertThat(error.rule).isEqualTo(ExchangeGraphRule.MOBILE_SUPPORT_STUDY_CONTEXT)
        }
        assertThat(ExchangeGraph.parse(ExchangeGraphKind.ACTIVE, graph.json)).isInstanceOf(ExchangeGraphParseResult.Valid::class.java)
    }

    @Test
    fun `a logical subject with defaults emits no Patient no study and no gateway`() {
        val assembler = ExchangeGraphAssembler(ConformanceFixtures.eventContext(sequence = 45), ADAPTER, null)
        val (graph, identifiers) = heartRate(assembler)
        val bundle = graph.toBundle()
        val observation = bundle.entry.map { it.resource }.filterIsInstance<Observation>().single()

        assertThat(bundle.entry.map { it.resource.fhirType() }).containsExactly("Device", "Device", "Observation", "Provenance").inOrder()
        assertThat(observation.subject.hasReference()).isFalse()
        assertThat(observation.subject.type).isEqualTo("Patient")
        assertThat(BusinessIdentifier.from(observation.subject.identifier)).isEqualTo(assembler.context.subject.identifier)
        assertThat(observation.hasDevice()).isFalse()
        assertThat(observation.hasExtension(ExchangeContract.GATEWAY_DEVICE_EXTENSION)).isFalse()
        assertThat(identifiers.recordingDeviceSnapshot).isNull()
        assertThat(identifiers.writerSnapshot).isNull()
        assertThat(bundle.id).isNull()
    }

    @Test
    fun `a gateway application is a second application snapshot that is neither the writer nor a repository node`() {
        val context = ConformanceFixtures.eventContext(
            sequence = 51,
            converterRole = ConverterRole.GatewayApplication(
                ApplicationDevice(name = "Wearable Companion", packageName = "com.example.wearable", version = "4.0"),
            ),
            repositoryIds = mapOf(ExchangeGraphNode.APPLICATION_DEVICE to RepositoryId("application-51")),
        )
        val assembler = ExchangeGraphAssembler(context, ADAPTER, null)
        val (graph, identifiers) = heartRate(assembler)
        val resources = graph.toBundle().entry.associate { it.fullUrl to it.resource }

        assertThat(resources.getValue(assembler.applicationEntry.fullUrl).idElement.idPart).isEqualTo("application-51")
        assertThat(resources.getValue(requireNotNull(assembler.gatewayApplicationEntry).fullUrl).hasId()).isFalse()
        assertThat(identifiers.writerSnapshot).isNull()
        assertThat(identifiers.writerHostSnapshot).isNull()
    }

    @Test
    fun `every repository id lands on the resource of its node`() {
        val ids = ExchangeGraphNode.entries.associateWith { RepositoryId(it.name.lowercase().replace('_', '-')) }
        val context = ConformanceFixtures.eventContext(sequence = 52, repositoryIds = ids)
        val assembler = ExchangeGraphAssembler(context, ADAPTER, RecordingDevice("unit-52"))
        val sourceRecord = context.identityScope.sourceRecord(ADAPTER, "HeartRateRecord", context.repositoryScope, "record-heart-052")
        val output = sourceRecord.output("sample", "0")
        val primary = GraphEntry(output, heartRateObservation(sourceRecord.identifier, output).also(assembler::decorate))
        val artifact = recordingDocument(sourceRecord)
        val outputs = listOf(assembler.primaryOutput(primary), assembler.sourceArtifact(artifact))
        val provenance = assembler.conversionProvenance(
            profile = ExchangeContract.MOBILE_CONVERSION_PROVENANCE_PROFILE,
            sourceRecord = sourceRecord.identifier,
            outputs = outputs,
            occurred = DateTimeType("2026-08-20T08:30:00.251-07:00"),
        )
        val bundle = assembler.activeGraph(outputs, provenance).toBundle()
        fun id(entry: GraphEntry) = bundle.entry.single { it.fullUrl == entry.fullUrl }.resource.idElement.idPart

        val landed = mapOf(
            ExchangeGraphNode.BUNDLE to bundle.idElement.idPart,
            ExchangeGraphNode.PRIMARY_OUTPUT to id(primary),
            ExchangeGraphNode.SOURCE_ARTIFACT to id(artifact),
            ExchangeGraphNode.RECORDING_DEVICE to id(requireNotNull(assembler.recordingDeviceEntry)),
            ExchangeGraphNode.APPLICATION_DEVICE to id(assembler.applicationEntry),
            ExchangeGraphNode.HOST_DEVICE to id(assembler.hostEntry),
            ExchangeGraphNode.PROVENANCE to bundle.entry.single { it.resource is Provenance }.resource.idElement.idPart,
        )
        assertThat(landed).isEqualTo(ids.filterKeys { it in landed }.mapValues { it.value.value })
        // No Kotlin adapter emits the writer's own Device snapshots, so those two nodes have no resource here.
        assertThat(ids.keys - landed.keys).containsExactly(ExchangeGraphNode.WRITER, ExchangeGraphNode.WRITER_HOST)
    }

    @Test
    fun `a gateway converter role references the converter application itself`() {
        val gatewayContext = ConformanceFixtures.eventContext(sequence = 46, converterRole = ConverterRole.Gateway)
        val assembler = ExchangeGraphAssembler(gatewayContext, ADAPTER, null)
        val (graph, _) = heartRate(assembler)
        val observation = graph.toBundle().entry.map { it.resource }.filterIsInstance<Observation>().single()
        val gateway = observation.getExtensionByUrl(ExchangeContract.GATEWAY_DEVICE_EXTENSION).value as org.hl7.fhir.r4.model.Reference
        assertThat(gateway.reference).isEqualTo(assembler.applicationEntry.fullUrl)
    }

    @Test
    fun `retraction targets derived from the graph rebuild a valid retraction event`() {
        val assembler = ExchangeGraphAssembler(ConformanceFixtures.eventContext(sequence = 47), ADAPTER, RecordingDevice("unit-9"))
        val (graph, identifiers) = heartRate(assembler)
        val targets = graph.retractionTargets()
        assertThat(targets.map { it.role }).containsExactly(
            RetractionTargetRole.DEVICE_SNAPSHOT,
            RetractionTargetRole.DEVICE_SNAPSHOT,
            RetractionTargetRole.DEVICE_SNAPSHOT,
            RetractionTargetRole.PRIMARY_OUTPUT,
        ).inOrder()
        assertThat(targets.last().identifier).isEqualTo(identifiers.primaryOutput)

        val retraction = RetractionEvent(
            targets = targets,
            context = ConformanceFixtures.eventContext(sequence = 48),
            sourceRecord = identifiers.sourceRecord,
            retractedAt = Instant.parse("2026-08-21T08:00:01Z"),
        )
        val replay = ExchangeGraph.parse(ExchangeGraphKind.RETRACTION, retraction.graph.json) as ExchangeGraphParseResult.Valid
        assertThat(replay.graph.retractionTargets()).containsExactlyElementsIn(targets)
        assertThat(replay.graph.eventIdentifier.sequence).isEqualTo(EventSequence.of(48))
        val provenance = replay.graph.toBundle().entry.single().resource as Provenance
        assertThat(provenance.agentFirstRep.who.identifier.value).isNotEqualTo(assembler.applicationSnapshot.identifier.value)
        assertThat(provenance.entityFirstRep.what.identifier.value).isEqualTo(identifiers.sourceRecord.identifier.value)
        assertThrows(ExchangeGraphException::class.java) {
            RetractionEvent(
                emptyList(),
                ConformanceFixtures.eventContext(sequence = 49),
                identifiers.sourceRecord,
                Instant.parse("2026-08-21T08:00:01Z"),
            )
        }
    }

    @Test
    fun `an output that breaks a registered rule fails graph assembly with that rule`() {
        val assembler = ExchangeGraphAssembler(ConformanceFixtures.eventContext(sequence = 50), ADAPTER, null)
        val error = assertThrows(ExchangeGraphException::class.java) {
            heartRate(assembler) { it.valueQuantity.code = "bpm" }
        }
        assertThat(error.error.diagnostic.code).isEqualTo("mobile-output.fixed-quantity-unit")
    }

    @Test
    fun `context construction rejects deployment faults before any record is converted`() {
        val context = ConformanceFixtures.eventContext()
        assertThrows(IllegalArgumentException::class.java) {
            ExchangeEventContext(
                subject = context.subject,
                event = context.event.copy(system = context.entryNodeIdentifierSystem),
                identityScope = context.identityScope,
                repositoryScope = context.repositoryScope,
                application = context.application,
                host = context.host,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            Subject.Logical(BusinessIdentifier(IdentifierSystem(ExchangeContract.GROVE_IDENTIFIER_ROLE), "participant-001"))
        }
        assertThrows(IllegalArgumentException::class.java) { RepositoryId("not valid!") }
        assertThrows(IllegalArgumentException::class.java) {
            StudyEnrollment(studyA.study, "relative/protocol", "1", studyA.enrollment)
        }
    }

    private fun heartRateObservation(sourceRecord: RoledIdentifier, output: RoledIdentifier): Observation = Observation().apply {
        meta.addProfile("${ExchangeContract.MOBILE_BASE}/StructureDefinition/grove-mobile-heart-rate")
        addIdentifier(sourceRecord.toFhir())
        addIdentifier(output.toFhir())
        status = Observation.ObservationStatus.FINAL
        addCategory(CodeableConcept(Coding("http://terminology.hl7.org/CodeSystem/observation-category", "vital-signs", "Vital Signs")))
        code = CodeableConcept(Coding("http://loinc.org", "8867-4", "Heart rate"))
        effective = DateTimeType("2026-08-20T08:30:00.251-07:00")
        issuedElement = InstantType("2026-08-20T17:30:02Z")
        value = Quantity().setValue(72).setSystem("http://unitsofmeasure.org").setCode("/min").setUnit("beats/minute")
    }

    private fun recordingDocument(sourceRecord: SourceRecordIdentity): GraphEntry {
        val output = sourceRecord.output("recording", "0")
        val payload = "time,bpm\n2026-08-20T15:30:00.251Z,72\n".toByteArray()
        val document = DocumentReference().apply {
            meta.addProfile("https://grovealliance.org/fhir/sensor/StructureDefinition/grove-sensor-recording-document")
            addIdentifier(sourceRecord.identifier.toFhir())
            addIdentifier(output.toFhir())
            addIdentifier(sourceRecord.artifact("heart-rate-samples", 0).toFhir())
            status = Enumerations.DocumentReferenceStatus.CURRENT
            addContent().apply {
                format = Coding(ExchangeContract.RECORDING_FORMAT_SYSTEM, "heart-rate-samples", null)
                attachment = Attachment().apply {
                    contentType = "text/csv"
                    data = payload
                    size = payload.size
                    hash = MessageDigest.getInstance("SHA-1").digest(payload)
                }
            }
        }
        return GraphEntry(output, document)
    }

    private fun heartRate(
        assembler: ExchangeGraphAssembler,
        mutate: (Observation) -> Unit = {},
    ): Pair<ExchangeGraph, ExchangeGraphIdentifiers> {
        val context = assembler.context
        val sourceRecord = context.identityScope.sourceRecord(ADAPTER, "HeartRateRecord", context.repositoryScope, "record-heart-001")
        val output = sourceRecord.output("sample", "2026-08-20T15:30:00.251000000Z|0")
        val observation = heartRateObservation(sourceRecord.identifier, output).apply {
            assembler.decorate(this)
            mutate(this)
        }
        val entry = GraphEntry(output, observation)
        val provenance = assembler.conversionProvenance(
            profile = ExchangeContract.MOBILE_CONVERSION_PROVENANCE_PROFILE,
            sourceRecord = sourceRecord.identifier,
            outputs = listOf(entry),
            occurred = DateTimeType("2026-08-20T08:30:00.251-07:00"),
        )
        val graph = assembler.activeGraph(listOf(entry), provenance)
        return graph to assembler.identifiers(sourceRecord.identifier, output, emptyList())
    }

    private companion object {
        const val ADAPTER = "health-connect"
    }
}
