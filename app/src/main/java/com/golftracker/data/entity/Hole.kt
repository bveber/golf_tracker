package com.golftracker.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "holes",
    foreignKeys = [
        ForeignKey(
            entity = Course::class,
            parentColumns = ["id"],
            childColumns = ["course_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class Hole(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    @ColumnInfo(name = "course_id", index = true)
    val courseId: Int,
    @ColumnInfo(name = "hole_number")
    val holeNumber: Int,
    val par: Int,
    @ColumnInfo(name = "handicap_index")
    val handicapIndex: Int? = null
)
