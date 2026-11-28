//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.fhir.contract

/**
 * What one record states about its producer and source repository beside the record itself: the
 * [adapter], the [identityScope], the [subject] and the [repositoryScope] every record of a producer
 * shares, and the [facts] about the application, its host and the studies.
 */
@InternalGroveFhirApi
data class ExchangeEnvelope(
    val adapter: ExchangeAdapterContract,
    val identityScope: OpaqueIdentityScope,
    val subject: Subject,
    val repositoryScope: BusinessIdentifier,
    val facts: ConversionFacts,
) {

    /**
     * The source-record identity of the native record [nativeRecordId] of [sourceType] in this repository.
     */
    fun sourceRecord(
        sourceType: String,
        nativeRecordId: String,
    ): Result<SourceRecordIdentity> = identityScope.sourceRecord(
        adapterId = adapter.adapterId,
        sourceType = sourceType,
        repositoryScope = repositoryScope,
        nativeRecordId = nativeRecordId,
    )

    companion object {

        /**
         * The envelope of [producer]'s records from the repository [repositoryScope], under [adapter].
         */
        fun of(
            producer: ExchangeProducer,
            adapter: ExchangeAdapterContract,
            repositoryScope: BusinessIdentifier,
        ): ExchangeEnvelope = ExchangeEnvelope(
            adapter = adapter,
            identityScope = producer.identityScope,
            subject = producer.subject,
            repositoryScope = repositoryScope,
            facts = producer.facts,
        )
    }
}
