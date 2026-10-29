//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.account

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * An [Account] whose signed-in state is driven directly through [supplyUserDetails] and
 * [removeUserDetails]. It has no service or key configuration.
 */
class FakeAccount : Account {
    private val _details = MutableStateFlow<AccountDetails?>(value = null)

    override val service: AccountService
        get() = error("FakeAccount has no account service.")

    override val configuration: AccountValueConfiguration
        get() = error("FakeAccount has no key configuration.")

    override val details: StateFlow<AccountDetails?> = _details.asStateFlow()

    override fun supplyUserDetails(details: AccountDetails) {
        _details.value = details
    }

    override fun removeUserDetails() {
        _details.value = null
    }
}
