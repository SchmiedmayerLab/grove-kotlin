//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

@file:OptIn(InternalGroveFhirApi::class)

package org.grovealliance.health.fhir

import androidx.health.connect.client.records.MealType
import androidx.health.connect.client.records.NutritionRecord
import org.grovealliance.fhir.InternalGroveFhirApi

/** Every nutrient the adapter projects out of one NutritionRecord, in the catalog's measurement order. */
internal val NUTRIENT_FIELDS: List<Pair<String, (NutritionRecord) -> Double?>> = listOf(
    "dietary-biotin" to { it.biotin?.inMicrograms },
    "dietary-caffeine" to { it.caffeine?.inMilligrams },
    "dietary-calcium" to { it.calcium?.inMilligrams },
    "dietary-carbohydrates" to { it.totalCarbohydrate?.inGrams },
    "dietary-chloride" to { it.chloride?.inMilligrams },
    "dietary-cholesterol" to { it.cholesterol?.inMilligrams },
    "dietary-chromium" to { it.chromium?.inMicrograms },
    "dietary-copper" to { it.copper?.inMicrograms },
    "dietary-energy" to { it.energy?.inKilocalories },
    "dietary-energy-from-fat" to { it.energyFromFat?.inKilocalories },
    "dietary-fat-monounsaturated" to { it.monounsaturatedFat?.inGrams },
    "dietary-fat-polyunsaturated" to { it.polyunsaturatedFat?.inGrams },
    "dietary-fat-saturated" to { it.saturatedFat?.inGrams },
    "dietary-fat-total" to { it.totalFat?.inGrams },
    "dietary-fat-trans" to { it.transFat?.inGrams },
    "dietary-fat-unsaturated" to { it.unsaturatedFat?.inGrams },
    "dietary-fiber" to { it.dietaryFiber?.inGrams },
    "dietary-folate" to { it.folate?.inMicrograms },
    "dietary-folic-acid" to { it.folicAcid?.inMicrograms },
    "dietary-iodine" to { it.iodine?.inMicrograms },
    "dietary-iron" to { it.iron?.inMilligrams },
    "dietary-magnesium" to { it.magnesium?.inMilligrams },
    "dietary-manganese" to { it.manganese?.inMilligrams },
    "dietary-molybdenum" to { it.molybdenum?.inMicrograms },
    "dietary-niacin" to { it.niacin?.inMilligrams },
    "dietary-pantothenic-acid" to { it.pantothenicAcid?.inMilligrams },
    "dietary-phosphorus" to { it.phosphorus?.inMilligrams },
    "dietary-potassium" to { it.potassium?.inMilligrams },
    "dietary-protein" to { it.protein?.inGrams },
    "dietary-riboflavin" to { it.riboflavin?.inMilligrams },
    "dietary-selenium" to { it.selenium?.inMicrograms },
    "dietary-sodium" to { it.sodium?.inMilligrams },
    "dietary-sugar" to { it.sugar?.inGrams },
    "dietary-thiamin" to { it.thiamin?.inMilligrams },
    "dietary-vitamin-a" to { it.vitaminA?.inMicrograms },
    "dietary-vitamin-b12" to { it.vitaminB12?.inMicrograms },
    "dietary-vitamin-b6" to { it.vitaminB6?.inMilligrams },
    "dietary-vitamin-c" to { it.vitaminC?.inMilligrams },
    "dietary-vitamin-d" to { it.vitaminD?.inMicrograms },
    "dietary-vitamin-e" to { it.vitaminE?.inMilligrams },
    "dietary-vitamin-k" to { it.vitaminK?.inMicrograms },
    "dietary-zinc" to { it.zinc?.inMilligrams },
)

/** Nutrition fans out into one Observation per present nutrient; an all-absent record is an admitted zero-output result. */
internal fun RecordConversion.convertNutrition(record: NutritionRecord): HealthConnectConversionResult {
    val withheld = buildSet {
        if (record.name != null) add("${type.token}.name")
        if (record.mealType != MealType.MEAL_TYPE_UNKNOWN) add("${type.token}.mealType")
    }
    if (withheld.isNotEmpty()) warn(HealthConnectConversionWarning.UnmodeledMetadataWithheld(withheld.sorted()))
    val effective = period(record.startTime.at(record.startZoneOffset) until record.endTime.at(record.endZoneOffset), type.token)
    val outputs = NUTRIENT_FIELDS.mapNotNull { (measurement, extract) ->
        val value = extract(record) ?: return@mapNotNull null
        val spec = spec(measurement)
        val quantitySpec = requireNotNull(spec.quantity)
        val decimal = decimal(value.at(measurement), quantitySpec)
        observation(spec, output(PRESENT_FIELD_ROLE, measurement)) {
            this.effective = effective.copy()
            this.value = quantity(quantitySpec, decimal)
        }
    }
    return finish(outputs.firstOrNull(), outputs.drop(1))
}

private const val PRESENT_FIELD_ROLE = "present-field"
