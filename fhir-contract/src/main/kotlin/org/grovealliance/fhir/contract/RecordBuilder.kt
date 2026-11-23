//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.fhir.contract

/**
 * Turns a finished conversion into its [ExchangeRecord], the one place a record version is minted.
 */
@InternalGroveFhirApi
internal class RecordBuilder {

    /**
     * The record of [provenance] and [entries]: the record version is minted over the Provenance and the
     * outputs it targets as they travel, then the Provenance repeats it in `grove-record-version`, the
     * one extension the digest leaves out.
     *
     * @param entries every other entry of the record, in Bundle order: its outputs and its shared nodes
     */
    fun conversionRecord(
        provenance: Provenance,
        sourceRecord: RoledIdentifier,
        entries: List<Bundle.Entry>,
        subject: BusinessIdentifier,
        identityScope: OpaqueIdentityScope,
    ): Result<ExchangeRecord> {
        val resources = entries.associate { it.fullUrl to it.resource }
        val unresolved = provenance.target.firstOrNull { resources[it.reference] == null }
        if (unresolved != null) return Result.failure(ExchangeAssemblyError.UnresolvedTarget(reference = unresolved.reference))
        return runCatching {
            record(
                provenance = provenance,
                sourceRecord = sourceRecord,
                entries = entries,
                outputs = provenance.target.mapNotNull { resources[it.reference]?.toJson() },
                subject = subject,
                identityScope = identityScope,
            ).validated().getOrThrow()
        }
    }

    @Suppress("detekt:LongParameterList")
    private fun record(
        provenance: Provenance,
        sourceRecord: RoledIdentifier,
        entries: List<Bundle.Entry>,
        outputs: List<LosslessJsonValue>,
        subject: BusinessIdentifier,
        identityScope: OpaqueIdentityScope,
    ): ExchangeRecord {
        val digest = LosslessJsonValue.recordContentDigest(provenance = provenance.toJson(), outputs = outputs).getOrThrow()
        val key = identityScope.recordVersion(sourceRecord = sourceRecord.identifier, contentDigest = digest).getOrThrow()
        val keyed = provenance.copy(
            extension = provenance.extension + Extension(
                url = ExchangeContract.RECORD_VERSION_EXTENSION,
                value = Extension.Value.IdentifierValue(value = key.fhirIdentifier),
            ),
        )
        val version = RecordVersion.create(identifier = key).getOrThrow()
        val allEntries = entries + Bundle.Entry.keyed(identifier = key, resource = keyed)
        val outputUrls = provenance.target.mapNotNull { it.reference }
        return ExchangeRecord(
            kind = ExchangeRecord.Kind.ACTIVE,
            subject = subject,
            sourceRecord = sourceRecord.identifier,
            version = version,
            outputFullUrls = outputUrls,
            entries = allEntries.map { ExchangeRecord.Entry(fullUrl = it.fullUrl, json = it.toJson().canonicalBytes()) },
            timestamp = keyed.recorded,
        )
    }
}
