//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.fhir.contract

/**
 * The `node-role` component of a content snapshot, which also selects the identity kind that keys it.
 */
@InternalGroveFhirApi
sealed interface NodeRole {

    /**
     * The role's component value.
     */
    val rawValue: String

    /**
     * The identity kind that keys a node in this role.
     */
    val kind: OpaqueIdentityKind

    /**
     * The application that assembled a graph.
     */
    data object Application : NodeRole {
        override val rawValue: String = "application"
        override val kind: OpaqueIdentityKind = OpaqueIdentityKind.DEVICE_SNAPSHOT
    }

    /**
     * The device the application ran on.
     */
    data object Host : NodeRole {
        override val rawValue: String = "host"
        override val kind: OpaqueIdentityKind = OpaqueIdentityKind.DEVICE_SNAPSHOT
    }

    /**
     * A device that recorded a measurement.
     */
    data object RecordingDevice : NodeRole {
        override val rawValue: String = "recording-device"
        override val kind: OpaqueIdentityKind = OpaqueIdentityKind.DEVICE_SNAPSHOT
    }

    /**
     * A shared context node in [role].
     */
    data class Context(val role: ContextSnapshotRole) : NodeRole {
        override val rawValue: String get() = role.rawValue
        override val kind: OpaqueIdentityKind get() = OpaqueIdentityKind.CONTEXT_SNAPSHOT
    }

    companion object {

        /**
         * The role [rawValue] states as a component of [kind], or `null` when it is none of that
         * kind's roles.
         */
        fun of(kind: OpaqueIdentityKind, rawValue: String): NodeRole? = when (kind) {
            OpaqueIdentityKind.DEVICE_SNAPSHOT -> listOf(Application, Host, RecordingDevice).firstOrNull { it.rawValue == rawValue }
            OpaqueIdentityKind.CONTEXT_SNAPSHOT -> ContextSnapshotRole.fromRawValue(rawValue = rawValue)?.let { Context(role = it) }
            else -> null
        }
    }
}

/**
 * The role of a shared context node keyed by its content.
 */
@InternalGroveFhirApi
enum class ContextSnapshotRole(val rawValue: String) {
    PATIENT("patient"),
    RESEARCH_STUDY("research-study"),
    RESEARCH_SUBJECT("research-subject"),
    PLAN_DEFINITION("plan-definition"),
    QUESTIONNAIRE_RESPONSE("questionnaire-response"),
    ;

    companion object {

        /**
         * The role [rawValue] names, or `null` when it names none.
         */
        fun fromRawValue(
            rawValue: String,
        ): ContextSnapshotRole? = entries.firstOrNull { it.rawValue == rawValue }
    }
}

/**
 * The providers the identity protocol admits. Their records mint under the provider identity
 * kinds, so the generic `source-*` kinds refuse these codes as adapter ids.
 */
@InternalGroveFhirApi
enum class ProviderCode(val rawValue: String) {
    GOOGLE_HEALTH_API("google-health-api"),
    OURA("oura"),
    WITHINGS("withings"),
    ;

    companion object {

        /**
         * The provider [rawValue] names, or `null` when it names none.
         */
        fun fromRawValue(rawValue: String): ProviderCode? = entries.firstOrNull { it.rawValue == rawValue }
    }
}
