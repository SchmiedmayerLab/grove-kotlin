//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

@file:Suppress("detekt:ReturnCount")

package org.grovealliance.fhir.contract.internal

import org.grovealliance.fhir.contract.BusinessIdentifier
import org.grovealliance.fhir.contract.ExchangeContract
import org.grovealliance.fhir.contract.ExchangeGraphError
import org.grovealliance.fhir.contract.ExchangeGraphRule
import org.grovealliance.fhir.contract.GroveIdentifierRole
import org.grovealliance.fhir.contract.HealthKitContract
import org.grovealliance.fhir.contract.IdentifierSystem
import org.grovealliance.fhir.contract.InternalGroveFhirApi
import org.grovealliance.fhir.contract.LosslessJsonValue.JsonObject
import org.grovealliance.fhir.contract.ProfileClaims
import org.grovealliance.fhir.contract.ResourceType
import org.grovealliance.fhir.contract.RoledIdentifier
import org.grovealliance.fhir.contract.internal.GraphDocument.Companion.array
import org.grovealliance.fhir.contract.internal.GraphDocument.Companion.obj
import org.grovealliance.fhir.contract.internal.GraphDocument.Companion.roleCodings
import org.grovealliance.fhir.contract.internal.GraphDocument.Companion.string
import org.grovealliance.fhir.contract.internal.GraphDocument.Companion.violation

/**
 * The record rules of an active graph: one transform Provenance per source record, every output
 * targeted by exactly the Provenance of its own source record, no reference across records, one
 * subject, a complete study context, and every supporting node reached from a record.
 */
