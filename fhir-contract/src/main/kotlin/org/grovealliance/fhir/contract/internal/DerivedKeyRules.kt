//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

@file:Suppress("detekt:ReturnCount")

package org.grovealliance.fhir.contract.internal

import org.grovealliance.fhir.contract.ContextSnapshotRole
import org.grovealliance.fhir.contract.ExchangeGraphError
import org.grovealliance.fhir.contract.ExchangeGraphRule
import org.grovealliance.fhir.contract.ExchangeRecord
import org.grovealliance.fhir.contract.GroveIdentifierRole
import org.grovealliance.fhir.contract.InternalGroveFhirApi
import org.grovealliance.fhir.contract.LosslessJsonValue
import org.grovealliance.fhir.contract.LosslessJsonValue.JsonObject
import org.grovealliance.fhir.contract.NodeRole
import org.grovealliance.fhir.contract.OpaqueIdentityScope
import org.grovealliance.fhir.contract.Profile
import org.grovealliance.fhir.contract.ResourceType
import org.grovealliance.fhir.contract.RoledIdentifier
import org.grovealliance.fhir.contract.internal.GraphDocument.Companion.array
import org.grovealliance.fhir.contract.internal.GraphDocument.Companion.string
import org.grovealliance.fhir.contract.internal.GraphDocument.Companion.violation

/**
 * The content-derived keys of a graph, recomputed in [scope]: each record version or retraction key
 * from its record, and each shared node's content snapshot from its content. Keys of another key or
 * epoch are not checked.
 */
@OptIn(InternalGroveFhirApi::class)
internal class DerivedKeyRules(
    private val document: GraphDocument,
    private val facts: GraphFacts,
    private val kind: ExchangeRecord.Kind,
    private val scope: OpaqueIdentityScope,
) {
    private val resources = document.entries.indices.associate { facts.fullUrl(index = it) to document.resource(index = it) }

    fun violation(): ExchangeGraphError? {
        val subject = facts.bundleSubject()
        val prefix = "v0:${scope.keyId}:${scope.epoch.rawValue}:"
        document.entries.indices.forEach { index ->
            val key = document.entryKey(index = index)?.takeIf { it.identifier.value.startsWith(prefix) } ?: return@forEach
            val resource = document.resource(index = index) ?: return@forEach
            val location = "Bundle.entry[$index].extension.valueIdentifier.value"
            if (document.type(index = index) == ResourceType.PROVENANCE) {
                if (lifecycleKey(provenance = resource) != key) {
                    val rule = if (kind == ExchangeRecord.Kind.ACTIVE) {
                        ExchangeGraphRule.MOBILE_EXCHANGE_RECORD_VERSION_KEY
                    } else {
                        ExchangeGraphRule.MOBILE_RETRACTION_RETRACTION_KEY
                    }
                    return violation(rule = rule, location = location)
                }
                return@forEach
            }
            val role = nodeRole(index = index, key = key) ?: return@forEach
            if (subject == null) {
                if (kind == ExchangeRecord.Kind.ACTIVE) {
                    return violation(
                        rule = ExchangeGraphRule.MOBILE_EXCHANGE_CONTENT_SNAPSHOT_KEY,
                        location = location,
                    )
                }
                return@forEach
            }
            val expected = resource.contentDigest().getOrNull()?.let { scope.contentSnapshot(
                subject = subject,
                role = role,
                contentDigest = it,
            ).getOrNull() }
            if (expected != key) return violation(rule = ExchangeGraphRule.MOBILE_EXCHANGE_CONTENT_SNAPSHOT_KEY, location = location)
        }
        return null
    }

    private fun lifecycleKey(provenance: JsonObject): RoledIdentifier? {
        val source = facts.exactSourceEntity(provenance = provenance).getOrNull() ?: return null
        if (kind == ExchangeRecord.Kind.RETRACTION) return scope.recordRetraction(sourceRecord = source.identifier).getOrNull()
        val outputs: List<LosslessJsonValue> = provenance.array(name = "target").orEmpty()
            .mapNotNull { (it as? JsonObject)?.string(name = "reference")?.let { reference -> resources[reference] } }
        val digest = LosslessJsonValue.recordContentDigest(provenance = provenance, outputs = outputs).getOrNull() ?: return null
        return scope.recordVersion(sourceRecord = source.identifier, contentDigest = digest).getOrNull()
    }

    private fun nodeRole(index: Int, key: RoledIdentifier): NodeRole? {
        if (key.role != GroveIdentifierRole.DEVICE_SNAPSHOT && key.role != GroveIdentifierRole.CONTEXT_SNAPSHOT) return null
        val profiles = facts.profiles(index = index)
        return when (val resourceType = document.type(index = index)) {
            ResourceType.DEVICE -> when {
                Profile.Mobile.GROVE_RECORDING_DEVICE in profiles -> NodeRole.RecordingDevice
                Profile.Mobile.GROVE_HOST_DEVICE in profiles -> NodeRole.Host
                else -> NodeRole.Application
            }
            else -> CONTEXT_ROLES[resourceType]?.let { NodeRole.Context(role = it) }
        }
    }

    private companion object {
        val CONTEXT_ROLES = mapOf(
            ResourceType.PATIENT to ContextSnapshotRole.PATIENT,
            ResourceType.RESEARCH_STUDY to ContextSnapshotRole.RESEARCH_STUDY,
            ResourceType.RESEARCH_SUBJECT to ContextSnapshotRole.RESEARCH_SUBJECT,
            ResourceType.PLAN_DEFINITION to ContextSnapshotRole.PLAN_DEFINITION,
            ResourceType.QUESTIONNAIRE_RESPONSE to ContextSnapshotRole.QUESTIONNAIRE_RESPONSE,
        )
    }
}
