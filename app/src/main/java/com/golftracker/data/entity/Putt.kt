package com.golftracker.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

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
    val strokesGained: Double? = null
)
