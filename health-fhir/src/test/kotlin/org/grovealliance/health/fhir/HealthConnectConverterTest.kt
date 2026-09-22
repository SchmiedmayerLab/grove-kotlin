//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.health.fhir

import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.ExerciseLap
import androidx.health.connect.client.records.ExerciseSegment
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Length
import com.google.common.truth.Truth.assertThat
import org.grovealliance.fhir.ApplicationDevice
import org.grovealliance.fhir.BusinessIdentifier
import org.grovealliance.fhir.ConverterRole
import org.grovealliance.fhir.ExchangeContract
import org.grovealliance.fhir.ExchangeGraph
import org.grovealliance.fhir.ExchangeGraphDiagnostic
import org.grovealliance.fhir.ExchangeGraphKind
import org.grovealliance.fhir.ExchangeGraphParseResult
import org.grovealliance.fhir.ExchangeGraphRule
import org.grovealliance.fhir.GovernedSourceIdentifierDisclosurePolicy
import org.grovealliance.fhir.GroveIdentifierRole
import org.grovealliance.fhir.IdentifierSystem
import org.grovealliance.fhir.RecordingDevice
import org.grovealliance.fhir.RetractionEvent
import org.grovealliance.fhir.RetractionTarget
import org.grovealliance.fhir.RetractionTargetRole
import org.grovealliance.fhir.RoledIdentifier
import org.grovealliance.fhir.StudyEnrollment
import org.hl7.fhir.r4.model.Bundle
import org.hl7.fhir.r4.model.Device
import org.hl7.fhir.r4.model.Observation
import org.hl7.fhir.r4.model.Provenance
import org.hl7.fhir.r4.model.Reference
import org.hl7.fhir.r4.model.Specimen
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Instant
import java.util.Locale

/** Converts representative records and reads the exchange graphs back the way a receiver would. */
class HealthConnectConverterTest {
    private val fixtures = HealthConnectTestFixtures
    private val records = HealthConnectFixtureRecords
    private val converter = fixtures.converter

    @Test
    fun `a steps record becomes one validated graph with its identities writer agent and provenance`() {
        val conversion = fixtures.converted(records.steps(count = 1042))
        val bundle = conversion.toBundle()
        val observation = bundle.observations().single()

        assertThat(conversion.source).isEqualTo(HealthConnectSourceRecord("fixture-step", HealthConnectSourceType.STEPS))
        assertThat(observation.meta.profile.map { it.value })
            .containsExactly(HealthConnectContract.MOBILE_STEP_COUNT_PROFILE, HealthConnectContract.HEALTH_CONNECT_OBSERVATION_PROFILE)
        assertThat(observation.valueQuantity.value.toPlainString()).isEqualTo("1042")
        assertThat(observation.valueQuantity.code).isEqualTo("{steps}")
        assertThat(observation.effectivePeriod.start.toInstant()).isEqualTo(records.instant)
        assertThat(observation.issuedElement.valueAsString).isEqualTo("2026-08-19T17:30:01Z")
        val roles = observation.identifier.mapNotNull(RoledIdentifier::from).map { it.role }
        assertThat(roles).containsExactly(GroveIdentifierRole.SOURCE_RECORD, GroveIdentifierRole.SOURCE_OUTPUT)
        assertThat(conversion.identifiers.primaryOutput).isEqualTo(
            fixtures.scope.sourceOutput(
                adapterId = HealthConnectContract.ADAPTER_ID,
                sourceType = "StepsRecord",
                repositoryScope = fixtures.repositoryScope,
                nativeRecordId = "fixture-step",
                outputRole = "single",
                outputDiscriminator = "step-count",
            ),
        )
        assertThat(conversion.identifiers.childOutputs).isEmpty()
        assertThat(observation.subject.identifier.value).isEqualTo("participant-001")
        assertThat(observation.getExtensionByUrl(HealthConnectContract.HEALTH_CONNECT_RECORD_TYPE_EXTENSION).value.primitiveValue())
            .isEqualTo("StepsRecord")

        val provenance = bundle.entry.map { it.resource }.filterIsInstance<Provenance>().single()
        assertThat(provenance.meta.profile.single().value).isEqualTo(HealthConnectContract.HEALTH_CONNECT_PROVENANCE_PROFILE)
        val writer = provenance.entityFirstRep.agentFirstRep.who
        assertThat(writer.hasReference()).isFalse()
        assertThat(writer.type).isEqualTo("Device")
        assertThat(writer.identifier.system).isEqualTo(HealthConnectContract.WRITER_PACKAGE_SYSTEM)
        assertThat(writer.identifier.value).isEqualTo(HealthConnectTestFixtures.WRITER_PACKAGE)
        assertThat(provenance.recordedElement.valueAsString).isEqualTo("2026-08-19T18:00:00Z")
        val assembler = provenance.agentFirstRep.who.reference
        val application = bundle.entry.single { it.fullUrl == assembler }.resource as Device
        assertThat(application.meta.profile.single().value).isEqualTo(ExchangeContract.MOBILE_APPLICATION_DEVICE_PROFILE)

        assertThat(bundle.entry.map { it.resource.fhirType() })
            .containsExactly("Device", "Device", "Observation", "Provenance").inOrder()
        assertThat(conversion.warnings).containsExactly(HealthConnectConversionWarning.RecordingDeviceOmitted(WATCH_NAME))
        val replay = ExchangeGraph.parse(ExchangeGraphKind.ACTIVE, conversion.graph.json) as ExchangeGraphParseResult.Valid
        assertThat(replay.graph.semanticallyEquals(conversion.graph)).isTrue()
    }

