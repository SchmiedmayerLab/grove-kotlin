//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.fhir

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.grovealliance.fhir.ConformanceFixtures.array
import org.grovealliance.fhir.ConformanceFixtures.objectValue
import org.grovealliance.fhir.ConformanceFixtures.scope
import org.grovealliance.fhir.ConformanceFixtures.string
import org.grovealliance.fhir.ConformanceFixtures.vectors
import org.junit.Assert.assertThrows
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.UUID

/** Executes the exact byte-level vectors published by catalog/exchange-protocol.json. */
class ExchangeProtocolVectorsTest {
    @Test
    fun `vendored vectors exactly match the configured normative catalog`() {
        val catalog = ConformanceFixtures.configuredDirectory(ConformanceFixtures.CATALOG_PROPERTY)
        assumeTrue("The ${ConformanceFixtures.CATALOG_PROPERTY} lane is not configured.", catalog != null)
        val external = Json.parseToJsonElement(requireNotNull(catalog).readText()).jsonObject.getValue("testVectors")

        assertThat(external).isEqualTo(vectors)
    }

    @Test
    fun `closed identity kinds and arities exactly match the configured normative catalog`() {
        val catalog = ConformanceFixtures.configuredDirectory(ConformanceFixtures.CATALOG_PROPERTY)
        assumeTrue("The ${ConformanceFixtures.CATALOG_PROPERTY} lane is not configured.", catalog != null)
        val kinds = Json.parseToJsonElement(requireNotNull(catalog).readText()).jsonObject
            .objectValue("opaqueIdentity").array("identityKinds")
            .associate { element ->
                val kind = element.jsonObject
                kind.string("kind") to Pair(
                    kind.string("identifierRole"),
                    kind.array("components").map { it.jsonPrimitive.content },
                )
            }

        assertThat(OpaqueIdentityKind.entries.associate { it.code to (it.identifierRole.code to it.components) })
            .isEqualTo(kinds)
    }

    @Test
    fun `derived identifier systems equal the vectors byte for byte`() {
        val expected = vectors.array("identitySystems").associate { row ->
            row.jsonObject.string("identityKind") to row.jsonObject.string("system")
        }

        val derived = ConformanceFixtures.derivedSystems

        assertThat(derived.opaque.all.entries.associate { (kind, system) -> kind.code to system.value })
            .isEqualTo(expected)
        assertThat(derived.event.value).isEqualTo(vectors.objectValue("event").string("system"))
        assertThat(derived.entryNode.value).isEqualTo(vectors.objectValue("entryNode").string("system"))
        assertThat(derived).isEqualTo(ConformanceFixtures.vectorSystems)
    }

    @Test
    fun `derived systems reject a root with a trailing slash query or fragment`() {
        listOf("https://study.example.org/fhir/", "https://study.example.org/fhir?x=1", "https://study.example.org/fhir#f")
            .forEach { root ->
                assertThrows(IllegalArgumentException::class.java) {
                    DeploymentIdentifierSystems.derived(IdentifierSystem(root), "store", EventSequence.of(1))
                }
            }
    }

    @Test
    fun `matches every normative opaque identity vector`() {
        val identities = vectors.array("identities")
        assertThat(identities.map { it.jsonObject.string("identityKind") }.toSet())
            .isEqualTo(OpaqueIdentityKind.entries.map(OpaqueIdentityKind::code).toSet())
        identities.forEach { element ->
            val vector = element.jsonObject
            val kind = requireNotNull(OpaqueIdentityKind.of(vector.string("identityKind")))
            val components = vector.array("components").map { it.jsonPrimitive.content }
            val minted = scope.mint(kind, components)
            assertThat(minted.identifier.value).isEqualTo(vector.string("value"))
            assertThat(minted.identifier.system).isEqualTo(ConformanceFixtures.vectorSystems.opaque[kind])
            assertThat(minted.role).isEqualTo(kind.identifierRole)
        }
    }

    @Test
    fun `rejects every vendored invalid opaque identity vector`() {
        val invalid = vectors.array("invalidIdentities")
        assertThat(invalid).isNotEmpty()
        invalid.forEach { element ->
            val vector = element.jsonObject
            val kind = requireNotNull(OpaqueIdentityKind.of(vector.string("identityKind")))
            val components = vector.array("components").map { it.jsonPrimitive.content }
            assertThrows(vector.string("id"), IllegalArgumentException::class.java) { scope.mint(kind, components) }
        }
    }

    @Test
    fun `every closed kind rejects missing excess empty and non-scalar components`() {
        OpaqueIdentityKind.entries.forEach { kind ->
            val exact = List(kind.componentCount) { index ->
                when {
                    index != 0 -> "component-$index"
                    kind.code.startsWith("provider-") -> "withings"
                    else -> "health-connect"
                }
            }
            assertThat(scope.mint(kind, exact).identifier.value).matches("v0:test-key:1:[A-Za-z0-9_-]{43}")
            assertThrows(IllegalArgumentException::class.java) { scope.mint(kind, exact.dropLast(1)) }
            assertThrows(IllegalArgumentException::class.java) { scope.mint(kind, exact + "excess") }
            val empty = assertThrows(ExchangeIdentityException::class.java) {
                scope.mint(kind, exact.toMutableList().apply { this[0] = "" })
            }
            assertThat(empty.error).isInstanceOf(ExchangeIdentityError.EmptyComponent::class.java)
            val surrogate = assertThrows(ExchangeIdentityException::class.java) {
                scope.mint(kind, exact.toMutableList().apply { this[0] = "prefix\ud800suffix" })
            }
            assertThat(surrogate.error.diagnostic.code).isEqualTo("mobile-input.text-not-unicode-scalar")
        }
    }

