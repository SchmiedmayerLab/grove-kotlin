//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

@file:OptIn(ExperimentalMindfulnessSessionApi::class)

package org.grovealliance.health.fhir

import androidx.health.connect.client.feature.ExperimentalMindfulnessSessionApi
import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.BodyTemperatureMeasurementLocation
import androidx.health.connect.client.records.CervicalMucusRecord
import androidx.health.connect.client.records.MealType
import androidx.health.connect.client.records.MenstruationFlowRecord
import androidx.health.connect.client.records.MindfulnessSessionRecord
import androidx.health.connect.client.records.OvulationTestRecord
import androidx.health.connect.client.records.SexualActivityRecord
import androidx.health.connect.client.records.SkinTemperatureRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.Vo2MaxRecord

/** The exact AndroidX 1.1.0 token behind every integer enumeration the adapter reads. */
internal object HealthConnectSourceTokens {
    val sleepStage: Map<Int, String> = mapOf(
        SleepSessionRecord.STAGE_TYPE_UNKNOWN to "STAGE_TYPE_UNKNOWN",
        SleepSessionRecord.STAGE_TYPE_AWAKE to "STAGE_TYPE_AWAKE",
        SleepSessionRecord.STAGE_TYPE_SLEEPING to "STAGE_TYPE_SLEEPING",
        SleepSessionRecord.STAGE_TYPE_OUT_OF_BED to "STAGE_TYPE_OUT_OF_BED",
        SleepSessionRecord.STAGE_TYPE_LIGHT to "STAGE_TYPE_LIGHT",
        SleepSessionRecord.STAGE_TYPE_DEEP to "STAGE_TYPE_DEEP",
        SleepSessionRecord.STAGE_TYPE_REM to "STAGE_TYPE_REM",
        SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED to "STAGE_TYPE_AWAKE_IN_BED",
    )

    val menstruationFlow: Map<Int, String> = mapOf(
        MenstruationFlowRecord.FLOW_UNKNOWN to "FLOW_UNKNOWN",
        MenstruationFlowRecord.FLOW_LIGHT to "FLOW_LIGHT",
        MenstruationFlowRecord.FLOW_MEDIUM to "FLOW_MEDIUM",
        MenstruationFlowRecord.FLOW_HEAVY to "FLOW_HEAVY",
    )

    val ovulationTestResult: Map<Int, String> = mapOf(
        OvulationTestRecord.RESULT_INCONCLUSIVE to "RESULT_INCONCLUSIVE",
        OvulationTestRecord.RESULT_POSITIVE to "RESULT_POSITIVE",
        OvulationTestRecord.RESULT_HIGH to "RESULT_HIGH",
        OvulationTestRecord.RESULT_NEGATIVE to "RESULT_NEGATIVE",
    )

    val sexualActivityProtection: Map<Int, String> = mapOf(
        SexualActivityRecord.PROTECTION_USED_UNKNOWN to "PROTECTION_USED_UNKNOWN",
        SexualActivityRecord.PROTECTION_USED_PROTECTED to "PROTECTION_USED_PROTECTED",
        SexualActivityRecord.PROTECTION_USED_UNPROTECTED to "PROTECTION_USED_UNPROTECTED",
    )

    val cervicalMucusAppearance: Map<Int, String> = mapOf(
        CervicalMucusRecord.APPEARANCE_UNKNOWN to "APPEARANCE_UNKNOWN",
        CervicalMucusRecord.APPEARANCE_DRY to "APPEARANCE_DRY",
        CervicalMucusRecord.APPEARANCE_STICKY to "APPEARANCE_STICKY",
        CervicalMucusRecord.APPEARANCE_CREAMY to "APPEARANCE_CREAMY",
        CervicalMucusRecord.APPEARANCE_WATERY to "APPEARANCE_WATERY",
        CervicalMucusRecord.APPEARANCE_EGG_WHITE to "APPEARANCE_EGG_WHITE",
        CervicalMucusRecord.APPEARANCE_UNUSUAL to "APPEARANCE_UNUSUAL",
    )

    val cervicalMucusSensation: Map<Int, String> = mapOf(
        CervicalMucusRecord.SENSATION_UNKNOWN to "SENSATION_UNKNOWN",
        CervicalMucusRecord.SENSATION_LIGHT to "SENSATION_LIGHT",
        CervicalMucusRecord.SENSATION_MEDIUM to "SENSATION_MEDIUM",
        CervicalMucusRecord.SENSATION_HEAVY to "SENSATION_HEAVY",
    )