    @Test
    fun `the same record under the same context always yields the same bytes`() {
        val first = fixtures.converted(records.weight())
        val second = fixtures.converted(records.weight())
        assertThat(second.graph.json).isEqualTo(first.graph.json)
        assertThat(second.graph.sha256).isEqualTo(first.graph.sha256)
        assertThat(first.graph.json).doesNotContain("fixture-weight")
    }

    @Test
    fun `heart rate samples fan out into member outputs under the record's one offset`() {
        val conversion = fixtures.converted(records.heartRate())
        val observations = conversion.toBundle().observations()
        assertThat(observations).hasSize(2)
        assertThat(observations.map { it.effectiveDateTimeType.valueAsString })
            .containsExactly("2026-08-19T09:00:00-07:00", "2026-08-19T09:00:30-07:00").inOrder()
        assertThat(observations.map { it.valueQuantity.value.toPlainString() }).containsExactly("72", "74").inOrder()
        assertThat(conversion.identifiers.childOutputs).hasSize(1)
        assertThat(conversion.warnings.filterIsInstance<HealthConnectConversionWarning.SourceOffsetUnavailable>()).isEmpty()
        assertThat(conversion.graph.outputTargets().map { it.identifier }).containsExactlyElementsIn(conversion.identifiers.outputs)
    }

    @Test
    fun `a heart rate record without samples is an admitted zero-output result`() {
        val result = converter.convert(records.heartRate(samples = emptyList()), fixtures.context())
        assertThat(result).isEqualTo(
            HealthConnectConversionResult.NoOutput(
                HealthConnectSourceRecord("heart-record", HealthConnectSourceType.HEART_RATE),
                listOf(HealthConnectConversionWarning.RecordingDeviceOmitted(WATCH_NAME)),
            ),
        )
    }

    @Test
    fun `refused records carry exactly one registered error code`() {
        val stale = converter.convert(records.steps(), fixtures.context(conversionInstant = Instant.parse("2026-08-19T17:00:00Z")))
        val failure = (stale as HealthConnectConversionResult.Failed).failure as HealthConnectConversionFailure.InvalidValue
        assertThat(failure.reason).isEqualTo(HealthConnectValueFailure.ConversionInstantPrecedesSourceVersion("Metadata.lastModifiedTime"))
        assertThat(failure.diagnostic.severity).isEqualTo(ExchangeGraphDiagnostic.Severity.ERROR)
        assertThat(ExchangeGraphRule.entries.map { it.code }).contains(failure.diagnostic.code)

        val unknownSpecimen = converter.convert(
            records.bloodGlucose("fixture-glucose-unknown", BloodGlucoseRecord.SPECIMEN_SOURCE_UNKNOWN),
            fixtures.context(),
        )
        val refused = (unknownSpecimen as HealthConnectConversionResult.Failed).failure as HealthConnectConversionFailure.InvalidValue
        assertThat(refused.reason).isEqualTo(HealthConnectValueFailure.UnsupportedSourceValue("BloodGlucoseRecord.specimenSource"))
    }

