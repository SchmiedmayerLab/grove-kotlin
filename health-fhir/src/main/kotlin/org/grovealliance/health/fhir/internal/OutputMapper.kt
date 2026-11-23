//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

@file:OptIn(ExperimentalMindfulnessSessionApi::class)
@file:Suppress("detekt:ReturnCount")

package org.grovealliance.health.fhir.internal

import androidx.health.connect.client.feature.ExperimentalMindfulnessSessionApi
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BasalBodyTemperatureRecord
import androidx.health.connect.client.records.BasalMetabolicRateRecord
import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.BodyTemperatureRecord
import androidx.health.connect.client.records.BodyWaterMassRecord
import androidx.health.connect.client.records.BoneMassRecord
import androidx.health.connect.client.records.CervicalMucusRecord
import androidx.health.connect.client.records.CyclingPedalingCadenceRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ElevationGainedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.FloorsClimbedRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.HeightRecord
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.IntermenstrualBleedingRecord
import androidx.health.connect.client.records.LeanBodyMassRecord
import androidx.health.connect.client.records.MenstruationFlowRecord
import androidx.health.connect.client.records.MenstruationPeriodRecord
import androidx.health.connect.client.records.MindfulnessSessionRecord
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.OvulationTestRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.PowerRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SexualActivityRecord
import androidx.health.connect.client.records.SkinTemperatureRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.SpeedRecord
import androidx.health.connect.client.records.StepsCadenceRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.Vo2MaxRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.WheelchairPushesRecord
import org.grovealliance.fhir.contract.ExchangeOutputDraft
import org.grovealliance.fhir.contract.FhirDecimal
import org.grovealliance.fhir.contract.InternalGroveFhirApi
import org.grovealliance.fhir.contract.MeasurementContract
import org.grovealliance.fhir.contract.Measurements
import org.grovealliance.fhir.contract.Observation
import org.grovealliance.fhir.contract.ProducerDiagnostic
import org.grovealliance.fhir.contract.Quantity
import org.grovealliance.health.fhir.HealthConnectConversionError
import org.grovealliance.health.fhir.HealthConnectConversionError.ValueFailure
import kotlin.reflect.KClass

/**
 * The outputs one Record converts to, as its Record class's contract counts them, with the warnings
 * about what they don't state.
 */