    @Test
    fun `provider coordinates require exact provider-specific identity domains`() {
        val pairs = listOf(
            OpaqueIdentityKind.SOURCE_RECORD to OpaqueIdentityKind.PROVIDER_RECORD,
            OpaqueIdentityKind.SOURCE_OUTPUT to OpaqueIdentityKind.PROVIDER_OUTPUT,
            OpaqueIdentityKind.SOURCE_ARTIFACT to OpaqueIdentityKind.PROVIDER_ARTIFACT,
        )
        pairs.forEach { (generic, provider) ->
            val providerComponents = List(provider.componentCount) { if (it == 0) "withings" else "component-$it" }
            val genericComponents = List(generic.componentCount) { if (it == 0) "health-connect" else "component-$it" }
            assertThrows(IllegalArgumentException::class.java) { scope.mint(generic, providerComponents) }
            assertThrows(IllegalArgumentException::class.java) { scope.mint(provider, genericComponents) }
        }
    }

    @Test
    fun `matches the normative event entry-node and fullUrl vectors`() {
        val eventVector = vectors.objectValue("event")
        val event = ExchangeEventIdentifier(
            IdentifierSystem(eventVector.string("system")),
            UUID.fromString(eventVector.string("producerInstance")),
            EventSequence(eventVector.string("sequence")),
        )
        assertThat(event.identifier.identifier.value).isEqualTo(eventVector.string("value"))
        assertThat(ExchangeEventIdentifier.from(event.identifier.identifier)).isEqualTo(event)

        val nodeVector = vectors.objectValue("entryNode")
        val node = EntryNodeKey(IdentifierSystem(nodeVector.string("system")), event, "conversion-provenance", 0)
        assertThat(node.identifier.identifier.value).isEqualTo(nodeVector.string("value"))
        assertThat(node.identifier.fullUrl).isEqualTo(nodeVector.string("fullUrl"))

        vectors.array("fullUrls").forEach { element ->
            val vector = element.jsonObject
            val identifier = BusinessIdentifier(IdentifierSystem(vector.string("system")), vector.string("value"))
            assertThat(identifier.fullUrl).isEqualTo(vector.string("fullUrl"))
        }
    }

    @Test
    fun `framing counts UTF-8 bytes and rejects unpaired surrogates`() {
        assertThat(ExchangeProtocol.frameFields(listOf(""))).isEqualTo(byteArrayOf(0, 0, 0, 0))
        assertThat(ExchangeProtocol.frameFields(listOf("😀")))
            .isEqualTo(byteArrayOf(0, 0, 0, 4, 0xf0.toByte(), 0x9f.toByte(), 0x98.toByte(), 0x80.toByte()))
        assertThat(ExchangeProtocol.frameFields(listOf("a", "bc")))
            .isNotEqualTo(ExchangeProtocol.frameFields(listOf("ab", "c")))
        assertThrows(IllegalArgumentException::class.java) { ExchangeProtocol.frameFields(listOf("prefix\ud800suffix")) }
    }

    @Test
    fun `the published conformance key is rejected outside conformance testing`() {
        assertThrows(IllegalArgumentException::class.java) {
            OpaqueIdentityScope(
                systems = ConformanceFixtures.vectorSystems,
                keyId = "test-key",
                epoch = EventSequence.of(1),
                key = javax.crypto.spec.SecretKeySpec(ByteArray(32) { it.toByte() }, "HmacSHA256"),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            OpaqueIdentityScope(
                systems = ConformanceFixtures.vectorSystems,
                keyId = "test-key",
                epoch = EventSequence.of(1),
                key = javax.crypto.spec.SecretKeySpec(ByteArray(16) { (it + 1).toByte() }, "HmacSHA256"),
            )
        }
        assertThat(scope.toString()).isEqualTo("OpaqueIdentityScope(keyId=test-key, epoch=1)")
    }

    @Test
    fun `business identifiers redact their value and compare by pair`() {
        val system = IdentifierSystem("https://example.org/system")
        val first = BusinessIdentifier(system, "café|東京")
        assertThat(first.toString()).doesNotContain("café")
        assertThat(first).isEqualTo(BusinessIdentifier(system, "café|東京"))
        assertThat(first.fullUrl).isEqualTo(BusinessIdentifier.from(first.toFhir()).fullUrl)
        assertThrows(IllegalArgumentException::class.java) { IdentifierSystem("not-absolute") }
        assertThrows(IllegalArgumentException::class.java) { IdentifierSystem("https://例.example/識別子") }
        assertThrows(IllegalArgumentException::class.java) { BusinessIdentifier(system, "\ud800") }
    }
}
