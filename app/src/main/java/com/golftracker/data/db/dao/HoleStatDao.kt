package com.golftracker.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.golftracker.data.entity.HoleStat
import kotlinx.coroutines.flow.Flow

@Dao
interface HoleStatDao {
    @Query("SELECT * FROM hole_stats WHERE round_id = :roundId ORDER BY hole_id ASC")
    fun getHoleStatsForRound(roundId: Int): Flow<List<HoleStat>>

    @Query("SELECT * FROM hole_stats WHERE round_id = :roundId AND hole_id = :holeId")
    suspend fun getHoleStat(roundId: Int, holeId: Int): HoleStat?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHoleStat(holeStat: HoleStat): Long

    @Update
    suspend fun updateHoleStat(holeStat: HoleStat)

    @Query("SELECT * FROM hole_stats WHERE id = :id")
    fun getHoleStatFlow(id: Int): Flow<HoleStat?>

    @Delete
    suspend fun deleteHoleStat(holeStat: HoleStat)
}
