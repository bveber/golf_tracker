package com.golftracker.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.golftracker.data.entity.Round
import kotlinx.coroutines.flow.Flow

@Dao
interface RoundDao {
    @Query("SELECT * FROM rounds ORDER BY date DESC")
    fun getAllRounds(): Flow<List<Round>>

    @Query("SELECT * FROM rounds WHERE is_finalized = 0 ORDER BY date DESC LIMIT 1")
    fun getActiveRound(): Flow<Round?>

    @Query("SELECT * FROM rounds WHERE is_finalized = 1 ORDER BY date DESC")
    fun getFinalizedRounds(): Flow<List<Round>>

    @Transaction // Required because of @Relation
    @Query("""
        SELECT DISTINCT r.* FROM rounds r
        LEFT JOIN hole_stats hs ON r.id = hs.round_id
        LEFT JOIN shots s ON hs.id = s.hole_stat_id
        LEFT JOIN putts p ON hs.id = p.hole_stat_id
        WHERE r.is_finalized = 1 
        ORDER BY r.date DESC
    """)
    fun getFinalizedRoundsWithDetails(): Flow<List<com.golftracker.data.model.RoundWithDetails>>

    @Transaction
    @Query("SELECT * FROM rounds WHERE id = :roundId")
    suspend fun getRoundWithDetails(roundId: Int): com.golftracker.data.model.RoundWithDetails?

    @Query("SELECT * FROM rounds WHERE id = :roundId")
    suspend fun getRound(roundId: Int): Round?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRound(round: Round): Long

    @Update
    suspend fun updateRound(round: Round)

    @Delete
    suspend fun deleteRound(round: Round)
}
