//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.health.fhir

import org.grovealliance.fhir.contract.ExchangeGraphError
import org.grovealliance.fhir.contract.ExchangeGraphRule
import org.grovealliance.fhir.contract.ProducerDiagnostic

/**
 * Why [HealthConnectFhirExporter] refused one Record; every case reports one registered [diagnostic].
 *
 * A Record with several faults is refused for the first in this order: its Record class, then the
 * metadata Health Connect assigns, then its content, then what it states about its writer.
 */
sealed class HealthConnectConversionError(message: String) : Exception(message) {

    /**
     * The registered diagnostic of this refusal.
     */
    abstract val diagnostic: ProducerDiagnostic

    /**
     * The contract converts no Record of [recordType].
     */
    data class UnsupportedRecordType(
        val recordType: String,
    ) : HealthConnectConversionError("$recordType is not converted.") {
        override val diagnostic: ProducerDiagnostic
            get() = ExchangeGraphRule.MOBILE_INPUT_UNSUPPORTED_SOURCE_TYPE.diagnostic(location = RECORD)
    }

    /**
     * The contract deliberately rejects every Record of [recordType].
     */
    data class IntentionallyUnsupported(
        val recordType: String,
    ) : HealthConnectConversionError("$recordType is rejected.") {
        override val diagnostic: ProducerDiagnostic
            get() = ExchangeGraphRule.MOBILE_INPUT_INTENTIONALLY_UNSUPPORTED_SOURCE_TYPE.diagnostic(location = RECORD)
    }

    /**
     * The contract admits [recordType], but this adapter version converts no Record of it.
     */
    data class NotYetConvertible(
        val recordType: String,
    ) : HealthConnectConversionError("$recordType is not convertible yet.") {
        override val diagnostic: ProducerDiagnostic
            get() = ExchangeGraphRule.MOBILE_INPUT_NOT_YET_CONVERTIBLE.diagnostic(location = RECORD)
    }

    /**
     * The value at [location] of a Record breaks its mapping, as [failure] states.
     */
    data class InvalidValue(
        val failure: ValueFailure,
        val location: String,
    ) : HealthConnectConversionError("${failure.name} at $location") {
        override val diagnostic: ProducerDiagnostic get() = failure.rule.diagnostic(location = location)
    }

    /**
     * The conversion instant precedes the Record's last modification, so it can't state this version.
     */
    data object ConversionPrecedesSourceVersion : HealthConnectConversionError("The conversion precedes the Record's version.") {
        override val diagnostic: ProducerDiagnostic
            get() = ExchangeGraphRule.MOBILE_INPUT_CONVERSION_INSTANT_PRECEDES_SOURCE_VERSION.diagnostic(location = LAST_MODIFIED)
    }

    /**
     * The converted record broke the exchange's identity or graph rules, as [cause] states.
     */
    data class Exchange(override val cause: Throwable) : HealthConnectConversionError(cause.message.orEmpty()) {
        override val diagnostic: ProducerDiagnostic
            get() = (cause as? ExchangeGraphError)?.diagnostic
                ?: ExchangeGraphRule.MOBILE_INPUT_UNCLASSIFIED.diagnostic(location = "Bundle")
    }

    /**
     * How a Record's value breaks its mapping.
     */
    enum class ValueFailure(internal val rule: ExchangeGraphRule) {
        SHAPE_INVALID(ExchangeGraphRule.MOBILE_INPUT_VALUE_SHAPE_INVALID),
        OUTSIDE_DOMAIN(ExchangeGraphRule.MOBILE_INPUT_VALUE_OUTSIDE_DOMAIN),
        UNSUPPORTED_VALUE(ExchangeGraphRule.MOBILE_INPUT_UNSUPPORTED_SOURCE_VALUE),
        REQUIRED_METADATA_MISSING(ExchangeGraphRule.MOBILE_INPUT_REQUIRED_METADATA_MISSING),
        REQUIRED_COMPONENT_MISSING(ExchangeGraphRule.MOBILE_INPUT_REQUIRED_COMPONENT_MISSING),
        EFFECTIVE_PERIOD_INVALID(ExchangeGraphRule.MOBILE_INPUT_EFFECTIVE_PERIOD_INVALID),
        TEXT_NOT_UNICODE_SCALAR(ExchangeGraphRule.MOBILE_INPUT_TEXT_NOT_UNICODE_SCALAR),
    }

    private companion object {
        const val RECORD = "Record"
        const val LAST_MODIFIED = "Record.metadata.lastModifiedTime"
    }
}
