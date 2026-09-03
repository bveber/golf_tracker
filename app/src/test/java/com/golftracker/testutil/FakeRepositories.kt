package com.golftracker.testutil

import com.golftracker.data.entity.Club
import com.golftracker.data.entity.Hole
import com.golftracker.data.entity.HoleStat
import com.golftracker.data.entity.Round
import com.golftracker.data.entity.Shot
import com.golftracker.data.repository.ClubRepository
import com.golftracker.data.repository.CourseRepository
import com.golftracker.data.repository.RawDispersionData
import com.golftracker.data.repository.RoundRepository
import com.golftracker.data.repository.StatsData
import com.golftracker.data.repository.StatsFilter
import com.golftracker.data.repository.StatsRepository
import com.golftracker.data.repository.UserPreferencesRepository
import com.golftracker.data.repository.WeatherData
import com.golftracker.data.repository.WeatherRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf

/**
 * Builds a MockK-backed ClubRepository with a controllable clubs list.
 * Call [setClubs] to push new data before assertions.
 */
fun fakeClubRepository(initialClubs: List<Club> = emptyList()): ClubRepository {
    val clubsFlow = MutableStateFlow(initialClubs)
    return mockk<ClubRepository>(relaxed = true) {
        every { activeClubs } returns clubsFlow
    }
}

/**
 * Returns the underlying MutableStateFlow so tests can push new clubs.
 * Usage: val (repo, clubsFlow) = fakeClubRepositoryWithFlow()
 */
fun fakeClubRepositoryWithFlow(
    initialClubs: List<Club> = emptyList()
): Pair<ClubRepository, MutableStateFlow<List<Club>>> {
    val clubsFlow = MutableStateFlow(initialClubs)
    val repo = mockk<ClubRepository>(relaxed = true) {
        every { activeClubs } returns clubsFlow
    }
    return repo to clubsFlow
}

/**
 * Builds a MockK-backed RoundRepository with in-memory data.
 */
fun fakeRoundRepository(
    round: Round? = null,
    holeStats: List<HoleStat> = emptyList(),
    shots: List<Shot> = emptyList()
): RoundRepository = mockk<RoundRepository>(relaxed = true) {
    coEvery { getRound(any()) } returns round
    every { getHoleStatsForRound(any()) } returns flowOf(holeStats)
    every { getHoleStatFlow(any()) } answers {
        val id = firstArg<Int>()
        flowOf(holeStats.find { it.id == id })
    }
    every { getShotsForHoleStat(any()) } returns flowOf(shots)
}

/**
 * Builds a MockK-backed CourseRepository that serves [holes] keyed by courseId.
 */
fun fakeCourseRepository(holes: List<Hole> = emptyList()): CourseRepository =
    mockk<CourseRepository>(relaxed = true) {
        every { getHoles(any()) } answers {
            val courseId = firstArg<Int>()
            flowOf(holes.filter { it.courseId == courseId })
        }
    }

fun fakeStatsRepository(): StatsRepository =
    mockk<StatsRepository>(relaxed = true) {
        every { getFilteredStatsData(any()) } returns flowOf(StatsData())
    }

fun fakeUserPrefsRepository(): UserPreferencesRepository =
    mockk<UserPreferencesRepository>(relaxed = true) {
        every { estimatedHandicapFlow } returns flowOf(15.0)
        every { dispersionRoundsFlow } returns flowOf(10)
    }

fun fakeWeatherRepository(): WeatherRepository =
    mockk<WeatherRepository>(relaxed = true) {
        coEvery { fetchWeather(any(), any()) } returns
            WeatherData(null, null, null, null, null, null)
    }
