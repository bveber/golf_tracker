package com.golftracker.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import java.util.Date

@Entity(
    tableName = "rounds",
    foreignKeys = [
        ForeignKey(
            entity = Course::class,
            parentColumns = ["id"],
            childColumns = ["course_id"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = TeeSet::class,
            parentColumns = ["id"],
            childColumns = ["tee_set_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ]
)
data class Round(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    @ColumnInfo(name = "course_id", index = true)
    val courseId: Int,
    @ColumnInfo(name = "tee_set_id", index = true)
    val teeSetId: Int,
    val date: Date = Date(),
    val notes: String = "",
    @ColumnInfo(name = "is_finalized")
    val isFinalized: Boolean = false,
    @ColumnInfo(name = "holes_played")
    val holesPlayed: Int = 18 // 9 or 18
)
