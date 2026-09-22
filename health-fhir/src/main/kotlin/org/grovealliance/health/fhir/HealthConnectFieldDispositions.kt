//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.health.fhir

/** What this producer deliberately does with one public AndroidX Health Connect source field. */
public data class HealthConnectFieldDisposition(
    public val status: Status,
    public val fhirPath: String? = null,
    public val rationale: String? = null,
) {
    public enum class Status { MAPPED, INTENTIONALLY_OMITTED, REJECTED, UNAVAILABLE }

    init {
        require((status == Status.MAPPED) == (fhirPath != null)) {
            "A mapped source field requires one FHIR destination; other dispositions must not claim one."
        }
        require(status == Status.MAPPED || !rationale.isNullOrBlank()) {
            "A non-mapped source field requires a reviewed rationale."
        }
    }
}

/**
 * Executable AndroidX 1.1.0 field inventory for every source type the catalog calls supported.
 *
 * Tests compare these keys to the pinned bytecode's public getters, so an AndroidX field addition
 * cannot silently inherit "supported" status. Nested source types are inventoried separately.
 */
internal object HealthConnectFieldDispositions {
    private fun mapped(
        path: String,
    ) = HealthConnectFieldDisposition(HealthConnectFieldDisposition.Status.MAPPED, fhirPath = path)

    private fun omitted(reason: String) =
        HealthConnectFieldDisposition(HealthConnectFieldDisposition.Status.INTENTIONALLY_OMITTED, rationale = reason)

    private val instant = mapOf(
        "time" to mapped("Observation.effectiveDateTime"),
        "zoneOffset" to mapped("Observation.effectiveDateTime lexical offset"),
        "metadata" to mapped("Observation.identifier/issued/device/extension and Provenance.entity/agent"),
    )
    private val interval = mapOf(
        "startTime" to mapped("Observation.effectivePeriod.start"),
        "startZoneOffset" to mapped("Observation.effectivePeriod.start lexical offset"),
        "endTime" to mapped("Observation.effectivePeriod.end"),
        "endZoneOffset" to mapped("Observation.effectivePeriod.end lexical offset"),
        "metadata" to mapped("Observation.identifier/issued/device/extension and Provenance.entity/agent"),
    )

    private fun instant(vararg fields: Pair<String, String>) = instant + fields.associate { (field, path) -> field to mapped(path) }

    private fun interval(vararg fields: Pair<String, String>) = interval + fields.associate { (field, path) -> field to mapped(path) }

    private val sessionText = mapOf(
        "title" to mapped("health-connect-session-title extension when the text policy retains it"),
        "notes" to mapped("Observation.note.text when the text policy retains it"),
    )

    private val nutrientFields = setOf(
        "biotin", "caffeine", "calcium", "energy", "energyFromFat", "chloride", "cholesterol", "chromium",
        "copper", "dietaryFiber", "folate", "folicAcid", "iodine", "iron", "magnesium", "manganese",
        "molybdenum", "monounsaturatedFat", "niacin", "pantothenicAcid", "phosphorus", "polyunsaturatedFat",
        "potassium", "protein", "riboflavin", "saturatedFat", "selenium", "sodium", "sugar", "thiamin",
        "totalCarbohydrate", "totalFat", "transFat", "unsaturatedFat", "vitaminA", "vitaminB12", "vitaminB6",
        "vitaminC", "vitaminD", "vitaminE", "vitaminK", "zinc",
    )

