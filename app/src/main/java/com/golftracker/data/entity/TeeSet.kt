package com.golftracker.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "tee_sets",
    foreignKeys = [
        ForeignKey(
            entity = Course::class,
            parentColumns = ["id"],
            childColumns = ["course_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class TeeSet(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    @ColumnInfo(name = "course_id", index = true)
    val courseId: Int,
    val name: String,
    val slope: Int,
    val rating: Double
)
