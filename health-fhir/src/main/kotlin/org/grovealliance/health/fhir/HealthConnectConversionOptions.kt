//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.health.fhir

import androidx.health.connect.client.records.metadata.Device
import org.grovealliance.fhir.ApplicationDevice
import org.grovealliance.fhir.BusinessIdentifier
import org.grovealliance.fhir.ConverterRole
import org.grovealliance.fhir.ExchangeEventContext
import org.grovealliance.fhir.ExchangeEventIdentifier
import org.grovealliance.fhir.ExchangeGraphNode
import org.grovealliance.fhir.GovernedSourceIdentifierDisclosurePolicy
import org.grovealliance.fhir.HostDevice
import org.grovealliance.fhir.OpaqueIdentityScope
import org.grovealliance.fhir.RecordingDevice
import org.grovealliance.fhir.RepositoryId
import org.grovealliance.fhir.RouteDisclosurePolicy
import org.grovealliance.fhir.StudyEnrollment
import org.grovealliance.fhir.Subject
import java.time.Instant

/** Whether the user-authored Health Connect session title and notes leave the device. */
public enum class UserAuthoredTextPolicy {
    /** The default: omit both as a data-minimization decision; the omission is chosen and never warns. */
    OMIT,

    /** Keep nonblank titles in the session-title extension and notes in `Observation.note.text`. */
    RETAIN,
}

/**
 * Resolves the governed per-unit token of the physical device a record names.
 *
 * Health Connect's `Metadata.device` carries no per-unit token, so the deployment decides which
 * devices it can identify stably; a null result omits the recording Device and warns.
 */
public fun interface RecordingDeviceResolver {
    public fun resolve(device: Device): RecordingDevice?

    public companion object {
        /** Never emits a recording Device; the default for Health Connect. */
        public val None: RecordingDeviceResolver = RecordingDeviceResolver { null }
    }
}

/** The adapter-specific choices one deployment makes for every Health Connect conversion; every disclosure defaults to omission. */
public data class HealthConnectConversionOptions(
    public val userAuthoredText: UserAuthoredTextPolicy = UserAuthoredTextPolicy.OMIT,
    public val recordingDevice: RecordingDeviceResolver = RecordingDeviceResolver.None,
    public val routeDisclosure: RouteDisclosurePolicy = RouteDisclosurePolicy.OMIT,
    public val nativeIdentifierDisclosure: GovernedSourceIdentifierDisclosurePolicy =
        GovernedSourceIdentifierDisclosurePolicy.Omit,
) {
    override fun toString(): String =
        "HealthConnectConversionOptions(userAuthoredText=$userAuthoredText, routeDisclosure=$routeDisclosure, " +
            "nativeIdentifierDisclosure=$nativeIdentifierDisclosure)"

    public companion object {
        /** Every policy omits and no recording device is resolved. */
        public val Default: HealthConnectConversionOptions = HealthConnectConversionOptions()
    }
}

/**
 * Everything one Health Connect conversion needs: the shared event context and the adapter options.
 *
 * The component constructor reads the host this process runs on and takes the conversion instant as
 * now, so a caller supplies only the subject, the event, the scope, the repository scope and the
 * application.
 */
public data class HealthConnectConversionContext(
    public val event: ExchangeEventContext,
    public val options: HealthConnectConversionOptions = HealthConnectConversionOptions.Default,
) {
    public constructor(
        subject: Subject,
        event: ExchangeEventIdentifier,
        identityScope: OpaqueIdentityScope,
        repositoryScope: BusinessIdentifier,
        application: ApplicationDevice,
        options: HealthConnectConversionOptions = HealthConnectConversionOptions.Default,
        host: HostDevice = HostDevice.current(),
        conversionInstant: Instant = Instant.now(),
        converterRole: ConverterRole = ConverterRole.Assembler,
        studies: List<StudyEnrollment> = emptyList(),
        repositoryIds: Map<ExchangeGraphNode, RepositoryId> = emptyMap(),
    ) : this(
        ExchangeEventContext(
            subject = subject,
            event = event,
            identityScope = identityScope,
            repositoryScope = repositoryScope,
            application = application,
            host = host,
            conversionInstant = conversionInstant,
            converterRole = converterRole,
            studies = studies,
            repositoryIds = repositoryIds,
        ),
        options,
    )

    init {
        val disclosure = options.nativeIdentifierDisclosure
        if (disclosure is GovernedSourceIdentifierDisclosurePolicy.Authorized) {
            require(disclosure.system !in event.identityScope.systems.all) {
                "A native identifier system requires its own repository namespace, never a Grove identity system."
            }
        }
        val uncarried = event.repositoryIds.keys intersect NODES_NEVER_CARRIED
        require(uncarried.isEmpty()) { "A Health Connect graph has no $uncarried node to assign a repository id to." }
    }

    override fun toString(): String = "HealthConnectConversionContext(event=$event, options=$options)"

    private companion object {
        /** The writer is named by package alone and no Health Connect record yields a source artifact. */
        val NODES_NEVER_CARRIED = setOf(ExchangeGraphNode.WRITER, ExchangeGraphNode.WRITER_HOST, ExchangeGraphNode.SOURCE_ARTIFACT)
    }
}
