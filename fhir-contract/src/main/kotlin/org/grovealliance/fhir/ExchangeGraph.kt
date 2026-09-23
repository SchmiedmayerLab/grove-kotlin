//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.fhir

import org.hl7.fhir.r4.formats.IParser
import org.hl7.fhir.r4.formats.JsonParser
import org.hl7.fhir.r4.model.Bundle
import org.hl7.fhir.r4.model.CodeType
import org.hl7.fhir.r4.model.Device
import org.hl7.fhir.r4.model.Identifier
import org.hl7.fhir.r4.model.Observation
import org.hl7.fhir.r4.model.Provenance
import org.hl7.fhir.r4.model.ResourceType

/** The two immutable exchange events the protocol defines. */
public enum class ExchangeGraphKind(public val profile: String) {
    ACTIVE(ExchangeContract.MOBILE_EXCHANGE_BUNDLE_PROFILE),
    RETRACTION(ExchangeContract.MOBILE_RETRACTION_BUNDLE_PROFILE),
}

/** The outcome of reading stored or received bytes as an exchange graph; parsing never throws. */
public sealed interface ExchangeGraphParseResult {
    /** The bytes are a valid exchange graph. */
    public data class Valid(public val graph: ExchangeGraph) : ExchangeGraphParseResult

    /** The bytes violate the protocol; [error] names the first registered rule they break. */
    public data class Invalid(public val error: ExchangeGraphError) : ExchangeGraphParseResult
}

/**
 * The validated collection Bundle of one exchange event, with its deterministic entry keys and full URLs.
 *
 * The graph is immutable: [toBundle] and [entry] return copies, [json] is the exact serialization an
 * exact retry resends, and [semanticallyEquals] decides content equality over lossless JSON tokens.
 */