    @Test
    fun `blood glucose joins a Specimen companion that the retraction targets name`() {
        val conversion = fixtures.converted(records.bloodGlucose("fixture-glucose", BloodGlucoseRecord.SPECIMEN_SOURCE_CAPILLARY_BLOOD))
        val bundle = conversion.toBundle()
        val observation = bundle.observations().single()
        val specimen = bundle.entry.map { it.resource }.filterIsInstance<Specimen>().single()
        assertThat(observation.meta.profile.map { it.value })
            .contains(HealthConnectContract.HEALTH_CONNECT_CAPILLARY_BLOOD_GLUCOSE_PROFILE)
        assertThat(observation.specimen.reference).isEqualTo(bundle.entry.single { it.resource === specimen }.fullUrl)
        assertThat(specimen.meta.profile.single().value).isEqualTo(HealthConnectContract.HEALTH_CONNECT_SPECIMEN_PROFILE)
        val mealContext = observation.getExtensionByUrl(HealthConnectContract.HEALTH_CONNECT_GLUCOSE_MEAL_CONTEXT)
        assertThat(mealContext.extension).hasSize(2)
        assertThat(conversion.graph.outputTargets().map { it.resourceType }).containsExactly("Observation", "Specimen")
        assertThat(conversion.graph.retractionTargets().map { it.role }).contains(RetractionTargetRole.DEVICE_SNAPSHOT)
    }

    @Test
    fun `warnings are the registry's warning rows and name what an accepted record lost`() {
        val routed = records.exercise(
            metadata = fixtures.metadata("fixture-exercise", base = Metadata.manualEntry()),
            laps = listOf(ExerciseLap(records.sessionStart, records.sessionStart.plusSeconds(600), null)),
        )
        val conversion = fixtures.converted(routed)
        assertThat(conversion.warnings).isEmpty()

        val withDevice = fixtures.converted(records.exercise())
        val warning = withDevice.warnings.single()
        assertThat(warning).isInstanceOf(HealthConnectConversionWarning.RecordingDeviceOmitted::class.java)
        assertThat(warning.diagnostic.severity).isEqualTo(ExchangeGraphDiagnostic.Severity.WARNING)
        assertThat(warning.diagnostic.location).isEqualTo("Observation.device")
        val warningRules = ExchangeGraphRule.entries.filter { it.severity == ExchangeGraphDiagnostic.Severity.WARNING }
        assertThat(warningRules).containsExactly(
            ExchangeGraphRule.MOBILE_OMISSION_RECORDING_DEVICE,
            ExchangeGraphRule.MOBILE_OMISSION_SOURCE_OFFSET,
            ExchangeGraphRule.MOBILE_OMISSION_UNMODELED_METADATA,
        )
        assertThat(
            listOf(
                HealthConnectConversionWarning.RecordingDeviceOmitted("x").diagnostic.code,
                HealthConnectConversionWarning.SourceOffsetUnavailable("x").diagnostic.code,
                HealthConnectConversionWarning.UnmodeledMetadataWithheld(setOf("x")).diagnostic.code,
            ),
        ).containsExactlyElementsIn(warningRules.map { it.code }).inOrder()
    }

    @Test
    fun `a resolved recording device joins the graph without its unit token`() {
        val resolver = RecordingDeviceResolver { device -> RecordingDevice("watch-unit-token-001", name = device.model) }
        val options = HealthConnectConversionOptions(UserAuthoredTextPolicy.RETAIN, recordingDevice = resolver)
        val conversion = fixtures.converted(records.weight(), fixtures.context(options = options))
        val bundle = conversion.toBundle()
        val observation = bundle.observations().single()
        val recorder = bundle.entry.single { it.fullUrl == observation.device.reference }.resource as Device
        assertThat(recorder.meta.profile.single().value).isEqualTo(ExchangeContract.MOBILE_RECORDING_DEVICE_PROFILE)
        assertThat(conversion.identifiers.recordingDeviceSnapshot).isNotNull()
        assertThat(conversion.graph.json).doesNotContain("watch-unit-token-001")
        assertThat(conversion.warnings).isEmpty()
    }

