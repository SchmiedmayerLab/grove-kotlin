//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.fhir

import java.time.Instant

/**
 * Everything one exchange event shares across every adapter: who, which event, under which identities.
 *
 * The event identifier lives under the scope's event system, and the entry-node keys under its
 * entry-node system; [host] is explicit here because this module cannot read the host it runs on.
 */
public data class ExchangeEventContext(
    public val subject: Subject,
    public val event: ExchangeEventIdentifier,
    public val identityScope: OpaqueIdentityScope,
    public val repositoryScope: BusinessIdentifier,
    public val application: ApplicationDevice,
    public val host: HostDevice,
    public val conversionInstant: Instant = Instant.now(),
    public val converterRole: ConverterRole = ConverterRole.Assembler,
    public val studies: List<StudyEnrollment> = emptyList(),
    public val repositoryIds: Map<ExchangeGraphNode, RepositoryId> = emptyMap(),
) {
    /** The system every entry-node key of this event is minted under. */
    public val entryNodeIdentifierSystem: IdentifierSystem
        get() = identityScope.systems.entryNode

    init {
        require(event.system == identityScope.systems.event) {
            "The event identifier must use the identity scope's event system."
        }
        require(conversionInstant in FHIR_INSTANT_RANGE) {
            "The conversion instant must have a four-digit FHIR year in the range 0001 through 9999."
        }
        require(studies.map { it.study }.toSet().size == studies.size) {
            "Every study enrollment names a distinct study."
        }
    }

    override fun toString(): String =
        "ExchangeEventContext(subject=$subject, event=$event, converterRole=$converterRole, " +
            "conversionInstant=$conversionInstant, studies=${studies.size})"

    public companion object {
        /** The instants a FHIR R4 instant or dateTime can state: four-digit years 0001 through 9999. */
        public val FHIR_INSTANT_RANGE: ClosedRange<Instant> =
            Instant.parse("0001-01-01T00:00:00Z")..Instant.parse("9999-12-31T23:59:59.999999999Z")
    }
}

/** Every identity one active exchange graph carries, so a caller never re-reads them from the Bundle. */
public data class ExchangeGraphIdentifiers(
    public val event: RoledIdentifier,
    public val sourceRecord: RoledIdentifier,
    public val primaryOutput: RoledIdentifier,
    public val applicationSnapshot: RoledIdentifier,
    public val hostSnapshot: RoledIdentifier,
    public val provenance: RoledIdentifier,
    public val childOutputs: List<RoledIdentifier>,
    public val sourceArtifact: RoledIdentifier?,
    public val recordingDeviceSnapshot: RoledIdentifier?,
    public val writerSnapshot: RoledIdentifier?,
    public val writerHostSnapshot: RoledIdentifier?,
) {
    /** Every output identity in Bundle order: the primary first, then its children. */
    public val outputs: List<RoledIdentifier>
        get() = listOf(primaryOutput) + childOutputs

    override fun toString(): String =
        "ExchangeGraphIdentifiers(event=$event, sourceRecord=$sourceRecord, primaryOutput=$primaryOutput, " +
            "childOutputs=${childOutputs.size})"
}

/** The conversions a batch produced and the records it refused, in input order. */
public data class ConversionBatch<Conversion, Failure>(
    public val conversions: List<Conversion>,
    public val failures: List<Failure>,
) {
    override fun toString(): String = "ConversionBatch(conversions=${conversions.size}, failures=${failures.size})"
}