    val mindfulnessSessionType: Map<Int, String> = mapOf(
        MindfulnessSessionRecord.MINDFULNESS_SESSION_TYPE_UNKNOWN to "MINDFULNESS_SESSION_TYPE_UNKNOWN",
        MindfulnessSessionRecord.MINDFULNESS_SESSION_TYPE_MEDITATION to "MINDFULNESS_SESSION_TYPE_MEDITATION",
        MindfulnessSessionRecord.MINDFULNESS_SESSION_TYPE_BREATHING to "MINDFULNESS_SESSION_TYPE_BREATHING",
        MindfulnessSessionRecord.MINDFULNESS_SESSION_TYPE_MUSIC to "MINDFULNESS_SESSION_TYPE_MUSIC",
        MindfulnessSessionRecord.MINDFULNESS_SESSION_TYPE_MOVEMENT to "MINDFULNESS_SESSION_TYPE_MOVEMENT",
        MindfulnessSessionRecord.MINDFULNESS_SESSION_TYPE_UNGUIDED to "MINDFULNESS_SESSION_TYPE_UNGUIDED",
    )

    val vo2MaxMeasurementMethod: Map<Int, String> = mapOf(
        Vo2MaxRecord.MEASUREMENT_METHOD_OTHER to "MEASUREMENT_METHOD_OTHER",
        Vo2MaxRecord.MEASUREMENT_METHOD_METABOLIC_CART to "MEASUREMENT_METHOD_METABOLIC_CART",
        Vo2MaxRecord.MEASUREMENT_METHOD_HEART_RATE_RATIO to "MEASUREMENT_METHOD_HEART_RATE_RATIO",
        Vo2MaxRecord.MEASUREMENT_METHOD_COOPER_TEST to "MEASUREMENT_METHOD_COOPER_TEST",
        Vo2MaxRecord.MEASUREMENT_METHOD_MULTISTAGE_FITNESS_TEST to "MEASUREMENT_METHOD_MULTISTAGE_FITNESS_TEST",
        Vo2MaxRecord.MEASUREMENT_METHOD_ROCKPORT_FITNESS_TEST to "MEASUREMENT_METHOD_ROCKPORT_FITNESS_TEST",
    )

    val relationToMeal: Map<Int, String> = mapOf(
        BloodGlucoseRecord.RELATION_TO_MEAL_UNKNOWN to "RELATION_TO_MEAL_UNKNOWN",
        BloodGlucoseRecord.RELATION_TO_MEAL_GENERAL to "RELATION_TO_MEAL_GENERAL",
        BloodGlucoseRecord.RELATION_TO_MEAL_FASTING to "RELATION_TO_MEAL_FASTING",
        BloodGlucoseRecord.RELATION_TO_MEAL_BEFORE_MEAL to "RELATION_TO_MEAL_BEFORE_MEAL",
        BloodGlucoseRecord.RELATION_TO_MEAL_AFTER_MEAL to "RELATION_TO_MEAL_AFTER_MEAL",
    )

    val mealType: Map<Int, String> = mapOf(
        MealType.MEAL_TYPE_UNKNOWN to "MEAL_TYPE_UNKNOWN",
        MealType.MEAL_TYPE_BREAKFAST to "MEAL_TYPE_BREAKFAST",
        MealType.MEAL_TYPE_LUNCH to "MEAL_TYPE_LUNCH",
        MealType.MEAL_TYPE_DINNER to "MEAL_TYPE_DINNER",
        MealType.MEAL_TYPE_SNACK to "MEAL_TYPE_SNACK",
    )

    val specimenSource: Map<Int, String> = mapOf(
        BloodGlucoseRecord.SPECIMEN_SOURCE_UNKNOWN to "SPECIMEN_SOURCE_UNKNOWN",
        BloodGlucoseRecord.SPECIMEN_SOURCE_INTERSTITIAL_FLUID to "SPECIMEN_SOURCE_INTERSTITIAL_FLUID",
        BloodGlucoseRecord.SPECIMEN_SOURCE_CAPILLARY_BLOOD to "SPECIMEN_SOURCE_CAPILLARY_BLOOD",
        BloodGlucoseRecord.SPECIMEN_SOURCE_PLASMA to "SPECIMEN_SOURCE_PLASMA",
        BloodGlucoseRecord.SPECIMEN_SOURCE_SERUM to "SPECIMEN_SOURCE_SERUM",
        BloodGlucoseRecord.SPECIMEN_SOURCE_TEARS to "SPECIMEN_SOURCE_TEARS",
        BloodGlucoseRecord.SPECIMEN_SOURCE_WHOLE_BLOOD to "SPECIMEN_SOURCE_WHOLE_BLOOD",
    )

