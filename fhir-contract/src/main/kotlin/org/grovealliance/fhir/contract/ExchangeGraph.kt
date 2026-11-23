//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.fhir.contract

import org.grovealliance.fhir.contract.internal.DerivedKeyRules
import org.grovealliance.fhir.contract.internal.EntryPolicyRules
import org.grovealliance.fhir.contract.internal.GraphDocument
import org.grovealliance.fhir.contract.internal.GraphFacts
import org.grovealliance.fhir.contract.internal.IdentityRules
import org.grovealliance.fhir.contract.internal.LifecycleRules
import org.grovealliance.fhir.contract.internal.LosslessJsonParser
import org.grovealliance.fhir.contract.internal.RecordExtraction
import org.grovealliance.fhir.contract.internal.ReferenceRules

/**
 * One validated exchange Bundle: one or more records of one [kind] and one subject, with their shared
 * nodes, kept as the exact [json] it was validated from, which is what travels.
 */
@Suppress("detekt:UseDataClass")
class ExchangeGraph private constructor(
    val kind: ExchangeRecord.Kind,
    private val bytes: ByteArray,
) {

    /**
     * The exact bytes the graph was validated from, as a copy the caller owns.
     */
    val json: ByteArray get() = bytes.copyOf()

    /**
     * The graph's records, in entry order: each lifecycle Provenance with its outputs and the shared
     * nodes it reaches, every entry exactly as this graph states it.
     */
    @OptIn(InternalGroveFhirApi::class)
    fun records(): Result<List<ExchangeRecord>> {
        val bundle = LosslessJsonParser(bytes = bytes, lenient = true).parse().getOrNull() as? LosslessJsonValue.JsonObject
            ?: return Result.failure(ExchangeGraphError.InvalidEntries(reason = "The Bundle is not a JSON object."))
        return RecordExtraction(
            document = GraphDocument(bundle = bundle),
            kind = kind,
        ).records()
    }

    /**
     * The first content-derived key of this graph that [scope] recomputes differently.
     */
    @OptIn(InternalGroveFhirApi::class)
    internal fun derivedKeyViolation(scope: OpaqueIdentityScope): ExchangeGraphError? {
        val bundle = LosslessJsonParser(bytes = bytes, lenient = true).parse().getOrNull() as? LosslessJsonValue.JsonObject ?: return null
        val document = GraphDocument(bundle = bundle)
        return DerivedKeyRules(
            document = document,
            facts = GraphFacts(document = document),
            kind = kind,
            scope = scope,
        ).violation()
    }

    companion object {

        /**
         * Validates stored or received [json] as a Bundle of [kind] before it is trusted, against every
         * structural rule of the exchange. A content-derived key is checked for shape only, as only a
         * holder of the identity key can recompute it.
         */
        @OptIn(InternalGroveFhirApi::class)
        fun validating(json: ByteArray, kind: ExchangeRecord.Kind): Result<ExchangeGraph> {
            val owned = json.copyOf()
            val parser = LosslessJsonParser(bytes = owned, lenient = true)
            val bundle = parser.parse().getOrNull() as? LosslessJsonValue.JsonObject
                ?: return Result.failure(ExchangeGraphError.InvalidEntries(reason = "The Bundle is not a JSON object."))
            val document = GraphDocument(bundle = bundle)
            val failure = EntryPolicyRules(document = document, kind = kind).violation()
                ?: IdentityRules(document = document, kind = kind).violation()
                ?: ReferenceRules(document = document).violation()
                ?: LifecycleRules(document = document, kind = kind).violation()
                ?: canonicalViolation(paths = parser.nonCanonicalPaths)
            return failure?.let { Result.failure(it) } ?: Result.success(
                ExchangeGraph(
                    kind = kind,
                    bytes = owned,
                ),
            )
        }

        /**
         * The first entry resource without a `grove-canonical-json-v0` reading, checked after every other rule.
         */
        private fun canonicalViolation(paths: List<List<String>>): ExchangeGraphError? {
            val path = paths.firstOrNull() ?: return null
            val isEntryResource = path.getOrNull(index = 0) == "entry" && path.getOrNull(index = 2) == "resource"
            val location = if (isEntryResource) "Bundle.entry[${path[1]}].resource" else "Bundle"
            return GraphDocument.violation(rule = ExchangeGraphRule.MOBILE_EXCHANGE_CANONICAL_JSON, location = location)
        }
    }
}
