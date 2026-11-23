//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.fhir.contract

import org.grovealliance.fhir.contract.internal.CanonicalJsonWriter
import org.grovealliance.fhir.contract.internal.ExchangeHashing
import org.grovealliance.fhir.contract.internal.LosslessJsonParser

/**
 * A JSON document read without losing what the exchange compares: number lexemes stay text, so
 * `72` and `72.0` differ, and strings keep every Unicode scalar they were written with.
 *
 * Member names are ASCII, as in every FHIR resource; the parser refuses others, so names compare
 * bytewise.
 */
@InternalGroveFhirApi
sealed interface LosslessJsonValue {

    /**
     * An object, by member name.
     */
    data class JsonObject(val members: Map<String, LosslessJsonValue>) : LosslessJsonValue

    /**
     * An array, in order.
     */
    data class JsonArray(val elements: List<LosslessJsonValue>) : LosslessJsonValue

    /**
     * A string.
     */
    data class JsonString(val value: String) : LosslessJsonValue

    /**
     * A number, as its exact lexeme.
     */
    data class JsonNumber(val lexeme: String) : LosslessJsonValue

    /**
     * `true` or `false`.
     */
    data class JsonBoolean(val value: Boolean) : LosslessJsonValue

    /**
     * `null`.
     */
    data object JsonNull : LosslessJsonValue

    /**
     * The document in `grove-canonical-json-v0`, the byte form every Grove digest hashes: members
     * sorted by name, no insignificant whitespace, strings escaped as RFC 8785 escapes them, and
     * numbers as their lexemes.
     */
    fun canonicalBytes(): ByteArray = CanonicalJsonWriter().write(value = this)

    /**
     * The `content-digest` component of a shared node's content-snapshot key: SHA-256, base64url
     * without padding, over the node's canonical bytes minus `id` and minus its own snapshot key.
     *
     * The key is the snapshot identifier in `identifier` (dropped if left empty); on a
     * QuestionnaireResponse, whose one identifier holds the submission's, it is every
     * `grove-context-snapshot` extension (with `extension` dropped if left empty).
     */
    fun contentDigest(): Result<String> {
        val content = (this as? JsonObject)?.withoutMember(name = "id")
            ?: return Result.failure(ExchangeIdentityError.InvalidDigestContent(reason = "A resource is a JSON object."))
        val resourceType = (content.members["resourceType"] as? JsonString)?.value
        val stripped = when (ResourceType.fromValue(value = resourceType)) {
            ResourceType.QUESTIONNAIRE_RESPONSE -> content.withoutElements(name = "extension") {
                it.isExtension(url = ExchangeContract.CONTEXT_SNAPSHOT_EXTENSION)
            }
            ResourceType.DEVICE ->
                content.withoutElements(name = "identifier") { it.isIdentifier(role = GroveIdentifierRole.DEVICE_SNAPSHOT) }
            ResourceType.PATIENT, ResourceType.RESEARCH_STUDY, ResourceType.RESEARCH_SUBJECT, ResourceType.PLAN_DEFINITION ->
                content.withoutElements(name = "identifier") { it.isIdentifier(role = GroveIdentifierRole.CONTEXT_SNAPSHOT) }
            else -> if (resourceType == null) {
                Result.failure(ExchangeIdentityError.InvalidDigestContent(reason = "A resource states no resourceType."))
            } else {
                Result.failure(ExchangeIdentityError.InvalidDigestContent(reason = "$resourceType carries no content-snapshot key."))
            }
        }
        return stripped.map { ExchangeHashing().run { base64Url(bytes = sha256(data = it.canonicalBytes())) } }
    }

