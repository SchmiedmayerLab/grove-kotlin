//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.health.fhir

import android.content.Context
import androidx.health.connect.client.records.Record
import org.grovealliance.fhir.ApplicationDevice
import org.grovealliance.fhir.BusinessIdentifier
import org.grovealliance.fhir.DeploymentIdentifierSystems
import org.grovealliance.fhir.EventSequence
import org.grovealliance.fhir.ExchangeEventIdentifier
import org.grovealliance.fhir.IdentifierSystem
import org.grovealliance.fhir.OpaqueIdentityScope
import org.grovealliance.fhir.ProducerDiagnostic
import org.grovealliance.fhir.RetractionEvent
import org.grovealliance.fhir.RoledIdentifier
import org.grovealliance.fhir.Subject
import java.time.Instant
import java.util.UUID
import javax.crypto.SecretKey

/** The three code blocks of the module README and its retraction block, compiled so they cannot drift from the API. */
internal object HealthConnectWalkthrough {
    data class Installation(
        val systems: DeploymentIdentifierSystems,
        val scope: OpaqueIdentityScope,
        val application: ApplicationDevice,
    )

    /** Once per installation: derive the systems, create the scope and read the application. */
    fun install(context: Context, identityKey: SecretKey, keyId: String, keyEpoch: EventSequence): Installation {
        val systems = DeploymentIdentifierSystems.derived(
            root = IdentifierSystem("https://study.example.org/fhir"),
            keyId = keyId,
            epoch = keyEpoch,
        )
        val scope = OpaqueIdentityScope(systems = systems, keyId = keyId, epoch = keyEpoch, key = identityKey)
        val application = ApplicationDevice.from(context)
        return Installation(systems, scope, application)
    }

    /** Per export: reserve the next sequence and create the context, using every default. */
    fun exportContext(
        installation: Installation,
        subject: Subject,
        repositoryScope: BusinessIdentifier,
        producerInstance: UUID,
        nextSequence: EventSequence,
    ): HealthConnectConversionContext {
        val (systems, scope, application) = installation
        val event = ExchangeEventIdentifier(systems.event, producerInstance, nextSequence)
        return HealthConnectConversionContext(
            subject = subject,
            event = event,
            identityScope = scope,
            repositoryScope = repositoryScope,
            application = application,
        )
    }

    /** Convert one record and hand the Bundle to the uploader. */
    fun convert(
        record: Record,
        context: HealthConnectConversionContext,
        upload: (ByteArray) -> Unit,
        log: (ProducerDiagnostic) -> Unit,
    ) {
        when (val result = HealthConnectConverter().convert(record, context)) {
            is HealthConnectConversionResult.Converted -> upload(result.conversion.graph.json.toByteArray())
            is HealthConnectConversionResult.NoOutput -> Unit
            is HealthConnectConversionResult.Failed -> log(result.failure.diagnostic)
        }
    }

    /** Retract a deleted steps record under the context it was exported with. */
    fun retract(
        id: String,
        context: HealthConnectConversionContext,
        nextContext: HealthConnectConversionContext,
        sourceRecord: RoledIdentifier,
    ): RetractionEvent {
        val targets = HealthConnectConverter().retractionTargets(HealthConnectSourceRecord(id, HealthConnectSourceType.STEPS), context)
        return RetractionEvent(targets, nextContext.event, sourceRecord, retractedAt = Instant.now())
    }
}
