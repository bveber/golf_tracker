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
    @ColumnInfo(name = "total_holes")
    val totalHoles: Int = 18, // 9 or 18
    @ColumnInfo(name = "start_hole")
    val startHole: Int = 1,
    @ColumnInfo(name = "is_practice")
    val isPractice: Boolean = false,
    @ColumnInfo(name = "weather_condition")
    val weatherCondition: String? = null,
    @ColumnInfo(name = "temperature_fahrenheit")
    val temperatureFahrenheit: Int? = null,
    @ColumnInfo(name = "wind_speed_mph")
    val windSpeedMph: Int? = null,
    @ColumnInfo(name = "wind_direction")
    val windDirection: String? = null,
    @ColumnInfo(name = "humidity_percent")
    val humidityPercent: Int? = null,
    @ColumnInfo(name = "pressure_in_hg")
    val pressureInHg: Double? = null
)
