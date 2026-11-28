//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.fhir.contract

/**
 * The semantic role a Grove exchange identifier carries in `Identifier.type`.
 */
enum class GroveIdentifierRole(val code: String) {
    SOURCE_RECORD("source-record"),
    SOURCE_OUTPUT("source-output"),
    WRITER_RECORD("writer-record"),
    SOURCE_ARTIFACT("source-artifact"),
    SOURCE_CONTEXT("source-context"),
    RECORDING_DEVICE("recording-device"),
    DEVICE_SNAPSHOT("device-snapshot"),
    CONTEXT_SNAPSHOT("context-snapshot"),
    RECORD_VERSION("record-version"),
    RECORD_RETRACTION("record-retraction"),
    ;

    companion object {

        /**
         * The code system the role's code belongs to.
         */
        const val CODE_SYSTEM: String = ExchangeContract.IDENTIFIER_ROLE_CODE_SYSTEM

        /**
         * The role [code] names, or `null` when it names none.
         */
        fun fromCode(code: String): GroveIdentifierRole? = entries.firstOrNull { it.code == code }
    }
}
