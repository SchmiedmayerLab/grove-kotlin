//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.fhir

import com.google.common.collect.Range
import com.google.common.truth.Truth.assertThat
import org.hl7.fhir.r4.model.Identifier
import org.hl7.fhir.r4.model.ResourceType
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Instant

/** Pins the defaults table: every optional parameter has the same default on every platform. */
class ContractDefaultsTest {
    @Test
    fun `event context defaults to the assembler role no studies and no repository ids`() {
        val context = ConformanceFixtures.eventContext()
        assertThat(context.converterRole).isEqualTo(ConverterRole.Assembler)
        assertThat(context.studies).isEmpty()
        assertThat(context.repositoryIds).isEmpty()
    }

    @Test
    fun `event context derives the entry-node system from the scope and the conversion instant from now`() {
        val fixture = ConformanceFixtures.eventContext()
        val before = Instant.now()
        val context = ExchangeEventContext(
            subject = fixture.subject,
            event = fixture.event,
            identityScope = fixture.identityScope,
            repositoryScope = fixture.repositoryScope,
            application = fixture.application,
            host = fixture.host,
        )
        assertThat(context.entryNodeIdentifierSystem).isEqualTo(ConformanceFixtures.vectorSystems.entryNode)
        assertThat(context.conversionInstant).isIn(Range.closed(before, Instant.now()))
        assertThat(ConformanceFixtures.vectorSystems.all).hasSize(12)
    }

    @Test
    fun `device facts default their optional fields to null`() {
        assertThat(ApplicationDevice(name = "App", packageName = "com.example", version = "1").build).isNull()
        val host = HostDevice(operatingSystemVersion = "16")
        assertThat(listOf(host.name, host.manufacturer, host.modelNumber)).containsExactly(null, null, null)
        val recorder = RecordingDevice(stableUnitToken = "unit-1")
        assertThat(listOf(recorder.name, recorder.manufacturer, recorder.modelNumber)).containsExactly(null, null, null)
    }

    @Test
    fun `disclosure defaults omit and a target carries no native identifier unless given`() {
        val authorized = GovernedSourceIdentifierDisclosurePolicy.Authorized(IdentifierSystem("https://example.org/records"))
        assertThat(authorized.type).isNull()
        assertThat(GovernedSourceIdentifierDisclosurePolicy.Omit).isInstanceOf(GovernedSourceIdentifierDisclosurePolicy::class.java)
        assertThat(RouteDisclosurePolicy.entries).containsExactly(RouteDisclosurePolicy.OMIT, RouteDisclosurePolicy.AUTHORIZED)
        val target = RetractionTarget(
            identifier = ConformanceFixtures.scope
                .sourceRecord("health-connect", "StepsRecord", ConformanceFixtures.eventContext().repositoryScope, "record-1")
                .output("single", "step-count"),
            resourceType = ResourceType.Observation,
            role = RetractionTargetRole.PRIMARY_OUTPUT,
        )
        assertThat(target.nativeRecordIdentifier).isNull()
    }

    @Test
    fun `a retraction target is typed and renders its native record identifier without a role`() {
        val output = ConformanceFixtures.scope
            .sourceRecord("health-connect", "StepsRecord", ConformanceFixtures.eventContext().repositoryScope, "record-1")
            .output("single", "step-count")
        val native = BusinessIdentifier(IdentifierSystem("https://study.example.org/fhir/identifiers/health-connect-records"), "record-1")
        val target = RetractionTarget(output, ResourceType.Observation, RetractionTargetRole.PRIMARY_OUTPUT, native)
        val rendered = target.toReference().getExtensionByUrl(ExchangeContract.RETRACTION_TARGET_NATIVE_IDENTIFIER_EXTENSION).value
            as Identifier
        assertThat(BusinessIdentifier.from(rendered)).isEqualTo(native)
        assertThat(rendered.hasType()).isFalse()
        assertThat(target.toReference().type).isEqualTo("Observation")
        assertThrows(IllegalArgumentException::class.java) {
            RetractionTarget(output, ResourceType.Device, RetractionTargetRole.PRIMARY_OUTPUT)
        }
    }

    @Test
    fun `the writer and its host are the graph nodes the IG Writer names`() {
        assertThat(ExchangeGraphNode.entries.map { it.name }).containsExactly(
            "BUNDLE", "PRIMARY_OUTPUT", "SOURCE_ARTIFACT", "RECORDING_DEVICE", "APPLICATION_DEVICE", "HOST_DEVICE",
            "WRITER", "WRITER_HOST", "PROVENANCE",
        ).inOrder()
    }

    @Test
    fun `identifier roles kinds and target roles carry the catalog vocabulary`() {
        assertThat(GroveIdentifierRole.entries.map { it.code }).containsExactly(
            "source-record", "source-output", "writer-record", "source-artifact", "source-context",
            "recording-device", "device-snapshot", "event", "entry-node",
        ).inOrder()
        assertThat(OpaqueIdentityKind.entries).hasSize(10)
        assertThat(RetractionTargetRole.entries.map { it.code })
            .containsExactly("child-output", "device-snapshot", "primary-output", "source-artifact", "specimen")
        assertThat(StudyContextEntryNodeRole.entries.map { it.code })
            .containsExactly("patient", "research-study", "research-subject", "plan-definition").inOrder()
        assertThat(ExchangeContract.entryIdentifierPriority.first()).isEqualTo(GroveIdentifierRole.SOURCE_OUTPUT)
    }
}
