package com.golftracker.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "hole_tee_yardages",
    foreignKeys = [
        ForeignKey(
            entity = Hole::class,
            parentColumns = ["id"],
            childColumns = ["hole_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TeeSet::class,
            parentColumns = ["id"],
            childColumns = ["tee_set_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class HoleTeeYardage(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    @ColumnInfo(name = "hole_id", index = true)
    val holeId: Int,
    @ColumnInfo(name = "tee_set_id", index = true)
    val teeSetId: Int,
    val yardage: Int
)
