//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

@file:OptIn(InternalGroveFhirApi::class)

package org.grovealliance.health.fhir

import androidx.health.connect.client.records.BloodGlucoseRecord
import org.grovealliance.fhir.GraphEntry
import org.grovealliance.fhir.InternalGroveFhirApi
import org.hl7.fhir.r4.model.Extension
import org.hl7.fhir.r4.model.Reference
import org.hl7.fhir.r4.model.Specimen

/** Blood glucose: the specimen source selects the adapter profile and a synthesized Specimen joins the graph. */
internal fun RecordConversion.convertBloodGlucose(record: BloodGlucoseRecord): HealthConnectConversionResult {
    val token = HealthConnectSourceTokens.specimenSource.token(record.specimenSource, "${type.token}.specimenSource")
    val specimenSource = HealthConnectContextMappings.bloodGlucoseSpecimen.firstOrNull { it.source == token }
        ?.takeIf { it.status == HealthConnectSourceStatus.SUPPORTED }
        ?: refuse(HealthConnectValueFailure.UnsupportedSourceValue("${type.token}.specimenSource"))
    val spec = spec(requireNotNull(specimenSource.measurement))
    val quantitySpec = requireNotNull(spec.quantity)
    val level = decimal(record.level.inMilligramsPerDeciliter.at("level"), quantitySpec)
    val specimenIdentity = output(SPECIMEN_ROLE, token)
    val specimen = GraphEntry(
        specimenIdentity,
        Specimen().apply {
            meta.addProfile(HealthConnectContract.HEALTH_CONNECT_SPECIMEN_PROFILE)
            addIdentifier(sourceRecord.identifier.toFhir())
            addIdentifier(specimenIdentity.toFhir())
            status = Specimen.SpecimenStatus.AVAILABLE
            type = requireNotNull(specimenSource.specimenType).concept()
            subject = subjectReference()
        },
    )
    val output = observation(spec, singleOutput(spec.id), disclosesNativeIdentifier = true) {
        effective = dateTime(record.time.at(record.zoneOffset), "${type.token}.time")
        value = quantity(quantitySpec, level)
        this.specimen = Reference(specimen.fullUrl)
        mealContext(record)?.let(::addExtension)
    }
    return finish(output, companions = listOf(specimen))
}

private fun RecordConversion.mealContext(record: BloodGlucoseRecord): Extension? {
    val relationToken = HealthConnectSourceTokens.relationToMeal.token(record.relationToMeal, "${type.token}.relationToMeal")
    val mealToken = HealthConnectSourceTokens.mealType.token(record.mealType, "${type.token}.mealType")
    val relation = HealthConnectContextMappings.relationToMeal.coding(relationToken)
        ?.takeIf { record.relationToMeal != BloodGlucoseRecord.RELATION_TO_MEAL_UNKNOWN }
    val meal = HealthConnectContextMappings.mealType.coding(mealToken)
        ?.takeIf { record.mealType != androidx.health.connect.client.records.MealType.MEAL_TYPE_UNKNOWN }
    if (relation == null && meal == null) return null
    return Extension(HealthConnectContract.HEALTH_CONNECT_GLUCOSE_MEAL_CONTEXT).apply {
        relation?.let { addExtension(Extension("relationToMeal", it)) }
        meal?.let { addExtension(Extension("mealType", it)) }
    }
}

private const val SPECIMEN_ROLE = "specimen"