    val bloodPressureBodyPosition: Map<Int, String> = mapOf(
        BloodPressureRecord.BODY_POSITION_UNKNOWN to "BODY_POSITION_UNKNOWN",
        BloodPressureRecord.BODY_POSITION_STANDING_UP to "BODY_POSITION_STANDING_UP",
        BloodPressureRecord.BODY_POSITION_SITTING_DOWN to "BODY_POSITION_SITTING_DOWN",
        BloodPressureRecord.BODY_POSITION_LYING_DOWN to "BODY_POSITION_LYING_DOWN",
        BloodPressureRecord.BODY_POSITION_RECLINING to "BODY_POSITION_RECLINING",
    )

    val bloodPressureMeasurementLocation: Map<Int, String> = mapOf(
        BloodPressureRecord.MEASUREMENT_LOCATION_UNKNOWN to "MEASUREMENT_LOCATION_UNKNOWN",
        BloodPressureRecord.MEASUREMENT_LOCATION_LEFT_WRIST to "MEASUREMENT_LOCATION_LEFT_WRIST",
        BloodPressureRecord.MEASUREMENT_LOCATION_RIGHT_WRIST to "MEASUREMENT_LOCATION_RIGHT_WRIST",
        BloodPressureRecord.MEASUREMENT_LOCATION_LEFT_UPPER_ARM to "MEASUREMENT_LOCATION_LEFT_UPPER_ARM",
        BloodPressureRecord.MEASUREMENT_LOCATION_RIGHT_UPPER_ARM to "MEASUREMENT_LOCATION_RIGHT_UPPER_ARM",
    )

    val temperatureMeasurementLocation: Map<Int, String> = mapOf(
        BodyTemperatureMeasurementLocation.MEASUREMENT_LOCATION_UNKNOWN to "MEASUREMENT_LOCATION_UNKNOWN",
        BodyTemperatureMeasurementLocation.MEASUREMENT_LOCATION_ARMPIT to "MEASUREMENT_LOCATION_ARMPIT",
        BodyTemperatureMeasurementLocation.MEASUREMENT_LOCATION_FINGER to "MEASUREMENT_LOCATION_FINGER",
        BodyTemperatureMeasurementLocation.MEASUREMENT_LOCATION_FOREHEAD to "MEASUREMENT_LOCATION_FOREHEAD",
        BodyTemperatureMeasurementLocation.MEASUREMENT_LOCATION_MOUTH to "MEASUREMENT_LOCATION_MOUTH",
        BodyTemperatureMeasurementLocation.MEASUREMENT_LOCATION_RECTUM to "MEASUREMENT_LOCATION_RECTUM",
        BodyTemperatureMeasurementLocation.MEASUREMENT_LOCATION_TEMPORAL_ARTERY to "MEASUREMENT_LOCATION_TEMPORAL_ARTERY",
        BodyTemperatureMeasurementLocation.MEASUREMENT_LOCATION_TOE to "MEASUREMENT_LOCATION_TOE",
        BodyTemperatureMeasurementLocation.MEASUREMENT_LOCATION_EAR to "MEASUREMENT_LOCATION_EAR",
        BodyTemperatureMeasurementLocation.MEASUREMENT_LOCATION_WRIST to "MEASUREMENT_LOCATION_WRIST",
        BodyTemperatureMeasurementLocation.MEASUREMENT_LOCATION_VAGINA to "MEASUREMENT_LOCATION_VAGINA",
    )

    val skinTemperatureMeasurementLocation: Map<Int, String> = mapOf(
        SkinTemperatureRecord.MEASUREMENT_LOCATION_UNKNOWN to "MEASUREMENT_LOCATION_UNKNOWN",
        SkinTemperatureRecord.MEASUREMENT_LOCATION_FINGER to "MEASUREMENT_LOCATION_FINGER",
        SkinTemperatureRecord.MEASUREMENT_LOCATION_TOE to "MEASUREMENT_LOCATION_TOE",
        SkinTemperatureRecord.MEASUREMENT_LOCATION_WRIST to "MEASUREMENT_LOCATION_WRIST",
    )
}

/** The token of a source enumeration value, or a refusal when AndroidX 1.1.0 does not define it. */
internal fun Map<Int, String>.token(value: Int, field: String): String =
    this[value] ?: refuse(HealthConnectValueFailure.UnsupportedSourceValue(field))
