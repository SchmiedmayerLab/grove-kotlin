//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.health.fhir

import org.grovealliance.fhir.ExchangeGraphDiagnostic
import org.grovealliance.fhir.ExchangeGraphError
import org.grovealliance.fhir.ExchangeGraphRule
import org.grovealliance.fhir.ExchangeIdentityError
import org.grovealliance.fhir.at

/** Why one source value refused conversion; every case is one `mobile-input.*` registry rule at a source field. */
public sealed interface HealthConnectValueFailure {
    /** The registered rule this failure reports. */
    public val rule: ExchangeGraphRule

    /** The source field, such as `HeartRateRecord.samples[2].time`. */
    public val path: String

    /** The registry diagnostic at the source field. */
    public val diagnostic: ExchangeGraphDiagnostic
        get() = rule.at(path)

    /** The source value does not have the shape its mapping requires. */
    public data class ValueShapeInvalid(override val path: String) : HealthConnectValueFailure {
        override val rule: ExchangeGraphRule get() = ExchangeGraphRule.MOBILE_INPUT_VALUE_SHAPE_INVALID
    }

    /** A number is nonfinite, outside the measurement's domain, or fractional where integers are required. */
    public data class ValueOutsideDomain(override val path: String) : HealthConnectValueFailure {
        override val rule: ExchangeGraphRule get() = ExchangeGraphRule.MOBILE_INPUT_VALUE_OUTSIDE_DOMAIN
    }

    /** A source enumeration has no published mapping. */
    public data class UnsupportedSourceValue(override val path: String) : HealthConnectValueFailure {
        override val rule: ExchangeGraphRule get() = ExchangeGraphRule.MOBILE_INPUT_UNSUPPORTED_SOURCE_VALUE
    }

    /** A source field the contract requires is absent. */
    public data class RequiredMetadataMissing(override val path: String) : HealthConnectValueFailure {
        override val rule: ExchangeGraphRule get() = ExchangeGraphRule.MOBILE_INPUT_REQUIRED_METADATA_MISSING
    }

    /** An effective instant or period is not a valid FHIR time, is inverted, or misplaces a sample. */
    public data class EffectivePeriodInvalid(override val path: String) : HealthConnectValueFailure {
        override val rule: ExchangeGraphRule get() = ExchangeGraphRule.MOBILE_INPUT_EFFECTIVE_PERIOD_INVALID
    }

    /** The conversion instant precedes the record's own last-modified time. */
    public data class ConversionInstantPrecedesSourceVersion(override val path: String) : HealthConnectValueFailure {
        override val rule: ExchangeGraphRule
            get() = ExchangeGraphRule.MOBILE_INPUT_CONVERSION_INSTANT_PRECEDES_SOURCE_VERSION
    }

    /** A source text carries an unpaired UTF-16 surrogate. */
    public data class TextNotUnicodeScalar(override val path: String) : HealthConnectValueFailure {
        override val rule: ExchangeGraphRule get() = ExchangeGraphRule.MOBILE_INPUT_TEXT_NOT_UNICODE_SCALAR
    }

    /** The native record identifier or writer record version is absent, blank or negative. */
    public data class NativeIdentifierInvalid(override val path: String) : HealthConnectValueFailure {
        override val rule: ExchangeGraphRule get() = ExchangeGraphRule.MOBILE_INPUT_NATIVE_IDENTIFIER_INVALID
    }
}

/** Why one Health Connect record produced no graph; every case carries exactly one registry diagnostic. */
public sealed interface HealthConnectConversionFailure {
    /** The registry diagnostic this failure reports. */
    public val diagnostic: ExchangeGraphDiagnostic

    /** The Record class is outside the catalog inventory or the catalog admits no profile for it. */
    public data class UnsupportedSourceType(public val recordType: String) : HealthConnectConversionFailure {
        override val diagnostic: ExchangeGraphDiagnostic
            get() = ExchangeGraphRule.MOBILE_INPUT_UNSUPPORTED_SOURCE_TYPE.at(recordType)
    }

    /** The catalog deliberately refuses the source type. */
    public data class IntentionallyUnsupported(
        public val type: HealthConnectSourceType,
    ) : HealthConnectConversionFailure {
        override val diagnostic: ExchangeGraphDiagnostic
            get() = ExchangeGraphRule.MOBILE_INPUT_INTENTIONALLY_UNSUPPORTED_SOURCE_TYPE.at(type.token)
    }

    /** The catalog admits the source type, but this producer version does not yet emit its graph. */
    public data class NotYetConvertible(public val type: HealthConnectSourceType) : HealthConnectConversionFailure {
        override val diagnostic: ExchangeGraphDiagnostic
            get() = ExchangeGraphRule.MOBILE_INPUT_NOT_YET_CONVERTIBLE.at(type.token)
    }

    /** The source type is admitted only as a platform-exclusive recording document. */
    public data class PlatformExclusive(public val type: HealthConnectSourceType) : HealthConnectConversionFailure {
        override val diagnostic: ExchangeGraphDiagnostic
            get() = ExchangeGraphRule.MOBILE_INPUT_PLATFORM_EXCLUSIVE_SOURCE_TYPE.at(type.token)
    }

    /** One source value refused conversion. */
    public data class InvalidValue(
        public val type: HealthConnectSourceType,
        public val reason: HealthConnectValueFailure,
    ) : HealthConnectConversionFailure {
        override val diagnostic: ExchangeGraphDiagnostic get() = reason.diagnostic
    }

    /** A source-derived identity could not be minted. */
    public data class ExchangeIdentity(public val error: ExchangeIdentityError) : HealthConnectConversionFailure {
        override val diagnostic: ExchangeGraphDiagnostic get() = error.diagnostic
    }

    /** The assembled graph violated the exchange protocol. */
    public data class ExchangeGraph(public val error: ExchangeGraphError) : HealthConnectConversionFailure {
        override val diagnostic: ExchangeGraphDiagnostic get() = error.diagnostic
    }

    /** A precondition of the FHIR model or the contract failed without a more specific rule. */
    public data class Unclassified(public val cause: Throwable) : HealthConnectConversionFailure {
        override val diagnostic: ExchangeGraphDiagnostic
            get() = ExchangeGraphRule.MOBILE_INPUT_UNCLASSIFIED.at("Record")
    }
}

/** Carries a value refusal out of a conversion step; the converter turns it into a result. */
internal class HealthConnectRecordRefusal(val failure: HealthConnectValueFailure) : RuntimeException(failure.toString())

internal fun refuse(failure: HealthConnectValueFailure): Nothing = throw HealthConnectRecordRefusal(failure)
