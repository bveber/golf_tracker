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
    fun testCalculateTotalRoundAdjustment_Basic() = runBlocking {
        // Par 72, Rating 74.0
        val teeSet = TeeSet(id = 1, courseId = 1, name = "Black", slope = 145, rating = 74.0)
        val round = Round(id = 1, courseId = 1, teeSetId = 1, date = Date(), totalHoles = 18, isFinalized = false)
        
        val holes = (1..18).map { Hole(id = it, courseId = 1, holeNumber = it, par = 4) }
        val yardages = holes.map { HoleTeeYardage(id = it.id, teeSetId = 1, holeId = it.id, yardage = 400) }
        
        coEvery { courseRepository.getTeeSet(1) } returns teeSet
        every { courseRepository.getHoles(1) } returns flowOf(holes)
        every { courseRepository.getYardagesForTeeSet(1) } returns flowOf(yardages)
        
        // Mock PGA Expected: 4.1 per 400y hole. Total = 18 * 4.1 = 73.8
        every { sgCalculator.getExpectedStrokes(400, any(), any()) } returns 4.1
        
        val totalAdj = useCase.calculateTotalRoundAdjustment(round)
        
        // totalRoundAdjustment = 74.0 - 73.8 = 0.2
        assertEquals(0.2, totalAdj, 0.01)
    }
}
