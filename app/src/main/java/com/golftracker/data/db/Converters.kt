package com.golftracker.data.db

import androidx.room.TypeConverter
import com.golftracker.data.model.ApproachLie
import com.golftracker.data.model.PenaltyType
import com.golftracker.data.model.ShotOutcome
import java.util.Date

class Converters {
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }

    @TypeConverter
    fun fromShotOutcome(value: String?): ShotOutcome? {
        return value?.let {
            try {
                ShotOutcome.valueOf(it)
            } catch (e: IllegalArgumentException) {
                null
            }
        }
    }

    @TypeConverter
    fun shotOutcomeToString(outcome: ShotOutcome?): String? {
        return outcome?.name
    }

    @TypeConverter
    fun fromApproachLie(value: String?): ApproachLie? {
        return value?.let {
            try {
                ApproachLie.valueOf(it)
            } catch (e: IllegalArgumentException) {
                null
            }
        }
    }

    @TypeConverter
    fun approachLieToString(lie: ApproachLie?): String? {
        return lie?.name
    }

    @TypeConverter
    fun fromPenaltyType(value: String?): PenaltyType? {
        return value?.let {
            try {
                PenaltyType.valueOf(it)
            } catch (e: IllegalArgumentException) {
                null
            }
        }
    }

    @TypeConverter
    fun penaltyTypeToString(type: PenaltyType?): String? {
        return type?.name
    }
}
