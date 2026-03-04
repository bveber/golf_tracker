package com.golftracker.data.repository

import com.golftracker.data.db.dao.HoleStatDao
import com.golftracker.data.db.dao.PenaltyDao
import com.golftracker.data.db.dao.PuttDao
import com.golftracker.data.db.dao.RoundDao
import com.golftracker.data.entity.HoleStat
import com.golftracker.data.entity.Penalty
import com.golftracker.data.entity.Putt
import com.golftracker.data.entity.Round
import com.golftracker.data.entity.Shot
import com.golftracker.data.db.dao.ShotDao
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoundRepository @Inject constructor(
    private val roundDao: RoundDao,
    private val holeStatDao: HoleStatDao,
    private val puttDao: PuttDao,
    private val penaltyDao: PenaltyDao,
    private val shotDao: ShotDao
) {
    // Rounds
    val allRounds: Flow<List<Round>> = roundDao.getAllRounds()
    val finalizedRounds: Flow<List<Round>> = roundDao.getFinalizedRounds()
    val finalizedRoundsWithDetails: Flow<List<com.golftracker.data.model.RoundWithDetails>> = roundDao.getFinalizedRoundsWithDetails()
    val activeRound: Flow<Round?> = roundDao.getActiveRound()

    suspend fun getRoundWithDetails(roundId: Int): com.golftracker.data.model.RoundWithDetails? = roundDao.getRoundWithDetails(roundId)


    suspend fun getRound(roundId: Int): Round? = roundDao.getRound(roundId)

    suspend fun insertRound(round: Round): Long = roundDao.insertRound(round)

    suspend fun updateRound(round: Round) = roundDao.updateRound(round)

    suspend fun deleteRound(round: Round) = roundDao.deleteRound(round)

    // Hole Stats
    fun getHoleStatsForRound(roundId: Int): Flow<List<HoleStat>> = holeStatDao.getHoleStatsForRound(roundId)

    fun getHoleStatFlow(id: Int): Flow<HoleStat?> = holeStatDao.getHoleStatFlow(id)

    suspend fun getHoleStat(roundId: Int, holeId: Int): HoleStat? = holeStatDao.getHoleStat(roundId, holeId)

    suspend fun insertHoleStat(holeStat: HoleStat): Long = holeStatDao.insertHoleStat(holeStat)

    suspend fun updateHoleStat(holeStat: HoleStat) = holeStatDao.updateHoleStat(holeStat)

    // Putts
    fun getPuttsForHoleStat(holeStatId: Int): Flow<List<Putt>> = puttDao.getPuttsForHoleStat(holeStatId)

    suspend fun insertPutt(putt: Putt) = puttDao.insertPutt(putt)
    
    suspend fun updatePutt(putt: Putt) = puttDao.updatePutt(putt)
    
    suspend fun deletePutt(putt: Putt) = puttDao.deletePutt(putt)
    
    suspend fun replacePutts(holeStatId: Int, putts: List<Putt>) {
        puttDao.deletePuttsForHoleStat(holeStatId)
        puttDao.insertPutts(putts)
    }

    // Penalties
    fun getPenaltiesForHoleStat(holeStatId: Int): Flow<List<Penalty>> = penaltyDao.getPenaltiesForHoleStat(holeStatId)

    suspend fun insertPenalty(penalty: Penalty) = penaltyDao.insertPenalty(penalty)
    
    suspend fun deletePenalty(penalty: Penalty) = penaltyDao.deletePenalty(penalty)

    // Shots
    fun getShotsForHoleStat(holeStatId: Int): Flow<List<Shot>> = shotDao.getShotsForHoleStat(holeStatId)

    suspend fun insertShot(shot: Shot): Long = shotDao.insertShot(shot)

    suspend fun updateShot(shot: Shot) = shotDao.updateShot(shot)

    suspend fun deleteShot(shot: Shot) = shotDao.deleteShot(shot)

    suspend fun replaceShots(holeStatId: Int, shots: List<Shot>) {
        shotDao.deleteShotsForHoleStat(holeStatId)
        shots.forEach { shotDao.insertShot(it) }
    }
}
