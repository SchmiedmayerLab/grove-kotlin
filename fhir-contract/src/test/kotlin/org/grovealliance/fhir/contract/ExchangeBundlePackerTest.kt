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
class ExchangeBundlePackerTest {
    private val producer = ExchangeProducer.create(
        identityScope = ExchangeTestScope.identityScope,
        subject = Subject.Logical(identifier = ExchangeTestScope.subject),
        application = ApplicationDevice.create(name = "App", packageName = "org.example.app", version = "1").getOrThrow(),
        host = HostDevice.create(operatingSystemVersion = "16").getOrThrow(),
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
    private val packedAt = Instant.parse("2026-01-01T12:00:00Z")

    @Test
    fun `states the nodes two records share once`() {
        // given
        val packer = ExchangeBundlePacker(kind = ExchangeRecord.Kind.ACTIVE)
        val first = record(nativeRecordId = "record-1", beats = 72.0)
        val second = record(nativeRecordId = "record-2", beats = 80.0)

        // when
        val results = listOf(first, second).map { packer.insert(record = it).getOrThrow() }

        // then
        assertThat(results).containsExactly(ExchangeBundlePacker.InsertResult.INSERTED, ExchangeBundlePacker.InsertResult.INSERTED)
        assertThat(entryCount(bundle = packer.bundle(packedAt = packedAt).getOrThrow().json)).isEqualTo(first.entries.size + 2)
    }

    @Test
    fun `defers a second version and skips an exact retry`() {
        // given
        val packer = ExchangeBundlePacker(kind = ExchangeRecord.Kind.ACTIVE)
        val original = record(nativeRecordId = "record-1", beats = 72.0)
        packer.insert(record = original).getOrThrow()

        // when
        val retry = packer.insert(record = original).getOrThrow()
        val revision = packer.insert(record = record(nativeRecordId = "record-1", beats = 73.0)).getOrThrow()

        // then
        assertThat(retry).isEqualTo(ExchangeBundlePacker.InsertResult.DUPLICATE)
        assertThat(revision).isEqualTo(ExchangeBundlePacker.InsertResult.DEFERRED)
        assertThat(packer.recordCount).isEqualTo(1)
    }

    @Test
    fun `defers a record past the record cap but admits any first record`() {
        // given
        val packer = ExchangeBundlePacker(kind = ExchangeRecord.Kind.ACTIVE, maximumRecords = 1, maximumBytes = 1)

        // when
        val first = packer.insert(record = record(nativeRecordId = "record-1", beats = 72.0)).getOrThrow()
        val second = packer.insert(record = record(nativeRecordId = "record-2", beats = 72.0)).getOrThrow()

        // then
        assertThat(first).isEqualTo(ExchangeBundlePacker.InsertResult.INSERTED)
        assertThat(second).isEqualTo(ExchangeBundlePacker.InsertResult.DEFERRED)
        assertThat(packer.isFull).isTrue()
    }

    @Test
    fun `refuses a record of the other kind`() {
        // given
        val packer = ExchangeBundlePacker(kind = ExchangeRecord.Kind.RETRACTION)

        // when
        val result = packer.insert(record = record(nativeRecordId = "record-1", beats = 72.0))

        // then
        assertThat(result.exceptionOrNull()).isEqualTo(ExchangeBundlePacker.Failure.MixedKinds)
    }

    @Test
    fun `retracts a source record under a key that a re-reported retraction repeats`() {
        // given
        val sourceRecord = envelope.sourceRecord(sourceType = "HeartRateRecord", nativeRecordId = "record-1").getOrThrow().identifier
        val target = RetractionRecord.Target.sourceRecord(sourceRecord = sourceRecord).getOrThrow()

        // when
        val first = retraction(sourceRecord = sourceRecord, target = target, at = packedAt)
        val reported = retraction(sourceRecord = sourceRecord, target = target, at = packedAt.plusSeconds(60))
        val packer = ExchangeBundlePacker(kind = ExchangeRecord.Kind.RETRACTION)
        val results = listOf(first, reported).map { packer.insert(record = it).getOrThrow() }

        // then
        assertThat(first.version).isEqualTo(reported.version)
        assertThat(first.version.identifier.role).isEqualTo(GroveIdentifierRole.RECORD_RETRACTION)
        assertThat(String(first.json, Charsets.UTF_8)).doesNotContain(ExchangeContract.RECORD_VERSION_EXTENSION)
        assertThat(results).containsExactly(ExchangeBundlePacker.InsertResult.INSERTED, ExchangeBundlePacker.InsertResult.DUPLICATE)
    }

    @Test
    fun `reads a retraction back from its own bytes`() {
        // given
        val sourceRecord = envelope.sourceRecord(sourceType = "HeartRateRecord", nativeRecordId = "record-1").getOrThrow().identifier
        val retraction = retraction(
            sourceRecord = sourceRecord,
            target = RetractionRecord.Target.sourceRecord(sourceRecord = sourceRecord).getOrThrow(),
            at = packedAt,
        )

        // when
        val read = ExchangeRecord.validating(json = retraction.json).getOrThrow()

        // then
        assertThat(read).isEqualTo(retraction)
        assertThat(read.kind).isEqualTo(ExchangeRecord.Kind.RETRACTION)
    }

    @Test
    fun `refuses a retraction whose target role doesn't fit its identity`() {
        // given
        val sourceRecord = envelope.sourceRecord(sourceType = "HeartRateRecord", nativeRecordId = "record-1").getOrThrow().identifier

        // when
        val result = RetractionRecord.Target.output(
            identifier = sourceRecord,
            resourceType = "Observation",
            role = RetractionRecord.Target.Role.PRIMARY_OUTPUT,
        )

        // then
        assertThat(result.exceptionOrNull()).isInstanceOf(RetractionRecord.ValidationError.TargetMismatch::class.java)
    }

    private fun retraction(sourceRecord: RoledIdentifier, target: RetractionRecord.Target, at: Instant) = assembler.retraction(
        sourceRecord = sourceRecord,
        targets = listOf(target),
        occurred = RetractionRecord.Occurrence.At(instant = at),
        instant = at,
    ).getOrThrow().record

    private fun record(nativeRecordId: String, beats: Double): ExchangeRecord = assembler.assemble(
        draft = ExchangeRecordDraft(
            instant = packedAt,
            sourceRecord = envelope.sourceRecord(sourceType = "HeartRateRecord", nativeRecordId = nativeRecordId).getOrThrow(),
            outputs = listOf(
                ExchangeOutputDraft(
                    role = "sample",
                    resource = ExchangeOutputDraft.Resource.ObservationResource(
                        observation = Observation(
                            meta = Meta(profile = listOf(Profile.Mobile.GROVE_MOBILE_HEART_RATE)),
                            code = CodeableConcept(coding = listOf(Coding(system = "http://loinc.org", code = "8867-4"))),
                            value = Observation.Value.QuantityValue(
                                value = Quantity(
                                    value = FhirDecimal.of(value = beats).getOrThrow(),
                                    system = "http://unitsofmeasure.org",
                                    code = "/min",
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        ),
    ).getOrThrow().record

    private fun entryCount(bundle: ByteArray): Int {
        val json = LosslessJsonValue.parse(bytes = bundle).getOrThrow() as LosslessJsonValue.JsonObject
        return (json.members.getValue("entry") as LosslessJsonValue.JsonArray).elements.size
    }
}
