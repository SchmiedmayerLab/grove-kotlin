//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.fhir

/** The Grove identifier-role codes an Identifier-shaped object claims, in order. */
internal fun JsonToken.Object.groveRoleCodes(): List<String> = groveRoleCodings().mapNotNull { it.text("code") }

internal fun JsonToken.Object.groveRoleCodings(): List<JsonToken.Object> =
    obj("type")?.items("coding").orEmpty().mapNotNull { it.asObject() }
        .filter { it.text("system") == ExchangeContract.GROVE_IDENTIFIER_ROLE }

/** The complete system and value of an Identifier-shaped object, or null when either is missing or invalid. */
internal fun JsonToken?.identifierPair(): BusinessIdentifier? {
    val identifier = asObject()
    val system = identifier?.text("system")?.takeIf { it.isNotEmpty() && ExchangeProtocol.isAbsoluteAsciiUri(it) }
    val value = identifier?.text("value")?.takeIf { it.isNotEmpty() }
    return if (system == null || value == null) null else BusinessIdentifier(IdentifierSystem(system), value)
}

/** Every object in the tree with its FHIR-style element path, parents before children. */
internal fun JsonToken.walkObjects(path: String, visit: (String, JsonToken.Object) -> Unit) {
    when (this) {
        is JsonToken.Object -> {
            visit(path, this)
            members.forEach { (key, child) -> child.walkObjects(if (path.isEmpty()) key else "$path.$key", visit) }
        }
        is JsonToken.Array -> items.forEachIndexed { index, child -> child.walkObjects("$path[$index]", visit) }
        else -> Unit
    }
}

/** Every Reference-shaped object carrying a literal reference, with its element path. */
internal fun JsonToken.literalReferences(): List<Pair<String, JsonToken.Object>> = buildList {
    walkObjects("") { path, node -> if (node["reference"] is JsonToken.Text) add(path to node) }
}

/** Every extension object in the tree, an object's own extensions before its children's. */
internal fun JsonToken.allExtensions(): List<JsonToken.Object> = buildList {
    walkObjects("") { _, node -> node.items("extension")?.forEach { item -> item.asObject()?.let(::add) } }
}

/** Every system and code pair of a Coding-shaped object in the tree. */
internal fun JsonToken.codingPairs(): List<Pair<String, String>> = buildList {
    walkObjects("") { _, node ->
        val system = node.text("system")
        val code = node.text("code")
        if (system != null && code != null) add(system to code)
    }
}

/** How often an exact Identifier system and value pair appears anywhere in the tree. */
internal fun JsonToken.identifierOccurrences(pair: BusinessIdentifier): Int {
    var count = 0
    walkObjects("") { _, node -> if (node.text("system") == pair.system.value && node.text("value") == pair.value) count++ }
    return count
}
