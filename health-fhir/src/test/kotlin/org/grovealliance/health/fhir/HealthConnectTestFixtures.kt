//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

@file:OptIn(ConformanceTestingApi::class)

package org.grovealliance.health.fhir

import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.testing.populatedWithTestValues
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.grovealliance.fhir.ApplicationDevice
import org.grovealliance.fhir.BusinessIdentifier
import org.grovealliance.fhir.ConformanceTestingApi
import org.grovealliance.fhir.ConverterRole
import org.grovealliance.fhir.DeploymentIdentifierSystems
import org.grovealliance.fhir.EventSequence
import org.grovealliance.fhir.ExchangeEventIdentifier
import org.grovealliance.fhir.HostDevice
import org.grovealliance.fhir.IdentifierSystem
import org.grovealliance.fhir.OpaqueIdentityScope
import org.grovealliance.fhir.StudyEnrollment
import org.grovealliance.fhir.Subject
import java.io.File
import java.time.Instant
import java.util.UUID
import javax.crypto.spec.SecretKeySpec

/** The conformance deployment of the normative vectors: its systems, key, subject and application. */
internal object HealthConnectTestFixtures {
    const val ROOT = "https://study.example.org/fhir"
    const val WRITER_PACKAGE = "com.example.source"

    val vectors: JsonObject by lazy {
        Json.parseToJsonElement(resourceText("/grove-exchange-protocol-test-vectors.json")).jsonObject
    }
    val keyId: String by lazy { vectors.string("keyId") }
    val epoch: EventSequence by lazy { EventSequence(vectors.string("epoch")) }
    val systems: DeploymentIdentifierSystems by lazy { DeploymentIdentifierSystems.derived(IdentifierSystem(ROOT), keyId, epoch) }
    val scope: OpaqueIdentityScope by lazy {
        OpaqueIdentityScope.forConformanceTesting(
            systems = systems,
            keyId = keyId,
            epoch = epoch,
            key = SecretKeySpec(vectors.string("keyHex").hexBytes(), "HmacSHA256"),
        )
    }

    // The official validator refuses example.org URLs, so the exported corpus lives under the conformance root.
    const val CONFORMANCE_ROOT = "https://conformance.grovealliance.org/fhir"
    val conformanceScope: OpaqueIdentityScope by lazy {
        OpaqueIdentityScope.forConformanceTesting(
            systems = DeploymentIdentifierSystems.derived(IdentifierSystem(CONFORMANCE_ROOT), keyId, epoch),
            keyId = keyId,
            epoch = epoch,
            key = SecretKeySpec(vectors.string("keyHex").hexBytes(), "HmacSHA256"),
        )
    }
    val conformanceSubject = BusinessIdentifier(IdentifierSystem("$CONFORMANCE_ROOT/identifiers/participant"), "participant-001")
    val producerInstance: UUID = UUID.fromString("1f5c58aa-6ec6-4e79-a682-829a9debd3f5")
    val repositoryScope = BusinessIdentifier(IdentifierSystem("urn:uuid:1f5c58aa-6ec6-4e79-a682-829a9debd3f5"), "default")
    val subject = Subject.Logical(BusinessIdentifier(IdentifierSystem("$ROOT/identifiers/participant"), "participant-001"))
    val application = ApplicationDevice(name = "Mobile Study", packageName = "com.example.app", version = "1.2.3")
    val host = HostDevice(operatingSystemVersion = "16", manufacturer = "Example", modelNumber = "Phone One")
    val watch = Device(manufacturer = "Example Device Company", model = "Study Watch", type = Device.TYPE_WATCH)
    val conversionInstant: Instant = Instant.parse("2026-08-19T18:00:00Z")
    val lastModified: Instant = Instant.parse("2026-08-19T17:30:01Z")
    val converter = HealthConnectConverter()

    fun event(sequence: Long, scope: OpaqueIdentityScope = this.scope): ExchangeEventIdentifier =
        ExchangeEventIdentifier(scope.systems.event, producerInstance, EventSequence.of(sequence))

    fun context(
        sequence: Long = 1,
        conversionInstant: Instant = this.conversionInstant,
        options: HealthConnectConversionOptions = HealthConnectConversionOptions(UserAuthoredTextPolicy.RETAIN),
        subject: Subject = this.subject,
        converterRole: ConverterRole = ConverterRole.Assembler,
        studies: List<StudyEnrollment> = emptyList(),
        scope: OpaqueIdentityScope = this.scope,
    ): HealthConnectConversionContext = HealthConnectConversionContext(
        subject = subject,
        event = event(sequence, scope),
        identityScope = scope,
        repositoryScope = repositoryScope,
        application = application,
        options = options,
        host = host,
        conversionInstant = conversionInstant,
        converterRole = converterRole,
        studies = studies,
    )

    fun metadata(id: String, lastModified: Instant = this.lastModified, base: Metadata = Metadata.autoRecorded(watch)): Metadata =
        base.populatedWithTestValues(id = id, dataOrigin = DataOrigin(WRITER_PACKAGE), lastModifiedTime = lastModified)

    fun converted(record: Record, context: HealthConnectConversionContext = context()): HealthConnectConversion =
        when (val result = converter.convert(record, context)) {
            is HealthConnectConversionResult.Converted -> result.conversion
            else -> throw AssertionError("Expected a conversion, got $result")
        }

    fun configuredFile(property: String): File? = System.getProperty(property)?.takeIf(String::isNotBlank)?.let(::File)

    fun resourceText(name: String): String =
        requireNotNull(HealthConnectTestFixtures::class.java.getResourceAsStream(name)) { "Missing vendored $name" }
            .bufferedReader(Charsets.UTF_8).use { it.readText() }

    fun JsonObject.string(name: String): String = getValue(name).jsonPrimitive.content

    fun JsonObject.array(name: String): JsonArray = getValue(name).jsonArray

    fun JsonObject.objectValue(name: String): JsonObject = getValue(name).jsonObject

    private fun String.hexBytes(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
