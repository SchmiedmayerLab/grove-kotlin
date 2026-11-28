//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.fhir.contract.internal

import com.google.common.truth.Truth.assertThat
import org.grovealliance.fhir.contract.ApplicationDevice
import org.grovealliance.fhir.contract.GroveIdentifierRole
import org.grovealliance.fhir.contract.HostDevice
import org.grovealliance.fhir.contract.InternalGroveFhirApi
import org.grovealliance.fhir.contract.Profile
import org.grovealliance.fhir.contract.RecordingDevice
import org.junit.Test

@OptIn(InternalGroveFhirApi::class)
class DeviceSnapshotsTest {
    private val snapshots = DeviceSnapshots(
        identityScope = ExchangeTestScope.identityScope,
        subject = ExchangeTestScope.subject,
        adapterId = "health-connect",
        applicationDeviceProfile = Profile.Mobile.GROVE_APPLICATION_DEVICE,
    )
    private val host = HostDevice.create(operatingSystemVersion = "16", productType = "Pixel 9").getOrThrow()
    private val application = ApplicationDevice.create(
        name = "My Heart Counts",
        packageName = "edu.stanford.myheartcounts",
        version = "1.0",
        build = "42",
    ).getOrThrow()

    @Test
    fun `keys a snapshot by its content and states the key first`() {
        // when
        val device = snapshots.host(host = host).getOrThrow()

        // then
        assertThat(device.identity.role).isEqualTo(GroveIdentifierRole.DEVICE_SNAPSHOT)
        assertThat(device.resource.identifier.first()).isEqualTo(device.identity.fhirIdentifier)
        assertThat(device.url).isEqualTo(device.identity.fullUrl)
        assertThat(snapshots.host(host = host).getOrThrow().identity).isEqualTo(device.identity)
    }

    @Test
    fun `gives the application a new snapshot when its host changes`() {
        // given
        val otherHost = HostDevice.create(operatingSystemVersion = "17", productType = "Pixel 9").getOrThrow()

        // when
        val onHost = snapshots.application(application = application, parentUrl = snapshots.host(host = host).getOrThrow().url).getOrThrow()
        val onOtherHost = snapshots.application(
            application = application,
            parentUrl = snapshots.host(host = otherHost).getOrThrow().url,
        ).getOrThrow()

        // then
        assertThat(onHost.identity).isNotEqualTo(onOtherHost.identity)
    }

    @Test
    fun `adds a unit identity to a recording device only with a stable unit token`() {
        // given
        val device = RecordingDevice(manufacturer = "Google", model = "Pixel Watch 3")

        // when
        val withoutToken = snapshots.recording(device = device).getOrThrow()
        val tokened = device.copy(stableUnitToken = "unit-1", tokenScope = "com.google.android.apps.fitness")
        val withToken = snapshots.recording(device = tokened).getOrThrow()

        // then
        assertThat(withoutToken.resource.identifier).hasSize(1)
        assertThat(withToken.resource.identifier.map { it.type?.coding?.first()?.code })
            .containsExactly("device-snapshot", "recording-device")
            .inOrder()
        assertThat(withToken.resource.modelNumber).isEqualTo("Pixel Watch 3")
    }
}
