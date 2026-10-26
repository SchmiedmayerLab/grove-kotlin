//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.study.internal

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.grovealliance.core.time.FakeTimeProvider
import org.grovealliance.foundation.fixtures.UUIDFixtures
import org.grovealliance.health.CollectionMode
import org.grovealliance.health.FakeHealth
import org.grovealliance.health.RecordType
import org.grovealliance.scheduler.FakeScheduleCalculator
import org.grovealliance.scheduler.FakeScheduler
import org.grovealliance.scheduler.NotificationThread
import org.grovealliance.scheduler.NotificationTime
import org.grovealliance.scheduler.RecurrenceFrequency
import org.grovealliance.scheduler.fixtures.TaskDraftFixtures
import org.grovealliance.study.ScheduledTaskAction
import org.grovealliance.study.ScheduledTaskActionKey
import org.grovealliance.study.StudyContextKey
import org.grovealliance.study.StudyHealthConfiguration
import org.grovealliance.study.StudyHealthDataMapping
import org.grovealliance.study.StudyTaskCategories
import org.grovealliance.study.StudyTaskId
import org.grovealliance.studydefinition.StudyBundle
import org.grovealliance.studydefinition.fixtures.StudyBundleFixtures
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.UUID
import kotlin.time.Duration.Companion.hours

class StudyManagerImplTest {

    private val zone: ZoneId = ZoneId.of("America/Los_Angeles")
    private val now = ZonedDateTime.of(2026, 7, 1, 8, 30, 0, 0, zone).toInstant()
    private val studyId = UUIDFixtures.repeating('1')

    private val scheduler = FakeScheduler()
    private val scheduleCalculator = FakeScheduleCalculator()
    private val dao = FakeStudyEnrollmentDao()
    private val timeProvider = FakeTimeProvider()
    private val compiler = ScheduleCompiler(scheduleCalculator, timeProvider)
    private val health = FakeHealth()
    private val managerScope = TestScope()
    private lateinit var bundleStore: BundleStore

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Before
    fun setup() {
        timeProvider.setNow(instant = now)
        timeProvider.setZone(zone = zone)
        scheduleCalculator.setNow(instant = now)
        scheduleCalculator.setZone(zone = zone)
        bundleStore = BundleStore(tempFolder.newFolder("study-bundles"))
    }

    @Test
    fun `enrolling registers a task per user-interactive schedule`() = runTest {
        // when
        manager().enroll(exampleBundle())

        // then
        assertThat(scheduler.tasks).hasSize(4)
        assertThat(scheduler.tasks.keys).containsExactly(
            StudyTaskId.of(studyId, UUIDFixtures.repeating('3'), UUIDFixtures.repeating('b')),
            StudyTaskId.of(studyId, UUIDFixtures.repeating('4'), UUIDFixtures.repeating('c')),
            StudyTaskId.of(studyId, UUIDFixtures.repeating('2'), UUIDFixtures.repeating('a')),
            StudyTaskId.of(studyId, UUIDFixtures.repeating('5'), UUIDFixtures.repeating('d')),
        )
    }

    @Test
    fun `does not create a task for the health data collection component`() = runTest {
        // when
        manager().enroll(exampleBundle())

        // then
        assertThat(scheduler.tasks.keys.none { it.contains("66666666") }).isTrue()
    }

    @Test
    fun `tasks carry study context and a scheduled action`() = runTest {
        // given
        val mgr = manager()

        // when
        mgr.enroll(exampleBundle())
        val enrollment = mgr.studyEnrollments().single()
        val questionnaireTask = scheduler.tasks.values.first { it.category == StudyTaskCategories.questionnaire }

        // then
        val context = questionnaireTask.context[StudyContextKey]
        assertThat(context).isNotNull()
        assertThat(context!!.studyId).isEqualTo(studyId)
        assertThat(context.enrollmentId).isEqualTo(enrollment.id)
        assertThat(questionnaireTask.context[ScheduledTaskActionKey])
            .isInstanceOf(ScheduledTaskAction.AnswerQuestionnaire::class.java)
        assertThat(questionnaireTask.title).isEqualTo("Daily Check-In")
    }

    @Test
    fun `weekly schedule compiles into a weekly recurrence`() = runTest {
        // when
        manager().enroll(exampleBundle())
        val walkTask = scheduler.tasks.values.first { it.category == StudyTaskCategories.timedWalkingTest }

        // then
        assertThat(walkTask.schedule.recurrence?.frequency).isEqualTo(RecurrenceFrequency.WEEKLY)
    }