    val records: Map<HealthConnectSourceType, Map<String, HealthConnectFieldDisposition>> = mapOf(
        HealthConnectSourceType.ACTIVE_CALORIES_BURNED to interval("energy" to "Observation.valueQuantity"),
        HealthConnectSourceType.BASAL_BODY_TEMPERATURE to instant(
            "temperature" to "Observation.valueQuantity",
            "measurementLocation" to "Observation.bodySite",
        ),
        HealthConnectSourceType.BASAL_METABOLIC_RATE to instant("basalMetabolicRate" to "Observation.valueQuantity"),
        HealthConnectSourceType.BLOOD_GLUCOSE to instant(
            "level" to "Observation.valueQuantity",
            "specimenSource" to "Observation.specimen and specimen-specific profile/code",
            "mealType" to "health-connect-glucose-meal-context extension",
            "relationToMeal" to "health-connect-glucose-meal-context extension",
        ),
        HealthConnectSourceType.BLOOD_PRESSURE to instant(
            "systolic" to "Observation.component[systolic].valueQuantity",
            "diastolic" to "Observation.component[diastolic].valueQuantity",
            "bodyPosition" to "observation-bodyPosition extension",
            "measurementLocation" to "Observation.bodySite",
        ),
        HealthConnectSourceType.BODY_FAT to instant("percentage" to "Observation.valueQuantity"),
        HealthConnectSourceType.BODY_TEMPERATURE to instant(
            "temperature" to "Observation.valueQuantity",
            "measurementLocation" to "Observation.bodySite",
        ),
        HealthConnectSourceType.BODY_WATER_MASS to instant("mass" to "Observation.valueQuantity"),
        HealthConnectSourceType.BONE_MASS to instant("mass" to "Observation.valueQuantity"),
        HealthConnectSourceType.CERVICAL_MUCUS to instant(
            "appearance" to "Observation.valueCodeableConcept",
            "sensation" to "Observation.component[cervical-mucus-sensation]",
        ),
        HealthConnectSourceType.CYCLING_PEDALING_CADENCE to interval("samples" to "one Observation per Sample"),
        HealthConnectSourceType.DISTANCE to interval("distance" to "Observation.valueQuantity"),
        HealthConnectSourceType.ELEVATION_GAINED to interval("elevation" to "Observation.valueQuantity"),
        HealthConnectSourceType.EXERCISE_SESSION to interval(
            "exerciseType" to "Observation.valueCodeableConcept",
            "segments" to "one workout-segment member Observation per ExerciseSegment",
            "laps" to "one workout-segment member Observation per ExerciseLap",
        ) + sessionText + mapOf(
            "exerciseRouteResult" to omitted(
                "The Grove FHIR contracts admit no safe route geometry profile; route data requires a separately " +
                    "reviewed source artifact.",
            ),
            "plannedExerciseSessionId" to omitted(
                "The referenced PlannedExerciseSessionRecord source type is explicitly deferred in AndroidX 1.1.0 support.",
            ),
        ),
        HealthConnectSourceType.FLOORS_CLIMBED to interval("floors" to "Observation.valueQuantity"),
        HealthConnectSourceType.HEART_RATE to interval("samples" to "one Observation per Sample"),
        HealthConnectSourceType.HEART_RATE_VARIABILITY_RMSSD to instant("heartRateVariabilityMillis" to "Observation.valueQuantity"),
        HealthConnectSourceType.HEIGHT to instant("height" to "Observation.valueQuantity"),
        HealthConnectSourceType.HYDRATION to interval("volume" to "Observation.valueQuantity"),
        HealthConnectSourceType.INTERMENSTRUAL_BLEEDING to instant(),
        HealthConnectSourceType.LEAN_BODY_MASS to instant("mass" to "Observation.valueQuantity"),
        HealthConnectSourceType.MENSTRUATION_FLOW to instant("flow" to "Observation.valueCodeableConcept"),
        HealthConnectSourceType.MENSTRUATION_PERIOD to interval(),
        HealthConnectSourceType.MINDFULNESS_SESSION to interval("mindfulnessSessionType" to "Observation.method") + sessionText,
        HealthConnectSourceType.NUTRITION to nutrition(),
        HealthConnectSourceType.OVULATION_TEST to instant("result" to "Observation.valueCodeableConcept"),
        HealthConnectSourceType.OXYGEN_SATURATION to instant("percentage" to "Observation.valueQuantity"),
        HealthConnectSourceType.POWER to interval("samples" to "one Observation per Sample"),
        HealthConnectSourceType.RESPIRATORY_RATE to instant("rate" to "Observation.valueQuantity"),
        HealthConnectSourceType.RESTING_HEART_RATE to instant("beatsPerMinute" to "Observation.valueQuantity"),
        HealthConnectSourceType.SEXUAL_ACTIVITY to instant("protectionUsed" to "Observation.valueCodeableConcept"),
        HealthConnectSourceType.SKIN_TEMPERATURE to interval(
            "deltas" to "one Observation per Delta",
            "baseline" to "combined with Delta for Observation.valueQuantity",
            "measurementLocation" to "Observation.bodySite",
        ),
        HealthConnectSourceType.SLEEP_SESSION to interval(
            "stages" to "one member Observation per Stage in exact platform-list occurrence order",
        ) + sessionText,
        HealthConnectSourceType.SPEED to interval("samples" to "one Observation per Sample"),
        HealthConnectSourceType.STEPS_CADENCE to interval("samples" to "one Observation per Sample"),
        HealthConnectSourceType.STEPS to interval("count" to "Observation.valueQuantity"),
        HealthConnectSourceType.TOTAL_CALORIES_BURNED to interval("energy" to "Observation.valueQuantity"),
        HealthConnectSourceType.VO2_MAX to instant(
            "vo2MillilitersPerMinuteKilogram" to "Observation.valueQuantity",
            "measurementMethod" to "Observation.method",
        ),
        HealthConnectSourceType.WEIGHT to instant("weight" to "Observation.valueQuantity"),
        HealthConnectSourceType.WHEELCHAIR_PUSHES to interval("count" to "Observation.valueQuantity"),
    )

