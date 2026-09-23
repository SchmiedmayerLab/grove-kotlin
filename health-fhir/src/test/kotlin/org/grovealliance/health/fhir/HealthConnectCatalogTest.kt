//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.health.fhir

import androidx.health.connect.client.records.CyclingPedalingCadenceRecord
import androidx.health.connect.client.records.ExerciseLap
import androidx.health.connect.client.records.ExerciseSegment
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.PowerRecord
import androidx.health.connect.client.records.SkinTemperatureRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.SpeedRecord
import androidx.health.connect.client.records.StepsCadenceRecord
import androidx.health.connect.client.records.metadata.Metadata
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.grovealliance.fhir.ConverterRole
import org.grovealliance.fhir.ExchangeGraphNode
import org.grovealliance.fhir.ExchangeGraphRule
import org.grovealliance.fhir.GovernedSourceIdentifierDisclosurePolicy
import org.grovealliance.fhir.ProducerDiagnostic
import org.grovealliance.fhir.RouteDisclosurePolicy
import org.junit.Test
import java.lang.reflect.Modifier
import java.time.Instant

/** Pins the generated catalog, the field dispositions, the defaults table and the registry codes. */
class HealthConnectCatalogTest {
    @Test
    fun `the catalog classifies the complete AndroidX 1_1 inventory exactly once`() {
        val types = HealthConnectSourceType.entries
        assertThat(types).hasSize(HealthConnectContract.RECORD_TYPE_COUNT)
        assertThat(types.map { it.token }.toSet()).hasSize(types.size)
        assertThat(types.count { it.status == HealthConnectSourceStatus.SUPPORTED }).isEqualTo(SUPPORTED_TYPES)
        assertThat(types.count { it.status == HealthConnectSourceStatus.DEFERRED }).isEqualTo(1)
        assertThat(HealthConnectCatalog.entries.keys).containsExactlyElementsIn(types)
        HealthConnectCatalog.entries.values.forEach { entry ->
            entry.outputs.mapNotNull { it.measurement }.forEach { measurement ->
                assertWithMessage("${entry.type.token} $measurement").that(HealthConnectMeasurements.byId).containsKey(measurement)
            }
        }
    }

    @Test
    fun `every public field of every supported AndroidX record has a reviewed disposition`() {
        HealthConnectSourceType.entries.filter { it.status == HealthConnectSourceStatus.SUPPORTED }.forEach { type ->
            assertWithMessage("${type.token} public source fields")
                .that(HealthConnectFieldDispositions.records.getValue(type).keys)
                .containsExactlyElementsIn(publicFields(type.recordClass.java))
        }
        assertWithMessage("Metadata public source fields")
            .that(HealthConnectFieldDispositions.metadata.keys)
            .containsExactlyElementsIn(publicFields(Metadata::class.java))
        nestedSourceTypes.forEach { (name, sourceType) ->
            assertWithMessage("$name public source fields")
                .that(HealthConnectFieldDispositions.nested.getValue(name).keys)
                .containsExactlyElementsIn(publicFields(sourceType))
        }
        assertThat(HealthConnectFieldDispositions.nested.keys).containsExactlyElementsIn(nestedSourceTypes.keys)
    }

    @Test
    fun `every diagnostic this producer can report is a registry row of the right severity`() {
        val codes = ExchangeGraphRule.entries.associateBy { it.code }
        val refusals = listOf(
            HealthConnectValueFailure.ValueShapeInvalid("x"),
            HealthConnectValueFailure.ValueOutsideDomain("x"),
            HealthConnectValueFailure.UnsupportedSourceValue("x"),
            HealthConnectValueFailure.RequiredMetadataMissing("x"),
            HealthConnectValueFailure.EffectivePeriodInvalid("x"),
            HealthConnectValueFailure.ConversionInstantPrecedesSourceVersion("x"),
            HealthConnectValueFailure.TextNotUnicodeScalar("x"),
            HealthConnectValueFailure.NativeIdentifierInvalid("x"),
        ).map { it.diagnostic } + listOf(
            HealthConnectConversionFailure.UnsupportedSourceType("x"),
            HealthConnectConversionFailure.IntentionallyUnsupported(HealthConnectSourceType.STEPS),
            HealthConnectConversionFailure.NotYetConvertible(HealthConnectSourceType.PLANNED_EXERCISE_SESSION),
            HealthConnectConversionFailure.PlatformExclusive(HealthConnectSourceType.STEPS),
            HealthConnectConversionFailure.RepositoryIdWithoutNode(ExchangeGraphNode.RECORDING_DEVICE),
            HealthConnectConversionFailure.Unclassified(IllegalStateException("x")),
        ).map { it.diagnostic } + listOf(
            HealthConnectProjectionRefusal.NotHealthConnectOutput("x"),
            HealthConnectProjectionRefusal.UnsupportedSourceType("x"),
            HealthConnectProjectionRefusal.ChildOutput("x"),
            HealthConnectProjectionRefusal.MissingElement("x"),
            HealthConnectProjectionRefusal.UnitMismatch("x"),
            HealthConnectProjectionRefusal.UnsupportedCode("x"),
        ).map { it.diagnostic }
        refusals.forEach { diagnostic ->
            assertWithMessage(diagnostic.code).that(codes[diagnostic.code]?.severity).isEqualTo(ProducerDiagnostic.Severity.ERROR)
        }
        val warnings = listOf(
            HealthConnectConversionWarning.RecordingDeviceOmitted("x"),
            HealthConnectConversionWarning.SourceOffsetUnavailable("x"),
            HealthConnectConversionWarning.UnmodeledMetadataWithheld(listOf("x")),
        ).map { it.diagnostic }
        warnings.forEach { diagnostic ->
            assertWithMessage(diagnostic.code).that(codes[diagnostic.code]?.severity).isEqualTo(ProducerDiagnostic.Severity.WARNING)
        }
    }

