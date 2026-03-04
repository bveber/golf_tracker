package com.golftracker.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "courses")
data class Course(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val city: String,
    val state: String,
    val holeCount: Int // 9 or 18
)
