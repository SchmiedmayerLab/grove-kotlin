//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.health.internal

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.grovealliance.core.lifecycle.AppLifecycle
import org.grovealliance.health.HealthConnectAvailability
import org.grovealliance.health.RecordType
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HealthClientProviderTest {
    private val sdk = FakeHealthConnectSdk()
    private val clientFactory = FakeHealthClientFactory()

    @Test
    fun `it should bind to Health Connect when it is available at creation`() = runTest(UnconfinedTestDispatcher()) {
        // when
        val provider = provider(scope = backgroundScope)

        // then
        assertThat(provider.availability.value).isEqualTo(HealthConnectAvailability.Available)
        assertThat(provider.client.value).isSameInstanceAs(clientFactory.createdClients.single())
    }

    @Test
    fun `it should stay on the no-op client while Health Connect needs an update`() = runTest(UnconfinedTestDispatcher()) {
        // given
        sdk.setAvailability(HealthConnectAvailability.UpdateRequired)

        // when
        val provider = provider(scope = backgroundScope)

        // then
        assertThat(provider.availability.value).isEqualTo(HealthConnectAvailability.UpdateRequired)
        assertThat(provider.client.value).isInstanceOf(NoOpHealthClient::class.java)
        assertThat(sdk.createClientCount).isEqualTo(0)
    }

    @Test
    fun `it should stay on the no-op client when Health Connect is unavailable`() = runTest(UnconfinedTestDispatcher()) {
        // given
        sdk.setAvailability(HealthConnectAvailability.Unavailable)

        // when
        val provider = provider(scope = backgroundScope)

        // then
        assertThat(provider.availability.value).isEqualTo(HealthConnectAvailability.Unavailable)
        assertThat(provider.client.value).isInstanceOf(NoOpHealthClient::class.java)
    }

    @Test
    fun `it should report an update as required when no client can be created`() = runTest(UnconfinedTestDispatcher()) {
        // given
        sdk.setClient(null)

        // when
        val provider = provider(scope = backgroundScope)

        // then
        assertThat(provider.availability.value).isEqualTo(HealthConnectAvailability.UpdateRequired)
        assertThat(provider.client.value).isInstanceOf(NoOpHealthClient::class.java)
    }

    @Test
    fun `it should configure the current client once`() = runTest(UnconfinedTestDispatcher()) {
        // given
        val provider = provider(scope = backgroundScope)

        // when
        provider.configure()
        provider.configure()

        // then
        assertThat(clientFactory.createdClients.single().configureCount).isEqualTo(1)
    }

    @Test
    fun `it should bind and configure a client once Health Connect is installed`() = runTest(UnconfinedTestDispatcher()) {
        // given
        sdk.setAvailability(HealthConnectAvailability.UpdateRequired)
        val provider = provider(scope = backgroundScope)
        provider.configure()
        sdk.setAvailability(HealthConnectAvailability.Available)

        // when
        provider.refreshAvailability()

        // then
        assertThat(provider.availability.value).isEqualTo(HealthConnectAvailability.Available)
        assertThat(provider.client.value).isSameInstanceAs(clientFactory.createdClients.single())
        assertThat(clientFactory.createdClients.single().configureCount).isEqualTo(1)
    }

    @Test
    fun `it should not configure a newly bound client before the provider is configured`() = runTest(UnconfinedTestDispatcher()) {
        // given
        sdk.setAvailability(HealthConnectAvailability.UpdateRequired)
        val provider = provider(scope = backgroundScope)
        sdk.setAvailability(HealthConnectAvailability.Available)

        // when
        provider.refreshAvailability()

        // then
        assertThat(clientFactory.createdClients.single().configureCount).isEqualTo(0)
    }

    @Test
    fun `it should not bind again once bound`() = runTest(UnconfinedTestDispatcher()) {
        // given
        val provider = provider(scope = backgroundScope)

        // when
        provider.refreshAvailability()

        // then
        assertThat(sdk.createClientCount).isEqualTo(1)
        assertThat(clientFactory.createdClients).hasSize(1)
    }

    @Test
    fun `it should report the authorization of the current client`() = runTest(UnconfinedTestDispatcher()) {
        // given
        sdk.setAvailability(HealthConnectAvailability.UpdateRequired)
        val provider = provider(scope = backgroundScope)
        sdk.setAvailability(HealthConnectAvailability.Available)
        provider.refreshAvailability()

        // when
        clientFactory.createdClients.single().setFullyAuthorized(isFullyAuthorized = true)

        // then
        assertThat(provider.isFullyAuthorizedState.value).isTrue()
    }

    @Test
    fun `it should apply the runtime configuration to the current client`() = runTest(UnconfinedTestDispatcher()) {
        // given
        val provider = provider(scope = backgroundScope)
        val components = setOf(RequestReadAccess(RecordType.steps))

        // when
        provider.updateConfiguration(components = components)

        // then
        assertThat(clientFactory.createdClients.single().runtimeComponents).isEqualTo(components)
    }

    @Test
    fun `it should apply the latest runtime configuration to a client bound later`() = runTest(UnconfinedTestDispatcher()) {
        // given
        sdk.setAvailability(HealthConnectAvailability.UpdateRequired)
        val provider = provider(scope = backgroundScope)
        provider.updateConfiguration(components = setOf(RequestReadAccess(RecordType.heartRate)))
        val latest = setOf(RequestReadAccess(RecordType.steps))
        provider.updateConfiguration(components = latest)
        sdk.setAvailability(HealthConnectAvailability.Available)

        // when
        provider.refreshAvailability()

        // then
        assertThat(clientFactory.createdClients.single().runtimeComponents).isEqualTo(latest)
    }

    private fun provider(scope: CoroutineScope) = HealthClientProvider(
        sdk = sdk,
        clientFactory = clientFactory,
        appLifecycle = AppLifecycle(),
        scope = scope,
    )
}
