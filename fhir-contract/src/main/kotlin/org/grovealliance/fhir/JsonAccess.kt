//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.fhir

internal fun JsonToken?.asObject(): JsonToken.Object? = this as? JsonToken.Object

internal fun JsonToken?.asText(): String? = (this as? JsonToken.Text)?.value

internal fun JsonToken?.asItems(): List<JsonToken>? = (this as? JsonToken.Array)?.items

internal operator fun JsonToken.Object.get(key: String): JsonToken? = members[key]

internal operator fun JsonToken.Object.contains(key: String): Boolean = key in members

internal fun JsonToken.Object.text(key: String): String? = members[key].asText()

internal fun JsonToken.Object.obj(key: String): JsonToken.Object? = members[key].asObject()

internal fun JsonToken.Object.items(key: String): List<JsonToken>? = members[key].asItems()

/** The member's object items, or null when the member is present but is not an array of objects. */
internal fun JsonToken.Object.objects(key: String): List<JsonToken.Object>? {
    val items = members[key] ?: return emptyList()
    val objects = items.asItems()?.map { it.asObject() ?: return null }
    return objects
}

/** The member's string items, or null when the member is present but is not an array of strings. */
internal fun JsonToken.Object.strings(key: String): List<String>? {
    val items = members[key] ?: return emptyList()
    return items.asItems()?.map { it.asText() ?: return null }
}

internal val JsonToken.Object.resourceType: String?
    get() = text("resourceType")

/** The direct `meta.profile` claims, or null when they are not an array of strings. */
internal fun JsonToken.Object.profiles(): List<String>? = obj("meta")?.strings("profile") ?: emptyList<String>().takeIf { "meta" !in this }