@OptIn(InternalGroveFhirApi::class)
@Suppress("detekt:LongParameterList")
internal class OutputMapper(
    private val times: SourceTimeMapper,
    private val observations: ObservationMapper,
    private val quantities: QuantityReadingMapper,
    private val bloodPressure: BloodPressureMapper,
    private val bloodGlucose: BloodGlucoseMapper,
    private val mindfulness: MindfulnessMapper,
    private val sleep: SleepMapper,
    private val exercise: ExerciseMapper,
    private val nutrition: NutritionMapper,
    private val skinTemperature: SkinTemperatureMapper,
    private val cycle: CycleMapper,
) {

    /**
     * The [drafts] of one Record and the [warnings] they carry.
     */
    data class Outputs(
        val drafts: List<ExchangeOutputDraft>,
        val warnings: List<ProducerDiagnostic>,
    )

    /**
     * The outputs of [record], a Record of [recordType] under [contract].
     */
    /**
     * Whether this version converts Records of [recordType].
     */
    fun converts(recordType: KClass<out Record>): Boolean = recordType in CONVERTIBLE_TYPES

    fun mapOutputs(
        record: Record,
        recordType: String,
        contract: HealthConnectRecordContract,
    ): Result<Outputs> = when {
        !converts(recordType = record::class) -> Result.failure(HealthConnectConversionError.NotYetConvertible(recordType = recordType))
        record is BloodPressureRecord -> bloodPressure.mapOutputs(record = record)
        record is BloodGlucoseRecord -> bloodGlucose.mapOutputs(record = record)
        record is MindfulnessSessionRecord -> mindfulness.mapOutputs(record = record)
        record is SleepSessionRecord -> sleep.mapOutputs(record = record)
        record is ExerciseSessionRecord -> exercise.mapOutputs(record = record)
        record is NutritionRecord -> nutrition.mapOutputs(record = record)
        record is SkinTemperatureRecord -> skinTemperature.mapOutputs(record = record)
        record::class in CYCLE_TYPES -> cycle.mapOutputs(record = record, recordType = recordType)
        else -> quantityOutputs(record = record, recordType = recordType, contract = contract)
    }

    private fun quantityOutputs(record: Record, recordType: String, contract: HealthConnectRecordContract): Result<Outputs> {
        val output = contract.outputs.singleOrNull()
        val readings = quantities.mapReadings(record = record)
        val measurement = output?.let { Measurements.byId[it.measurement] }
        if (output == null || readings == null || measurement?.quantity == null) {
            return Result.failure(HealthConnectConversionError.NotYetConvertible(recordType = recordType))
        }
        val occurrences = mutableMapOf<String, Int>()
        val drafts = readings.map { reading ->
            val observation = quantityObservation(
                record = record,
                recordType = recordType,
                measurement = measurement,
                reading = reading,
            ).getOrElse { return Result.failure(it) }
            val sampleTime = reading.sampleTime?.let { times.mapDiscriminator(instant = it) }
            val discriminator = sampleTime?.let { "$it|${occurrences.merge(it, 1, Int::plus)?.minus(1)}" } ?: measurement.id
            val isPerSample = output.countRule == HealthConnectRecordContract.CountRule.ONE_PER_SAMPLE
            val role = if (isPerSample) OutputRole.SAMPLE else OutputRole.SINGLE
            ExchangeOutputDraft(
                role = role.value,
                discriminator = discriminator,
                resource = ExchangeOutputDraft.Resource.ObservationResource(observation = observation),
            )
        }
        if (drafts.isEmpty()) return Result.failure(HealthConnectConversionError.NotYetConvertible(recordType = recordType))
        return Result.success(
            Outputs(
                drafts = drafts,
                warnings = times.mapOffsetWarnings(effectives = readings.filterNot { it.statesOffset }.map { it.effective }),
            ),
        )
    }

    private fun quantityObservation(
        record: Record,
        recordType: String,
        measurement: MeasurementContract,
        reading: QuantityReadingMapper.Reading,
    ): Result<Observation> {
        val quantity = requireNotNull(measurement.quantity)
        reading.unsupportedField?.let {
            return Result.failure(HealthConnectConversionError.InvalidValue(
                failure = ValueFailure.UNSUPPORTED_VALUE,
                location = "$recordType.$it",
            ))
        }
        val location = "$recordType.${reading.field}"
        val value = reading.value?.takeIf { quantity.valueDomain?.contains(value = it) != false }
            ?: return Result.failure(HealthConnectConversionError.InvalidValue(failure = ValueFailure.OUTSIDE_DOMAIN, location = location))
        val decimal = FhirDecimal.parse(lexeme = value.stripTrailingZeros().toPlainString()).getOrElse {
            return Result.failure(HealthConnectConversionError.InvalidValue(failure = ValueFailure.SHAPE_INVALID, location = location))
        }
        return Result.success(
            observations.mapObservation(
                claim = observations.mapClaim(measurement = measurement),
                recordType = recordType,
                metadata = record.metadata,
                effective = reading.effective,
                issued = times.mapIssued(instant = record.metadata.lastModifiedTime),
                value = Observation.Value.QuantityValue(
                    value = Quantity(
                        value = decimal,
                        unit = quantity.unit,
                        system = quantity.system,
                        code = quantity.code,
                    ),
                ),
            ).copy(
                bodySite = reading.bodySite,
                method = reading.method,
            ),
        )
    }

    private companion object {
        val CYCLE_TYPES: Set<KClass<out Record>> = setOf(
            CervicalMucusRecord::class,
            IntermenstrualBleedingRecord::class,
            MenstruationFlowRecord::class,
            MenstruationPeriodRecord::class,
            OvulationTestRecord::class,
            SexualActivityRecord::class,
        )
        val CONVERTIBLE_TYPES: Set<KClass<out Record>> = setOf(
            SkinTemperatureRecord::class,
            SexualActivityRecord::class,
            OvulationTestRecord::class,
            NutritionRecord::class,
            MenstruationPeriodRecord::class,
            MenstruationFlowRecord::class,
            IntermenstrualBleedingRecord::class,
            CervicalMucusRecord::class,
            ActiveCaloriesBurnedRecord::class,
            BasalBodyTemperatureRecord::class,
            BasalMetabolicRateRecord::class,
            BloodGlucoseRecord::class,
            BloodPressureRecord::class,
            BodyFatRecord::class,
            BodyTemperatureRecord::class,
            BodyWaterMassRecord::class,
            BoneMassRecord::class,
            CyclingPedalingCadenceRecord::class,
            DistanceRecord::class,
            ElevationGainedRecord::class,
            ExerciseSessionRecord::class,
            FloorsClimbedRecord::class,
            HeartRateRecord::class,
            HeartRateVariabilityRmssdRecord::class,
            HeightRecord::class,
            HydrationRecord::class,
            LeanBodyMassRecord::class,
            MindfulnessSessionRecord::class,
            OxygenSaturationRecord::class,
            PowerRecord::class,
            RespiratoryRateRecord::class,
            SleepSessionRecord::class,
            RestingHeartRateRecord::class,
            SpeedRecord::class,
            StepsCadenceRecord::class,
            StepsRecord::class,
            TotalCaloriesBurnedRecord::class,
            Vo2MaxRecord::class,
            WeightRecord::class,
            WheelchairPushesRecord::class,
        )
    }
}
