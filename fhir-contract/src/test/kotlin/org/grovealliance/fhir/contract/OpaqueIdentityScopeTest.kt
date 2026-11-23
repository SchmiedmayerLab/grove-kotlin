//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.fhir.contract

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import javax.crypto.spec.SecretKeySpec

class OpaqueIdentityScopeTest {
    private val catalog = ExchangeProtocolCatalog()
    private val root = IdentifierSystem.create(rawValue = "https://study.example.org/fhir").getOrThrow()
    private val epoch = KeyEpoch.parse(text = catalog.epoch).getOrThrow()
    private val systems = DeploymentIdentifierSystems.derived(
        root = root,
        keyId = catalog.keyId,
        epoch = epoch,
    ).getOrThrow()
    private val scope = OpaqueIdentityScope.forConformanceTesting(
        systems = systems,
        keyId = catalog.keyId,
        epoch = epoch,
    ).getOrThrow()

    @Test
    fun `derives the recommended system of every identity kind`() {
        // given
        val expected = catalog.vectors(name = "identitySystems").associate { it.text(name = "identityKind") to it.text(name = "system") }

        // when
        val derived = OpaqueIdentityKind.entries.associate { it.rawValue to systems[it].rawValue }

        // then
        assertThat(derived).isEqualTo(expected)
    }

    @Test
    fun `mints every identity vector`() {
        // given
        val vectors = catalog.vectors(name = "identities")

        // when
        val minted = vectors.map { vector ->
            val kind = requireNotNull(OpaqueIdentityKind.fromRawValue(rawValue = vector.text(name = "identityKind")))
            kind to scope.identifier(kind = kind, components = vector.texts(name = "components")).getOrThrow()
        }

        // then
        assertThat(vectors).hasSize(70)
        assertThat(minted.map { (_, identifier) -> identifier.identifier.value }).isEqualTo(vectors.map { it.text(name = "value") })
        assertThat(minted.all { (kind, identifier) -> identifier.identifier.system == systems[kind] }).isTrue()
    }

    @Test
    fun `refuses every invalid identity vector with its error`() {
        // given
        val vectors = catalog.vectors(name = "invalidIdentities")

        // when
        val errors = vectors.associate { vector ->
            val kind = requireNotNull(OpaqueIdentityKind.fromRawValue(rawValue = vector.text(name = "identityKind")))
            val error = scope.identifier(kind = kind, components = vector.texts(name = "components")).exceptionOrNull()
            vector.text(name = "expectedError") to error
        }

        // then
        assertThat(vectors).hasSize(8)
        assertThat(errors.mapValues { (_, error) -> error?.let { errorCode(error = it) } }).isEqualTo(errors.keys.associateWith { it })
    }

    @Test
    fun `fingerprints the conformance key per subject`() {
        // given
        val key = SecretKeySpec(catalog.keyHex.chunked(2).map { it.toInt(radix = 16).toByte() }.toByteArray(), "HmacSHA256")

        // when / then
        catalog.vectors(name = "keyFingerprint").forEach { vector ->
            val subject = BusinessIdentifier.create(
                system = IdentifierSystem.create(rawValue = vector.text(name = "subjectSystem")).getOrThrow(),
                value = vector.text(name = "subjectValue"),
            ).getOrThrow()
            val fingerprint = OpaqueIdentityScope.keyFingerprint(key = key, subject = subject).getOrThrow()
            assertThat(fingerprint).isEqualTo(vector.text(name = "fingerprint"))
            assertThat(OpaqueIdentityScope.recommendedKeyId(prefix = "s", key = key, subject = subject).getOrThrow())
                .isEqualTo("s" + vector.text(name = "keyIdSuffix"))
        }
    }

    @Test
    fun `refuses the published conformance key and short keys outside conformance tests`() {
        // given
        val published = SecretKeySpec(ByteArray(32) { it.toByte() }, "HmacSHA256")
        val short = SecretKeySpec(ByteArray(16) { 1 }, "HmacSHA256")

        // when
        val publishedResult = OpaqueIdentityScope.create(systems = systems, keyId = "k", epoch = epoch, key = published)
        val shortResult = OpaqueIdentityScope.create(systems = systems, keyId = "k", epoch = epoch, key = short)

        // then
        assertThat(publishedResult.exceptionOrNull()).isEqualTo(ExchangeIdentityError.PublishedConformanceKeyProhibited)
        assertThat(shortResult.exceptionOrNull()).isEqualTo(ExchangeIdentityError.KeyTooShort(actualBytes = 16))
    }

    @Test
    fun `mints outputs that extend their source record`() {
        // given
        val repository = BusinessIdentifier.create(
            system = IdentifierSystem.create(rawValue = "urn:uuid:1f5c58aa-6ec6-4e79-a682-829a9debd3f5").getOrThrow(),
            value = "default",
        ).getOrThrow()
        val record = scope.sourceRecord(
            adapterId = "health-connect",
            sourceType = "HeartRateRecord",
            repositoryScope = repository,
            nativeRecordId = "record-heart-001",
        ).getOrThrow()

        // when
        val output = record.output(role = "sample", discriminator = "2026-08-19T10:30:00.000000000Z|0").getOrThrow()

        // then
        assertThat(output.identifier.value).isEqualTo("v0:test-key:1:MYPFjAMsSt0suOqpN29y_KjG__sagIpCbYKAfVKx6ck")
        assertThat(output.role).isEqualTo(GroveIdentifierRole.SOURCE_OUTPUT)
        assertThat(record.toString()).doesNotContain("record-heart-001")
    }

    @Test
    fun `refuses an output role that is not a code token`() {
        // given
        val repository = BusinessIdentifier.create(system = root, value = "default").getOrThrow()
        val record = scope.sourceRecord(
            adapterId = "health-connect",
            sourceType = "HeartRateRecord",
            repositoryScope = repository,
            nativeRecordId = "record",
        ).getOrThrow()

        // when
        val result = record.output(role = "Sample", discriminator = "0")

        // then
        assertThat(result.exceptionOrNull()).isEqualTo(
            ExchangeIdentityError.InvalidCodeToken(
                field = "source-output.output-role",
                value = "Sample",
            ),
        )
    }

    private fun errorCode(error: Throwable): String = when (error) {
        is ExchangeIdentityError.EmptyComponent -> "empty-component"
        is ExchangeIdentityError.ProviderKindRequired -> "provider-kind-required"
        is ExchangeIdentityError.NonCanonicalPartIndex -> "non-canonical-part-index"
        is ExchangeIdentityError.NonCanonicalDigest -> "non-canonical-digest"
        else -> error.toString()
    }
}