    @Test
    fun `enabled notifications compile into the task's notification settings`() = runTest {
        // when
        manager().enroll(exampleBundle())
        val questionnaireTask = scheduler.tasks.values.first { it.category == StudyTaskCategories.questionnaire }
        val walkTask = scheduler.tasks.values.first { it.category == StudyTaskCategories.timedWalkingTest }

        // then
        assertThat(questionnaireTask.scheduleNotifications).isTrue()
        assertThat(questionnaireTask.notificationThread).isEqualTo(NotificationThread.Custom("daily-checkin"))
        assertThat(questionnaireTask.notificationTime).isEqualTo(NotificationTime(hour = 9, minute = 0, second = 0))
        // the walk schedule has notifications disabled
        assertThat(walkTask.scheduleNotifications).isFalse()
    }

    @Test
    fun `activation anchors event tasks to an earlier enrollment date`() = runTest {
        // given
        val enrollmentDate = now.minus(30, ChronoUnit.DAYS)

        // when
        manager().enroll(
            studyBundle = activationBundle(),
            enrollmentDate = enrollmentDate,
        )

        // then
        val task = scheduler.tasks.getValue(
            StudyTaskId.of(studyId, UUIDFixtures.repeating('5'), UUIDFixtures.repeating('d'))
        )
        val expected = enrollmentDate.atZone(zone).plusDays(7).withHour(9).withMinute(0).withSecond(0).withNano(0)
        assertThat(task.schedule.start).isEqualTo(expected.toInstant())
    }

    @Test
    fun `re-enrolling at the same revision is a no-op`() = runTest {
        // given
        val mgr = manager()
        mgr.enroll(exampleBundle())
        val tasksAfterFirst = scheduler.tasks.keys.toSet()

        // when
        mgr.enroll(exampleBundle())

        // then
        assertThat(mgr.studyEnrollments()).hasSize(1)
        assertThat(scheduler.tasks.keys).isEqualTo(tasksAfterFirst)
    }

    @Test
    fun `enrolling at a newer revision forwards to an update`() = runTest {
        // given
        val mgr = manager()
        mgr.enroll(exampleBundle())

        // when
        mgr.enroll(exampleBundle(revision = 2))

        // then
        assertThat(mgr.studyEnrollments()).hasSize(1)
        assertThat(mgr.studyEnrollments().single().studyRevision).isEqualTo(2u)
    }

    @Test
    fun `unenrolling removes tasks and the enrollment`() = runTest {
        // given
        val mgr = manager()
        mgr.enroll(exampleBundle())
        val enrollment = mgr.studyEnrollments().single()

        // when
        mgr.unenroll(enrollment)

        // then
        assertThat(mgr.studyEnrollments()).isEmpty()
        assertThat(scheduler.tasks).isEmpty()
    }

    @Test
    fun `removeOrphanedTasks deletes study tasks without a matching enrollment`() = runTest {
        // given
        val mgr = manager()
        scheduler.createOrUpdateTask(
            draft = TaskDraftFixtures.create(
                id = StudyTaskId.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()),
                title = "orphan",
                schedule = org.grovealliance.scheduler.Schedule.once(at = now),
            ),
            effectiveFrom = now,
        )

        // when
        mgr.removeOrphanedTasks()

