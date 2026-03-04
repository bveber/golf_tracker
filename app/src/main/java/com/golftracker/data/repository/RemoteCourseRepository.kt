package com.golftracker.data.repository

import android.util.Log
import com.golftracker.data.api.CourseApiService
import com.golftracker.data.api.model.NetworkCourseSummary
import com.golftracker.data.db.dao.CourseDao
import com.golftracker.data.entity.Course
import com.golftracker.data.entity.Hole
import com.golftracker.data.entity.HoleTeeYardage
import com.golftracker.data.entity.TeeSet
import javax.inject.Inject
import javax.inject.Singleton
import androidx.room.Transaction

@Singleton
class RemoteCourseRepository @Inject constructor(
    private val apiService: CourseApiService,
    private val courseDao: CourseDao
) {

    suspend fun searchCourses(query: String): List<NetworkCourseSummary> {
        return try {
            val response = apiService.searchCourses(query)
            response.courses
        } catch (e: Exception) {
            Log.e("RemoteCourseRepository", "Error searching courses", e)
            emptyList()
        }
    }

    suspend fun fetchCourseDetails(courseId: Int): com.golftracker.data.api.model.NetworkCourseDetails {
        return apiService.getCourseDetails(courseId).course
    }

    @Transaction
    suspend fun saveSelectedCourseTees(
        courseDetails: com.golftracker.data.api.model.NetworkCourseDetails,
        selectedTees: List<com.golftracker.data.api.model.NetworkScorecard>
    ): Long {
        Log.d("RemoteCourseRepository", "Saving Course: ${courseDetails.name}")
        val course = Course(
            name = courseDetails.name,
            city = courseDetails.location?.city ?: "",
            state = courseDetails.location?.state ?: "",
            holeCount = 18 
        )
        val newCourseId = courseDao.insertCourse(course).toInt()

        val firstScorecard = selectedTees.firstOrNull()
        val holeIdMap = mutableMapOf<Int, Int>() 

        if (firstScorecard != null) {
            firstScorecard.holes.forEachIndexed { index, networkHole ->
                val holeNumber = index + 1
                val hole = Hole(
                    courseId = newCourseId,
                    holeNumber = holeNumber,
                    par = networkHole.par,
                    handicapIndex = networkHole.handicapIndex ?: (index + 1)
                )
                val newHoleId = courseDao.insertHole(hole).toInt()
                holeIdMap[holeNumber] = newHoleId
            }
        }

        selectedTees.forEach { scorecard ->
            val teeSet = TeeSet(
                courseId = newCourseId,
                name = scorecard.teeName,
                slope = scorecard.slope ?: 113,
                rating = scorecard.rating ?: 72.0
            )
            val newTeeSetId = courseDao.insertTeeSet(teeSet).toInt()

            scorecard.holes.forEachIndexed { index, networkHole ->
                val holeNumber = index + 1
                val holeId = holeIdMap[holeNumber]
                if (holeId != null) {
                    val yardage = HoleTeeYardage(
                        holeId = holeId,
                        teeSetId = newTeeSetId,
                        yardage = networkHole.yardage
                    )
                    Log.d("RemoteCourseRepository", "Inserting yardage for holeId=$holeId, teeSetId=$newTeeSetId, yardage=${networkHole.yardage}")
                    courseDao.insertYardage(yardage)
                }
            }
        }

        return newCourseId.toLong()
    }
}
