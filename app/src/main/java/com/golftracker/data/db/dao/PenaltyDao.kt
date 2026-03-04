package com.golftracker.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.golftracker.data.entity.Penalty
import kotlinx.coroutines.flow.Flow

@Dao
interface PenaltyDao {
    @Query("SELECT * FROM penalties WHERE hole_stat_id = :holeStatId")
    fun getPenaltiesForHoleStat(holeStatId: Int): Flow<List<Penalty>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPenalty(penalty: Penalty): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPenalties(penalties: List<Penalty>)

    @Update
    suspend fun updatePenalty(penalty: Penalty)

    @Delete
    suspend fun deletePenalty(penalty: Penalty)
    
    @Query("DELETE FROM penalties WHERE hole_stat_id = :holeStatId")
    suspend fun deletePenaltiesForHoleStat(holeStatId: Int)
}
