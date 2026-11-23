//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.health.fhir.internal

import androidx.health.connect.client.records.ExerciseSegment
import androidx.health.connect.client.records.ExerciseSessionRecord
import org.grovealliance.fhir.contract.WorkoutActivity
import org.grovealliance.fhir.contract.WorkoutSegmentType

/**
 * The shared workout vocabulary's reading of every Health Connect exercise and segment type: a type
 * that is a kind of one shared activity reports as that activity, and every other type as `other`.
 * A segment type that names no activity reports as its shared structural segment type.
 */
internal class ExerciseProjection {

    /**
     * The shared activity of the exercise type [type].
     */
    fun mapActivity(type: Int): WorkoutActivity = ACTIVITIES[type] ?: WorkoutActivity.OTHER

    /**
     * The shared code of the segment type [type]: an activity, or a structural segment type when [isStructural].
     */
    fun mapSegment(type: Int): Segment = STRUCTURAL_SEGMENTS[type]?.let { Segment(code = it.code, isStructural = true) }
        ?: Segment(code = (SEGMENT_ACTIVITIES[type] ?: WorkoutActivity.OTHER).code, isStructural = false)

    /**
     * A segment's shared [code], from the structural segment vocabulary when [isStructural], else from the activity vocabulary.
     */
    data class Segment(
        val code: String,
        val isStructural: Boolean,
    )

