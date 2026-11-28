//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.health.fhir

import androidx.health.connect.client.records.Record
import org.grovealliance.fhir.contract.BusinessIdentifier
import org.grovealliance.fhir.contract.ExchangeProducer
import org.grovealliance.fhir.contract.IdentifierSystem
import org.grovealliance.fhir.contract.InternalGroveFhirApi
import org.grovealliance.health.fhir.internal.HealthConnectCatalog
import org.grovealliance.health.fhir.internal.RecordConversion
import java.time.Instant

/**
 * Turns Health Connect Records into exchange records for one deployment, participant and Health
 * Connect repository, named by [repositoryScope].
 *
 * It never reads Health Connect: the caller passes Records it read. A Record that can't be converted
 * is reported as a refusal rather than ending the call, and an exact redelivery reproduces the same
 * record versions while nothing the record states changed.
 */
class HealthConnectFhirExporter private constructor(
    producer: ExchangeProducer,
    repositoryScope: BusinessIdentifier,
    options: Options,
) {
    private val conversion = RecordConversion(
        producer = producer,
        repositoryScope = repositoryScope,
        options = options,
    )

    /**
     * Why an exporter can't be configured.
     */
    sealed class ConfigurationError(message: String) : Exception(message) {

        /**
         * The disclosed native identifier's [system] is one of the deployment's own identity systems.
         */
        data class ReservedNativeIdentifierSystem(val system: IdentifierSystem) :
            ConfigurationError("$system is reserved for graph identities.")

        /**
         * The writer application [system] is not the deployment's own: it is one of the deployment's
         * identity systems, or the shared system naming the data-origin application.
         */
        data class ReservedWriterApplicationSystem(val system: IdentifierSystem) :
            ConfigurationError("$system is not a deployment-owned system for writer applications.")
    }

    /**
     * What the exporter carries of a Record beyond its measurements.
     *
     * @property userText whether a session's title and note, which a person wrote, travel with it
     * @property writerApplicationSystem the deployment's own system naming Android application packages,
     *   under which the application that assigned a Record's client record id is named in its writer
     *   identity
     * @property nativeIdentifierSystem the deployment's own system under which a Record's Health Connect
     *   id is disclosed, on the one output the contract designates as its primary representation and on
     *   its retraction, so a receiver can dedupe copies of it; `null` discloses nothing
     */
    data class Options(
        val userText: UserText,
        val writerApplicationSystem: IdentifierSystem,
        val nativeIdentifierSystem: IdentifierSystem? = null,
    ) {

        /**
         * Whether user-authored session text travels.
         */
        enum class UserText {
            RETAIN,
            OMIT,
        }
    }

    companion object {

        /**
         * An exporter for [producer]'s participant and the Health Connect store [repositoryScope] names.
         */
        fun create(
            producer: ExchangeProducer,
            repositoryScope: BusinessIdentifier,
            options: Options,
        ): Result<HealthConnectFhirExporter> {
            val system = options.nativeIdentifierSystem

            @OptIn(InternalGroveFhirApi::class)
            val reserved = producer.identityScope.systems.all
            if (system != null && system in reserved) {
                return Result.failure(ConfigurationError.ReservedNativeIdentifierSystem(system = system))
            }
            val writer = options.writerApplicationSystem
            if (writer in reserved || writer.rawValue == HealthConnectCatalog.PACKAGE_NAME_SYSTEM) {
                return Result.failure(ConfigurationError.ReservedWriterApplicationSystem(system = writer))
            }
            return Result.success(
                HealthConnectFhirExporter(
                    producer = producer,
                    repositoryScope = repositoryScope,
                    options = options,
                ),
            )
        }
    }

    /**
     * One export per Record, in input order, each converted at [instant], the moment of conversion.
     */
    fun export(records: List<Record>, instant: Instant): List<HealthConnectExport> = records.map { record ->
        conversion.export(record = record, instant = instant)
    }

    /**
     * One retraction per deletion, in input order, each recorded at [instant]. A retraction names the
     * deleted Record's whole source record, recomputed from its id and type, so nothing from the
     * original export needs to be kept.
     */
    fun retract(
        deletions: List<HealthConnectDeletion>,
        instant: Instant,
    ): List<HealthConnectRetraction> = deletions.map { deletion ->
        HealthConnectRetraction(
            deletion = deletion,
            outcome = conversion.retract(deletion = deletion, instant = instant),
        )
    }
}
