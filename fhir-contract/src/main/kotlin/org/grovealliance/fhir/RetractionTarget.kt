//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.fhir

import org.hl7.fhir.r4.model.CodeType
import org.hl7.fhir.r4.model.Extension
import org.hl7.fhir.r4.model.Identifier
import org.hl7.fhir.r4.model.Reference

/** One prior graph node a retraction names by its typed logical identifier, target role and resource type. */
public class RetractionTarget(
    public val identifier: RoledIdentifier,
    public val resourceType: String,
    public val role: RetractionTargetRole,
    nativeRecordIdentifier: Identifier? = null,
) {
    private val nativeSnapshot: Identifier? = nativeRecordIdentifier?.copy()

    init {
        require(identifier.role == role.identifierRole) {
            "Retraction target role ${role.code} requires the ${role.identifierRole.code} identifier role."
        }
        require(resourceType in role.resourceTypes) {
            "Retraction target role ${role.code} does not admit resource type $resourceType."
        }
        nativeSnapshot?.let { native ->
            BusinessIdentifier.from(native)
            require(native.type.coding.none { it.system == ExchangeContract.GROVE_IDENTIFIER_ROLE }) {
                "A native record identifier never carries a Grove identifier-role coding."
            }
        }
    }

    /** The exact source-native identifier of the retracted record, when the deployment disclosed it. */
    public val nativeRecordIdentifier: Identifier?
        get() = nativeSnapshot?.copy()

    internal fun toReference(): Reference = Reference().apply {
        type = resourceType
        identifier = this@RetractionTarget.identifier.toFhir()
        addExtension(Extension(ExchangeContract.RETRACTION_TARGET_ROLE_EXTENSION, CodeType(role.code)))
        nativeSnapshot?.let {
            addExtension(Extension(ExchangeContract.RETRACTION_TARGET_NATIVE_IDENTIFIER_EXTENSION, it.copy()))
        }
    }

    override fun equals(other: Any?): Boolean =
        other is RetractionTarget && identifier == other.identifier && resourceType == other.resourceType &&
            role == other.role && (nativeSnapshot?.equalsDeep(other.nativeSnapshot) ?: (other.nativeSnapshot == null))

    override fun hashCode(): Int = listOf(identifier, resourceType, role).hashCode()

    override fun toString(): String =
        "RetractionTarget(identifier=$identifier, resourceType=$resourceType, role=${role.code})"
}
