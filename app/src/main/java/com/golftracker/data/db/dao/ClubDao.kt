package com.golftracker.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.golftracker.data.entity.Club
import kotlinx.coroutines.flow.Flow

@Dao
interface ClubDao {
    @Query("SELECT * FROM clubs WHERE isRetired = 0 ORDER BY sortOrder ASC, type ASC, name ASC")
    fun getActiveClubs(): Flow<List<Club>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClub(club: Club): Long

    @Update
    suspend fun updateClub(club: Club)

    @Delete
    suspend fun deleteClub(club: Club)
}
