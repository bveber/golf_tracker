package com.golftracker.util

import android.content.Context
import com.golftracker.data.entity.Course
import com.golftracker.data.entity.Hole
import com.golftracker.data.entity.HoleStat
import com.golftracker.data.entity.Round
import com.golftracker.data.entity.TeeSet
import com.golftracker.data.model.HoleStatWithHole
import com.golftracker.data.model.RoundWithDetails
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Date

class JsonExporterTest {

    @Test
    fun testExportRoundToCache_CreatesValidJsonFile() {
        val mockContext = mockk<Context>()
        val tempDir = System.getProperty("java.io.tmpdir")
        val cacheDir = File(tempDir, "test_cache").apply { mkdirs() }
        every { mockContext.cacheDir } returns cacheDir

        val course = Course(id = 1, name = "Test Course", city = "Test City", state = "TS", holeCount = 18)
        val teeSet = TeeSet(id = 1, courseId = 1, name = "Blue", rating = 72.0, slope = 113)
        val round = Round(id = 1, courseId = 1, teeSetId = 1, date = Date())
        
        val hole = Hole(id = 1, courseId = 1, holeNumber = 1, par = 4)
        val holeStat = HoleStat(id = 1, roundId = 1, holeId = 1, score = 4, putts = 2)
        val holeStatWithHole = HoleStatWithHole(
            holeStat = holeStat,
            hole = hole,
            putts = emptyList(),
            penalties = emptyList(),
            shots = emptyList()
        )

        val roundDetails = RoundWithDetails(
            round = round,
            course = course,
            teeSet = teeSet,
            holeStats = listOf(holeStatWithHole)
        )

        val exporter = JsonExporter()
        val file = exporter.exportRoundToCache(mockContext, roundDetails)

        assertTrue("File should have been created", file != null && file.exists())
        val content = file!!.readText()
        assertTrue("Content should contain course object", content.contains("\"course\": {"))
        assertTrue("Content should contain holeStats array", content.contains("\"holeStats\": ["))
        assertTrue("Content should contain hole object", content.contains("\"hole\": {"))
        assertTrue("Content should contain shots array", content.contains("\"shots\": ["))
        
        // Specific value check within keys
        assertTrue("Content should contain holeNumber value", content.contains("\"holeNumber\": 1"))
        assertTrue("Content should contain par value", content.contains("\"par\": 4"))
        assertTrue("Content should contain score value", content.contains("\"score\": 4"))
        
        // Cleanup
        file.delete()
        cacheDir.delete()
    }
}
