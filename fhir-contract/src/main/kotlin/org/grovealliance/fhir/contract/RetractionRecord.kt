//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.fhir.contract

import java.time.Instant
import java.time.ZoneOffset

/**
 * The assertion that a source no longer exposes a source record, as a record of its own.
 *
 * It addresses the whole source record its Provenance's entity names: a receiver suppresses every
 * output of every version of it, and the [Target]s record what the producer emitted without binding
 * one. Its key depends on the source record alone, so a re-reported retraction repeats it and the
 * first accepted wins; it carries the key on its entry only.
 */
@InternalGroveFhirApi
class RetractionRecord private constructor(val record: ExchangeRecord) {

    /**
     * Why a retraction could not be built.
     */
    sealed class ValidationError(message: String) : Exception(message) {

        /**
         * The retraction names no target.
         */
        data object EmptyTargets : ValidationError("A retraction names at least one target.")

        /**
         * Two targets name the same identity.
         */
        data object DuplicateTarget : ValidationError("Two targets name the same identity.")

        /**
         * The source record is not a canonical opaque `source-record` identity.
         */
        data object InvalidSourceRecord : ValidationError("The retracted source record is not an opaque source-record identity.")

        /**
         * A native record identifier is numbered in one of the deployment's own identity systems.
         */
        data object ReservedIdentifierSystem : ValidationError("A native record identifier reuses a graph identity system.")

        /**
         * The occurrence period starts after it ends.
         */
        data object InvalidOccurrencePeriod : ValidationError("The retraction period starts after it ends.")

        /**
         * [role] can't name an identity whose role is [identifierRole], or a resource of [resourceType].
         */
        data class TargetMismatch(
            val role: Target.Role,
            val identifierRole: GroveIdentifierRole,
            val resourceType: String?,
        ) : ValidationError("A ${role.code} target can't name a ${identifierRole.code} identity of $resourceType.")
    }

    /**
     * One logical target: an identity the retracted record emitted, in [role], of [resourceType], with
     * the adapter's own [nativeRecordIdentifier] when the deployment discloses it. Targets are
     * informative; devices are never retracted.
     */
    @ConsistentCopyVisibility
    data class Target private constructor(
        val identifier: RoledIdentifier,
        val resourceType: String?,
        val role: Role,
        val nativeRecordIdentifier: BusinessIdentifier?,
    ) {

        /**
         * The role a prior output played in the retracted source record.
         */
        enum class Role(val code: String) {
            PRIMARY_OUTPUT("primary-output"),
            SOURCE_ARTIFACT("source-artifact"),
            CHILD_OUTPUT("child-output"),
            SPECIMEN("specimen"),
            SOURCE_RECORD("source-record"),
        }

        /**
         * The target as `Provenance.target`: a typed logical reference with its role.
         */
        internal val reference: Reference
            get() = Reference(
                extension = listOfNotNull(
                    Extension(
                        url = ExchangeContract.RETRACTION_TARGET_ROLE_EXTENSION,
                        value = Extension.Value.CodeValue(value = role.code),
                    ),
                    nativeRecordIdentifier?.let {
                        Extension(
                            url = ExchangeContract.RETRACTION_TARGET_NATIVE_IDENTIFIER_EXTENSION,
                            value = Extension.Value.IdentifierValue(value = it.fhirIdentifier),
                        )
                    },
                ),
                identifier = identifier.fhirIdentifier,
                type = resourceType,
            )

        companion object {

            /**
             * The target that retracts every output of [sourceRecord], including children a deletion
             * notification no longer lists.
             */
            fun sourceRecord(
                sourceRecord: RoledIdentifier,
                nativeRecordIdentifier: BusinessIdentifier? = null,
            ): Result<Target> =
                create(
                    identifier = sourceRecord,
                    resourceType = null,
                    role = Role.SOURCE_RECORD,
                    nativeRecordIdentifier = nativeRecordIdentifier,
                )

            /**
             * The target naming the output [identifier] of [resourceType] in [role].
             */
            fun output(
                identifier: RoledIdentifier,
                resourceType: String,
                role: Role,
                nativeRecordIdentifier: BusinessIdentifier? = null,
            ): Result<Target> = create(
                identifier = identifier,
                resourceType = resourceType,
                role = role,
                nativeRecordIdentifier = nativeRecordIdentifier,
            )

            private fun create(
                identifier: RoledIdentifier,
                resourceType: String?,
                role: Role,
                nativeRecordIdentifier: BusinessIdentifier?,
            ): Result<Target> {
                val expectedRole = if (role == Role.SOURCE_RECORD) GroveIdentifierRole.SOURCE_RECORD else GroveIdentifierRole.SOURCE_OUTPUT
                val allowedTypes = when (role) {
                    Role.PRIMARY_OUTPUT -> setOf(
                        ResourceType.OBSERVATION.value,
                        ResourceType.VISION_PRESCRIPTION.value,
                        ResourceType.MEDICATION_ADMINISTRATION.value,
                        ResourceType.MEDICATION_STATEMENT.value,
                    )
                    Role.SOURCE_ARTIFACT -> setOf(ResourceType.DOCUMENT_REFERENCE.value)
                    Role.CHILD_OUTPUT -> setOf(ResourceType.OBSERVATION.value)
                    Role.SPECIMEN -> setOf(ResourceType.SPECIMEN.value)
                    Role.SOURCE_RECORD -> setOf(null)
                }
                return if (identifier.role == expectedRole && resourceType in allowedTypes) {
                    Result.success(
                        Target(
                            identifier = identifier,
                            resourceType = resourceType,
                            role = role,
                            nativeRecordIdentifier = nativeRecordIdentifier,
                        ),
                    )
                } else {
                    Result.failure(
                        ValidationError.TargetMismatch(
                            role = role,
                            identifierRole = identifier.role,
                            resourceType = resourceType,
                        ),
                    )
                }
            }
        }
    }

