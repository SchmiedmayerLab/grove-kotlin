//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.health.fhir

import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.Record
import org.grovealliance.fhir.ConversionBatch
import org.grovealliance.fhir.ExchangeEventContext
import org.grovealliance.fhir.ExchangeGraphException
import org.grovealliance.fhir.ExchangeIdentityException
import org.grovealliance.fhir.RetractionTarget

/**
 * Converts AndroidX Health Connect records into Grove Mobile exchange graphs.
 *
 * The converter holds no state: every call takes the record and the context the deployment reserved
 * for it, and every refusal is a result carrying exactly one registry code.
 */
public class HealthConnectConverter {
    /** Converts one record; the result is never an exception for a refused record. */
    public fun convert(record: Record, context: HealthConnectConversionContext): HealthConnectConversionResult {
        val type = HealthConnectSourceType.of(record)
            ?: return failed(HealthConnectConversionFailure.UnsupportedSourceType(record::class.java.simpleName))
        return when (type.status) {
            HealthConnectSourceStatus.SUPPORTED -> convertSupported(record, type, context)
            HealthConnectSourceStatus.DEFERRED -> failed(HealthConnectConversionFailure.NotYetConvertible(type))
            HealthConnectSourceStatus.INTENTIONALLY_UNSUPPORTED -> failed(HealthConnectConversionFailure.IntentionallyUnsupported(type))
            HealthConnectSourceStatus.PLATFORM_EXCLUSIVE -> failed(HealthConnectConversionFailure.PlatformExclusive(type))
            HealthConnectSourceStatus.MAPPED_STANDARD,
            HealthConnectSourceStatus.UNMODELED,
            -> failed(HealthConnectConversionFailure.UnsupportedSourceType(type.token))
        }
    }

    /** Converts every record with the context the caller reserves for it, keeping conversions and refusals in input order. */
    public fun convert(
        records: Iterable<Record>,
        context: (Record) -> HealthConnectConversionContext,
    ): ConversionBatch<HealthConnectConversion, HealthConnectRecordFailure> {
        val conversions = mutableListOf<HealthConnectConversion>()
        val failures = mutableListOf<HealthConnectRecordFailure>()
        records.forEach { record ->
            when (val result = convert(record, context(record))) {
                is HealthConnectConversionResult.Converted -> conversions += result.conversion
                is HealthConnectConversionResult.NoOutput -> Unit
                is HealthConnectConversionResult.Failed -> failures += HealthConnectRecordFailure(sourceOf(record), result.failure)
            }
        }
        return ConversionBatch(conversions, failures)
    }

    /**
     * The retraction targets of a record's prior active graph, derived from the catalog alone.
     *
     * Only source types whose every output is an exactly-one measurement can be named without the
     * record's content; a fan-out type requires the stored graph's own `ExchangeGraph.retractionTargets()`.
     */
    public fun retractionTargets(
        record: HealthConnectSourceRecord,
        context: ExchangeEventContext,
    ): List<RetractionTarget> {
        val outputs = HealthConnectCatalog.outputs(record.type)
        require(outputs.isNotEmpty() && outputs.all { it.countRule == HealthConnectOutputCountRule.EXACTLY_ONE }) {
            "${record.type.token} outputs depend on the record's content; derive its targets from the stored graph."
        }
        return outputs.map { output ->
            val measurement = requireNotNull(output.measurement)
            val identifier = context.identityScope.sourceOutput(
                HealthConnectContract.ADAPTER_ID,
                record.type.token,
                context.repositoryScope,
                record.id,
                output.outputRole,
                measurement,
            )
            RetractionTarget(identifier, output.resourceType, output.retractionRole)
        }
    }

    private fun convertSupported(
        record: Record,
        type: HealthConnectSourceType,
        context: HealthConnectConversionContext,
    ): HealthConnectConversionResult = try {
        val conversion = RecordConversion(record, type, context)
        when (record) {
            is BloodGlucoseRecord -> conversion.convertBloodGlucose(record)
            is NutritionRecord -> conversion.convertNutrition(record)
            else -> conversion.convertScalar() ?: conversion.convertSeries() ?: conversion.convertSession()
                ?: failed(HealthConnectConversionFailure.NotYetConvertible(type))
        }
    } catch (refusal: HealthConnectRecordRefusal) {
        failed(HealthConnectConversionFailure.InvalidValue(type, refusal.failure))
    } catch (error: ExchangeIdentityException) {
        failed(HealthConnectConversionFailure.ExchangeIdentity(error.error))
    } catch (error: ExchangeGraphException) {
        failed(HealthConnectConversionFailure.ExchangeGraph(error.error))
    } catch (error: IllegalArgumentException) {
        failed(HealthConnectConversionFailure.Unclassified(error))
    }

    private fun failed(failure: HealthConnectConversionFailure) = HealthConnectConversionResult.Failed(failure)

    private fun sourceOf(record: Record): HealthConnectSourceRecord? {
        val type = HealthConnectSourceType.of(record)?.takeIf { record.metadata.id.isNotBlank() } ?: return null
        return HealthConnectSourceRecord(record.metadata.id, type)
    }
}
