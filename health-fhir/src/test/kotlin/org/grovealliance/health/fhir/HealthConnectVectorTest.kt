//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.health.fhir

import androidx.health.connect.client.records.HeartRateRecord
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.grovealliance.fhir.OpaqueIdentityKind
import org.grovealliance.health.fhir.HealthConnectTestFixtures.array
import org.grovealliance.health.fhir.HealthConnectTestFixtures.objectValue
import org.grovealliance.health.fhir.HealthConnectTestFixtures.string
import org.junit.Test
import java.time.Instant

/** The converter reproduces the normative Health Connect identities of the exchange-protocol vectors. */
class HealthConnectVectorTest {
    private val fixtures = HealthConnectTestFixtures
    private val vectors = fixtures.vectors

    @Test
    fun `the derived deployment systems equal the vendored vectors`() {
        val expected = vectors.array("identitySystems").associate { row ->
            row.jsonObject.string("identityKind") to row.jsonObject.string("system")
        }
        assertThat(fixtures.systems.opaque.all.entries.associate { (kind, system) -> kind.code to system.value }).isEqualTo(expected)
        assertThat(fixtures.systems.event.value).isEqualTo(vectors.objectValue("event").string("system"))
        assertThat(fixtures.systems.entryNode.value).isEqualTo(vectors.objectValue("entryNode").string("system"))
        assertThat(fixtures.systems.all).hasSize(12)
    }

    @Test
    fun `the converter mints the heart-rate identities of the vectors`() {
        val identities = vectors.array("identities").associate { it.jsonObject.string("id") to it.jsonObject }
        val multiOutput = HeartRateRecord(
            startTime = Instant.parse("2026-08-19T10:00:00Z"),
            startZoneOffset = null,
            endTime = Instant.parse("2026-08-19T11:00:00Z"),
            endZoneOffset = null,
            samples = listOf(HeartRateRecord.Sample(Instant.parse("2026-08-19T10:30:00Z"), 72)),
            metadata = fixtures.metadata("record-heart-001"),
        )
        val conversion = fixtures.converted(multiOutput)
        assertThat(conversion.identifiers.primaryOutput.identifier.value)
            .isEqualTo(identities.getValue("multi-output-sample").string("value"))
        assertThat(conversion.identifiers.sourceRecord.identifier.value)
            .isEqualTo(identities.getValue("normative-corpus-heart-rate-source-record").string("value"))
        assertThat(conversion.warnings).contains(HealthConnectConversionWarning.SourceOffsetUnavailable("Observation.effectiveDateTime"))

        val corpus = fixtures.converted(
            HeartRateRecord(
                startTime = Instant.parse("2026-08-20T15:29:00Z"),
                startZoneOffset = HealthConnectFixtureRecords.pacific,
                endTime = Instant.parse("2026-08-20T15:31:00Z"),
                endZoneOffset = HealthConnectFixtureRecords.pacific,
                samples = listOf(HeartRateRecord.Sample(Instant.parse("2026-08-20T15:30:00.251Z"), 72)),
                metadata = fixtures.metadata("record-heart-001"),
            ),
        )
        assertThat(corpus.identifiers.primaryOutput.identifier.value)
            .isEqualTo(identities.getValue("normative-corpus-heart-rate-source-output").string("value"))
        val sourceRecord = fixtures.scope.sourceRecord(
            HealthConnectContract.ADAPTER_ID,
            HealthConnectSourceType.HEART_RATE.token,
            fixtures.repositoryScope,
            "record-heart-001",
        )
        assertThat(sourceRecord.identifier).isEqualTo(corpus.identifiers.sourceRecord)
        assertThat(sourceRecord.artifact("heart-rate-samples", 0).identifier.value)
            .isEqualTo(identities.getValue("normative-corpus-heart-rate-source-artifact").string("value"))
    }

    @Test
    fun `every health-connect identity vector mints through the scope`() {
        vectors.array("identities").map { it.jsonObject }
            .filter { it.array("components").first().jsonPrimitive.content == HealthConnectContract.ADAPTER_ID }
            .forEach { vector ->
                val kind = requireNotNull(OpaqueIdentityKind.of(vector.string("identityKind")))
                val minted = fixtures.scope.mint(kind, vector.array("components").map { it.jsonPrimitive.content })
                assertThat(minted.identifier.value).isEqualTo(vector.string("value"))
            }
    }
}
