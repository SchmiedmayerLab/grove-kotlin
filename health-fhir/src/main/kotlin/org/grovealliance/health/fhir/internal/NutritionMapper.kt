//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

@file:Suppress("detekt:ReturnCount")

package org.grovealliance.health.fhir.internal

import androidx.health.connect.client.records.MealType
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Mass
import org.grovealliance.fhir.contract.ExchangeGraphRule
import org.grovealliance.fhir.contract.ExchangeOutputDraft
import org.grovealliance.fhir.contract.FhirDecimal
import org.grovealliance.fhir.contract.InternalGroveFhirApi
import org.grovealliance.fhir.contract.MeasurementContract
import org.grovealliance.fhir.contract.Measurements
import org.grovealliance.fhir.contract.Observation
import org.grovealliance.fhir.contract.Quantity
import org.grovealliance.health.fhir.HealthConnectConversionError
import org.grovealliance.health.fhir.HealthConnectConversionError.ValueFailure

/**
 * One dietary Observation per nutrient a nutrition Record states, over the Record's interval, in the
 * unit its measurement fixes. The food's name and meal type are not modelled.
 */
@OptIn(InternalGroveFhirApi::class)
internal class NutritionMapper(
    private val times: SourceTimeMapper,
    private val observations: ObservationMapper,
) {

    /**
     * The nutrient [field] of a Record that the dietary [measurement] states, as [read] reads it.
     */
    private data class NutrientField(
        val measurement: MeasurementContract,
        val field: String,
        val read: (NutritionRecord) -> Any?,
    )

    fun mapOutputs(record: NutritionRecord): Result<OutputMapper.Outputs> {
        val effective = times.mapPeriod(
            start = record.startTime,
            startOffset = record.startZoneOffset,
            end = record.endTime,
            endOffset = record.endZoneOffset,
        )
        val drafts = FIELDS.mapNotNull { field ->
            val amount = field.read(record) ?: return@mapNotNull null
            val measurement = field.measurement
            val quantity = requireNotNull(measurement.quantity)
            val value = amount(amount = amount, unit = quantity.code)
                ?.let { FhirDecimal.of(value = it).getOrNull() }
                ?: return Result.failure(
                    HealthConnectConversionError.InvalidValue(
                        failure = ValueFailure.OUTSIDE_DOMAIN,
                        location = "$RECORD_TYPE.${field.field}",
                    ),
                )
            ExchangeOutputDraft(
                role = OutputRole.PRESENT_FIELD.value,
                discriminator = measurement.id,
                resource = ExchangeOutputDraft.Resource.ObservationResource(
                    observation = observations.mapObservation(
                        claim = observations.mapClaim(measurement = measurement),
                        recordType = RECORD_TYPE,
                        metadata = record.metadata,
                        effective = effective,
                        issued = times.mapIssued(instant = record.metadata.lastModifiedTime),
                        value = Observation.Value.QuantityValue(
                            value = Quantity(
                                value = value,
                                unit = quantity.unit,
                                system = quantity.system,
                                code = quantity.code,
                            ),
                        ),
                    ),
                ),
            )
        }
        if (drafts.isEmpty()) return Result.failure(HealthConnectConversionError.NotYetConvertible(recordType = RECORD_TYPE))
        val statesOffset = record.startZoneOffset != null && record.endZoneOffset != null
        val omitted = listOfNotNull(
            "$RECORD_TYPE.name".takeIf { record.name != null },
            "$RECORD_TYPE.mealType".takeIf { record.mealType != MealType.MEAL_TYPE_UNKNOWN },
        ).map { ExchangeGraphRule.MOBILE_OMISSION_UNMODELED_METADATA.diagnostic(location = it) }
        val offsetWarnings = if (statesOffset) emptyList() else times.mapOffsetWarnings(effectives = listOf(effective))
        return Result.success(
            OutputMapper.Outputs(
                drafts = drafts,
                warnings = offsetWarnings + omitted,
            ),
        )
    }

    /**
     * [amount] in the UCUM [unit] its measurement fixes, or `null` when the unit is none the Record's quantities read in.
     */
    private fun amount(amount: Any, unit: String): Double? = when {
        amount is Energy && unit == "kcal" -> amount.inKilocalories
        amount is Mass && unit == "g" -> amount.inGrams
        amount is Mass && unit == "mg" -> amount.inMilligrams
        amount is Mass && unit == "ug" -> amount.inMicrograms
        else -> null
    }

    private companion object {
        const val RECORD_TYPE = "NutritionRecord"
        val FIELDS = listOf(
            NutrientField(measurement = Measurements.DIETARY_BIOTIN, field = "biotin", read = { it.biotin }),
            NutrientField(measurement = Measurements.DIETARY_CAFFEINE, field = "caffeine", read = { it.caffeine }),
            NutrientField(measurement = Measurements.DIETARY_CALCIUM, field = "calcium", read = { it.calcium }),
            NutrientField(measurement = Measurements.DIETARY_CARBOHYDRATES, field = "totalCarbohydrate", read = { it.totalCarbohydrate }),
            NutrientField(measurement = Measurements.DIETARY_CHLORIDE, field = "chloride", read = { it.chloride }),
            NutrientField(measurement = Measurements.DIETARY_CHOLESTEROL, field = "cholesterol", read = { it.cholesterol }),
            NutrientField(measurement = Measurements.DIETARY_CHROMIUM, field = "chromium", read = { it.chromium }),
            NutrientField(measurement = Measurements.DIETARY_COPPER, field = "copper", read = { it.copper }),
            NutrientField(measurement = Measurements.DIETARY_ENERGY, field = "energy", read = { it.energy }),
            NutrientField(measurement = Measurements.DIETARY_ENERGY_FROM_FAT, field = "energyFromFat", read = { it.energyFromFat }),
            NutrientField(
                measurement = Measurements.DIETARY_FAT_MONOUNSATURATED,
                field = "monounsaturatedFat",
                read = { it.monounsaturatedFat },
            ),
            NutrientField(
                measurement = Measurements.DIETARY_FAT_POLYUNSATURATED,
                field = "polyunsaturatedFat",
                read = { it.polyunsaturatedFat },
            ),
            NutrientField(measurement = Measurements.DIETARY_FAT_SATURATED, field = "saturatedFat", read = { it.saturatedFat }),
            NutrientField(measurement = Measurements.DIETARY_FAT_TOTAL, field = "totalFat", read = { it.totalFat }),
            NutrientField(measurement = Measurements.DIETARY_FAT_TRANS, field = "transFat", read = { it.transFat }),
            NutrientField(measurement = Measurements.DIETARY_FAT_UNSATURATED, field = "unsaturatedFat", read = { it.unsaturatedFat }),
            NutrientField(measurement = Measurements.DIETARY_FIBER, field = "dietaryFiber", read = { it.dietaryFiber }),
            NutrientField(measurement = Measurements.DIETARY_FOLATE, field = "folate", read = { it.folate }),
            NutrientField(measurement = Measurements.DIETARY_FOLIC_ACID, field = "folicAcid", read = { it.folicAcid }),
            NutrientField(measurement = Measurements.DIETARY_IODINE, field = "iodine", read = { it.iodine }),
            NutrientField(measurement = Measurements.DIETARY_IRON, field = "iron", read = { it.iron }),
            NutrientField(measurement = Measurements.DIETARY_MAGNESIUM, field = "magnesium", read = { it.magnesium }),
            NutrientField(measurement = Measurements.DIETARY_MANGANESE, field = "manganese", read = { it.manganese }),
            NutrientField(measurement = Measurements.DIETARY_MOLYBDENUM, field = "molybdenum", read = { it.molybdenum }),
            NutrientField(measurement = Measurements.DIETARY_NIACIN, field = "niacin", read = { it.niacin }),
            NutrientField(measurement = Measurements.DIETARY_PANTOTHENIC_ACID, field = "pantothenicAcid", read = { it.pantothenicAcid }),
            NutrientField(measurement = Measurements.DIETARY_PHOSPHORUS, field = "phosphorus", read = { it.phosphorus }),
            NutrientField(measurement = Measurements.DIETARY_POTASSIUM, field = "potassium", read = { it.potassium }),
            NutrientField(measurement = Measurements.DIETARY_PROTEIN, field = "protein", read = { it.protein }),
            NutrientField(measurement = Measurements.DIETARY_RIBOFLAVIN, field = "riboflavin", read = { it.riboflavin }),
            NutrientField(measurement = Measurements.DIETARY_SELENIUM, field = "selenium", read = { it.selenium }),
            NutrientField(measurement = Measurements.DIETARY_SODIUM, field = "sodium", read = { it.sodium }),
            NutrientField(measurement = Measurements.DIETARY_SUGAR, field = "sugar", read = { it.sugar }),
            NutrientField(measurement = Measurements.DIETARY_THIAMIN, field = "thiamin", read = { it.thiamin }),
            NutrientField(measurement = Measurements.DIETARY_VITAMIN_A, field = "vitaminA", read = { it.vitaminA }),
            NutrientField(measurement = Measurements.DIETARY_VITAMIN_B12, field = "vitaminB12", read = { it.vitaminB12 }),
            NutrientField(measurement = Measurements.DIETARY_VITAMIN_B6, field = "vitaminB6", read = { it.vitaminB6 }),
            NutrientField(measurement = Measurements.DIETARY_VITAMIN_C, field = "vitaminC", read = { it.vitaminC }),
            NutrientField(measurement = Measurements.DIETARY_VITAMIN_D, field = "vitaminD", read = { it.vitaminD }),
            NutrientField(measurement = Measurements.DIETARY_VITAMIN_E, field = "vitaminE", read = { it.vitaminE }),
            NutrientField(measurement = Measurements.DIETARY_VITAMIN_K, field = "vitaminK", read = { it.vitaminK }),
            NutrientField(measurement = Measurements.DIETARY_ZINC, field = "zinc", read = { it.zinc }),
        )
    }
}