        // then
        assertThat(scheduler.tasks).isEmpty()
    }

    @Test
    fun `updating the preferred locale retitles the enrolled study's tasks`() = runTest {
        // given
        val manager = manager()
        manager.enroll(britishEnglishBundle())

        // when
        manager.updatePreferredLocale(locale = Locale.forLanguageTag("en-GB"))
        managerScope.testScheduler.advanceUntilIdle()

        // then
        assertThat(scheduler.tasks.values.map { it.title })
            .contains("Welcome to the Example Study, UK edition")
    }

    @Test
    fun `tasks enrolled after updating the preferred locale use it`() = runTest {
        // given
        val manager = manager()
        manager.updatePreferredLocale(locale = Locale.forLanguageTag("en-GB"))

        // when
        manager.enroll(britishEnglishBundle())

        // then
        assertThat(scheduler.tasks.values.map { it.title })
            .contains("Welcome to the Example Study, UK edition")
    }

    @Test
    fun `enrolling collects the required health data sample types`() = runTest {
        // when
        manager().enroll(exampleBundle())

        // then
        assertThat(health.collectedRecordTypes).containsExactly(RecordType.heartRate, RecordType.steps)
        assertThat(health.dataAccessRequirements.read).containsExactly(RecordType.heartRate, RecordType.steps)
    }

    @Test
    fun `enrolling collects optional sample types only once reading them is permitted`() = runTest {
        // given
        health.setAuthorizedToRead(types = setOf(RecordType.activeCaloriesBurned))

        // when
        manager().enroll(exampleBundle())

        // then
        assertThat(health.collectedRecordTypes)
            .containsExactly(RecordType.heartRate, RecordType.steps, RecordType.activeCaloriesBurned)
    }

    @Test
    fun `informing about a study requests read access without collecting`() = runTest {
        // when
        manager().informAboutStudies(listOf(exampleBundle()))

        // then
        assertThat(health.dataAccessRequirements.read).containsExactly(RecordType.heartRate, RecordType.steps)
        assertThat(health.collectedRecordTypes).isEmpty()
    }

    @Test
    fun `unenrolling stops collecting the study's sample types`() = runTest {
        // given
        val manager = manager()
        manager.enroll(exampleBundle())
        val enrollment = manager.studyEnrollments().single()

        // when
        manager.unenroll(enrollment)

        // then
        assertThat(health.collectedRecordTypes).isEmpty()
    }

    @Test
    fun `enrolling collects at the configured polling interval`() = runTest {
        // given
        val configuration = StudyHealthConfiguration(
            mapping = StudyHealthDataMapping.Default,
            pollingInterval = 1.hours,
        )

        // when
        manager(healthConfiguration = configuration).enroll(exampleBundle())

        // then
        assertThat(health.collectionModes.values.toSet())
            .containsExactly(CollectionMode.Automatic(pollingInterval = 1.hours))
    }

    @Test
    fun `enrolling works without a health configuration`() = runTest {
        // when
        val manager = manager(healthConfiguration = null)
        manager.enroll(exampleBundle())

        // then
        assertThat(manager.studyEnrollments()).hasSize(1)
        assertThat(health.configurationUpdateCount).isEqualTo(0)
    }

    private fun manager(
        healthConfiguration: StudyHealthConfiguration? = StudyHealthConfiguration.DEFAULT,
    ) = StudyManagerImpl(
        enrollmentDao = dao,
        scheduler = scheduler,
        scheduleCompiler = compiler,
        timeProvider = timeProvider,
        bundleStore = bundleStore,
        mapper = StudyEnrollmentMapper(),
        scope = managerScope,
        healthCollector = healthConfiguration?.let {
            StudyHealthCollector(
                health = health,
                configuration = it,
            )
        },
        environment = StudyEnvironment(preferredLocale = Locale.forLanguageTag("en-US")),
    )

    /**
     * The example bundle with its lifecycle events switched to activation, which unlike enrollment
     * is fired even when enrolling with an earlier date.
     */
    private fun activationBundle(): StudyBundle {
        val copy = tempFolder.newFolder()
            .resolve("example.studybundle")
            .also { StudyBundleFixtures.exampleBundleDir().copyRecursively(it, overwrite = true) }
        val definition = File(copy, "definition.json")
        definition.writeText(definition.readText().replace("\"enrollment\": {}", "\"activation\": {}"))
        return StudyBundle.open(copy)
    }

    /**
     * The example bundle with a British English localization of its welcome article.
     */
    private fun britishEnglishBundle(): StudyBundle {
        val copy = tempFolder.newFolder()
            .resolve("example.studybundle")
            .also { StudyBundleFixtures.exampleBundleDir().copyRecursively(it, overwrite = true) }
        val article = File(copy, "article/welcome+en-US.md").readText()
        File(copy, "article/welcome+en-GB.md").writeText(
            article.replace("title: Welcome to the Example Study", "title: Welcome to the Example Study, UK edition"),
        )
        return StudyBundle.open(copy)
    }

    private fun exampleBundle(revision: Int? = null): StudyBundle {
        val source = StudyBundleFixtures.exampleBundleDir()
        if (revision == null) return StudyBundle.open(source)
        val copy = tempFolder.newFolder()
            .resolve("example.studybundle").also { source.copyRecursively(it, overwrite = true) }
        val definition = File(copy, "definition.json")
        definition.writeText(definition.readText().replace("\"studyRevision\": 1", "\"studyRevision\": $revision"))
        return StudyBundle.open(copy)
    }
}
