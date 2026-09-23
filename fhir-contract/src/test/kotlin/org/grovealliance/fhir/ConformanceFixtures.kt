//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.fhir

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.time.Instant
import java.util.UUID
import javax.crypto.spec.SecretKeySpec

/** The vendored normative vectors and the conformance scope that reproduces them. */
internal object ConformanceFixtures {
    const val ROOT = "https://study.example.org/fhir"
    const val CATALOG_PROPERTY = "grove.exchange-protocol.catalog"
    const val EXCHANGE_CORPUS_PROPERTY = "grove.mobile-exchange.corpus-directory"
    const val RECEIVER_CORPUS_PROPERTY = "grove.receiver-lifecycle.corpus-directory"

    val vectors: JsonObject by lazy { resourceJson("/grove-exchange-protocol-test-vectors.json").jsonObject }

    val keyId: String get() = vectors.string("keyId")
    val epoch: EventSequence get() = EventSequence(vectors.string("epoch"))

    val vectorSystems: DeploymentIdentifierSystems by lazy {
        DeploymentIdentifierSystems(
            opaque = OpaqueIdentitySystems(
                vectors.array("identitySystems").associate { row ->
                    val kind = requireNotNull(OpaqueIdentityKind.of(row.jsonObject.string("identityKind")))
                    kind to IdentifierSystem(row.jsonObject.string("system"))
                },
            ),
            event = IdentifierSystem(vectors.objectValue("event").string("system")),
            entryNode = IdentifierSystem(vectors.objectValue("entryNode").string("system")),
        )
    }

    val derivedSystems: DeploymentIdentifierSystems by lazy {
        DeploymentIdentifierSystems.derived(IdentifierSystem(ROOT), keyId, epoch)
    }

    @OptIn(ConformanceTestingApi::class)
    val scope: OpaqueIdentityScope by lazy {
        OpaqueIdentityScope.forConformanceTesting(
            systems = vectorSystems,
            keyId = keyId,
            epoch = epoch,
            key = SecretKeySpec(vectors.string("keyHex").hexBytes(), "HmacSHA256"),
        )
    }

    fun eventContext(
        sequence: Long = 1,
        subject: Subject = Subject.Logical(BusinessIdentifier(IdentifierSystem("$ROOT/identifiers/participant"), "participant-001")),
        converterRole: ConverterRole = ConverterRole.Assembler,
        studies: List<StudyEnrollment> = emptyList(),
        repositoryIds: Map<ExchangeGraphNode, RepositoryId> = emptyMap(),
    ): ExchangeEventContext = ExchangeEventContext(
        subject = subject,
        event = ExchangeEventIdentifier(
            derivedSystems.event,
            UUID.fromString("1f5c58aa-6ec6-4e79-a682-829a9debd3f5"),
            EventSequence.of(sequence),
        ),
        identityScope = scope,
        repositoryScope = BusinessIdentifier(IdentifierSystem("urn:uuid:1f5c58aa-6ec6-4e79-a682-829a9debd3f5"), "default"),
        application = ApplicationDevice(name = "Mobile Study", packageName = "com.example.app", version = "1.2.3"),
        host = HostDevice(operatingSystemVersion = "16", manufacturer = "Example", modelNumber = "Phone One"),
        conversionInstant = Instant.parse("2026-08-20T17:30:02Z"),
        converterRole = converterRole,
        studies = studies,
        repositoryIds = repositoryIds,
    )

    fun configuredDirectory(property: String): File? =
        System.getProperty(property)?.takeIf(String::isNotBlank)?.let(::File)

    fun resourceText(name: String): String =
        requireNotNull(ConformanceFixtures::class.java.getResourceAsStream(name)) { "Missing vendored $name" }
            .bufferedReader(Charsets.UTF_8).use { it.readText() }

    fun resourceJson(name: String): JsonElement = Json.parseToJsonElement(resourceText(name))

    fun JsonObject.string(name: String): String = getValue(name).jsonPrimitive.content

    fun JsonObject.array(name: String): JsonArray = getValue(name).jsonArray

    fun JsonObject.objectValue(name: String): JsonObject = getValue(name).jsonObject

    private fun String.hexBytes(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    /** Applies one RFC 6902 patch operation of the shared corpus. */
    fun JsonElement.applyPatch(patch: JsonObject): JsonElement {
        val operation = patch.string("op")
        val tokens = patch.string("path").split('/').drop(1).map { it.replace("~1", "/").replace("~0", "~") }
        return patched(tokens, operation, patch["value"])
    }

    private fun JsonElement.patched(tokens: List<String>, operation: String, value: JsonElement?): JsonElement {
        require(tokens.isNotEmpty()) { "The shared corpus does not patch the JSON document root." }
        val head = tokens.first()
        val tail = tokens.drop(1)
        return when (this) {
            is JsonObject -> JsonObject(toMutableMap().apply { patchMember(head, tail, operation, value) })
            is JsonArray -> JsonArray(toMutableList().apply { patchItem(head, tail, operation, value) })
            else -> error("Corpus JSON Pointer traverses a primitive at /${tokens.joinToString("/")}.")
        }
    }

    private fun MutableMap<String, JsonElement>.patchMember(head: String, tail: List<String>, operation: String, value: JsonElement?) {
        if (tail.isNotEmpty()) {
            put(head, getValue(head).patched(tail, operation, value))
            return
        }
        when (operation) {
            "add", "replace" -> put(head, requireNotNull(value))
            "remove" -> requireNotNull(remove(head))
            else -> error("Unsupported corpus patch operation $operation")
        }
    }

    private fun MutableList<JsonElement>.patchItem(head: String, tail: List<String>, operation: String, value: JsonElement?) {
        if (tail.isNotEmpty()) {
            set(head.toInt(), get(head.toInt()).patched(tail, operation, value))
            return
        }
        when (operation) {
            "add" -> if (head == "-") add(requireNotNull(value)) else add(head.toInt(), requireNotNull(value))
            "replace" -> set(head.toInt(), requireNotNull(value))
            "remove" -> removeAt(head.toInt())
            else -> error("Unsupported corpus patch operation $operation")
        }
    }
}