    companion object {

        /**
         * The document [bytes] state, or a [LosslessJsonParseException] naming the
         * `grove-canonical-json-v0` refusal.
         */
        fun parse(bytes: ByteArray): Result<LosslessJsonValue> = LosslessJsonParser(bytes = bytes).parse()

        /**
         * The `record-content-digest` component of a record-version key: SHA-256, base64url without
         * padding, over the length-framed record-content label, the canonical bytes of [provenance] and
         * those of each of [outputs].
         *
         * The Provenance loses `id` and every `grove-record-version` extension (with `extension` dropped
         * if left empty); each output loses `id`. Parse every resource from the bytes the record carries,
         * never from a FHIR model's re-encoding, which rewrites decimal lexemes.
         *
         * @param provenance the record's conversion Provenance
         * @param outputs the resources `Provenance.target` resolves to, in target order
         */
        @Suppress("detekt:ReturnCount")
        fun recordContentDigest(
            provenance: LosslessJsonValue,
            outputs: List<LosslessJsonValue>,
        ): Result<String> {
            val members = (provenance as? JsonObject)?.members
            if ((members?.get("resourceType") as? JsonString)?.value != ResourceType.PROVENANCE.value) {
                return Result.failure(ExchangeIdentityError.InvalidDigestContent(reason = "The record content starts with a Provenance."))
            }
            val targets = (members["target"] as? JsonArray)?.elements.orEmpty()
            if (targets.isEmpty()) {
                return Result.failure(
                    ExchangeIdentityError.InvalidDigestContent(reason = "A conversion Provenance targets at least one output."),
                )
            }
            if (targets.size != outputs.size) {
                return Result.failure(
                    ExchangeIdentityError.InvalidDigestContent(reason = "A record states exactly one output per Provenance.target."),
                )
            }
            val provenanceContent = provenance.withoutMember(name = "id")
                .withoutElements(name = "extension") { it.isExtension(url = ExchangeContract.RECORD_VERSION_EXTENSION) }
                .getOrElse { return Result.failure(it) }
            val outputContents = outputs.map { output ->
                (output as? JsonObject)?.withoutMember(name = "id")
                    ?: return Result.failure(ExchangeIdentityError.InvalidDigestContent(reason = "A resource is a JSON object."))
            }
            val hashing = ExchangeHashing()
            val fields = listOf(ExchangeContract.RECORD_CONTENT_DOMAIN.toByteArray(Charsets.UTF_8), provenanceContent.canonicalBytes()) +
                outputContents.map { it.canonicalBytes() }
            return Result.success(hashing.base64Url(bytes = hashing.sha256(data = hashing.lengthFramed(fields = fields))))
        }

        private fun JsonObject.withoutMember(name: String) = JsonObject(members = members - name)

        /**
         * This object minus every element of the array member [name] that [isRemoved] matches, and minus
         * [name] itself if it is left empty.
         */
        private fun JsonObject.withoutElements(
            name: String,
            isRemoved: (LosslessJsonValue) -> Boolean,
        ): Result<JsonObject> {
            val value = members[name] ?: return Result.success(this)
            val elements = (value as? JsonArray)?.elements
                ?: return Result.failure(ExchangeIdentityError.InvalidDigestContent(reason = "$name is a JSON array."))
            val kept = elements.filterNot(isRemoved)
            val remaining = if (kept.isEmpty()) members - name else members + (name to JsonArray(elements = kept))
            return Result.success(JsonObject(members = remaining))
        }

        private fun LosslessJsonValue.isExtension(url: String) = ((this as? JsonObject)?.members?.get("url") as? JsonString)?.value == url

        private fun LosslessJsonValue.isIdentifier(role: GroveIdentifierRole): Boolean {
            val codings = (((this as? JsonObject)?.members?.get("type") as? JsonObject)?.members?.get("coding") as? JsonArray)?.elements
            return codings.orEmpty().any { coding ->
                val members = (coding as? JsonObject)?.members
                (members?.get("system") as? JsonString)?.value == GroveIdentifierRole.CODE_SYSTEM &&
                    (members["code"] as? JsonString)?.value == role.code
            }
        }
    }
}

/**
 * Why a document has no lossless reading, by its `grove-canonical-json-v0` refusal [reason].
 */
@InternalGroveFhirApi
class LosslessJsonParseException(val reason: Reason) : Exception(reason.code) {

    /**
     * A refusal, with the [code] the exchange protocol names it by.
     */
    enum class Reason(val code: String) {
        INVALID_JSON("invalid-json"),
        INVALID_UTF8("invalid-utf8"),
        BYTE_ORDER_MARK("byte-order-mark"),
        DUPLICATE_MEMBER("duplicate-member"),
        NON_ASCII_MEMBER_NAME("non-ascii-member-name"),
        UNPAIRED_SURROGATE("unpaired-surrogate"),
    }
}