    /**
     * When the source retracted a record, as precisely as the producer knows it.
     */
    sealed interface Occurrence {

        /**
         * The source's own deletion time, or the time the producer detected the deletion.
         */
        data class At(val instant: Instant) : Occurrence

        /**
         * Bounds on a deletion time the source doesn't state: after [start] when known, no later than [end].
         */
        data class Between(
            val start: Instant?,
            val end: Instant,
        ) : Occurrence
    }

    companion object {
        private val EARLIEST_STATABLE: Instant = Instant.parse("0001-01-01T00:00:00Z")

        /**
         * The retraction of [sourceRecord], recorded at [instant] and assembled by [application], which
         * runs on [host].
         */
        @Suppress("detekt:LongParameterList")
        internal fun create(
            instant: Instant,
            identityScope: OpaqueIdentityScope,
            subject: BusinessIdentifier,
            application: Bundle.Entry,
            host: Bundle.Entry,
            sourceRecord: RoledIdentifier,
            targets: List<Target>,
            occurred: Occurrence,
        ): Result<RetractionRecord> {
            validationError(
                identityScope = identityScope,
                sourceRecord = sourceRecord,
                targets = targets,
                occurred = occurred,
            )?.let { return Result.failure(it) }
            val provenance = provenance(
                instant = instant,
                application = application,
                sourceRecord = sourceRecord,
                targets = targets,
                occurred = occurred,
            )
            return identityScope.recordRetraction(sourceRecord = sourceRecord.identifier).fold(
                onSuccess = { key ->
                    RecordVersion.create(identifier = key).mapCatching { version ->
                        val entries = listOf(Bundle.Entry.keyed(identifier = key, resource = provenance), host, application)
                        val record = ExchangeRecord(
                            kind = ExchangeRecord.Kind.RETRACTION,
                            subject = subject,
                            sourceRecord = sourceRecord.identifier,
                            version = version,
                            outputFullUrls = emptyList(),
                            entries = entries.map { ExchangeRecord.Entry(fullUrl = it.fullUrl, json = it.toJson().canonicalBytes()) },
                            timestamp = provenance.recorded,
                        )
                        RetractionRecord(record = record.validated().getOrThrow())
                    }
                },
                onFailure = { Result.failure(it) },
            )
        }

        private fun provenance(
            instant: Instant,
            application: Bundle.Entry,
            sourceRecord: RoledIdentifier,
            targets: List<Target>,
            occurred: Occurrence,
        ): Provenance {
            val exchangeInstant = ExchangeInstant()
            return Provenance(
                meta = Meta(profile = listOf(ExchangeContract.RETRACTION_PROVENANCE_PROFILE)),
                target = targets.map { it.reference },
                occurredDateTime = (occurred as? Occurrence.At)?.let { exchangeInstant.utc(instant = it.instant) },
                occurredPeriod = (occurred as? Occurrence.Between)?.let { period ->
                    Period(
                        start = period.start?.let { exchangeInstant.at(instant = maxOf(it, EARLIEST_STATABLE), offset = ZoneOffset.UTC) },
                        end = exchangeInstant.at(instant = period.end, offset = ZoneOffset.UTC),
                    )
                },
                recorded = exchangeInstant.utc(instant = instant),
                activity = CodeableConcept(
                    coding = listOf(
                        Coding(
                            system = ExchangeContract.LIFECYCLE_EVENT_CODE_SYSTEM,
                            code = ExchangeContract.RETRACTION_ACTIVITY_CODE,
                            display = "Source record retracted",
                        ),
                    ),
                ),
                agent = listOf(
                    Provenance.Agent(
                        type = CodeableConcept(
                            coding = listOf(
                                Coding(
                                    system = Hl7Url.PROVENANCE_PARTICIPANT_TYPE,
                                    code = "assembler",
                                    display = "Assembler",
                                ),
                            ),
                        ),
                        who = Reference(reference = application.fullUrl),
                    ),
                ),
                entity = listOf(Provenance.Entity(role = "source", what = Reference(identifier = sourceRecord.fhirIdentifier))),
            )
        }

        private fun validationError(
            identityScope: OpaqueIdentityScope,
            sourceRecord: RoledIdentifier,
            targets: List<Target>,
            occurred: Occurrence,
        ): ValidationError? {
            val reserved = identityScope.systems.all.toSet()
            return when {
                targets.isEmpty() -> ValidationError.EmptyTargets
                targets.map { it.identifier.identifier }.toSet().size != targets.size -> ValidationError.DuplicateTarget
                sourceRecord.role != GroveIdentifierRole.SOURCE_RECORD ||
                    !OpaqueIdentityScope.isCanonicalOpaqueValue(value = sourceRecord.identifier.value) ->
                    ValidationError.InvalidSourceRecord
                targets.any { it.nativeRecordIdentifier?.system in reserved } -> ValidationError.ReservedIdentifierSystem
                occurred is Occurrence.Between && occurred.start != null && occurred.start > occurred.end ->
                    ValidationError.InvalidOccurrencePeriod
                else -> null
            }
        }
    }
}
