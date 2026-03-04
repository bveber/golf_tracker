package com.golftracker.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "clubs")
data class Club(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val type: String, // DRIVER, WOOD, HYBRID, IRON, WEDGE, PUTTER
    val isRetired: Boolean = false,
    val sortOrder: Int = 0,
    val stockDistance: Int? = null // Stock distance in yards
)
