//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.fhir.contract

import com.google.common.truth.Truth.assertThat
import org.grovealliance.fhir.contract.internal.ExchangeTestScope
import org.junit.Test
import java.time.Instant

@OptIn(InternalGroveFhirApi::class)
class ExchangeGraphAssemblerTest {
    private val producer = ExchangeProducer.create(
        identityScope = ExchangeTestScope.identityScope,
        subject = Subject.Logical(identifier = ExchangeTestScope.subject),
        application = ApplicationDevice.create(
            name = "My Heart Counts",
            packageName = "edu.stanford.myheartcounts",
            version = "1.0",
        ).getOrThrow(),
        host = HostDevice.create(operatingSystemVersion = "16", productType = "Pixel 9").getOrThrow(),
    ).getOrThrow()
    private val envelope = ExchangeEnvelope.of(
        producer = producer,
        adapter = ExchangeAdapterContract(
            adapterId = "health-connect",
            provenanceProfile = ExchangeContract.CONVERSION_PROVENANCE_PROFILE,
            applicationDeviceProfile = Profile.Mobile.GROVE_APPLICATION_DEVICE,
        ),
        repositoryScope = ExchangeTestScope.identifier(system = "urn:uuid:1f5c58aa-6ec6-4e79-a682-829a9debd3f5", value = "default"),
    )
    private val assembler = ExchangeGraphAssembler(envelope = envelope)
    private val heartRate = Observation(
        meta = Meta(profile = listOf(Profile.Mobile.GROVE_MOBILE_HEART_RATE)),
        code = CodeableConcept(coding = listOf(Coding(system = "http://loinc.org", code = "8867-4"))),
        effective = Observation.Effective.DateTime(value = "2026-01-01T10:30:00Z"),
        value = Observation.Value.QuantityValue(
            value = Quantity(
                value = FhirDecimal.of(value = 72.0).getOrThrow(),
                unit = "beats/minute",
                system = "http://unitsofmeasure.org",
                code = "/min",
            ),
        ),
    )

    @Test
    fun `mints a record version that recomputes from the record's own bytes`() {
        // given
        val draft = draft(outputs = listOf(output()))

        // when
        val record = assembler.assemble(draft = draft).getOrThrow().record

        // then
        val bundle = LosslessJsonValue.parse(bytes = record.json).getOrThrow() as LosslessJsonValue.JsonObject
        val resources = (bundle.members.getValue("entry") as LosslessJsonValue.JsonArray).elements.associate { entry ->
            val members = (entry as LosslessJsonValue.JsonObject).members
            (members.getValue("fullUrl") as LosslessJsonValue.JsonString).value to members.getValue("resource")
        }
        val digest = LosslessJsonValue.recordContentDigest(
            provenance = resources.getValue(record.version.fullUrl),
            outputs = record.outputFullUrls.map { resources.getValue(it) },
        ).getOrThrow()
        val recomputed = ExchangeTestScope.identityScope.recordVersion(
            sourceRecord = record.sourceRecord,
            contentDigest = digest,
        ).getOrThrow()
        assertThat(recomputed).isEqualTo(record.version.identifier)
    }

    @Test
    fun `assembles the same draft into the same bytes`() {
        // given
        val draft = draft(outputs = listOf(output()))

        // when
        val first = assembler.assemble(draft = draft).getOrThrow().record
        val second = assembler.assemble(draft = draft).getOrThrow().record

        // then
        assertThat(first.json).isEqualTo(second.json)
        assertThat(first).isEqualTo(second)
    }

    @Test
    fun `changes the record version when a decimal lexeme changes`() {
        // given
        val draft = draft(outputs = listOf(output()))
        val lexeme = FhirDecimal.parse(lexeme = "72.0").getOrThrow()
        val quantity = (heartRate.value as Observation.Value.QuantityValue).value.copy(value = lexeme)
        val restated = heartRate.copy(value = Observation.Value.QuantityValue(value = quantity))

        // when
        val original = assembler.assemble(draft = draft).getOrThrow().record
        val changed = assembler.assemble(draft = draft(outputs = listOf(output(observation = restated)))).getOrThrow().record

        // then
        assertThat(changed.version).isNotEqualTo(original.version)
        assertThat(changed.outputFullUrls).isEqualTo(original.outputFullUrls)
    }

    @Test
    fun `states outputs, then context, then devices, then the provenance`() {
        // when
        val assembled = assembler.assemble(draft = draft(outputs = listOf(output()))).getOrThrow()

        // then
        val record = assembled.record
        val urls = record.entries.map { it.fullUrl }
        assertThat(urls.first()).isEqualTo(assembled.identifiers.primaryOutput.fullUrl)
        assertThat(urls.last()).isEqualTo(record.version.fullUrl)
        assertThat(record.sharedNodeFullUrls).containsExactly(
            assembled.identifiers.hostSnapshot.fullUrl,
            assembled.identifiers.applicationSnapshot.fullUrl,
        )
        assertThat(assembled.identifiers.provenance.role).isEqualTo(GroveIdentifierRole.RECORD_VERSION)
    }

    @Test
    fun `links members to the primary and a derived child back to it`() {
        // given
        val outputs = listOf(
            output(),
            output(discriminator = "member").copy(memberOfPrimary = true),
            output(discriminator = "derived").copy(derivedFromPrimary = true),
        )

        // when
        val assembled = assembler.assemble(draft = draft(outputs = outputs)).getOrThrow()

        // then
        val json = String(assembled.record.json, Charsets.UTF_8)
        assertThat(json).contains("\"hasMember\":[{\"reference\":\"${assembled.identifiers.childOutputs[0].fullUrl}\"")
        assertThat(json).contains("\"derivedFrom\":[{\"reference\":\"${assembled.identifiers.primaryOutput.fullUrl}\"}]")
    }

    @Test
    fun `reads a record back from its own bytes`() {
        // given
        val record = assembler.assemble(draft = draft(outputs = listOf(output()))).getOrThrow().record

        // when
        val read = ExchangeRecord.validating(json = record.json).getOrThrow()

        // then
        assertThat(read).isEqualTo(record)
        assertThat(read.outputFullUrls).isEqualTo(record.outputFullUrls)
    }

    @Test
    fun `refuses a draft without outputs`() {
        // when
        val result = assembler.assemble(draft = draft(outputs = emptyList()))

        // then
        assertThat(result.exceptionOrNull()).isEqualTo(ExchangeAssemblyError.NoOutputs)
    }

    private fun output(observation: Observation = heartRate, discriminator: String = "single") = ExchangeOutputDraft(
        role = "sample",
        discriminator = discriminator,
        resource = ExchangeOutputDraft.Resource.ObservationResource(observation = observation),
    )

    private fun draft(outputs: List<ExchangeOutputDraft>) = ExchangeRecordDraft(
        instant = Instant.parse("2026-01-01T12:00:00Z"),
        sourceRecord = envelope.sourceRecord(sourceType = "HeartRateRecord", nativeRecordId = "record-1").getOrThrow(),
        outputs = outputs,
    )
}