    @Test
    fun `native identifiers appear only under an authorized policy in a repository namespace`() {
        val omitted = fixtures.converted(records.weight()).toBundle().observations().single()
        assertThat(omitted.identifier.map { it.value }).doesNotContain("fixture-weight")

        val system = IdentifierSystem("${HealthConnectTestFixtures.ROOT}/identifiers/health-connect-records")
        val authorized = HealthConnectConversionOptions(
            UserAuthoredTextPolicy.RETAIN,
            nativeIdentifierDisclosure = GovernedSourceIdentifierDisclosurePolicy.Authorized(system),
        )
        val disclosed = fixtures.converted(records.weight(), fixtures.context(options = authorized)).toBundle().observations().single()
        assertThat(disclosed.identifier.single { it.system == system.value }.value).isEqualTo("fixture-weight")

        val reserved = GovernedSourceIdentifierDisclosurePolicy.Authorized(fixtures.systems.opaque.sourceRecord)
        assertThrows(IllegalArgumentException::class.java) {
            fixtures.context(options = HealthConnectConversionOptions(UserAuthoredTextPolicy.RETAIN, nativeIdentifierDisclosure = reserved))
        }
    }

    @Test
    fun `a batch keeps input order and reports refused records with their source`() {
        var sequence = 0L
        val batch = converter.convert(listOf(records.steps("batch-1"), records.steps(""), records.weight("batch-3"))) {
            fixtures.context(sequence = ++sequence)
        }
        assertThat(batch.conversions.map { it.source.id }).containsExactly("batch-1", "batch-3").inOrder()
        assertThat(batch.conversions.map { it.identifiers.event.identifier.value.substringAfterLast(':') }).containsExactly("1", "3")
        val failure = batch.failures.single()
        assertThat(failure.source).isNull()
        assertThat((failure.failure as HealthConnectConversionFailure.InvalidValue).reason)
            .isEqualTo(HealthConnectValueFailure.NativeIdentifierInvalid("Metadata.id"))
    }

    @Test
    fun `retraction targets derive from the catalog for exactly-one types and from the graph otherwise`() {
        val context = fixtures.context()
        val conversion = fixtures.converted(records.steps(), context)
        val steps = HealthConnectSourceRecord("fixture-step", HealthConnectSourceType.STEPS)
        val fromCatalog = converter.retractionTargets(steps, context.event)
        assertThat(fromCatalog.map { it.identifier }).containsExactlyElementsIn(conversion.graph.outputTargets().map { it.identifier })
        assertThrows(IllegalArgumentException::class.java) {
            converter.retractionTargets(HealthConnectSourceRecord("heart-record", HealthConnectSourceType.HEART_RATE), context.event)
        }
        val retraction = RetractionEvent(
            targets = fromCatalog,
            context = fixtures.context(sequence = 2).event,
            sourceRecord = conversion.identifiers.sourceRecord,
            retractedAt = Instant.parse("2026-08-19T18:00:05Z"),
        )
        val replay = ExchangeGraph.parse(ExchangeGraphKind.RETRACTION, retraction.graph.json)
        assertThat(replay).isInstanceOf(ExchangeGraphParseResult.Valid::class.java)
        assertThat(retraction.graph.toBundle().entry.map { it.resource.fhirType() }).containsExactly("Provenance")
    }

    @Test
    fun `a sleep session is a summary with one member per stage under the session offset`() {
        val conversion = fixtures.converted(records.sleep())
        val observations = conversion.toBundle().observations()
        val summary = observations.single { it.hasMember.isNotEmpty() }
        assertThat(summary.meta.profile.map { it.value }).contains(HealthConnectContract.MOBILE_SLEEP_DURATION_PROFILE)
        assertThat(summary.hasMember).hasSize(8)
        assertThat(summary.valueQuantity.value.toPlainString()).isEqualTo("8")
        val title = summary.getExtensionByUrl(HealthConnectContract.HEALTH_CONNECT_SESSION_TITLE).value.primitiveValue()
        assertThat(title).isEqualTo("Night sleep")
        assertThat(summary.noteFirstRep.text).isEqualTo("Participant-reported note")
        val stages = observations - summary
        assertThat(stages.map { it.effectivePeriod.startElement.valueAsString }.first()).isEqualTo("2026-08-19T01:00:00-07:00")
        assertThat(stages.map { it.valueCodeableConcept.coding.size }.toSet()).containsExactly(2)
        assertThat(conversion.identifiers.childOutputs).hasSize(8)

        val omitting = HealthConnectConversionOptions(UserAuthoredTextPolicy.OMIT)
        val withheld = fixtures.converted(records.sleep(), fixtures.context(options = omitting))
        val silent = withheld.toBundle().observations().single { it.hasMember.isNotEmpty() }
        assertThat(silent.hasExtension(HealthConnectContract.HEALTH_CONNECT_SESSION_TITLE)).isFalse()
        assertThat(silent.note).isEmpty()
        assertThat(withheld.warnings.filterIsInstance<HealthConnectConversionWarning.UnmodeledMetadataWithheld>()).isEmpty()
    }

