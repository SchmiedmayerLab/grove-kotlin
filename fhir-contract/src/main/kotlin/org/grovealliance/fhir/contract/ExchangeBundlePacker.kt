//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.fhir.contract

import org.grovealliance.fhir.contract.internal.CanonicalJsonWriter
import java.time.Instant

/**
 * Packs records of one [kind] and one subject into one exchange Bundle, stating each shared node once.
 *
 * Shared nodes are content-addressed, so two records that reference the same fullUrl share its one
 * entry. The packer splices canonical entry bytes and never re-encodes, so packing changes no record.
 *
 * @param maximumRecords the most records one Bundle holds
 * @param maximumBytes the most bytes of uncompressed entries one Bundle holds
 */
class ExchangeBundlePacker(
    private val kind: ExchangeRecord.Kind,
    private val maximumRecords: Int = DEFAULT_MAXIMUM_RECORDS,
    private val maximumBytes: Int = DEFAULT_MAXIMUM_BYTES,
) {
    private var subject: BusinessIdentifier? = null
    private val versions = mutableMapOf<BusinessIdentifier, RecordVersion>()
    private val nodes = mutableMapOf<String, ByteArray>()
    private val entries = mutableListOf<ByteArray>()

    /**
     * How many records the Bundle holds.
     */
    var recordCount: Int = 0
        private set

    /**
     * The canonical bytes of the entries packed so far; the Bundle adds its short header to them.
     */
    var byteCount: Int = 0
        private set

    /**
     * Whether the Bundle reached a cap, so no further record can join without being deferred.
     */
    val isFull: Boolean get() = recordCount == maximumRecords || byteCount >= maximumBytes

    /**
     * Why a record can't join this packer.
     */
    sealed class Failure(message: String) : Exception(message) {

        /**
         * The record isn't of the packer's kind.
         */
        data object MixedKinds : Failure("The record is of another kind than the Bundle.")

        /**
         * The record states another subject than the records already packed; a fresh packer would only hide it.
         */
        data object MixedSubjects : Failure("The record is about another subject than the Bundle.")

        /**
         * The record states other bytes than a packed one under [fullUrl]. A key is a digest of its
         * content, so this is an integrity failure.
         */
        data class ConflictingNode(val fullUrl: String) : Failure("$fullUrl is stated with other content.")
    }

    /**
     * What happened to a record the packer accepted.
     */
    enum class InsertResult {

        /**
         * The record's entries were added.
         */
        INSERTED,

        /**
         * The packer already holds this version and every entry it states, or this retraction key; the
         * first accepted retraction of a source record wins.
         */
        DUPLICATE,

        /**
         * The record belongs in the next Bundle: a second version of a packed source record, the same
         * version stating an entry this Bundle doesn't hold, or a record past a cap.
         */
        DEFERRED,
    }

    /**
     * Adds [record] to the Bundle, or reports why it doesn't join this one. An empty packer of the
     * record's kind accepts any one record, even one over [maximumBytes], so a caller never stalls.
     */
    fun insert(record: ExchangeRecord): Result<InsertResult> {
        val held = heldVersion(record = record)
        val failure = refusal(record = record) ?: if (held == null) conflict(record = record) else null
        return when {
            failure != null -> Result.failure(failure)
            held != null -> Result.success(held)
            else -> Result.success(add(record = record))
        }
    }

    private fun refusal(record: ExchangeRecord): Failure? {
        val packedSubject = subject
        return when {
            record.kind != kind -> Failure.MixedKinds
            packedSubject != null && record.subject != null && packedSubject != record.subject -> Failure.MixedSubjects
            else -> null
        }
    }

    /**
     * The outcome for a source record already packed under a retraction or another version.
     */
    private fun heldVersion(record: ExchangeRecord): InsertResult? {
        val packed = versions[record.sourceRecord] ?: return null
        return when {
            kind == ExchangeRecord.Kind.RETRACTION -> InsertResult.DUPLICATE
            packed != record.version -> InsertResult.DEFERRED
            else -> null
        }
    }

    private fun conflict(record: ExchangeRecord): Failure? = record.entries
        .firstOrNull { entry -> nodes[entry.fullUrl]?.let { !it.contentEquals(entry.json) } ?: false }
        ?.let { Failure.ConflictingNode(fullUrl = it.fullUrl) }

    private fun add(record: ExchangeRecord): InsertResult {
        val added = record.entries.filterNot { nodes.containsKey(it.fullUrl) }
        val addedBytes = added.sumOf { it.json.size }
        val isPastCap = recordCount > 0 && (recordCount >= maximumRecords || byteCount + addedBytes > maximumBytes)
        return when {
            versions.containsKey(record.sourceRecord) -> if (added.isEmpty()) InsertResult.DUPLICATE else InsertResult.DEFERRED
            isPastCap -> InsertResult.DEFERRED
            else -> {
                subject = subject ?: record.subject
                versions[record.sourceRecord] = record.version
                added.forEach { entry ->
                    nodes[entry.fullUrl] = entry.json
                    entries += entry.json
                }
                recordCount += 1
                byteCount += addedBytes
                InsertResult.INSERTED
            }
        }
    }

    /**
     * The Bundle of every packed record in `grove-canonical-json-v0`, validated as stored bytes are:
     * its `timestamp` states [packedAt] and its `identifier` the [transportIdentifier]; neither enters
     * any identity.
     */
    @OptIn(InternalGroveFhirApi::class)
    fun bundle(
        packedAt: Instant,
        transportIdentifier: BusinessIdentifier? = null,
    ): Result<ExchangeGraph> = ExchangeGraph.validating(
        json = CanonicalJsonWriter().bundle(
            profile = kind.bundleProfile,
            timestamp = ExchangeInstant().utc(instant = packedAt),
            identifier = transportIdentifier?.let { it.system.rawValue to it.value },
            entries = entries,
        ),
        kind = kind,
    )

    private companion object {
        const val DEFAULT_MAXIMUM_RECORDS = 2_000
        const val DEFAULT_MAXIMUM_BYTES = 16 shl 20
    }
}
