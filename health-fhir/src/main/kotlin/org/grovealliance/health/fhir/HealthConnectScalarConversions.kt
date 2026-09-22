//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

@file:OptIn(InternalGroveFhirApi::class)

package org.grovealliance.health.fhir

import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BasalBodyTemperatureRecord
import androidx.health.connect.client.records.BasalMetabolicRateRecord
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.BodyTemperatureRecord
import androidx.health.connect.client.records.BodyWaterMassRecord
import androidx.health.connect.client.records.BoneMassRecord
import androidx.health.connect.client.records.CervicalMucusRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ElevationGainedRecord
import androidx.health.connect.client.records.FloorsClimbedRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.HeightRecord
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.IntermenstrualBleedingRecord
import androidx.health.connect.client.records.LeanBodyMassRecord
import androidx.health.connect.client.records.MenstruationFlowRecord
import androidx.health.connect.client.records.MenstruationPeriodRecord
import androidx.health.connect.client.records.OvulationTestRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SexualActivityRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.Vo2MaxRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.WheelchairPushesRecord
import org.grovealliance.fhir.InternalGroveFhirApi
import org.hl7.fhir.r4.model.CodeableConcept
import org.hl7.fhir.r4.model.Coding
import org.hl7.fhir.r4.model.Extension
import org.hl7.fhir.r4.model.Observation
import org.hl7.fhir.r4.model.Type

private const val CENTIMETERS_PER_METER = 100.0
private const val PRESENT = "present"

