package com.golftracker.domain

import android.content.Context
import com.golftracker.data.entity.*
import com.golftracker.data.repository.CourseRepository
import com.golftracker.data.repository.RoundRepository
import com.golftracker.util.StrokesGainedCalculator
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Date

class SgRecalculationUseCaseTest {

    private val context: Context = mockk()
    private val roundRepository: RoundRepository = mockk()
    private val courseRepository: CourseRepository = mockk()
    private val sgCalculator: StrokesGainedCalculator = mockk()
    
    private val useCase = SgRecalculationUseCase(
        context, roundRepository, courseRepository, sgCalculator
    )

    @Test
    fun testCalculateAdjustmentPerShot_HardCourse() = runBlocking {
        // Par 72, Rating 76.6
        val teeSet = TeeSet(id = 1, courseId = 1, name = "Black", slope = 145, rating = 76.6)
        val round = Round(id = 1, courseId = 1, teeSetId = 1, date = Date(), totalHoles = 18, isFinalized = false)
        
        val holes = (1..18).map { Hole(id = it, courseId = 1, holeNumber = it, par = 4) }
        
        coEvery { courseRepository.getTeeSet(1) } returns teeSet
        every { courseRepository.getHoles(1) } returns flowOf(holes)
        
        // Scenario 1: No holes played yet
        every { roundRepository.getHoleStatsForRound(1) } returns flowOf(emptyList())
        
        val adj1 = useCase.calculateAdjustmentPerShot(round)
        // roundAdjustment = 76.6 - 72 = 4.6
        // adj = 4.6 / 76.6 = 0.06005...
        assertEquals(0.06, adj1, 0.01)
        
        // Scenario 2: One hole played, Birdie (score 3)
        // Previous buggy behavior would give 4.6 / 3 = 1.53
        val stat = HoleStat(id = 1, roundId = 1, holeId = 1, score = 3)
        every { roundRepository.getHoleStatsForRound(1) } returns flowOf(listOf(stat))
        
        val finalizedRound = round.copy(isFinalized = true)
        val adj2 = useCase.calculateAdjustmentPerShot(finalizedRound)
        // New fixed behavior should still be ~0.06
        assertEquals(0.06, adj2, 0.01)
    }
}
