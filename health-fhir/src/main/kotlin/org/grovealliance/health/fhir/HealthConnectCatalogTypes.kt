//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.health.fhir

import org.grovealliance.fhir.QuantityValueDomain
import org.grovealliance.fhir.RetractionTargetRole
import org.hl7.fhir.r4.model.CodeableConcept
import org.hl7.fhir.r4.model.Coding

/** One adapter catalog row: how the catalog classifies a Record class and what it emits for it. */
public data class HealthConnectCatalogEntry(
    public val type: HealthConnectSourceType,
    public val status: HealthConnectSourceStatus,
    public val contexts: Set<String>,
    public val reason: String?,
    public val outputs: List<HealthConnectOutput>,
)

/** One output the catalog admits for a source type, with its count rule and retraction role. */
public data class HealthConnectOutput(
    public val measurement: String?,
    public val resourceType: String,
    public val countRule: HealthConnectOutputCountRule,
    public val outputRole: String,
    public val retractionRole: RetractionTargetRole,
    public val graphRule: String?,
    public val condition: String?,
)

internal data class CatalogCoding(val system: String, val code: String, val display: String?) {
    fun coding(): Coding = Coding(system, code, display)

    fun concept(): CodeableConcept = CodeableConcept(coding())
}

internal data class QuantitySpec(val system: String, val code: String, val unit: String, val domain: QuantityValueDomain?)

internal enum class MeasurementValueKind { QUANTITY, CODEABLE_CONCEPT, COMPONENTS }

internal enum class EffectiveShape { DATE_TIME, PERIOD, DATE_TIME_OR_PERIOD }

internal data class ComponentSpec(val id: String, val code: CatalogCoding, val quantity: QuantitySpec?)

/** One catalog measurement as the adapter emits it: profile, codings, value shape and effective shape. */
internal data class MeasurementSpec(
    val id: String,
    val profile: String,
    val adapterSpecific: Boolean,
    val code: CatalogCoding,
    val requiredCodings: List<CatalogCoding>,
    val category: CatalogCoding?,
    val valueKind: MeasurementValueKind,
    val quantity: QuantitySpec?,
    val resultCodeSystem: String?,
    val components: List<ComponentSpec>,
    val effective: EffectiveShape,
) {
    fun component(id: String): ComponentSpec = components.first { it.id == id }
}

internal data class SourceCodedValue(val source: String, val code: String, val display: String, val shared: String)

/** A source enumeration absorbed into a shared Grove CodeSystem while retaining the exact source token. */
internal data class SourceCodedMapping(
    val sourceSystem: String,
    val appliesToMeasurement: String,
    val values: List<SourceCodedValue>,
) {
    fun value(source: String): SourceCodedValue? = values.firstOrNull { it.source == source }
}

internal data class SourceOnlyValue(val source: String, val code: String, val display: String)

/** A source enumeration carried only in its own Health Connect CodeSystem. */
internal data class SourceOnlyMapping(val codeSystem: String, val values: List<SourceOnlyValue>) {
    fun coding(
        source: String,
    ): Coding? = values.firstOrNull { it.source == source }?.let { Coding(codeSystem, it.code, it.display) }
}

internal data class ExternalCodedValue(val source: String, val coding: CatalogCoding?)

/** A source enumeration mapped onto an external terminology; a null coding means the value is omitted. */
internal data class ExternalCodedMapping(val values: List<ExternalCodedValue>) {
    fun value(source: String): ExternalCodedValue? = values.firstOrNull { it.source == source }
}

internal data class SpecimenSourceValue(
    val source: String,
    val status: HealthConnectSourceStatus,
    val measurement: String?,
    val specimenType: CatalogCoding?,
)

/** The closed source tokens a measurement admits in its exact-source coding. */
internal data class AllowedSourceCodes(
    val sourceSystem: String,
    val appliesToMeasurement: String,
    val codes: Set<String>,
)
