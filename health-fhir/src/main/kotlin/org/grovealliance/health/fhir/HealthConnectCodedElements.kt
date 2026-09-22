//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.health.fhir

import org.hl7.fhir.r4.model.CodeableConcept
import org.hl7.fhir.r4.model.Coding

/** A shared Grove coding followed by the exact source coding, as every absorbed enumeration is stated. */
internal fun codedValue(mapping: SourceCodedMapping, sharedSystem: String, value: SourceCodedValue): CodeableConcept =
    CodeableConcept().apply {
        addCoding(Coding(sharedSystem, value.shared, HealthConnectContextMappings.displays[sharedSystem]?.get(value.shared)))
        addCoding(Coding(mapping.sourceSystem, value.code, value.display))
    }

/** The external coding of a source enumeration, or null when the catalog maps the value to nothing. */
internal fun RecordConversion.externalCoding(
    mapping: ExternalCodedMapping,
    tokens: Map<Int, String>,
    value: SourceEnum,
): Coding? {
    val field = "${type.token}.${value.field}"
    val token = tokens.token(value.value, field)
    val mapped = mapping.value(token) ?: refuse(HealthConnectValueFailure.UnsupportedSourceValue(field))
    return mapped.coding?.coding()
}

internal fun RecordConversion.externalConcept(mapping: ExternalCodedMapping, tokens: Map<Int, String>, value: SourceEnum) =
    externalCoding(mapping, tokens, value)?.let(::CodeableConcept)

internal fun RecordConversion.sourceOnlyCoding(mapping: SourceOnlyMapping, tokens: Map<Int, String>, value: SourceEnum): Coding {
    val field = "${type.token}.${value.field}"
    return mapping.coding(tokens.token(value.value, field)) ?: refuse(HealthConnectValueFailure.UnsupportedSourceValue(field))
}
