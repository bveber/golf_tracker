package com.golftracker.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import com.golftracker.data.model.ApproachLie
import com.golftracker.data.model.ShotOutcome

@Entity(
    tableName = "shots",
    foreignKeys = [
        ForeignKey(
            entity = HoleStat::class,
            parentColumns = ["id"],
            childColumns = ["hole_stat_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Club::class,
            parentColumns = ["id"],
            childColumns = ["club_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ]
)
data class Shot(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    @ColumnInfo(name = "hole_stat_id", index = true)
    val holeStatId: Int,
    @ColumnInfo(name = "shot_number")
    val shotNumber: Int,
    @ColumnInfo(name = "club_id", index = true)
    val clubId: Int? = null,
    
    val outcome: ShotOutcome? = null,
    val lie: ApproachLie? = null,
    val slope: com.golftracker.data.model.LieSlope? = null,
    val stance: com.golftracker.data.model.LieStance? = null,
    
    @ColumnInfo(name = "dispersion_left")
    val dispersionLeft: Int? = null,
    @ColumnInfo(name = "dispersion_right")
    val dispersionRight: Int? = null,
    @ColumnInfo(name = "dispersion_short")
    val dispersionShort: Int? = null,
    @ColumnInfo(name = "dispersion_long")
    val dispersionLong: Int? = null,
    
    /**
     * Starting latitude coordinate of the shot.
     */
    @ColumnInfo(name = "start_lat")
    val startLat: Double? = null,
    /**
     * Starting longitude coordinate of the shot.
     */
    @ColumnInfo(name = "start_lng")
    val startLng: Double? = null,
    /**
     * Ending latitude coordinate of the shot (where the ball came to rest).
     */
    @ColumnInfo(name = "end_lat")
    val endLat: Double? = null,
    /**
     * Ending longitude coordinate of the shot (where the ball came to rest).
     */
    @ColumnInfo(name = "end_lng")
    val endLng: Double? = null,
    
    /**
     * Intended target latitude coordinate.
     */
    @ColumnInfo(name = "target_lat")
    val targetLat: Double? = null,
    /**
     * Intended target longitude coordinate.
     */
    @ColumnInfo(name = "target_lng")
    val targetLng: Double? = null,
    
    @ColumnInfo(name = "distance_to_pin")
    val distanceToPin: Int? = null,
    
    @ColumnInfo(name = "distance_traveled")
    val distanceTraveled: Int? = null,
    
    /**
     * Indicates if the shot was a recovery shot (e.g., played from trouble with the primary goal of advancing the ball rather than hitting the green).
     */
    @ColumnInfo(name = "is_recovery")
    val isRecovery: Boolean = false,
    
    @ColumnInfo(name = "strokes_gained")
    val strokesGained: Double? = null,

    @ColumnInfo(name = "penalty_attribution", defaultValue = "0.0")
    val penaltyAttribution: Double = 0.0
)
