package com.golftracker.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import com.golftracker.data.model.ApproachLie
import com.golftracker.data.model.ShotOutcome

@Entity(
    tableName = "hole_stats",
    foreignKeys = [
        ForeignKey(
            entity = Round::class,
            parentColumns = ["id"],
            childColumns = ["round_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Hole::class,
            parentColumns = ["id"],
            childColumns = ["hole_id"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = Club::class,
            parentColumns = ["id"],
            childColumns = ["tee_club_id"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = Club::class,
            parentColumns = ["id"],
            childColumns = ["approach_club_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ]
)
data class HoleStat(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    @ColumnInfo(name = "round_id", index = true)
    val roundId: Int,
    @ColumnInfo(name = "hole_id", index = true)
    val holeId: Int,
    
    val score: Int = 0,
    
    @ColumnInfo(name = "tee_outcome")
    val teeOutcome: ShotOutcome? = null,
    @ColumnInfo(name = "tee_in_trouble")
    val teeInTrouble: Boolean = false,
    @ColumnInfo(name = "tee_club_id", index = true)
    val teeClubId: Int? = null,
    
    @ColumnInfo(name = "approach_outcome")
    val approachOutcome: ShotOutcome? = null,
    @ColumnInfo(name = "approach_lie")
    val approachLie: ApproachLie? = null,
    @ColumnInfo(name = "approach_club_id", index = true)
    val approachClubId: Int? = null,
    
    @ColumnInfo(name = "gir")
    val gir: Boolean = false,
    /**
     * User manually toggled GIR, overriding the automatic calculation.
     */
    @ColumnInfo(name = "gir_override")
    val girOverride: Boolean = false,
    /**
     * Indicates the approach shot landed within a highly makeable distance but not on the green (e.g., fringe).
     */
    @ColumnInfo(name = "near_gir")
    val nearGir: Boolean = false,
    
    val chips: Int = 0,
    @ColumnInfo(name = "sand_shots")
    val sandShots: Int = 0,
    @ColumnInfo(name = "tee_shot_distance")
    val teeShotDistance: Int? = null,
    @ColumnInfo(name = "approach_shot_distance")
    val approachShotDistance: Int? = null,
    
    @ColumnInfo(name = "chip_distance")
    val chipDistance: Int? = null,
    
    @ColumnInfo(name = "chip_lie")
    val chipLie: ApproachLie? = null,
    
    @ColumnInfo(name = "recovery_chip", defaultValue = "0")
    val recoveryChip: Boolean = false,
    
    val putts: Int = 0,
    // Putts are stored in separate table
    // Penalties are stored in separate table
    
    /**
     * Total Strokes Gained for the hole, compared to a scratch golfer.
     */
    @ColumnInfo(name = "strokes_gained")
    val strokesGained: Double? = null,
    
    /**
     * Strokes Gained: Off-the-Tee. Measures performance of the tee shot on par 4s and par 5s.
     */
    @ColumnInfo(name = "sg_off_tee")
    val sgOffTee: Double? = null,
    /**
     * Strokes Gained: Approach-the-Green. Measures performance of approach shots (typically taken outside 30 yards).
     */
    @ColumnInfo(name = "sg_approach")
    val sgApproach: Double? = null,
    /**
     * Strokes Gained: Around-the-Green. Measures performance of any shot within 30 yards of the edge of the green.
     */
    @ColumnInfo(name = "sg_around_green")
    val sgAroundGreen: Double? = null,
    /**
     * Strokes Gained: Putting. Measures performance of putts on the green.
     */
    @ColumnInfo(name = "sg_putting")
    val sgPutting: Double? = null
)