@OptIn(InternalGroveFhirApi::class)
@Suppress("detekt:TooManyFunctions")
internal class ActiveRecordRules(
    private val document: GraphDocument,
    private val facts: GraphFacts,
) {

    /**
     * One record: the [index] of its Provenance, its [source] record, and the fullUrls of its [targets].
     */
    private data class Record(
        val index: Int,
        val source: RoledIdentifier,
        val targets: List<String>,
    )

    private data class Output(
        val sourceRecord: RoledIdentifier,
        val resourceType: String,
    )

    fun violation(): ExchangeGraphError? {
        provenanceViolation()?.let { return it }
        val outputs = outputs().getOrElse { return it as ExchangeGraphError }
        sourceMarkerViolation()?.let { return it }
        val records = records(outputs = outputs).getOrElse { return it as ExchangeGraphError }
        return recordingDocumentViolation(records = records)
            ?: boundaryViolation(records = records)
            ?: (if (facts.subjectStatements().size != 1 || facts.hasUnresolvedSubject()) {
                violation(
                    rule = ExchangeGraphRule.MOBILE_EXCHANGE_SINGLE_SUBJECT,
                    location = "Bundle.entry",
                )
            } else {
                null
            })
            ?: studyContextViolation()
            ?: facts.connectivityViolation(supportingTypes = ExchangeContract.ACTIVE_SUPPORTING_RESOURCE_TYPES)
            ?: patientSubjectViolation()
            ?: adapterGraphViolation(records = records)
    }

    private fun provenanceViolation(): ExchangeGraphError? {
        val provenances = facts.provenances()
        val lifecycles = provenances.map { facts.lifecycle(provenance = requireNotNull(document.resource(index = it))) }
        if (GraphFacts.Lifecycle.INVALID in lifecycles) return violation(rule = ExchangeGraphRule.MOBILE_EXCHANGE_LIFECYCLE_CODING)
        if (provenances.isEmpty() || lifecycles.any { it != GraphFacts.Lifecycle.ACTIVE }) {
            return violation(rule = ExchangeGraphRule.MOBILE_EXCHANGE_TRANSFORM_PROVENANCE)
        }
        facts.recordTimesViolation(indices = provenances)?.let { return it }
        return if (provenances.all { facts.assemblerResolves(provenance = requireNotNull(document.resource(index = it))) }) {
            null
        } else {
            violation(rule = ExchangeGraphRule.MOBILE_EXCHANGE_TRANSFORM_PROVENANCE)
        }
    }

    /**
     * Every output is keyed by its one source output and names one source record; a Device carries its snapshot identity.
     */
    private fun outputs(): Result<Map<String, Output>> {
        val outputs = mutableMapOf<String, Output>()
        val missing = violation(rule = ExchangeGraphRule.MOBILE_OUTPUT_SOURCE_OUTPUT_REQUIRED)
        document.entries.indices.forEach { index ->
            val key = document.entryKey(index = index) ?: return Result.failure(missing)
            val identifiers = document.typedIdentifiers(index = index)
            if (identifiers.toSet().size != identifiers.size) return Result.failure(missing)
            if (document.type(index = index) == ResourceType.DEVICE) {
                facts.deviceIdentityViolation(index = index, key = key, identifiers = identifiers)?.let { return Result.failure(it) }
            }
            if (!facts.isActiveOutput(index = index)) return@forEach
            val sourceRecords = identifiers.filter { it.role == GroveIdentifierRole.SOURCE_RECORD }
            val fullUrl = document.fullUrl(index = index)
            val sourceOutputs = identifiers.filter { it.role == GroveIdentifierRole.SOURCE_OUTPUT }
            val isKeyed = key.role == GroveIdentifierRole.SOURCE_OUTPUT && sourceOutputs == listOf(key)
            if (!isKeyed || sourceRecords.size != 1 || fullUrl == null) return Result.failure(missing)
            if (document.type(index = index) == ResourceType.DOCUMENT_REFERENCE && !isRecordingDocumentIdentity(
                    index = index,
                    identifiers = identifiers,
                )) {
                return Result.failure(violation(rule = ExchangeGraphRule.SENSOR_RECORDING_DOCUMENT_IDENTITY_AND_CONTENT))
            }
            outputs[fullUrl] = Output(
                sourceRecord = sourceRecords.single(),
                resourceType = requireNotNull(document.resourceType(index = index)),
            )
        }
        return if (outputs.isEmpty()) {
            Result.failure(violation(rule = ExchangeGraphRule.MOBILE_EXCHANGE_OUTPUT_REQUIRED))
        } else {
            Result.success(outputs)
        }
    }

    private fun isRecordingDocumentIdentity(index: Int, identifiers: List<RoledIdentifier>): Boolean {
        val writers = identifiers.count { it.role == GroveIdentifierRole.WRITER_RECORD }
        val single = listOf(GroveIdentifierRole.SOURCE_RECORD, GroveIdentifierRole.SOURCE_OUTPUT, GroveIdentifierRole.SOURCE_ARTIFACT)
        return document.resource(index = index)?.array(name = "content").orEmpty().size == 1 &&
            single.all { role -> identifiers.count { it.role == role } == 1 } &&
            writers <= 1 && identifiers.size == single.size + writers
    }

    /**
     * An output claiming an adapter profile carries its one source-type marker; no other output carries one.
     */
    private fun sourceMarkerViolation(): ExchangeGraphError? = document.entries.indices
        .filter { facts.isActiveOutput(index = it) }
        .firstNotNullOfOrNull { index ->
            val markers = document.resource(index = index)?.array(name = "extension").orEmpty()
                .count { (it as? JsonObject)?.string(name = "url") == HealthKitContract.SOURCE_TYPE_EXTENSION }
            val claimsAdapter = facts.profiles(index = index).any { it.startsWith("${HealthKitContract.CANONICAL}/") }
            if (markers != (if (claimsAdapter) 1 else 0)) {
                violation(
                    rule = ExchangeGraphRule.MOBILE_OUTPUT_ADAPTER_SOURCE_MARKER,
                    location = "${document.resourceType(index = index)}.extension",
                )
            } else {
                null
            }
        }

    private fun records(outputs: Map<String, Output>): Result<List<Record>> {
        val sources = mutableSetOf<RoledIdentifier>()
        val provenances = facts.provenances()
        val sourceByIndex = provenances.associateWith { index ->
            val provenance = requireNotNull(document.resource(index = index))
            val source = facts.exactSourceEntity(provenance = provenance).getOrElse { return Result.failure(it) }
            if (!sources.add(source)) {
                return Result.failure(violation(
                    rule = ExchangeGraphRule.MOBILE_EXCHANGE_DISTINCT_SOURCE_RECORD,
                    location = "Bundle.entry[$index].resource.entity",
                ))
            }
            source
        }
        val records = provenances.map { index ->
            Record(
                index = index,
                source = sourceByIndex.getValue(index),
                targets = targets(index = index, outputs = outputs).getOrElse { return Result.failure(it) },
            )
        }
        val targeted = mutableSetOf<String>()
        val once = violation(rule = ExchangeGraphRule.MOBILE_EXCHANGE_OUTPUT_TARGETED_ONCE, location = "Bundle.entry")
        if (!records.flatMap { it.targets }.all { targeted.add(it) } || targeted.size != outputs.size) return Result.failure(once)
        return if (records.any { record -> record.targets.any { outputs[it]?.sourceRecord != record.source } }) {
            Result.failure(violation(rule = ExchangeGraphRule.MOBILE_EXCHANGE_PROVENANCE_TARGETS, location = "Provenance.target"))
        } else {
            Result.success(records)
        }
    }

    private fun targets(index: Int, outputs: Map<String, Output>): Result<List<String>> {
        val targets = document.resource(index = index)?.array(name = "target").orEmpty()
        if (targets.isEmpty()) {
            return Result.failure(violation(
                rule = ExchangeGraphRule.MOBILE_EXCHANGE_OUTPUT_REQUIRED,
                location = "Bundle.entry[$index].resource.target",
            ))
        }
        val seen = mutableSetOf<String>()
        return Result.success(
            targets.mapIndexed { targetIndex, value ->
                val target = value as? JsonObject
                val reference = target?.string(name = "reference")
                val output = reference?.let { outputs[it] }
                val declared = target?.string(name = "type")
                val isOutput = output != null && (declared == null || declared == output.resourceType)
                if (target == null || target.members.containsKey("identifier") || !isOutput) {
                    return Result.failure(violation(
                        rule = ExchangeGraphRule.MOBILE_EXCHANGE_PROVENANCE_TARGETS,
                        location = "Provenance.target",
                    ))
                }
                if (!seen.add(reference)) {
                    return Result.failure(
                        violation(
                            rule = ExchangeGraphRule.MOBILE_EXCHANGE_OUTPUT_TARGETED_ONCE,
                            location = "Bundle.entry[$index].resource.target[$targetIndex]",
                        ),
                    )
                }
                reference
            },
        )
    }

    /**
     * A recording document carries at most one governed source identifier, and only as the single output of its record.
     */
    private fun recordingDocumentViolation(records: List<Record>): ExchangeGraphError? {
        val outputCounts = records.flatMap { record -> record.targets.map { it to record.targets.size } }.toMap()
        val documents = document.entries.indices.filter { document.type(index = it) == ResourceType.DOCUMENT_REFERENCE }
        return documents.firstNotNullOfOrNull { index ->
            val all = document.resource(index = index)?.array(name = "identifier").orEmpty().filterIsInstance<JsonObject>()
            val typed = document.typedIdentifiers(index = index)
            val plain = all.filter { it.roleCodings().isEmpty() }
            val governed = plain.singleOrNull()
            val isValid = all.size == typed.size + plain.size && plain.size <= 1 &&
                (governed == null || (outputCounts[facts.fullUrl(index = index)] == 1 && isGovernedSourceIdentifier(identifier = governed)))
            if (isValid) null else violation(rule = ExchangeGraphRule.SENSOR_RECORDING_DOCUMENT_IDENTITY_AND_CONTENT)
        }
    }

    private fun isGovernedSourceIdentifier(identifier: JsonObject): Boolean {
        val system = identifier.string(name = "system")
        val isIdentifier = system != null && IdentifierSystem.create(rawValue = system).isSuccess
        if (!isIdentifier || identifier.string(name = "value").isNullOrEmpty()) return false
        val type = identifier.obj(name = "type") ?: return true
        val text = type.string(name = "text")
        val codings = type.array(name = "coding").orEmpty().filterIsInstance<JsonObject>()
        val isNamed = if (text == null) codings.isNotEmpty() else text.isNotBlank()
        return isNamed && codings.all { isGovernedType(coding = it) }
    }

    private fun isGovernedType(coding: JsonObject): Boolean {
        val system = coding.string(name = "system")
        val code = coding.string(name = "code")
        val display = coding.string(name = "display")
        val isSystem = system != null && system != ExchangeContract.IDENTIFIER_ROLE_CODE_SYSTEM &&
            IdentifierSystem.create(rawValue = system).isSuccess
        val isCode = !code.isNullOrEmpty() && code == code.trim() && "  " !in code
        return isSystem && isCode && (display == null || display.isNotBlank())
    }

    /**
     * No reference reaches a Provenance or an output of another record.
     */
    private fun boundaryViolation(records: List<Record>): ExchangeGraphError? {
        val recordByOutput = records.flatMap { record -> record.targets.map { it to record.index } }.toMap()
        val recordByProvenance = records.associate { facts.fullUrl(index = it.index) to it.index }
        return facts.crossingReferenceViolation { referrer, reference ->
            val own = recordByOutput[referrer] ?: recordByProvenance[referrer]
            recordByProvenance.containsKey(reference) || (recordByOutput[reference]?.let { it != own } ?: false)
        }
    }

    /**
     * Every ResearchStudy names one complete protocol, every ResearchSubject enrolls the graph's subject in a study of the graph,
     * and no PlanDefinition or ResearchStudy stands alone.
     */
    private fun studyContextViolation(): ExchangeGraphError? {
        val byType = document.entries.indices.groupBy { document.type(index = it) }
        val plans = byType[ResourceType.PLAN_DEFINITION].orEmpty()
            .associate { facts.fullUrl(index = it) to requireNotNull(document.resource(index = it)) }
        val studies = byType[ResourceType.RESEARCH_STUDY].orEmpty()
            .associate { facts.fullUrl(index = it) to requireNotNull(document.resource(index = it)) }
        val failure = violation(rule = ExchangeGraphRule.MOBILE_SUPPORT_STUDY_CONTEXT)
        val protocols = studies.values.map { study ->
            val planUrl = (study.array(name = "protocol")?.singleOrNull() as? JsonObject)?.string(name = "reference")
            val plan = planUrl?.let { plans[it] }
            if (plan == null || plan.string(name = "url").isNullOrEmpty() || plan.string(name = "version").isNullOrEmpty()) return failure
            planUrl
        }.toSet()
        val subjects = facts.subjectStatements()
        val enrolled = byType[ResourceType.RESEARCH_SUBJECT].orEmpty().map { index ->
            val researchSubject = requireNotNull(document.resource(index = index))
            val studyUrl = researchSubject.obj(name = "study")?.string(name = "reference")
            val individual = researchSubject.obj(name = "individual")?.let { facts.subjectStatement(reference = it) }
            val isEnrolled = individual != null && individual in subjects
            if (studyUrl == null || !studies.containsKey(studyUrl) || !isEnrolled) return failure
            studyUrl
        }.toSet()
        return if (protocols.size == plans.size && enrolled.size == studies.size) null else failure
    }

    /**
     * Every Patient entry names the graph's one subject.
     */
    private fun patientSubjectViolation(): ExchangeGraphError? {
        val subject: BusinessIdentifier = facts.bundleSubject() ?: return null
        return document.entries.indices.firstOrNull { index ->
            document.type(index = index) == ResourceType.PATIENT &&
                facts.patientPseudonym(patient = requireNotNull(document.resource(index = index))) != subject
        }?.let { violation(rule = ExchangeGraphRule.MOBILE_EXCHANGE_PATIENT_PSEUDONYM, location = "Bundle.entry[$it].resource.identifier") }
    }

    /**
     * An adapter conversion Provenance targets exactly the adapter outputs of its source record, and every such group has one.
     */
    private fun adapterGraphViolation(records: List<Record>): ExchangeGraphError? {
        ProfileClaims.adapterProvenanceTargetProfiles.forEach { (provenanceProfile, outputProfiles) ->
            val governing = records.filter { provenanceProfile in facts.profiles(index = it.index) }
            val bySource = governing.associateBy { it.source }
            val groups = linkedMapOf<RoledIdentifier, MutableSet<String>>()
            document.entries.indices.filter { index -> facts.profiles(index = index).any { it in outputProfiles } }.forEach { index ->
                val source = document.typedIdentifiers(index = index).filter { it.role == GroveIdentifierRole.SOURCE_RECORD }.singleOrNull()
                    ?: return ExchangeGraphError.InvalidEntries(reason = UNSOURCED_ADAPTER_OUTPUT)
                groups.getOrPut(source) { mutableSetOf() }.add(facts.fullUrl(index = index))
            }
            groups.forEach { (source, fullUrls) ->
                val record = bySource[source] ?: return violation(
                    rule = ExchangeGraphRule.MOBILE_EXCHANGE_ADAPTER_PROVENANCE_GRAPH,
                    location = "Bundle.entry",
                )
                if (record.targets.toSet() != fullUrls) {
                    return violation(
                        rule = ExchangeGraphRule.MOBILE_EXCHANGE_ADAPTER_PROVENANCE_GRAPH,
                        location = "Provenance.target",
                    )
                }
            }
            if (governing.any { it.source !in groups.keys }) {
                return violation(rule = ExchangeGraphRule.MOBILE_EXCHANGE_ADAPTER_PROVENANCE_GRAPH, location = "Provenance.entity")
            }
        }
        return null
    }

    private companion object {
        const val UNSOURCED_ADAPTER_OUTPUT = "An adapter-profiled entry carries no single typed source-record identity."
    }
}