    @Test
    fun `an exercise session carries its segments and laps as workout-segment members`() {
        val start = records.sessionStart
        val record = records.exercise(
            segments = listOf(ExerciseSegment(start, start.plusSeconds(300), ExerciseSegment.EXERCISE_SEGMENT_TYPE_RUNNING, 0)),
            laps = listOf(ExerciseLap(start.plusSeconds(300), start.plusSeconds(600), Length.meters(400.0))),
        )
        val conversion = fixtures.converted(record)
        val observations = conversion.toBundle().observations()
        val summary = observations.single { it.hasMember.isNotEmpty() }
        assertThat(summary.meta.profile.map { it.value }).contains(HealthConnectContract.MOBILE_WORKOUT_PROFILE)
        assertThat(summary.valueCodeableConcept.coding.map { it.code }).contains("EXERCISE_TYPE_RUNNING")
        val members = observations - summary
        assertThat(members).hasSize(2)
        assertThat(members.map { it.valueCodeableConcept.coding.map { coding -> coding.code } }.flatten())
            .containsAtLeast("EXERCISE_SEGMENT_TYPE_RUNNING", "EXERCISE_LAP")
        val lap = members.single { it.valueCodeableConcept.coding.any { coding -> coding.code == "EXERCISE_LAP" } }
        assertThat(lap.componentFirstRep.valueQuantity.value.toPlainString()).isEqualTo("400.0")
    }

    @Test
    fun `a known enrollment bundles its study context and every output names the study`() {
        val root = HealthConnectTestFixtures.ROOT
        val enrollment = StudyEnrollment(
            study = BusinessIdentifier(IdentifierSystem("$root/studies"), "a"),
            protocolUrl = "$root/PlanDefinition/a",
            protocolVersion = "1",
            enrollment = BusinessIdentifier(IdentifierSystem("$root/enrollments"), "enrollment-a"),
        )
        val companion = ApplicationDevice(name = "Companion", packageName = "com.example.companion", version = "4")
        val gateway = ConverterRole.GatewayApplication(companion)
        val conversion = fixtures.converted(records.weight(), fixtures.context(studies = listOf(enrollment), converterRole = gateway))
        val bundle = conversion.toBundle()
        assertThat(bundle.entry.map { it.resource.fhirType() })
            .containsAtLeast("ResearchStudy", "PlanDefinition", "ResearchSubject")
        val observation = bundle.observations().single()
        val study = observation.getExtensionByUrl(ExchangeContract.RESEARCH_STUDY_EXTENSION).value as Reference
        assertThat(bundle.entry.single { it.fullUrl == study.reference }.resource.fhirType()).isEqualTo("ResearchStudy")
        val gatewayDevice = observation.getExtensionByUrl(ExchangeContract.GATEWAY_DEVICE_EXTENSION).value as Reference
        assertThat(bundle.entry.single { it.fullUrl == gatewayDevice.reference }.resource.fhirType()).isEqualTo("Device")
        assertThat(conversion.identifiers.sourceAuthorSnapshot).isNotNull()
        val replay = ExchangeGraph.parse(ExchangeGraphKind.ACTIVE, conversion.graph.json)
        assertThat(replay).isInstanceOf(ExchangeGraphParseResult.Valid::class.java)
    }

    @Test
    fun `nutrition fans out one output per present nutrient and withholds the meal`() {
        val conversion = fixtures.converted(records.nutrition())
        val observations = conversion.toBundle().observations()
        assertThat(observations.map { it.code.codingFirstRep.code.lowercase(Locale.ROOT) }).hasSize(3)
        assertThat(conversion.identifiers.childOutputs).hasSize(2)
        assertThat(conversion.warnings).contains(
            HealthConnectConversionWarning.UnmodeledMetadataWithheld(setOf("NutritionRecord.name", "NutritionRecord.mealType")),
        )
    }

    private fun Bundle.observations(): List<Observation> = entry.map { it.resource }.filterIsInstance<Observation>()

    /** The retraction targets that name outputs and companions, without the device snapshots every graph carries. */
    private fun ExchangeGraph.outputTargets(): List<RetractionTarget> =
        retractionTargets().filter { it.role != RetractionTargetRole.DEVICE_SNAPSHOT }

    private companion object {
        const val WATCH_NAME = "Example Device Company Study Watch"
    }
}