    private companion object {
        val ACTIVITIES = mapOf(
            ExerciseSessionRecord.EXERCISE_TYPE_BADMINTON to WorkoutActivity.BADMINTON,
            ExerciseSessionRecord.EXERCISE_TYPE_BASEBALL to WorkoutActivity.BASEBALL,
            ExerciseSessionRecord.EXERCISE_TYPE_BASKETBALL to WorkoutActivity.BASKETBALL,
            ExerciseSessionRecord.EXERCISE_TYPE_BIKING to WorkoutActivity.CYCLING,
            ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY to WorkoutActivity.CYCLING,
            ExerciseSessionRecord.EXERCISE_TYPE_BOXING to WorkoutActivity.BOXING,
            ExerciseSessionRecord.EXERCISE_TYPE_CALISTHENICS to WorkoutActivity.STRENGTH_TRAINING,
            ExerciseSessionRecord.EXERCISE_TYPE_DANCING to WorkoutActivity.DANCING,
            ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL to WorkoutActivity.ELLIPTICAL,
            ExerciseSessionRecord.EXERCISE_TYPE_FOOTBALL_AMERICAN to WorkoutActivity.AMERICAN_FOOTBALL,
            ExerciseSessionRecord.EXERCISE_TYPE_GOLF to WorkoutActivity.GOLF,
            ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING to WorkoutActivity.HIGH_INTENSITY_INTERVAL_TRAINING,
            ExerciseSessionRecord.EXERCISE_TYPE_HIKING to WorkoutActivity.HIKING,
            ExerciseSessionRecord.EXERCISE_TYPE_MARTIAL_ARTS to WorkoutActivity.MARTIAL_ARTS,
            ExerciseSessionRecord.EXERCISE_TYPE_PILATES to WorkoutActivity.PILATES,
            ExerciseSessionRecord.EXERCISE_TYPE_ROWING to WorkoutActivity.ROWING,
            ExerciseSessionRecord.EXERCISE_TYPE_ROWING_MACHINE to WorkoutActivity.ROWING,
            ExerciseSessionRecord.EXERCISE_TYPE_RUNNING to WorkoutActivity.RUNNING,
            ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL to WorkoutActivity.RUNNING,
            ExerciseSessionRecord.EXERCISE_TYPE_SKIING to WorkoutActivity.SKIING,
            ExerciseSessionRecord.EXERCISE_TYPE_SNOWBOARDING to WorkoutActivity.SNOWBOARDING,
            ExerciseSessionRecord.EXERCISE_TYPE_SOCCER to WorkoutActivity.SOCCER,
            ExerciseSessionRecord.EXERCISE_TYPE_SQUASH to WorkoutActivity.SQUASH,
            ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING to WorkoutActivity.STAIR_CLIMBING,
            ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING_MACHINE to WorkoutActivity.STAIR_CLIMBING,
            ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING to WorkoutActivity.STRENGTH_TRAINING,
            ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER to WorkoutActivity.SWIMMING,
            ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL to WorkoutActivity.SWIMMING,
            ExerciseSessionRecord.EXERCISE_TYPE_TABLE_TENNIS to WorkoutActivity.TABLE_TENNIS,
            ExerciseSessionRecord.EXERCISE_TYPE_TENNIS to WorkoutActivity.TENNIS,
            ExerciseSessionRecord.EXERCISE_TYPE_VOLLEYBALL to WorkoutActivity.VOLLEYBALL,
            ExerciseSessionRecord.EXERCISE_TYPE_WALKING to WorkoutActivity.WALKING,
            ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING to WorkoutActivity.STRENGTH_TRAINING,
            ExerciseSessionRecord.EXERCISE_TYPE_YOGA to WorkoutActivity.YOGA,
        )
        val SEGMENT_ACTIVITIES = mapOf(
            ExerciseSegment.EXERCISE_SEGMENT_TYPE_BIKING to WorkoutActivity.CYCLING,
            ExerciseSegment.EXERCISE_SEGMENT_TYPE_BIKING_STATIONARY to WorkoutActivity.CYCLING,
            ExerciseSegment.EXERCISE_SEGMENT_TYPE_ELLIPTICAL to WorkoutActivity.ELLIPTICAL,
            ExerciseSegment.EXERCISE_SEGMENT_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING to WorkoutActivity.HIGH_INTENSITY_INTERVAL_TRAINING,
            ExerciseSegment.EXERCISE_SEGMENT_TYPE_PILATES to WorkoutActivity.PILATES,
            ExerciseSegment.EXERCISE_SEGMENT_TYPE_ROWING_MACHINE to WorkoutActivity.ROWING,
            ExerciseSegment.EXERCISE_SEGMENT_TYPE_RUNNING to WorkoutActivity.RUNNING,
            ExerciseSegment.EXERCISE_SEGMENT_TYPE_RUNNING_TREADMILL to WorkoutActivity.RUNNING,
            ExerciseSegment.EXERCISE_SEGMENT_TYPE_STAIR_CLIMBING to WorkoutActivity.STAIR_CLIMBING,
            ExerciseSegment.EXERCISE_SEGMENT_TYPE_STAIR_CLIMBING_MACHINE to WorkoutActivity.STAIR_CLIMBING,
            ExerciseSegment.EXERCISE_SEGMENT_TYPE_WALKING to WorkoutActivity.WALKING,
            ExerciseSegment.EXERCISE_SEGMENT_TYPE_YOGA to WorkoutActivity.YOGA,
            ExerciseSegment.EXERCISE_SEGMENT_TYPE_SWIMMING_BACKSTROKE to WorkoutActivity.SWIMMING,
            ExerciseSegment.EXERCISE_SEGMENT_TYPE_SWIMMING_BREASTSTROKE to WorkoutActivity.SWIMMING,
            ExerciseSegment.EXERCISE_SEGMENT_TYPE_SWIMMING_BUTTERFLY to WorkoutActivity.SWIMMING,
            ExerciseSegment.EXERCISE_SEGMENT_TYPE_SWIMMING_FREESTYLE to WorkoutActivity.SWIMMING,
            ExerciseSegment.EXERCISE_SEGMENT_TYPE_SWIMMING_MIXED to WorkoutActivity.SWIMMING,
            ExerciseSegment.EXERCISE_SEGMENT_TYPE_SWIMMING_OPEN_WATER to WorkoutActivity.SWIMMING,
            ExerciseSegment.EXERCISE_SEGMENT_TYPE_SWIMMING_OTHER to WorkoutActivity.SWIMMING,
            ExerciseSegment.EXERCISE_SEGMENT_TYPE_SWIMMING_POOL to WorkoutActivity.SWIMMING,
            ExerciseSegment.EXERCISE_SEGMENT_TYPE_ARM_CURL to WorkoutActivity.STRENGTH_TRAINING,
            ExerciseSegment.EXERCISE_SEGMENT_TYPE_BACK_EXTENSION to WorkoutActivity.STRENGTH_TRAINING,
            ExerciseSegment.EXERCISE_SEGMENT_TYPE_BALL_SLAM to WorkoutActivity.STRENGTH_TRAINING,
            ExerciseSegment.EXERCISE_SEGMENT_TYPE_BARBELL_SHOULDER_PRESS to WorkoutActivity.STRENGTH_TRAINING,
            ExerciseSegment.EXERCISE_SEGMENT_TYPE_BENCH_PRESS to WorkoutActivity.STRENGTH_TRAINING,
            ExerciseSegment.EXERCISE_SEGMENT_TYPE_BENCH_SIT_UP to WorkoutActivity.STRENGTH_TRAINING,
            ExerciseSegment.EXERCISE_SEGMENT_TYPE_BURPEE to WorkoutActivity.STRENGTH_TRAINING,
            ExerciseSegment.EXERCISE_SEGMENT_TYPE_CRUNCH to WorkoutActivity.STRENGTH_TRAINING,
            ExerciseSegment.EXERCISE_SEGMENT_TYPE_DEADLIFT to WorkoutActivity.STRENGTH_TRAINING,
            ExerciseSegment.EXERCISE_SEGMENT_TYPE_DOUBLE_ARM_TRICEPS_EXTENSION to WorkoutActivity.STRENGTH_TRAINING,
            ExerciseSegment.EXERCISE_SEGMENT_TYPE_DUMBBELL_CURL_LEFT_ARM to WorkoutActivity.STRENGTH_TRAINING,
            ExerciseSegment.EXERCISE_SEGMENT_TYPE_DUMBBELL_CURL_RIGHT_ARM to WorkoutActivity.STRENGTH_TRAINING,
            ExerciseSegment.EXERCISE_SEGMENT_TYPE_DUMBBELL_FRONT_RAISE to WorkoutActivity.STRENGTH_TRAINING,
            ExerciseSegment.EXERCISE_SEGMENT_TYPE_DUMBBELL_LATERAL_RAISE to WorkoutActivity.STRENGTH_TRAINING,
            ExerciseSegment.EXERCISE_SEGMENT_TYPE_DUMBBELL_ROW to WorkoutActivity.STRENGTH_TRAINING,
            ExerciseSegment.EXERCISE_SEGMENT_TYPE_DUMBBELL_TRICEPS_EXTENSION_LEFT_ARM to WorkoutActivity.STRENGTH_TRAINING,
            ExerciseSegment.EXERCISE_SEGMENT_TYPE_DUMBBELL_TRICEPS_EXTENSION_RIGHT_ARM to WorkoutActivity.STRENGTH_TRAINING,
            ExerciseSegment.EXERCISE_SEGMENT_TYPE_DUMBBELL_TRICEPS_EXTENSION_TWO_ARM to WorkoutActivity.STRENGTH_TRAINING,
            ExerciseSegment.EXERCISE_SEGMENT_TYPE_FORWARD_TWIST to WorkoutActivity.STRENGTH_TRAINING,
            ExerciseSegment.EXERCISE_SEGMENT_TYPE_FRONT_RAISE to WorkoutActivity.STRENGTH_TRAINING,
            ExerciseSegment.EXERCISE_SEGMENT_TYPE_HIP_THRUST to WorkoutActivity.STRENGTH_TRAINING,
            ExerciseSegment.EXERCISE_SEGMENT_TYPE_JUMPING_JACK to WorkoutActivity.STRENGTH_TRAINING,
            ExerciseSegment.EXERCISE_SEGMENT_TYPE_KETTLEBELL_SWING to WorkoutActivity.STRENGTH_TRAINING,
            ExerciseSegment.EXERCISE_SEGMENT_TYPE_LATERAL_RAISE to WorkoutActivity.STRENGTH_TRAINING,
            ExerciseSegment.EXERCISE_SEGMENT_TYPE_LAT_PULL_DOWN to WorkoutActivity.STRENGTH_TRAINING,
            ExerciseSegment.EXERCISE_SEGMENT_TYPE_LEG_CURL to WorkoutActivity.STRENGTH_TRAINING,
            ExerciseSegment.EXERCISE_SEGMENT_TYPE_LEG_EXTENSION to WorkoutActivity.STRENGTH_TRAINING,
            ExerciseSegment.EXERCISE_SEGMENT_TYPE_LEG_PRESS to WorkoutActivity.STRENGTH_TRAINING,
            ExerciseSegment.EXERCISE_SEGMENT_TYPE_LEG_RAISE to WorkoutActivity.STRENGTH_TRAINING,
            ExerciseSegment.EXERCISE_SEGMENT_TYPE_LUNGE to WorkoutActivity.STRENGTH_TRAINING,
            ExerciseSegment.EXERCISE_SEGMENT_TYPE_MOUNTAIN_CLIMBER to WorkoutActivity.STRENGTH_TRAINING,
            ExerciseSegment.EXERCISE_SEGMENT_TYPE_PLANK to WorkoutActivity.STRENGTH_TRAINING,
            ExerciseSegment.EXERCISE_SEGMENT_TYPE_PULL_UP to WorkoutActivity.STRENGTH_TRAINING,
            ExerciseSegment.EXERCISE_SEGMENT_TYPE_SHOULDER_PRESS to WorkoutActivity.STRENGTH_TRAINING,
            ExerciseSegment.EXERCISE_SEGMENT_TYPE_SINGLE_ARM_TRICEPS_EXTENSION to WorkoutActivity.STRENGTH_TRAINING,
            ExerciseSegment.EXERCISE_SEGMENT_TYPE_SIT_UP to WorkoutActivity.STRENGTH_TRAINING,
            ExerciseSegment.EXERCISE_SEGMENT_TYPE_SQUAT to WorkoutActivity.STRENGTH_TRAINING,
            ExerciseSegment.EXERCISE_SEGMENT_TYPE_UPPER_TWIST to WorkoutActivity.STRENGTH_TRAINING,
            ExerciseSegment.EXERCISE_SEGMENT_TYPE_WEIGHTLIFTING to WorkoutActivity.STRENGTH_TRAINING,
        )
        val STRUCTURAL_SEGMENTS = mapOf(
            ExerciseSegment.EXERCISE_SEGMENT_TYPE_PAUSE to WorkoutSegmentType.PAUSE,
            ExerciseSegment.EXERCISE_SEGMENT_TYPE_REST to WorkoutSegmentType.REST,
            ExerciseSegment.EXERCISE_SEGMENT_TYPE_UNKNOWN to WorkoutSegmentType.UNKNOWN,
            ExerciseSegment.EXERCISE_SEGMENT_TYPE_OTHER_WORKOUT to WorkoutSegmentType.OTHER_WORKOUT,
        )
    }
}
