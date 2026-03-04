package com.golftracker.data.repository

import com.golftracker.data.db.dao.ClubDao
import com.golftracker.data.entity.Club
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClubRepository @Inject constructor(
    private val clubDao: ClubDao
) {
    val activeClubs: Flow<List<Club>> = clubDao.getActiveClubs()

    suspend fun insertClub(club: Club) = clubDao.insertClub(club)

    suspend fun updateClub(club: Club) = clubDao.updateClub(club)

    suspend fun deleteClub(club: Club) = clubDao.deleteClub(club)
}
