//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

@file:Suppress("detekt:ReturnCount")

package org.grovealliance.fhir.contract.internal

import org.grovealliance.fhir.contract.ExchangeGraphError
import org.grovealliance.fhir.contract.ExchangeRecord
import org.grovealliance.fhir.contract.InternalGroveFhirApi
import org.grovealliance.fhir.contract.LosslessJsonValue.JsonObject
import org.grovealliance.fhir.contract.RecordVersion
import org.grovealliance.fhir.contract.internal.GraphDocument.Companion.array
import org.grovealliance.fhir.contract.internal.GraphDocument.Companion.string

/**
 * The records of a validated graph of [kind], in entry order: each lifecycle Provenance with its
 * outputs and the shared nodes it reaches, every entry as it is stated.
 */
@OptIn(InternalGroveFhirApi::class)
internal class RecordExtraction(
    private val document: GraphDocument,
    private val kind: ExchangeRecord.Kind,
) {
    private val facts = GraphFacts(document = document)

    fun records(): Result<List<ExchangeRecord>> {
        val subject = facts.bundleSubject()
        val provenances = facts.provenances()
        val provenanceUrls = provenances.map { facts.fullUrl(index = it) }.toSet()
        val indexes = document.entries.indices.associateBy { facts.fullUrl(index = it) }
        return Result.success(
            provenances.map { index ->
                val provenance = requireNotNull(document.resource(index = index))
                val fullUrl = facts.fullUrl(index = index)
                val outputs = if (kind == ExchangeRecord.Kind.ACTIVE) {
                    provenance.array(name = "target").orEmpty().mapNotNull { (it as? JsonObject)?.string(name = "reference") }
                } else {
                    emptyList()
                }
                val unit = facts.unit(starts = listOf(fullUrl) + outputs)
                if (unit.intersect(provenanceUrls) != setOf(fullUrl)) {
                    return Result.failure(ExchangeGraphError.InvalidEntries(reason = "A record states exactly one lifecycle Provenance."))
                }
                val source = facts.exactSourceEntity(provenance = provenance).getOrElse { return Result.failure(it) }
                val key = document.entryKey(index = index)
                    ?: return Result.failure(ExchangeGraphError.InvalidEntries(reason = "A lifecycle Provenance states no entry key."))
                val version = RecordVersion.create(identifier = key).getOrElse { return Result.failure(it) }
                ExchangeRecord(
                    kind = kind,
                    subject = subject,
                    sourceRecord = source.identifier,
                    version = version,
                    outputFullUrls = outputs,
                    entries = unit.mapNotNull { indexes[it] }.sorted().map { member ->
                        ExchangeRecord.Entry(
                            fullUrl = facts.fullUrl(index = member),
                            json = document.entries[member].canonicalBytes(),
                        )
                    },
                    timestamp = provenance.string(name = "recorded").orEmpty(),
                )
            },
        )
    }
}