    val metadata: Map<String, HealthConnectFieldDisposition> = mapOf(
        "recordingMethod" to mapped("grove-recording-method extension"),
        "id" to mapped(
            "opaque source-record/source-output HMAC input; optional governed Identifier on the designated primary output",
        ),
        "dataOrigin" to mapped("writer agent of the conversion Provenance and the writer-record identity"),
        "lastModifiedTime" to mapped("Observation.issued"),
        "clientRecordId" to mapped("opaque writer-record identifier"),
        "clientRecordVersion" to mapped("grove-writer-record-version extension"),
        "device" to mapped("recording Device reference when the deployment resolves a stable per-unit token"),
    )

    val nested: Map<String, Map<String, HealthConnectFieldDisposition>> = mapOf(
        "HeartRateRecord.Sample" to mapOf(
            "time" to mapped("Observation.effectiveDateTime and source-list occurrence output discriminator"),
            "beatsPerMinute" to mapped("Observation.valueQuantity"),
        ),
        "CyclingPedalingCadenceRecord.Sample" to mapOf(
            "time" to mapped("Observation.effectiveDateTime and source-list occurrence output discriminator"),
            "revolutionsPerMinute" to mapped("Observation.valueQuantity"),
        ),
        "PowerRecord.Sample" to mapOf(
            "time" to mapped("Observation.effectiveDateTime and source-list occurrence output discriminator"),
            "power" to mapped("Observation.valueQuantity"),
        ),
        "SpeedRecord.Sample" to mapOf(
            "time" to mapped("Observation.effectiveDateTime and source-list occurrence output discriminator"),
            "speed" to mapped("Observation.valueQuantity"),
        ),
        "StepsCadenceRecord.Sample" to mapOf(
            "time" to mapped("Observation.effectiveDateTime and source-list occurrence output discriminator"),
            "rate" to mapped("Observation.valueQuantity"),
        ),
        "SleepSessionRecord.Stage" to mapOf(
            "startTime" to mapped("member Observation.effectivePeriod.start and occurrence discriminator"),
            "endTime" to mapped("member Observation.effectivePeriod.end and occurrence discriminator"),
            "stage" to mapped("member Observation.valueCodeableConcept and occurrence discriminator"),
        ),
        "SkinTemperatureRecord.Delta" to mapOf(
            "time" to mapped("Observation.effectiveDateTime and source-list occurrence output discriminator"),
            "delta" to mapped("combined with baseline for Observation.valueQuantity"),
        ),
        "ExerciseSegment" to mapOf(
            "startTime" to mapped("member Observation.effectivePeriod.start"),
            "endTime" to mapped("member Observation.effectivePeriod.end"),
            "segmentType" to mapped("member Observation.valueCodeableConcept"),
            "repetitions" to mapped("member Observation.component[repetitions]"),
        ),
        "ExerciseLap" to mapOf(
            "startTime" to mapped("member Observation.effectivePeriod.start"),
            "endTime" to mapped("member Observation.effectivePeriod.end"),
            "length" to mapped("member Observation.component[lap-length]"),
        ),
    )

    init {
        val supported = HealthConnectSourceType.entries.filter { it.status == HealthConnectSourceStatus.SUPPORTED }.toSet()
        check(records.keys == supported) { "Every supported Health Connect type requires one field-disposition inventory." }
    }

    private fun nutrition(): Map<String, HealthConnectFieldDisposition> = interval +
        nutrientFields.associateWith { mapped("one nutrient Observation.valueQuantity when present") } +
        mapOf(
            "name" to omitted(
                "Free-text meal names are not retained without a dedicated deployment privacy policy and profile element.",
            ),
            "mealType" to omitted(
                "The current nutrition profiles describe nutrient results and do not define meal-event context semantics.",
            ),
        )
}
