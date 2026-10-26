//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.study.internal

import org.grovealliance.core.logging.groveLogger
import org.grovealliance.health.AnyRecordType
import org.grovealliance.health.CollectionMode
import org.grovealliance.health.Health
import org.grovealliance.study.StudyHealthConfiguration
import org.grovealliance.studydefinition.Component
import org.grovealliance.studydefinition.StudyBundle

/**
 * Configures [health] from the health data collection components of study bundles.
 */
internal class StudyHealthCollector(
    private val health: Health,
    private val configuration: StudyHealthConfiguration,
) {

    private val logger by groveLogger()

    /**
     * Replaces [health]'s configuration: read access to the required sample types of every bundle in
     * [knownBundles] and [enrolledBundles], so that it can be granted before enrolling, and automatic
     * background collection for [enrolledBundles]. Optional sample types of enrolled studies are
     * collected only when reading them is already permitted, and a study no longer enrolled in stops
     * being collected.
     */
    suspend fun update(knownBundles: Collection<StudyBundle>, enrolledBundles: Collection<StudyBundle>) {
        val readTypes = (knownBundles + enrolledBundles).recordTypes { it.sampleTypes }
        val optionalTypes = enrolledBundles.recordTypes { it.optionalSampleTypes }
            .filter { health.isAuthorizedToRead(it) }
        val collectedTypes = enrolledBundles.recordTypes { it.sampleTypes } + optionalTypes
        health.updateConfiguration {
            requestReadAccess(readTypes)
            collectedTypes.forEach { recordType ->
                collectRecord(
                    recordType = recordType,
                    start = CollectionMode.Automatic(pollingInterval = configuration.pollingInterval),
                    continueInBackground = true,
                )
            }
        }
        logger.i {
            "Collecting ${collectedTypes.map { it.identifier }}; " +
                "requesting read access to ${readTypes.map { it.identifier }}"
        }
    }

    private fun Collection<StudyBundle>.recordTypes(
        sampleTypes: (Component.HealthDataCollection) -> List<String>,
    ): Set<AnyRecordType> = flatMap { it.studyDefinition.healthDataCollectionComponents }
        .flatMap(sampleTypes)
        .flatMap { configuration.mapping.recordTypes(it) }
        .toSet()
}
