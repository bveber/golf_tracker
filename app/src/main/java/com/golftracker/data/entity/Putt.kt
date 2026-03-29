package com.golftracker.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

enum class PuttBreak { BIG_LEFT, SMALL_LEFT, STRAIGHT, SMALL_RIGHT, BIG_RIGHT }
enum class PuttSlopeDirection { STEEP_UPHILL, UPHILL, FLAT, DOWNHILL, STEEP_DOWNHILL }
enum class PaceMiss { BIG_SHORT, SHORT, GOOD, LONG, BIG_LONG }
enum class DirectionMiss { BIG_LEFT, LEFT, STRAIGHT, RIGHT, BIG_RIGHT }

// Stats layer only — not stored in the database
enum class SlideMiss { HIGH, LOW }

@Entity(
    tableName = "putts",
    foreignKeys = [
        ForeignKey(
            entity = HoleStat::class,
            parentColumns = ["id"],
            childColumns = ["hole_stat_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class Putt(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    @ColumnInfo(name = "hole_stat_id", index = true)
    val holeStatId: Int,
    @ColumnInfo(name = "putt_number")
    val puttNumber: Int,
    val distance: Float? = null, // in feet
    val made: Boolean = false,

    @ColumnInfo(name = "strokes_gained")
    val strokesGained: Double? = null,

    // Advanced fields — null means not recorded.
    // breakDirection and slopeDirection apply to all putts (including made).
    // paceMiss and directionMiss apply to missed putts only.
    @ColumnInfo(name = "break_direction")
    val breakDirection: PuttBreak? = null,
    @ColumnInfo(name = "slope_direction")
    val slopeDirection: PuttSlopeDirection? = null,
    @ColumnInfo(name = "pace_miss")
    val paceMiss: PaceMiss? = null,
    @ColumnInfo(name = "direction_miss")
    val directionMiss: DirectionMiss? = null,
)