public class ExchangeGraph private constructor(
    public val kind: ExchangeGraphKind,
    public val eventIdentifier: ExchangeEventIdentifier,
    private val bundle: Bundle,
    public val json: String,
) {
    /** Lowercase SHA-256 of the UTF-8 bytes of [json]. */
    public val sha256: String = ExchangeProtocol.sha256Hex(json)

    /** A copy of the validated Bundle. */
    public fun toBundle(): Bundle = bundle.copy()

    /** A copy of the entry at one full URL, or null when the graph has no such entry. */
    public fun entry(fullUrl: String): Bundle.BundleEntryComponent? =
        bundle.entry.firstOrNull { it.fullUrl == fullUrl }?.copy()

    /** Whether both graphs carry the same content: the same tokens in any member order, decimal lexemes included. */
    public fun semanticallyEquals(other: ExchangeGraph): Boolean =
        kind == other.kind && JsonToken.parse(json) == JsonToken.parse(other.json)

    /**
     * The retraction targets an active graph's outputs and device snapshots require, or the targets a
     * retraction graph already names.
     */
    public fun retractionTargets(): List<RetractionTarget> = when (kind) {
        ExchangeGraphKind.ACTIVE -> activeRetractionTargets()
        ExchangeGraphKind.RETRACTION -> declaredRetractionTargets()
    }

    internal val entries: List<Bundle.BundleEntryComponent>
        get() = bundle.entry

    private fun activeRetractionTargets(): List<RetractionTarget> {
        val members = bundle.entry.mapNotNull { it.resource as? Observation }
            .flatMap { it.hasMember }
            .mapNotNull { it.reference }
            .toSet()
        return bundle.entry.mapNotNull { entry ->
            val resource = entry.resource
            val typed = typedGroveIdentifiers(resource)
            when {
                resource is Device -> typed[GroveIdentifierRole.DEVICE_SNAPSHOT]?.let {
                    RetractionTarget(it, resource.resourceType, RetractionTargetRole.DEVICE_SNAPSHOT)
                }
                resource.fhirType() in ExchangeContract.activeOutputResourceTypes -> {
                    val output = typed.getValue(GroveIdentifierRole.SOURCE_OUTPUT)
                    val role = when (resource.resourceType) {
                        ResourceType.DocumentReference -> RetractionTargetRole.SOURCE_ARTIFACT
                        ResourceType.Specimen -> RetractionTargetRole.SPECIMEN
                        ResourceType.Observation ->
                            if (entry.fullUrl in members) RetractionTargetRole.CHILD_OUTPUT else RetractionTargetRole.PRIMARY_OUTPUT
                        else -> RetractionTargetRole.PRIMARY_OUTPUT
                    }
                    val native = resource.directIdentifiers().singleOrNull { it.groveRoleCodings().isEmpty() }
                    RetractionTarget(output, resource.resourceType, role, native?.let(BusinessIdentifier::from))
                }
                else -> null
            }
        }
    }

    private fun declaredRetractionTargets(): List<RetractionTarget> =
        bundle.entry.map { it.resource }.filterIsInstance<Provenance>().single().target.map { target ->
            val roleCode = (
                target.getExtensionByUrl(ExchangeContract.RETRACTION_TARGET_ROLE_EXTENSION).value as CodeType
                ).value
            val native = target.getExtensionByUrl(ExchangeContract.RETRACTION_TARGET_NATIVE_IDENTIFIER_EXTENSION)
                ?.value as? Identifier
            RetractionTarget(
                identifier = requireNotNull(RoledIdentifier.from(target.identifier)),
                resourceType = ResourceType.fromCode(target.type),
                role = requireNotNull(RetractionTargetRole.of(roleCode)),
                nativeRecordIdentifier = native?.let(BusinessIdentifier::from),
            )
        }

    override fun toString(): String = "ExchangeGraph(kind=$kind, eventIdentifier=$eventIdentifier, sha256=$sha256)"

    public companion object {
        /** Validates an assembled Bundle, or throws [ExchangeGraphException] naming the first rule it breaks. */
        public fun of(
            kind: ExchangeGraphKind,
            eventIdentifier: ExchangeEventIdentifier,
            bundle: Bundle,
        ): ExchangeGraph {
            val snapshot = bundle.copy()
            if (!snapshot.identifier.matchesPair(eventIdentifier.identifier.identifier)) {
                throw ExchangeGraphException(
                    ExchangeGraphError.RuleViolation(
                        ExchangeGraphRule.MOBILE_EXCHANGE_EVENT_IDENTITY,
                        "Bundle.identifier",
                        "The Bundle identifier is not the event identifier the graph was built for.",
                    ),
                )
            }
            val json = JsonParser().setOutputStyle(IParser.OutputStyle.NORMAL).composeString(snapshot)
            ExchangeGraphValidator.validate(kind, tokens(json))
            return ExchangeGraph(kind, eventIdentifier, snapshot, json)
        }

        /** Re-validates stored or received bytes; an expected failure is a result, never an exception. */
        public fun parse(kind: ExchangeGraphKind, json: String): ExchangeGraphParseResult {
            val tokens = try {
                tokens(json)
            } catch (error: IllegalArgumentException) {
                return ExchangeGraphParseResult.Invalid(ExchangeGraphError.Malformed(error.message ?: "The bytes are not JSON."))
            }
            return try {
                ExchangeGraphValidator.validate(kind, tokens)
                val bundle = runCatching { JsonParser().parse(json) as Bundle }.getOrElse { error ->
                    return ExchangeGraphParseResult.Invalid(
                        ExchangeGraphError.Malformed(error::class.simpleName ?: "The bytes are not FHIR JSON."),
                    )
                }
                val event = try {
                    ExchangeEventIdentifier.from(BusinessIdentifier.from(bundle.identifier))
                } catch (error: ExchangeIdentityException) {
                    throw ExchangeGraphException(
                        ExchangeGraphError.RuleViolation(
                            ExchangeGraphRule.MOBILE_EXCHANGE_EVENT_IDENTITY,
                            "Bundle.identifier.value",
                            error.error.toString(),
                        ),
                    )
                }
                ExchangeGraphParseResult.Valid(ExchangeGraph(kind, event, bundle, json))
            } catch (error: ExchangeGraphException) {
                ExchangeGraphParseResult.Invalid(error.error)
            }
        }

        private fun tokens(json: String): JsonToken.Object {
            val root = JsonToken.parse(json) as? JsonToken.Object
            require(root != null && root.resourceType == "Bundle") { "The JSON is not a Bundle." }
            return root
        }
    }
}
