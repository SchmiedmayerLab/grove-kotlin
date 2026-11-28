//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

@file:Suppress("detekt:ReturnCount")

package org.grovealliance.health.fhir.internal

import androidx.health.connect.client.records.BloodGlucoseRecord
import org.grovealliance.fhir.contract.CodeableConcept
import org.grovealliance.fhir.contract.ExchangeOutputDraft
import org.grovealliance.fhir.contract.Extension
import org.grovealliance.fhir.contract.FhirDecimal
import org.grovealliance.fhir.contract.InternalGroveFhirApi
import org.grovealliance.fhir.contract.Meta
import org.grovealliance.fhir.contract.Observation
import org.grovealliance.fhir.contract.Quantity
import org.grovealliance.fhir.contract.Specimen
import org.grovealliance.health.fhir.HealthConnectConversionError
import org.grovealliance.health.fhir.HealthConnectConversionError.ValueFailure

/**
 * The two outputs of a glucose Record with an admitted specimen source: the glucose Observation of the
 * specimen-specific profile that source selects, with its meal context, and the Specimen it names.
 */
@OptIn(InternalGroveFhirApi::class)
internal class BloodGlucoseMapper(
    private val times: SourceTimeMapper,
    private val observations: ObservationMapper,
) {

    fun mapOutputs(record: BloodGlucoseRecord): Result<OutputMapper.Outputs> {
        val specimen = HealthConnectCatalog.glucoseSpecimens[record.specimenSource]
            ?: return Result.failure(HealthConnectConversionError.InvalidValue(
                failure = ValueFailure.UNSUPPORTED_VALUE,
                location = "$RECORD_TYPE.specimenSource",
            ))
        val measurement = HealthConnectCatalog.adapterMeasurements.getValue(specimen.measurement)
        val mealContext = mealContext(record = record).getOrElse { return Result.failure(it) }
        val value = FhirDecimal.of(value = record.level.inMilligramsPerDeciliter).getOrElse {
            return Result.failure(HealthConnectConversionError.InvalidValue(
                failure = ValueFailure.OUTSIDE_DOMAIN,
                location = "$RECORD_TYPE.level",
            ))
        }
        val effective = times.mapDateTime(instant = record.time, offset = record.zoneOffset)
        val recorded = observations.mapObservation(
            claim = ObservationMapper.Claim(
                profiles = listOf(measurement.profile),
                codings = listOf(measurement.code),
                category = null,
            ),
            recordType = RECORD_TYPE,
            metadata = record.metadata,
            effective = effective,
            issued = times.mapIssued(instant = record.metadata.lastModifiedTime),
            value = Observation.Value.QuantityValue(
                value = Quantity(
                    value = value,
                    unit = measurement.quantity.unit,
                    system = measurement.quantity.system,
                    code = measurement.quantity.code,
                ),
            ),
        )
        val observation = recorded.copy(extension = recorded.extension + listOfNotNull(mealContext))
        return Result.success(
            OutputMapper.Outputs(
                drafts = listOf(
                    ExchangeOutputDraft(
                        role = OutputRole.SINGLE.value,
                        discriminator = measurement.id,
                        resource = ExchangeOutputDraft.Resource.ObservationResource(observation = observation),
                    ),
                    ExchangeOutputDraft(
                        role = OutputRole.SPECIMEN.value,
                        discriminator = specimen.token,
                        resource = ExchangeOutputDraft.Resource.SpecimenResource(
                            specimen = Specimen(
                                meta = Meta(profile = listOf(HealthConnectCatalog.SPECIMEN_PROFILE)),
                                type = CodeableConcept(coding = listOf(specimen.type)),
                            ),
                        ),
                        specimenOfPrimary = true,
                    ),
                ),
                warnings = if (record.zoneOffset == null) times.mapOffsetWarnings(effectives = listOf(effective)) else emptyList(),
            ),
        )
    }

    /**
     * The meal context a Record states, `null` when it states neither a relation to a meal nor a meal type.
     */
    private fun mealContext(record: BloodGlucoseRecord): Result<Extension?> {
        if (!HealthConnectCatalog.relationsToMeal.containsKey(record.relationToMeal)) {
            return Result.failure(HealthConnectConversionError.InvalidValue(
                failure = ValueFailure.UNSUPPORTED_VALUE,
                location = "$RECORD_TYPE.relationToMeal",
            ))
        }
        if (!HealthConnectCatalog.mealTypes.containsKey(record.mealType)) {
            return Result.failure(HealthConnectConversionError.InvalidValue(
                failure = ValueFailure.UNSUPPORTED_VALUE,
                location = "$RECORD_TYPE.mealType",
            ))
        }
        val parts = listOfNotNull(
            HealthConnectCatalog.relationsToMeal[record.relationToMeal]?.let { Extension(
                url = "relationToMeal",
                value = Extension.Value.CodingValue(value = it),
            ) },
            HealthConnectCatalog.mealTypes[record.mealType]?.let { Extension(
                url = "mealType",
                value = Extension.Value.CodingValue(value = it),
            ) },
        )
        return Result.success(parts.takeIf { it.isNotEmpty() }?.let { Extension(
            url = HealthConnectCatalog.MEAL_CONTEXT_EXTENSION,
            extension = it,
        ) })
    }

    private companion object {
        const val RECORD_TYPE = "BloodGlucoseRecord"
    }
}
