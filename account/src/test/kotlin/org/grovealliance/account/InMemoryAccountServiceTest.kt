//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.account

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import org.grovealliance.core.requireDependency
import org.grovealliance.testing.core.testGroveApplication
import org.grovealliance.ui.StringResource
import org.grovealliance.ui.account.DataDisplayComposable
import org.grovealliance.ui.account.DataEntryComposable
import org.junit.Before
import org.junit.Test
import kotlin.reflect.KClass

class InMemoryAccountServiceTest {

    private val storageProvider = FakeAccountStorageProvider()
    private val service = InMemoryAccountService()

    @Before
    fun setup() {
        testGroveApplication {
            accountConfiguration(
                service = { service },
                storageProvider = storageProvider,
                initialDetails = AccountDetails().apply { setAny(AccountKeys.accountId::class, ACCOUNT_ID) },
            ) {
                manual(key = NoteKey)
            }
        }
    }

    @Test
    fun `it should apply an externally stored modification`() = runTest {
        // when
        val result = service.updateAccountDetails(modifications = noteModification())

        // then
        assertThat(result.isSuccess).isTrue()
        assertThat(requireDependency<Account>().details.value?.get(NoteKey::class)).isEqualTo(NOTE)
    }

    @Test
    fun `it should fail when the storage provider cannot store a modification`() = runTest {
        // given
        val failure = IllegalStateException("offline")
        storageProvider.setStoreResult(result = Result.failure(failure))

        // when
        val result = service.updateAccountDetails(modifications = noteModification())

        // then
        assertThat(result.exceptionOrNull()).isSameInstanceAs(failure)
    }

    @Test
    fun `it should keep the account details when the storage provider cannot store a modification`() = runTest {
        // given
        storageProvider.setStoreResult(result = Result.failure(IllegalStateException("offline")))

        // when
        service.updateAccountDetails(modifications = noteModification())

        // then
        assertThat(requireDependency<Account>().details.value?.get(NoteKey::class)).isNull()
    }

    private fun noteModification() = AccountModifications(
        modifiedDetails = AccountDetails().apply { setAny(NoteKey::class, NOTE) },
    ).getOrThrow()

    data object NoteKey : AccountKey<String> {
        override val identifier: String = "note"
        override val name: StringResource = StringResource("Note")
        override val serializer: KSerializer<String> = String.serializer()
        override val initialValue: InitialValue<String> = InitialValue.string
        override val display: DataDisplayComposable<String>? = null
        override val entry: DataEntryComposable<String>? = null
        override val valueType: KClass<String> = String::class
    }

    private class FakeAccountStorageProvider : AccountStorageProvider {
        private var storeResult: Result<Unit> = Result.success(Unit)

        fun setStoreResult(result: Result<Unit>) {
            storeResult = result
        }

        override suspend fun load(accountId: String, keys: Set<AnyAccountKey>): Result<AccountDetails> =
            Result.success(AccountDetails())

        override suspend fun store(accountId: String, modifications: AccountModifications): Result<Unit> = storeResult

        override suspend fun disassociate(accountId: String): Result<Unit> = Result.success(Unit)

        override suspend fun delete(accountId: String): Result<Unit> = Result.success(Unit)
    }

    private companion object {
        const val ACCOUNT_ID = "account-id"
        const val NOTE = "A note"
    }
}
