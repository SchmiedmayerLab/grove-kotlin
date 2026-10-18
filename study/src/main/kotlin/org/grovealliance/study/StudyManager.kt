//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.study

import kotlinx.coroutines.flow.Flow
import org.grovealliance.core.Module
import org.grovealliance.studydefinition.StudyBundle
import java.time.Instant
import java.util.Locale
import java.util.UUID

/**
 * Manages enrollment and participation in studies.
 *
 * Enrolling into a study compiles its component schedules into scheduler tasks; unenrolling removes
 * them. The manager keeps its enrollments across launches and reconciles the derived tasks whenever
 * a study's definition changes.
 *
 * When configured with a [StudyHealthConfiguration], the manager also configures the `Health` module
 * from the studies' health data collection components: read access to the required sample types of
 * every study it was enrolled in or informed about, and background collection for enrolled studies.
 */
interface StudyManager : Module {

    /**
     * A live stream of the current enrollments, re-emitting whenever they change.
     */
    val enrollments: Flow<List<StudyEnrollment>>

    /**
     * A snapshot of the current enrollments.
     */
    suspend fun studyEnrollments(): List<StudyEnrollment>

    /**
     * Enrolls into the study defined by [studyBundle], anchoring relative schedules to
     * [enrollmentDate] (the current time when `null`).
     *
     * Enrolling again into the same study is a no-op at the same revision, forwards to
     * [informAboutStudies] for a newer revision, and throws
     * [StudyEnrollmentException.AlreadyEnrolledInNewerRevision] for an older one. Throws
     * [StudyEnrollmentException.MissingStudyDependency] when the study depends on another the
     * participant is not enrolled in.
     */
    suspend fun enroll(studyBundle: StudyBundle, enrollmentDate: Instant? = null)

    /**
     * Unenrolls from [enrollment], deleting its tasks, stored bundle, and enrollment record.
     */
    suspend fun unenroll(enrollment: StudyEnrollment)

    /**
     * Updates any existing enrollment whose study matches one of [studyBundles] and whose revision
     * is older, re-compiling its tasks against the newer definition.
     */
    suspend fun informAboutStudies(studyBundles: Collection<StudyBundle>)

    /**
     * The enrollment with [id], or `null` if none exists.
     */
    suspend fun enrollment(id: UUID): StudyEnrollment?

    /**
     * Deletes study-domain tasks that no longer correspond to any current enrollment.
     */
    suspend fun removeOrphanedTasks()

    /**
     * Deletes stored study bundles that no longer correspond to any current enrollment.
     */
    suspend fun removeOrphanedStudyBundles()

    /**
     * Resolves localized study content, such as task titles and instructions, in [locale] from now
     * on, replacing the locale the manager was configured with. The tasks of current enrollments are
     * updated in the background, and with them the texts of their scheduled notifications.
     */
    fun updatePreferredLocale(locale: Locale)
}

/**
 * An error preventing enrollment into a study.
 */
sealed class StudyEnrollmentException(message: String) : Exception(message) {
    /**
     * Enrollment was attempted into an older revision than the one already enrolled in.
     */
    class AlreadyEnrolledInNewerRevision :
        StudyEnrollmentException("Already enrolled in a newer revision of this study.")

    /**
     * The study depends on another study the participant is not enrolled in.
     */
    class MissingStudyDependency :
        StudyEnrollmentException("Cannot enroll: a required study dependency is missing.")
}
