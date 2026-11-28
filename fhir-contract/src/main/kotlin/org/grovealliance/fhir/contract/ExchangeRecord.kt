//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.fhir.contract

import org.grovealliance.fhir.contract.internal.CanonicalJsonWriter

/**
 * One source-record revision: its outputs, its one conversion or retraction Provenance, and the
 * shared nodes they reference.
 *
 * A record is the unit of conversion, of staging and of retry; [ExchangeBundlePacker] packs records
 * into the Bundles that travel. [json] is the record as a one-record Bundle in
 * `grove-canonical-json-v0`, whose `timestamp` states `Provenance.recorded`.
 *
 * @property subject the participant the record is about, when its content states one
 * @property version the Provenance's entry key
 * @property outputFullUrls the fullUrls of the outputs, in `Provenance.target` order; empty for a retraction
 */
@Suppress("detekt:UseDataClass", "detekt:LongParameterList")
class ExchangeRecord internal constructor(
    val kind: Kind,
    val subject: BusinessIdentifier?,
    val sourceRecord: BusinessIdentifier,
    val version: RecordVersion,
    val outputFullUrls: List<String>,
    internal val entries: List<Entry>,
    internal val timestamp: String,
) {

    /**
     * The fullUrls of the entries that are neither an output nor the Provenance.
     */
    val sharedNodeFullUrls: Set<String>
        get() = entries.map { it.fullUrl }.toSet() - outputFullUrls.toSet() - version.fullUrl

    /**
     * Whether a record states a source-record revision or takes a whole source record back.
     */
    enum class Kind {
        ACTIVE,
        RETRACTION,
        ;

        /**
         * The profile the Bundle of a record of this kind claims.
         */
        @InternalGroveFhirApi
        val bundleProfile: String
            get() = when (this) {
                ACTIVE -> ExchangeContract.ACTIVE_BUNDLE_PROFILE
                RETRACTION -> ExchangeContract.RETRACTION_BUNDLE_PROFILE
            }
    }

    /**
     * One Bundle entry of the record: its [fullUrl] and its canonical [json].
     */
    @Suppress("detekt:UseDataClass")
    internal class Entry(
        val fullUrl: String,
        val json: ByteArray,
    )

    /**
     * The record as a one-record Bundle in `grove-canonical-json-v0`.
     */
    val json: ByteArray
        get() = CanonicalJsonWriter().bundle(
            profile = @OptIn(InternalGroveFhirApi::class) kind.bundleProfile,
            timestamp = timestamp,
            identifier = null,
            entries = entries.map { it.json },
        )

    companion object {
        private const val SINGLE_PROVENANCE = "A record states exactly one lifecycle Provenance."

        /**
         * The one record a stored one-record Bundle of either kind states, after every structural rule of
         * [ExchangeGraph.validating]. Its version is checked for shape only, as only a holder of the
         * identity key can recompute it.
         */
        @OptIn(InternalGroveFhirApi::class)
        fun validating(json: ByteArray): Result<ExchangeRecord> {
            val profiles = ((LosslessJsonValue.parse(bytes = json).getOrNull() as? LosslessJsonValue.JsonObject)
                ?.members?.get("meta") as? LosslessJsonValue.JsonObject)
                ?.members?.get("profile") as? LosslessJsonValue.JsonArray
            val claimsRetraction = profiles?.elements.orEmpty()
                .any { (it as? LosslessJsonValue.JsonString)?.value == ExchangeContract.RETRACTION_BUNDLE_PROFILE }
            val kind = if (claimsRetraction) Kind.RETRACTION else Kind.ACTIVE
            return ExchangeGraph.validating(json = json, kind = kind).fold(
                onSuccess = { graph ->
                    graph.records().fold(
                        onSuccess = { records ->
                            records.singleOrNull()?.let { Result.success(it) }
                                ?: Result.failure(ExchangeGraphError.InvalidEntries(reason = SINGLE_PROVENANCE))
                        },
                        onFailure = { Result.failure(it) },
                    )
                },
                onFailure = { Result.failure(it) },
            )
        }
    }

    /**
     * This record, once its one-record Bundle passes every structural rule of [ExchangeGraph.validating].
     */
    internal fun validated(): Result<ExchangeRecord> = ExchangeGraph.validating(json = json, kind = kind).map { this }

    override fun equals(other: Any?): Boolean = other is ExchangeRecord && version == other.version && json.contentEquals(other.json)

    override fun hashCode(): Int = version.hashCode()

    override fun toString(): String = "ExchangeRecord(kind=$kind, version=${version.identifier.identifier.value})"
}
