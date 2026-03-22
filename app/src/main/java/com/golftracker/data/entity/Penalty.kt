package com.golftracker.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import com.golftracker.data.model.PenaltyType

@Entity(
    tableName = "penalties",
    foreignKeys = [
        ForeignKey(
            entity = HoleStat::class,
            parentColumns = ["id"],
            childColumns = ["hole_stat_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class Penalty(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    @ColumnInfo(name = "hole_stat_id", index = true)
    val holeStatId: Int,
    val type: PenaltyType,
    val strokes: Int = 1,
    @ColumnInfo(name = "shot_number")
    val shotNumber: Int? = null
)
