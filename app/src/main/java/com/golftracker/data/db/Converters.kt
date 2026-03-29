package com.golftracker.data.db

import androidx.room.TypeConverter
import com.golftracker.data.entity.DirectionMiss
import com.golftracker.data.entity.PaceMiss
import com.golftracker.data.entity.PuttBreak
import com.golftracker.data.entity.PuttSlopeDirection
import com.golftracker.data.model.ApproachLie
import com.golftracker.data.model.PenaltyType
import com.golftracker.data.model.ShotOutcome
import com.golftracker.data.model.LieSlope
import com.golftracker.data.model.LieStance
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

    @TypeConverter
    fun fromLieSlope(value: String?): LieSlope? {
        return value?.let {
            try {
                LieSlope.valueOf(it)
            } catch (e: IllegalArgumentException) {
                null
            }
        }
    }

    @TypeConverter
    fun lieSlopeToString(slope: LieSlope?): String? {
        return slope?.name
    }

    @TypeConverter
    fun fromLieStance(value: String?): LieStance? {
        return value?.let {
            try {
                LieStance.valueOf(it)
            } catch (e: IllegalArgumentException) {
                null
            }
        }
    }

    @TypeConverter
    fun lieStanceToString(stance: LieStance?): String? {
        return stance?.name
    }

    @TypeConverter
    fun toPuttBreak(value: String?): PuttBreak? = value?.let {
        try { PuttBreak.valueOf(it) } catch (e: IllegalArgumentException) { null }
    }
    @TypeConverter
    fun fromPuttBreak(value: PuttBreak?): String? = value?.name

    @TypeConverter
    fun toPuttSlopeDirection(value: String?): PuttSlopeDirection? = value?.let {
        try { PuttSlopeDirection.valueOf(it) } catch (e: IllegalArgumentException) { null }
    }
    @TypeConverter
    fun fromPuttSlopeDirection(value: PuttSlopeDirection?): String? = value?.name

    @TypeConverter
    fun toPaceMiss(value: String?): PaceMiss? = value?.let {
        try { PaceMiss.valueOf(it) } catch (e: IllegalArgumentException) { null }
    }
    @TypeConverter
    fun fromPaceMiss(value: PaceMiss?): String? = value?.name

    @TypeConverter
    fun toDirectionMiss(value: String?): DirectionMiss? = value?.let {
        try { DirectionMiss.valueOf(it) } catch (e: IllegalArgumentException) { null }
    }
    @TypeConverter
    fun fromDirectionMiss(value: DirectionMiss?): String? = value?.name
}