/** Every source type that emits exactly one Observation from one source value. */
@Suppress("CyclomaticComplexMethod", "LongMethod") // One dispatch arm per exactly-one source type.
internal fun RecordConversion.convertScalar(): HealthConnectConversionResult? = when (val record = record) {
    is ActiveCaloriesBurnedRecord -> intervalQuantity(
        "active-energy",
        record.energy.inKilocalories.at("energy"),
        record.startTime.at(record.startZoneOffset) until record.endTime.at(record.endZoneOffset),
    )
    is BasalBodyTemperatureRecord -> instantQuantity(
        measurement = "basal-body-temperature",
        value = record.temperature.inCelsius.at("temperature"),
        moment = record.time.at(record.zoneOffset),
        bodySite = temperatureSite(record.measurementLocation),
    )
    is BasalMetabolicRateRecord -> instantQuantity(
        "basal-metabolic-rate",
        record.basalMetabolicRate.inKilocaloriesPerDay.at("basalMetabolicRate"),
        record.time.at(record.zoneOffset),
    )
    is BloodPressureRecord -> convertBloodPressure(record)
    is BodyFatRecord -> instantQuantity("body-fat-percentage", record.percentage.value.at("percentage"), record.time.at(record.zoneOffset))
    is BodyTemperatureRecord -> instantQuantity(
        measurement = "body-temperature",
        value = record.temperature.inCelsius.at("temperature"),
        moment = record.time.at(record.zoneOffset),
        bodySite = temperatureSite(record.measurementLocation),
    )
    is BodyWaterMassRecord -> instantQuantity("body-water-mass", record.mass.inKilograms.at("mass"), record.time.at(record.zoneOffset))
    is BoneMassRecord -> instantQuantity("bone-mass", record.mass.inKilograms.at("mass"), record.time.at(record.zoneOffset))
    is DistanceRecord -> intervalQuantity(
        "distance",
        record.distance.inMeters.at("distance"),
        record.startTime.at(record.startZoneOffset) until record.endTime.at(record.endZoneOffset),
    )
    is ElevationGainedRecord -> intervalQuantity(
        "elevation-gained",
        record.elevation.inMeters.at("elevation"),
        record.startTime.at(record.startZoneOffset) until record.endTime.at(record.endZoneOffset),
    )
    is FloorsClimbedRecord -> intervalQuantity(
        "flights-climbed",
        record.floors.at("floors"),
        record.startTime.at(record.startZoneOffset) until record.endTime.at(record.endZoneOffset),
    )
    is HeartRateVariabilityRmssdRecord -> instantQuantity(
        "heart-rate-variability-rmssd",
        record.heartRateVariabilityMillis.at("heartRateVariabilityMillis"),
        record.time.at(record.zoneOffset),
    )
    is HeightRecord -> instantQuantity(
        "body-height",
        (record.height.inMeters * CENTIMETERS_PER_METER).at("height"),
        record.time.at(record.zoneOffset),
    )
    is HydrationRecord -> intervalQuantity(
        "fluid-intake",
        record.volume.inMilliliters.at("volume"),
        record.startTime.at(record.startZoneOffset) until record.endTime.at(record.endZoneOffset),
    )
    is LeanBodyMassRecord -> instantQuantity("lean-body-mass", record.mass.inKilograms.at("mass"), record.time.at(record.zoneOffset))
    is OxygenSaturationRecord -> instantQuantity(
        "oxygen-saturation",
        record.percentage.value.at("percentage"),
        record.time.at(record.zoneOffset),
    )
    is RespiratoryRateRecord -> instantQuantity("respiratory-rate", record.rate.at("rate"), record.time.at(record.zoneOffset))
    is RestingHeartRateRecord -> {
        if (record.beatsPerMinute < 0L) refuse(HealthConnectValueFailure.ValueOutsideDomain("${type.token}.beatsPerMinute"))
        instantQuantity("resting-heart-rate", record.beatsPerMinute.at("beatsPerMinute"), record.time.at(record.zoneOffset))
    }
    is StepsRecord -> intervalQuantity(
        "step-count",
        record.count.at("count"),
        record.startTime.at(record.startZoneOffset) until record.endTime.at(record.endZoneOffset),
    )
    is TotalCaloriesBurnedRecord -> intervalQuantity(
        "total-energy",
        record.energy.inKilocalories.at("energy"),
        record.startTime.at(record.startZoneOffset) until record.endTime.at(record.endZoneOffset),
    )
    is Vo2MaxRecord -> instantQuantity(
        measurement = "vo2-max",
        value = record.vo2MillilitersPerMinuteKilogram.at("vo2MillilitersPerMinuteKilogram"),
        moment = record.time.at(record.zoneOffset),
        method = CodeableConcept(
            sourceOnlyCoding(
                HealthConnectContextMappings.vo2MaxMeasurementMethod,
                HealthConnectSourceTokens.vo2MaxMeasurementMethod,
                record.measurementMethod.at("measurementMethod"),
            ),
        ),
    )
    is WeightRecord -> instantQuantity("body-weight", record.weight.inKilograms.at("weight"), record.time.at(record.zoneOffset))
    is WheelchairPushesRecord -> intervalQuantity(
        "wheelchair-push-count",
        record.count.at("count"),
        record.startTime.at(record.startZoneOffset) until record.endTime.at(record.endZoneOffset),
    )
    is MenstruationFlowRecord -> instantCoded(
        "menstruation-flow",
        record.time.at(record.zoneOffset),
        SourceCode(HealthConnectContextMappings.menstruationFlow, HealthConnectSourceTokens.menstruationFlow.token(record.flow.at("flow"))),
    )
    is OvulationTestRecord -> instantCoded(
        "ovulation-test-result",
        record.time.at(record.zoneOffset),
        SourceCode(
            HealthConnectContextMappings.ovulationTestResult,
            HealthConnectSourceTokens.ovulationTestResult.token(record.result.at("result")),
        ),
    )
    is SexualActivityRecord -> instantCoded(
        "sexual-activity",
        record.time.at(record.zoneOffset),
        SourceCode(
            HealthConnectContextMappings.sexualActivityProtection,
            HealthConnectSourceTokens.sexualActivityProtection.token(record.protectionUsed.at("protectionUsed")),
        ),
    )
    is CervicalMucusRecord -> convertCervicalMucus(record)
    is IntermenstrualBleedingRecord ->
        presentCoded("intermenstrual-bleeding", dateTime(record.time.at(record.zoneOffset), "${type.token}.time"))
    is MenstruationPeriodRecord -> presentCoded(
        "menstruation-period",
        period(record.startTime.at(record.startZoneOffset) until record.endTime.at(record.endZoneOffset), type.token),
    )
    else -> null
}

private fun RecordConversion.instantQuantity(
    measurement: String,
    value: SourceValue,
    moment: SourceMoment,
    bodySite: CodeableConcept? = null,
    method: CodeableConcept? = null,
): HealthConnectConversionResult = singleQuantity(measurement, value, dateTime(moment, "${type.token}.time")) {
    this.bodySite = bodySite
    this.method = method
}

private fun RecordConversion.intervalQuantity(measurement: String, value: SourceValue, interval: SourceInterval) =
    singleQuantity(measurement, value, period(interval, type.token))

