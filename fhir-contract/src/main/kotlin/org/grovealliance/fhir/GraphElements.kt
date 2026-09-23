//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.fhir

import org.hl7.fhir.r4.model.Bundle
import org.hl7.fhir.r4.model.Coding
import org.hl7.fhir.r4.model.Extension
import org.hl7.fhir.r4.model.Identifier
import org.hl7.fhir.r4.model.Reference
import org.hl7.fhir.r4.model.Resource

/** One resource with the complete business identity that keys its Bundle entry. */
@InternalGroveFhirApi
public class GraphEntry(public val identifier: RoledIdentifier, resource: Resource) {
    private val snapshot: Resource = resource.copy()

    /** The deterministic UUID URN of this entry. */
    public val fullUrl: String
        get() = identifier.fullUrl

    /** A copy of the entry resource. */
    public val resource: Resource
        get() = snapshot.copy()

    /** A literal reference to this entry. */
    public fun reference(): Reference = Reference(fullUrl)

    internal fun addTo(bundle: Bundle): Bundle.BundleEntryComponent = bundle.addEntry().apply {
        fullUrl = this@GraphEntry.fullUrl
        addExtension(Extension(ExchangeContract.ENTRY_NODE_KEY_EXTENSION, identifier.toFhir()))
        resource = snapshot.copy()
    }
}

internal fun Resource.directIdentifiers(): List<Identifier> =
    getChildByName("identifier")?.values.orEmpty().filterIsInstance<Identifier>()

internal fun Identifier.groveRoleCodings(): List<Coding> = type.coding.filter { it.system == ExchangeContract.GROVE_IDENTIFIER_ROLE }

internal fun Identifier.matchesPair(other: BusinessIdentifier): Boolean =
    system == other.system.value && value == other.value

/** The typed Grove business identifiers of one validated resource, by role. */
internal fun typedGroveIdentifiers(resource: Resource): Map<GroveIdentifierRole, RoledIdentifier> =
    resource.directIdentifiers().mapNotNull(RoledIdentifier::from).associateBy { it.role }
