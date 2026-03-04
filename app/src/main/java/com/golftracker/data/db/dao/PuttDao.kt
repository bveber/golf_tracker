package com.golftracker.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.golftracker.data.entity.Putt
import kotlinx.coroutines.flow.Flow

@Dao
interface PuttDao {
    @Query("SELECT * FROM putts WHERE hole_stat_id = :holeStatId ORDER BY putt_number ASC")
    fun getPuttsForHoleStat(holeStatId: Int): Flow<List<Putt>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPutt(putt: Putt): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPutts(putts: List<Putt>)

    @Update
    suspend fun updatePutt(putt: Putt)

    @Delete
    suspend fun deletePutt(putt: Putt)
    
    @Query("DELETE FROM putts WHERE hole_stat_id = :holeStatId")
    suspend fun deletePuttsForHoleStat(holeStatId: Int)
}
