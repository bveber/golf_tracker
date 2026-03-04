package com.golftracker.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.golftracker.data.entity.Shot
import kotlinx.coroutines.flow.Flow

@Dao
interface ShotDao {
    @Query("SELECT * FROM shots WHERE hole_stat_id = :holeStatId ORDER BY shot_number ASC")
    fun getShotsForHoleStat(holeStatId: Int): Flow<List<Shot>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShot(shot: Shot): Long

    @Update
    suspend fun updateShot(shot: Shot)

    @Delete
    suspend fun deleteShot(shot: Shot)

    @Query("DELETE FROM shots WHERE hole_stat_id = :holeStatId")
    suspend fun deleteShotsForHoleStat(holeStatId: Int)
}
