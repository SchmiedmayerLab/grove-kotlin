//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

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
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SexualActivityRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.Vo2MaxRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.WheelchairPushesRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Length
import androidx.health.connect.client.units.Mass
import androidx.health.connect.client.units.Percentage
import androidx.health.connect.client.units.Power
import androidx.health.connect.client.units.Pressure
import androidx.health.connect.client.units.Temperature
import androidx.health.connect.client.units.Volume
import org.hl7.fhir.r4.model.CodeableConcept
import org.hl7.fhir.r4.model.Observation
import org.hl7.fhir.r4.model.Specimen

/** Inverts the generated catalog: the outputs of one source record back into its AndroidX record. */
internal object HealthConnectProjection {
    fun project(observations: List<Observation>, companions: List<Specimen>, clientRecordId: String?): Record {
        val tokens = observations.map { it.recordTypeToken() }.toSet()
        val token = tokens.singleOrNull()
            ?: refuseProjection(HealthConnectProjectionRefusal.NotHealthConnectOutput("Observation.extension"))
        val type = HealthConnectSourceType.of(token) ?: refuseProjection(HealthConnectProjectionRefusal.UnsupportedSourceType(token))
        val primary = observations.primary()
        val group = OutputGroup(type, primary, observations - primary, companions, primary.projectedMetadata(clientRecordId))
        return group.scalar() ?: group.series() ?: group.session() ?: group.special()
            ?: refuseProjection(HealthConnectProjectionRefusal.UnsupportedSourceType(token))
    }

    /** The summary output when the graph names members, otherwise the first output. */
    private fun List<Observation>.primary(): Observation {
        val members = flatMap { it.hasMember }.mapNotNull { it.reference }.toSet()
        return firstOrNull { observation -> members.isEmpty() || observation.hasMember.isNotEmpty() } ?: first()
    }
}