private fun RecordConversion.singleQuantity(
    measurement: String,
    value: SourceValue,
    effective: Type,
    configure: Observation.() -> Unit = {},
): HealthConnectConversionResult {
    val spec = spec(measurement)
    val quantitySpec = requireNotNull(spec.quantity) { "$measurement is a Quantity measurement." }
    val decimal = decimal(value, quantitySpec)
    val output = observation(spec, singleOutput(measurement), disclosesNativeIdentifier = true) {
        this.effective = effective
        this.value = quantity(quantitySpec, decimal)
        configure()
    }
    return finish(output)
}

private fun RecordConversion.instantCoded(
    measurement: String,
    moment: SourceMoment,
    code: SourceCode,
    configure: Observation.() -> Unit = {},
): HealthConnectConversionResult {
    val spec = spec(measurement)
    val coded = code.mapping.value(code.token) ?: refuse(HealthConnectValueFailure.UnsupportedSourceValue("${type.token}.${code.token}"))
    val output = observation(spec, singleOutput(measurement), disclosesNativeIdentifier = true) {
        effective = dateTime(moment, "${type.token}.time")
        value = codedValue(code.mapping, requireNotNull(spec.resultCodeSystem), coded)
        configure()
    }
    return finish(output)
}

private fun RecordConversion.presentCoded(measurement: String, effective: Type): HealthConnectConversionResult {
    val spec = spec(measurement)
    val system = requireNotNull(spec.resultCodeSystem)
    val output = observation(spec, singleOutput(measurement), disclosesNativeIdentifier = true) {
        this.effective = effective
        value = CodeableConcept(Coding(system, PRESENT, HealthConnectContextMappings.displays[system]?.get(PRESENT)))
    }
    return finish(output)
}

private fun RecordConversion.convertCervicalMucus(record: CervicalMucusRecord): HealthConnectConversionResult {
    val sensationToken = HealthConnectSourceTokens.cervicalMucusSensation.token(record.sensation.at("sensation"))
    val sensation = HealthConnectContextMappings.cervicalMucusSensation.value(sensationToken)
        ?.takeIf { record.sensation != CervicalMucusRecord.SENSATION_UNKNOWN }
    val appearance = SourceCode(
        HealthConnectContextMappings.cervicalMucusAppearance,
        HealthConnectSourceTokens.cervicalMucusAppearance.token(record.appearance.at("appearance")),
    )
    val sensationComponent = spec("cervical-mucus-quality").component("sensation")
    return instantCoded("cervical-mucus-quality", record.time.at(record.zoneOffset), appearance) {
        sensation?.let { coded ->
            addComponent().apply {
                code = sensationComponent.code.concept()
                value = codedValue(
                    HealthConnectContextMappings.cervicalMucusSensation,
                    HealthConnectContract.GROVE_CERVICAL_MUCUS_SENSATION,
                    coded,
                )
            }
        }
    }
}

private fun RecordConversion.convertBloodPressure(record: BloodPressureRecord): HealthConnectConversionResult {
    val spec = spec("blood-pressure")
    val systolic = spec.component("systolic")
    val diastolic = spec.component("diastolic")
    val systolicValue = decimal(record.systolic.inMillimetersOfMercury.at("systolic"), systolic.quantity)
    val diastolicValue = decimal(record.diastolic.inMillimetersOfMercury.at("diastolic"), diastolic.quantity)
    val output = observation(spec, singleOutput("blood-pressure"), disclosesNativeIdentifier = true) {
        effective = dateTime(record.time.at(record.zoneOffset), "${type.token}.time")
        addComponent().apply {
            code = systolic.code.concept()
            value = quantity(requireNotNull(systolic.quantity), systolicValue)
        }
        externalCoding(
            HealthConnectContextMappings.bloodPressureBodyPosition,
            HealthConnectSourceTokens.bloodPressureBodyPosition,
            record.bodyPosition.at("bodyPosition"),
        )?.let { addExtension(Extension(HealthConnectContract.OBSERVATION_BODY_POSITION, CodeableConcept(it))) }
        bodySite = externalConcept(
            HealthConnectContextMappings.bloodPressureMeasurementLocation,
            HealthConnectSourceTokens.bloodPressureMeasurementLocation,
            record.measurementLocation.at("measurementLocation"),
        )
        addComponent().apply {
            code = diastolic.code.concept()
            value = quantity(requireNotNull(diastolic.quantity), diastolicValue)
        }
    }
    return finish(output)
}

private fun RecordConversion.temperatureSite(location: Int): CodeableConcept? = externalConcept(
    HealthConnectContextMappings.temperatureMeasurementLocation,
    HealthConnectSourceTokens.temperatureMeasurementLocation,
    location.at("measurementLocation"),
)
