//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.fhir.contract

import org.grovealliance.fhir.contract.internal.JsonObjectBuilder

/**
 * A FHIR `Bundle` of [type] `collection`: an exchange or retraction Bundle of whole records.
 */
data class Bundle(
    val meta: Meta? = null,
    val identifier: Identifier? = null,
    val type: String = "collection",
    val timestamp: String? = null,
    val entry: List<Entry> = emptyList(),
) : FhirResource {

    override val resourceType: ResourceType get() = ResourceType.BUNDLE

    /**
     * One entry: [resource] at its deterministic [fullUrl], keyed in an entry-key [extension].
     */
    data class Entry(
        val extension: List<Extension> = emptyList(),
        val fullUrl: String,
        val resource: FhirResource,
    ) : FhirElement {

        @InternalGroveFhirApi
        override fun toJson(): LosslessJsonValue = JsonObjectBuilder()
            .elements(name = "extension", values = extension)
            .string(name = "fullUrl", value = fullUrl)
            .element(name = "resource", value = resource)
            .build()

        companion object {

            /**
             * The entry of [resource], keyed by [identifier] in the entry-key extension, at the [fullUrl]
             * that identifier names.
             */
            @InternalGroveFhirApi
            fun keyed(
                identifier: RoledIdentifier,
                resource: FhirResource,
                fullUrl: String = identifier.fullUrl,
            ): Entry = Entry(
                extension = listOf(
                    Extension(
                        url = ExchangeContract.ENTRY_KEY_EXTENSION,
                        value = Extension.Value.IdentifierValue(value = identifier.fhirIdentifier),
                    ),
                ),
                fullUrl = fullUrl,
                resource = resource,
            )
        }
    }

    @InternalGroveFhirApi
    override fun toJson(): LosslessJsonValue = JsonObjectBuilder()
        .string(name = "resourceType", value = resourceType.value)
        .element(name = "meta", value = meta)
        .element(name = "identifier", value = identifier)
        .string(name = "type", value = type)
        .string(name = "timestamp", value = timestamp)
        .elements(name = "entry", values = entry)
        .build()
}
