//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.fhir

import org.hl7.fhir.r4.model.CodeType
import org.hl7.fhir.r4.model.Extension
import org.hl7.fhir.r4.model.Reference
import org.hl7.fhir.r4.model.ResourceType

/** One prior graph node a retraction names by its typed logical identifier, target role and resource type. */
public class RetractionTarget(
    public val identifier: RoledIdentifier,
    public val resourceType: ResourceType,
    public val role: RetractionTargetRole,
    /** The exact source-native identifier of the retracted record, when the deployment's disclosure policy authorized it. */
    public val nativeRecordIdentifier: BusinessIdentifier? = null,
) {
    init {
        require(identifier.role == role.identifierRole) {
            "Retraction target role ${role.code} requires the ${role.identifierRole.code} identifier role."
        }
        require(resourceType in role.resourceTypes) {
            "Retraction target role ${role.code} does not admit resource type $resourceType."
        }
    }

    internal fun toReference(): Reference = Reference().apply {
        type = resourceType.name
        identifier = this@RetractionTarget.identifier.toFhir()
        addExtension(Extension(ExchangeContract.RETRACTION_TARGET_ROLE_EXTENSION, CodeType(role.code)))
        nativeRecordIdentifier?.let {
            addExtension(Extension(ExchangeContract.RETRACTION_TARGET_NATIVE_IDENTIFIER_EXTENSION, it.toFhir()))
        }
    }

    override fun equals(other: Any?): Boolean =
        other is RetractionTarget && identifier == other.identifier && resourceType == other.resourceType &&
            role == other.role && nativeRecordIdentifier == other.nativeRecordIdentifier

    override fun hashCode(): Int = listOf(identifier, resourceType, role, nativeRecordIdentifier).hashCode()

    override fun toString(): String =
        "RetractionTarget(identifier=$identifier, resourceType=$resourceType, role=${role.code})"
}
