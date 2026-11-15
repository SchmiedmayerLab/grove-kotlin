//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.account

/**
 * An [AccountService] that records the account updates it receives and answers them with a
 * configured result, and counts verification emails and reloads. Every other operation succeeds
 * without effect.
 */
class FakeAccountService : AccountService {
    private var updateResult: Result<Unit> = Result.success(Unit)
    private val _updates = mutableListOf<AccountModifications>()

    /**
     * The modifications passed to [updateAccountDetails], in call order.
     */
    val updates: List<AccountModifications> get() = _updates

    override val configuration: AccountServiceConfiguration
        get() = error("FakeAccountService has no configuration.")

    /**
     * How often a verification email was requested.
     */
    var verificationEmails = 0
        private set

    /**
     * How often the account was reloaded.
     */
    var refreshes = 0
        private set

    private var sendEmailVerificationResult: Result<Unit> = Result.success(Unit)
    private var onRefresh: () -> Unit = {}

    /**
     * Makes every following verification email request return [result].
     */
    fun setSendEmailVerificationResult(result: Result<Unit>) {
        sendEmailVerificationResult = result
    }

    /**
     * Runs [action] on every following reload of the account, such as supplying the reloaded
     * details.
     */
    fun setOnRefresh(action: () -> Unit) {
        onRefresh = action
    }

    /**
     * Sets what [updateAccountDetails] returns.
     */
    fun setUpdateResult(result: Result<Unit>) {
        updateResult = result
    }

    override suspend fun updateAccountDetails(modifications: AccountModifications): Result<Unit> {
        _updates.add(modifications)
        return updateResult
    }

    override suspend fun signUp(signupDetails: AccountDetails): Result<Unit> = Result.success(Unit)

    override suspend fun logout(): Result<Unit> = Result.success(Unit)

    override suspend fun delete(): Result<Unit> = Result.success(Unit)

    override suspend fun login(credential: UserIdPasswordCredential): Result<Unit> = Result.success(Unit)

    override suspend fun resetPassword(userId: String): Result<Unit> = Result.success(Unit)

    override suspend fun sendEmailVerification(): Result<Unit> {
        verificationEmails++
        return sendEmailVerificationResult
    }

    override suspend fun refresh(): Result<Unit> {
        refreshes++
        onRefresh()
        return Result.success(Unit)
    }

    override suspend fun signIn(provider: AuthProvider): Result<Unit> = Result.success(Unit)
}
