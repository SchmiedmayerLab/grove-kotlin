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
    fun `closed identity kinds arities and component forms exactly match the configured normative catalog`() {
        val catalog = ConformanceFixtures.configuredDirectory(ConformanceFixtures.CATALOG_PROPERTY)
        assumeTrue("The ${ConformanceFixtures.CATALOG_PROPERTY} lane is not configured.", catalog != null)
        val opaque = Json.parseToJsonElement(requireNotNull(catalog).readText()).jsonObject.objectValue("opaqueIdentity")
        val unsignedDecimal = opaque.objectValue("componentRequirements").array("unsignedDecimal")
            .map { it.jsonPrimitive.content }
        val kinds = opaque.array("identityKinds")
            .associate { element ->
                val kind = element.jsonObject
                kind.string("kind") to Pair(
                    kind.string("identifierRole"),
                    kind.array("components").map { it.jsonPrimitive.content },
                )
            }

        assertThat(OpaqueIdentityKind.entries.associate { it.code to (it.identifierRole.code to it.components) })
            .isEqualTo(kinds)
        assertThat(ExchangeContract.unsignedDecimalComponents).containsExactlyElementsIn(unsignedDecimal)
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
    fun `record identities mint every record output and artifact vector`() {
        val recordKinds = setOf(
            OpaqueIdentityKind.SOURCE_RECORD,
            OpaqueIdentityKind.SOURCE_OUTPUT,
            OpaqueIdentityKind.SOURCE_ARTIFACT,
            OpaqueIdentityKind.PROVIDER_RECORD,
            OpaqueIdentityKind.PROVIDER_OUTPUT,
            OpaqueIdentityKind.PROVIDER_ARTIFACT,
        )
        val extending = vectors.array("identities").map { it.jsonObject }
            .filter { OpaqueIdentityKind.of(it.string("identityKind")) in recordKinds }
        assertThat(extending.mapNotNull { OpaqueIdentityKind.of(it.string("identityKind")) }.toSet()).isEqualTo(recordKinds)
        extending.forEach { vector ->
            val kind = requireNotNull(OpaqueIdentityKind.of(vector.string("identityKind")))
            val components = vector.array("components").map { it.jsonPrimitive.content }
            val minted = recordFirst(kind, components)
            assertThat(minted.identifier.value).isEqualTo(vector.string("value"))
            assertThat(minted).isEqualTo(scope.mint(kind, components))
        }
    }

    @Test
    fun `record identities print neither the key nor the native record id`() {
        val repository = BusinessIdentifier(IdentifierSystem("https://accounts.example.org"), "patient")
        val source = scope.sourceRecord("health-connect", "StepsRecord", repository, "native-record-7")
        val provider = scope.providerRecord("withings", "measure", repository, "native-record-7")

        assertThat(source.toString()).isEqualTo("SourceRecordIdentity(identifier=${source.identifier})")
        assertThat(provider.toString()).isEqualTo("ProviderRecordIdentity(identifier=${provider.identifier})")
        assertThat(source.toString() + provider.toString()).doesNotContain("native-record-7")
    }

    @Test
    fun `record identities refuse a negative artifact part index`() {
        val repository = BusinessIdentifier(IdentifierSystem("https://accounts.example.org"), "patient")
        val source = scope.sourceRecord("health-connect", "StepsRecord", repository, "native-record-7")
        val provider = scope.providerRecord("withings", "measure", repository, "native-record-7")

        val sourceError = assertThrows(ExchangeIdentityException::class.java) { source.artifact("csv", -1) }.error
        val providerError = assertThrows(ExchangeIdentityException::class.java) { provider.artifact("csv", -1) }.error
        assertThat(sourceError).isEqualTo(ExchangeIdentityError.NonCanonicalPartIndex("source-artifact.part-index"))
        assertThat(providerError).isEqualTo(ExchangeIdentityError.NonCanonicalPartIndex("provider-artifact.part-index"))
        assertThat(source.artifact("csv", 0).identifier.value).startsWith("v0:")
    }

    @Test
    fun `component errors report their registered rules`() {
        val path = "source-artifact.part-index"
        val rules = mapOf(
            ExchangeIdentityError.NonScalarText(path) to ExchangeGraphRule.MOBILE_INPUT_TEXT_NOT_UNICODE_SCALAR,
            ExchangeIdentityError.EmptyComponent(path) to ExchangeGraphRule.MOBILE_INPUT_REQUIRED_METADATA_MISSING,
            ExchangeIdentityError.NonCanonicalPartIndex(path) to ExchangeGraphRule.MOBILE_INPUT_UNCLASSIFIED,
        )

        rules.forEach { (error, rule) -> assertThat(error.diagnostic).isEqualTo(rule.at(path)) }
    }

    @Test
    fun `rejects every vendored invalid opaque identity vector`() {
        val invalid = vectors.array("invalidIdentities")
        assertThat(invalid).isNotEmpty()
        invalid.forEach { element ->
            val vector = element.jsonObject
            val kind = requireNotNull(OpaqueIdentityKind.of(vector.string("identityKind")))
            val components = vector.array("components").map { it.jsonPrimitive.content }
            val thrown = assertThrows(vector.string("id"), IllegalArgumentException::class.java) { scope.mint(kind, components) }
            val error = (thrown as? ExchangeIdentityException)?.error
            when (val expected = vector.string("expectedError")) {
                "empty-component" -> assertThat(error).isInstanceOf(ExchangeIdentityError.EmptyComponent::class.java)
                "non-canonical-part-index" ->
                    assertThat(error).isEqualTo(ExchangeIdentityError.NonCanonicalPartIndex("${kind.code}.part-index"))
                "provider-kind-required" -> assertThat(error).isNull()
                else -> throw AssertionError("${vector.string("id")} expects the unknown error $expected")
            }
        }
    }

    @Test
    fun `every closed kind rejects missing excess empty and non-scalar components`() {
        OpaqueIdentityKind.entries.forEach { kind ->
            val exact = components(kind, if (kind.code.startsWith("provider-")) "withings" else "health-connect")
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
            val providerComponents = components(provider, "withings")
            val genericComponents = components(generic, "health-connect")
            assertThrows(IllegalArgumentException::class.java) { scope.mint(generic, providerComponents) }
            assertThrows(IllegalArgumentException::class.java) { scope.mint(provider, genericComponents) }
        }
        val repository = BusinessIdentifier(IdentifierSystem("https://accounts.example.org"), "patient")
        assertThrows(IllegalArgumentException::class.java) { scope.sourceRecord("withings", "measure", repository, "record-1") }
        assertThrows(IllegalArgumentException::class.java) { scope.providerRecord("health-connect", "StepsRecord", repository, "record-1") }
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
    fun `application and host facts derive the source-device tokens of the snapshot vectors`() {
        val identities = vectors.array("identities").associate { it.jsonObject.string("id") to it.jsonObject }
        val applications = mapOf(
            "device-snapshot-per-event" to ApplicationDevice(name = "Mobile Study", packageName = "com.example.app", version = "1.2.3"),
            "questionnaire-extraction-application-snapshot" to
                ApplicationDevice(name = "Client", packageName = "org.grovealliance.example.client", version = "1.4.0", build = "1402"),
        )
        applications.forEach { (id, application) ->
            val vector = identities.getValue(id)
            val components = vector.array("components").map { it.jsonPrimitive.content }
            val event = ExchangeEventIdentifier.from(BusinessIdentifier(IdentifierSystem(components[0]), components[1]))
            assertThat(application.sourceDeviceToken).isEqualTo(components.last())
            assertThat(scope.deviceSnapshot(event, DeviceSnapshotRole.APPLICATION, application.sourceDeviceToken).identifier.value)
                .isEqualTo(vector.string("value"))
        }
        assertThat(HostDevice(operatingSystemVersion = "16", manufacturer = "Example", modelNumber = "Phone One").sourceDeviceToken)
            .isEqualTo("Example|Phone One|16")
        assertThat(HostDevice(operatingSystemVersion = "16", name = "Pixel").sourceDeviceToken).isEqualTo("||16")
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

    private fun components(kind: OpaqueIdentityKind, first: String): List<String> =
        List(kind.componentCount) { index ->
            when {
                index == 0 -> first
                kind.components[index] in ExchangeContract.unsignedDecimalComponents -> "$index"
                else -> "component-$index"
            }
        }

    private fun recordFirst(kind: OpaqueIdentityKind, components: List<String>): RoledIdentifier {
        val (code, sourceType, system, value, nativeRecordId) = components
        val recordScope = BusinessIdentifier(IdentifierSystem(system), value)
        val source by lazy { scope.sourceRecord(code, sourceType, recordScope, nativeRecordId) }
        val provider by lazy { scope.providerRecord(code, sourceType, recordScope, nativeRecordId) }
        return when (kind) {
            OpaqueIdentityKind.SOURCE_RECORD -> source.identifier
            OpaqueIdentityKind.SOURCE_OUTPUT -> source.output(role = components[5], discriminator = components[6])
            OpaqueIdentityKind.SOURCE_ARTIFACT -> source.artifact(formatCode = components[5], partIndex = components[6].toLong())
            OpaqueIdentityKind.PROVIDER_RECORD -> provider.identifier
            OpaqueIdentityKind.PROVIDER_OUTPUT -> provider.output(role = components[5], discriminator = components[6])
            OpaqueIdentityKind.PROVIDER_ARTIFACT -> provider.artifact(formatCode = components[5], partIndex = components[6].toLong())
            else -> error("${kind.code} does not extend a record identity.")
        }
    }
}