/** The outputs one source record produced, with the record metadata they project back into. */
internal class OutputGroup(
    val type: HealthConnectSourceType,
    val primary: Observation,
    val children: List<Observation>,
    val companions: List<Specimen>,
    val metadata: Metadata,
) {
    val primaryMeasurement: String? = primary.measurementId()

    fun spec(measurement: String): MeasurementSpec = HealthConnectMeasurements.spec(measurement)

    fun quantity(observation: Observation, measurement: String): Double =
        observation.quantityValue(requireNotNull(spec(measurement).quantity))

    /** The sole output of an exactly-one type; members mean the caller must project the whole graph. */
    fun requireSingle(): Observation {
        if (children.isNotEmpty()) refuseProjection(HealthConnectProjectionRefusal.ChildOutput(type.token))
        return primary
    }

    /** The summary output of a session, refusing a member that was projected alone. */
    fun summary(measurement: String): Observation {
        if (primaryMeasurement != measurement) {
            refuseProjection(HealthConnectProjectionRefusal.ChildOutput(primaryMeasurement ?: type.token))
        }
        return primary
    }

    fun temperatureLocation(observation: Observation): Int = HealthConnectSourceTokens.temperatureMeasurementLocation.externalValue(
        HealthConnectContextMappings.temperatureMeasurementLocation,
        observation.bodySite?.codingFirstRep?.takeIf { it.hasCode() },
        BODY_SITE_PATH,
    )

    @Suppress("CyclomaticComplexMethod", "LongMethod") // One arm per exactly-one source type.
    fun scalar(): Record? = when (type) {
        HealthConnectSourceType.ACTIVE_CALORIES_BURNED -> requireSingle().let { o ->
            val span = o.interval()
            ActiveCaloriesBurnedRecord(
                startTime = span.start.time,
                startZoneOffset = span.start.offset,
                endTime = span.end.time,
                endZoneOffset = span.end.offset,
                energy = Energy.kilocalories(quantity(o, "active-energy")),
                metadata = metadata,
            )
        }
        HealthConnectSourceType.BASAL_BODY_TEMPERATURE -> requireSingle().let { o ->
            BasalBodyTemperatureRecord(
                time = o.moment().time,
                zoneOffset = o.moment().offset,
                metadata = metadata,
                temperature = Temperature.celsius(quantity(o, "basal-body-temperature")),
                measurementLocation = temperatureLocation(o),
            )
        }
        HealthConnectSourceType.BASAL_METABOLIC_RATE -> requireSingle().let { o ->
            BasalMetabolicRateRecord(
                time = o.moment().time,
                zoneOffset = o.moment().offset,
                basalMetabolicRate = Power.kilocaloriesPerDay(quantity(o, "basal-metabolic-rate")),
                metadata = metadata,
            )
        }
        HealthConnectSourceType.BLOOD_PRESSURE -> requireSingle().let(::bloodPressure)
        HealthConnectSourceType.BODY_FAT -> requireSingle().let { o ->
            BodyFatRecord(
                time = o.moment().time,
                zoneOffset = o.moment().offset,
                percentage = Percentage(quantity(o, "body-fat-percentage")),
                metadata = metadata,
            )
        }
        HealthConnectSourceType.BODY_TEMPERATURE -> requireSingle().let { o ->
            BodyTemperatureRecord(
                time = o.moment().time,
                zoneOffset = o.moment().offset,
                metadata = metadata,
                temperature = Temperature.celsius(quantity(o, "body-temperature")),
                measurementLocation = temperatureLocation(o),
            )
        }
        HealthConnectSourceType.BODY_WATER_MASS -> requireSingle().let { o ->
            BodyWaterMassRecord(
                time = o.moment().time,
                zoneOffset = o.moment().offset,
                mass = Mass.kilograms(quantity(o, "body-water-mass")),
                metadata = metadata,
            )
        }
        HealthConnectSourceType.BONE_MASS -> requireSingle().let { o ->
            BoneMassRecord(
                time = o.moment().time,
                zoneOffset = o.moment().offset,
                mass = Mass.kilograms(quantity(o, "bone-mass")),
                metadata = metadata,
            )
        }
        HealthConnectSourceType.DISTANCE -> requireSingle().let { o ->
            val span = o.interval()
            DistanceRecord(
                startTime = span.start.time,
                startZoneOffset = span.start.offset,
                endTime = span.end.time,
                endZoneOffset = span.end.offset,
                distance = Length.meters(quantity(o, "distance")),
                metadata = metadata,
            )
        }
        HealthConnectSourceType.ELEVATION_GAINED -> requireSingle().let { o ->
            val span = o.interval()
            ElevationGainedRecord(
                startTime = span.start.time,
                startZoneOffset = span.start.offset,
                endTime = span.end.time,
                endZoneOffset = span.end.offset,
                elevation = Length.meters(quantity(o, "elevation-gained")),
                metadata = metadata,
            )
        }
        HealthConnectSourceType.FLOORS_CLIMBED -> requireSingle().let { o ->
            val span = o.interval()
            FloorsClimbedRecord(
                startTime = span.start.time,
                startZoneOffset = span.start.offset,
                endTime = span.end.time,
                endZoneOffset = span.end.offset,
                floors = quantity(o, "flights-climbed"),
                metadata = metadata,
            )
        }
        HealthConnectSourceType.HEART_RATE_VARIABILITY_RMSSD -> requireSingle().let { o ->
            HeartRateVariabilityRmssdRecord(
                time = o.moment().time,
                zoneOffset = o.moment().offset,
                heartRateVariabilityMillis = quantity(o, "heart-rate-variability-rmssd"),
                metadata = metadata,
            )
        }
        HealthConnectSourceType.HEIGHT -> requireSingle().let { o ->
            HeightRecord(
                time = o.moment().time,
                zoneOffset = o.moment().offset,
                height = Length.meters(quantity(o, "body-height") / CENTIMETERS_PER_METER),
                metadata = metadata,
            )
        }
        HealthConnectSourceType.HYDRATION -> requireSingle().let { o ->
            val span = o.interval()
            HydrationRecord(
                startTime = span.start.time,
                startZoneOffset = span.start.offset,
                endTime = span.end.time,
                endZoneOffset = span.end.offset,
                volume = Volume.milliliters(quantity(o, "fluid-intake")),
                metadata = metadata,
            )
        }
        HealthConnectSourceType.LEAN_BODY_MASS -> requireSingle().let { o ->
            LeanBodyMassRecord(
                time = o.moment().time,
                zoneOffset = o.moment().offset,
                mass = Mass.kilograms(quantity(o, "lean-body-mass")),
                metadata = metadata,
            )
        }
        HealthConnectSourceType.OXYGEN_SATURATION -> requireSingle().let { o ->
            OxygenSaturationRecord(
                time = o.moment().time,
                zoneOffset = o.moment().offset,
                percentage = Percentage(quantity(o, "oxygen-saturation")),
                metadata = metadata,
            )
        }
        HealthConnectSourceType.RESPIRATORY_RATE -> requireSingle().let { o ->
            RespiratoryRateRecord(
                time = o.moment().time,
                zoneOffset = o.moment().offset,
                rate = quantity(o, "respiratory-rate"),
                metadata = metadata,
            )
        }
        HealthConnectSourceType.RESTING_HEART_RATE -> requireSingle().let { o ->
            RestingHeartRateRecord(
                time = o.moment().time,
                zoneOffset = o.moment().offset,
                beatsPerMinute = quantity(o, "resting-heart-rate").toLong(),
                metadata = metadata,
            )
        }
        HealthConnectSourceType.STEPS -> requireSingle().let { o ->
            val span = o.interval()
            StepsRecord(
                startTime = span.start.time,
                startZoneOffset = span.start.offset,
                endTime = span.end.time,
                endZoneOffset = span.end.offset,
                count = quantity(o, "step-count").toLong(),
                metadata = metadata,
            )
        }
        HealthConnectSourceType.TOTAL_CALORIES_BURNED -> requireSingle().let { o ->
            val span = o.interval()
            TotalCaloriesBurnedRecord(
                startTime = span.start.time,
                startZoneOffset = span.start.offset,
                endTime = span.end.time,
                endZoneOffset = span.end.offset,
                energy = Energy.kilocalories(quantity(o, "total-energy")),
                metadata = metadata,
            )
        }
        HealthConnectSourceType.VO2_MAX -> requireSingle().let { o ->
            val method = o.methodCode(HealthConnectContextMappings.vo2MaxMeasurementMethod.codeSystem)
            Vo2MaxRecord(
                time = o.moment().time,
                zoneOffset = o.moment().offset,
                metadata = metadata,
                vo2MillilitersPerMinuteKilogram = quantity(o, "vo2-max"),
                measurementMethod = HealthConnectSourceTokens.vo2MaxMeasurementMethod.sourceValue(method, METHOD_PATH),
            )
        }
        HealthConnectSourceType.WEIGHT -> requireSingle().let { o ->
            WeightRecord(
                time = o.moment().time,
                zoneOffset = o.moment().offset,
                weight = Mass.kilograms(quantity(o, "body-weight")),
                metadata = metadata,
            )
        }
        HealthConnectSourceType.WHEELCHAIR_PUSHES -> requireSingle().let { o ->
            val span = o.interval()
            WheelchairPushesRecord(
                startTime = span.start.time,
                startZoneOffset = span.start.offset,
                endTime = span.end.time,
                endZoneOffset = span.end.offset,
                count = quantity(o, "wheelchair-push-count").toLong(),
                metadata = metadata,
            )
        }
        HealthConnectSourceType.MENSTRUATION_FLOW -> requireSingle().let { o ->
            val token = o.sourceToken(HealthConnectContextMappings.menstruationFlow.sourceSystem)
            MenstruationFlowRecord(
                time = o.moment().time,
                zoneOffset = o.moment().offset,
                metadata = metadata,
                flow = HealthConnectSourceTokens.menstruationFlow.sourceValue(token, VALUE_PATH),
            )
        }
        HealthConnectSourceType.OVULATION_TEST -> requireSingle().let { o ->
            val token = o.sourceToken(HealthConnectContextMappings.ovulationTestResult.sourceSystem)
            OvulationTestRecord(
                time = o.moment().time,
                zoneOffset = o.moment().offset,
                result = HealthConnectSourceTokens.ovulationTestResult.sourceValue(token, VALUE_PATH),
                metadata = metadata,
            )
        }
        HealthConnectSourceType.SEXUAL_ACTIVITY -> requireSingle().let { o ->
            val token = o.sourceToken(HealthConnectContextMappings.sexualActivityProtection.sourceSystem)
            SexualActivityRecord(
                time = o.moment().time,
                zoneOffset = o.moment().offset,
                metadata = metadata,
                protectionUsed = HealthConnectSourceTokens.sexualActivityProtection.sourceValue(token, VALUE_PATH),
            )
        }
        HealthConnectSourceType.CERVICAL_MUCUS -> requireSingle().let(::cervicalMucus)
        HealthConnectSourceType.INTERMENSTRUAL_BLEEDING -> requireSingle().let { o ->
            IntermenstrualBleedingRecord(time = o.moment().time, zoneOffset = o.moment().offset, metadata = metadata)
        }
        HealthConnectSourceType.MENSTRUATION_PERIOD -> requireSingle().let { o ->
            val span = o.interval()
            MenstruationPeriodRecord(
                startTime = span.start.time,
                startZoneOffset = span.start.offset,
                endTime = span.end.time,
                endZoneOffset = span.end.offset,
                metadata = metadata,
            )
        }
        else -> null
    }

    private fun bloodPressure(observation: Observation): BloodPressureRecord {
        val spec = spec("blood-pressure")
        val systolic = observation.componentValue(spec.component("systolic"))
            ?: refuseProjection(HealthConnectProjectionRefusal.MissingElement("Observation.component[systolic]"))
        val diastolic = observation.componentValue(spec.component("diastolic"))
            ?: refuseProjection(HealthConnectProjectionRefusal.MissingElement("Observation.component[diastolic]"))
        val position = observation.getExtensionByUrl(HealthConnectContract.OBSERVATION_BODY_POSITION)?.value as? CodeableConcept
        return BloodPressureRecord(
            time = observation.moment().time,
            zoneOffset = observation.moment().offset,
            metadata = metadata,
            systolic = Pressure.millimetersOfMercury(systolic),
            diastolic = Pressure.millimetersOfMercury(diastolic),
            bodyPosition = HealthConnectSourceTokens.bloodPressureBodyPosition.externalValue(
                HealthConnectContextMappings.bloodPressureBodyPosition,
                position?.codingFirstRep?.takeIf { it.hasCode() },
                "Observation.extension[bodyPosition]",
            ),
            measurementLocation = HealthConnectSourceTokens.bloodPressureMeasurementLocation.externalValue(
                HealthConnectContextMappings.bloodPressureMeasurementLocation,
                observation.bodySite?.codingFirstRep?.takeIf { it.hasCode() },
                BODY_SITE_PATH,
            ),
        )
    }

    private fun cervicalMucus(observation: Observation): CervicalMucusRecord {
        val appearance = observation.sourceToken(HealthConnectContextMappings.cervicalMucusAppearance.sourceSystem)
        val sensation = observation.component.firstOrNull()?.valueCodeableConcept?.coding
            ?.firstOrNull { it.system == HealthConnectContextMappings.cervicalMucusSensation.sourceSystem }?.code
        return CervicalMucusRecord(
            time = observation.moment().time,
            zoneOffset = observation.moment().offset,
            metadata = metadata,
            appearance = HealthConnectSourceTokens.cervicalMucusAppearance.sourceValue(appearance, VALUE_PATH),
            sensation = sensation?.let { HealthConnectSourceTokens.cervicalMucusSensation.sourceValue(it, "Observation.component") }
                ?: UNKNOWN_SOURCE_VALUE,
        )
    }

    companion object {
        const val CENTIMETERS_PER_METER = 100.0
        const val VALUE_PATH = "Observation.valueCodeableConcept"
        const val BODY_SITE_PATH = "Observation.bodySite"
        const val METHOD_PATH = "Observation.method"
    }
}