    @Test
    fun `the Health Connect defaults follow the shared defaults table`() {
        val options = HealthConnectConversionOptions.Default
        assertThat(options).isEqualTo(HealthConnectConversionOptions())
        assertThat(options.userAuthoredText).isEqualTo(UserAuthoredTextPolicy.OMIT)
        assertThat(options.recordingDevice).isSameInstanceAs(RecordingDeviceResolver.None)
        assertThat(options.routeDisclosure).isEqualTo(RouteDisclosurePolicy.OMIT)
        assertThat(options.nativeIdentifierDisclosure).isEqualTo(GovernedSourceIdentifierDisclosurePolicy.Omit)
        assertThat(options.recordingDevice.resolve(HealthConnectTestFixtures.watch)).isNull()

        val before = Instant.now()
        val context = HealthConnectConversionContext(
            subject = HealthConnectTestFixtures.subject,
            event = HealthConnectTestFixtures.event(1),
            identityScope = HealthConnectTestFixtures.scope,
            repositoryScope = HealthConnectTestFixtures.repositoryScope,
            application = HealthConnectTestFixtures.application,
            host = HealthConnectTestFixtures.host,
        )
        assertThat(context.options).isSameInstanceAs(HealthConnectConversionOptions.Default)
        assertThat(HealthConnectConversionContext(context.event)).isEqualTo(context)
        assertThat(context.event.converterRole).isEqualTo(ConverterRole.Assembler)
        assertThat(context.event.studies).isEmpty()
        assertThat(context.event.repositoryIds).isEmpty()
        assertThat(context.event.conversionInstant).isAtLeast(before)
        assertThat(context.event.entryNodeIdentifierSystem).isEqualTo(HealthConnectTestFixtures.systems.entryNode)
    }

    @Test
    fun `exports the exact implementation capability inventory`() {
        val destination = HealthConnectTestFixtures.configuredFile("grove.capability.export") ?: return
        destination.parentFile?.let { parent -> check(parent.isDirectory || parent.mkdirs()) { "Cannot create $parent" } }
        val byStatus = { status: HealthConnectSourceStatus ->
            HealthConnectSourceType.entries.filter { it.status == status }.map { it.token }.sorted()
        }
        destination.writeText(
            capabilityManifest(
                all = HealthConnectSourceType.entries.map { it.token }.sorted(),
                supported = byStatus(HealthConnectSourceStatus.SUPPORTED),
                deferred = byStatus(HealthConnectSourceStatus.DEFERRED),
            ),
        )
    }

    private fun capabilityManifest(all: List<String>, supported: List<String>, deferred: List<String>): String = buildString {
        append("{\n")
        append("  \"schemaVersion\": 0,\n")
        append("  \"sourcePackage\": \"${HealthConnectContract.SOURCE_PACKAGE}\",\n")
        append("  \"sourceVersion\": \"${HealthConnectContract.SOURCE_VERSION}\",\n")
        append("  \"sourceTypeExtension\": \"${HealthConnectContract.HEALTH_CONNECT_RECORD_TYPE_EXTENSION}\",\n")
        append("  \"fieldDispositionSourceVersion\": \"${HealthConnectContract.SOURCE_VERSION}\",\n")
        append("  \"allRecordTypes\": ${all.jsonArray()},\n")
        append("  \"supportedRecordTypes\": ${supported.jsonArray()},\n")
        append("  \"deferredRecordTypes\": ${deferred.jsonArray()}\n")
        append("}\n")
    }

    private fun List<String>.jsonArray(): String = joinToString(", ", "[", "]") { "\"$it\"" }

    private fun publicFields(type: Class<*>): Set<String> = type.declaredMethods
        .asSequence()
        .filter { method ->
            Modifier.isPublic(method.modifiers) &&
                !Modifier.isStatic(method.modifiers) &&
                method.parameterCount == 0 &&
                method.name.matches(PUBLIC_GETTER)
        }
        .map { method -> method.name.removePrefix("get").replaceFirstChar(Char::lowercaseChar) }
        .toSet()

    private companion object {
        const val SUPPORTED_TYPES = 40
        val PUBLIC_GETTER = Regex("get[A-Z][A-Za-z0-9]*")
        val nestedSourceTypes = mapOf(
            "HeartRateRecord.Sample" to HeartRateRecord.Sample::class.java,
            "CyclingPedalingCadenceRecord.Sample" to CyclingPedalingCadenceRecord.Sample::class.java,
            "PowerRecord.Sample" to PowerRecord.Sample::class.java,
            "SpeedRecord.Sample" to SpeedRecord.Sample::class.java,
            "StepsCadenceRecord.Sample" to StepsCadenceRecord.Sample::class.java,
            "SleepSessionRecord.Stage" to SleepSessionRecord.Stage::class.java,
            "SkinTemperatureRecord.Delta" to SkinTemperatureRecord.Delta::class.java,
            "ExerciseSegment" to ExerciseSegment::class.java,
            "ExerciseLap" to ExerciseLap::class.java,
        )
    }
}
