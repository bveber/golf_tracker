package com.golftracker.ui.course

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.golftracker.data.entity.Course
import com.golftracker.data.repository.CourseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CourseWithLongestTee(
    val course: Course,
    val distance: Int?,
    val rating: Double?,
    val slope: Int?
)

@HiltViewModel
class CourseViewModel @Inject constructor(
    private val courseRepository: CourseRepository
) : ViewModel() {

    val allCoursesWithStats: StateFlow<List<CourseWithLongestTee>> = combine(
        courseRepository.allCourses,
        courseRepository.allTeeSets,
        courseRepository.allYardages
    ) { courses, allTeeSets, allYardages ->
        val yardagesByTeeSet = allYardages.groupBy { it.teeSetId }
        
        val teeSetStats = allTeeSets.map { teeSet ->
            val totalDistance = yardagesByTeeSet[teeSet.id]?.sumOf { it.yardage } ?: 0
            object {
                val teeSetId = teeSet.id
                val courseId = teeSet.courseId
                val distance = totalDistance
                val rating = teeSet.rating
                val slope = teeSet.slope
            }
        }.groupBy { it.courseId }

        courses.map { course ->
            val courseTeeSets = teeSetStats[course.id] ?: emptyList()
            val longestTee = courseTeeSets.maxByOrNull { it.distance }
            
            CourseWithLongestTee(
                course = course,
                distance = longestTee?.distance?.takeIf { it > 0 },
                rating = longestTee?.rating?.takeIf { it > 0.0 },
                slope = longestTee?.slope?.takeIf { it > 0 }
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun deleteCourse(course: Course) {
        viewModelScope.launch {
            courseRepository.deleteCourse(course)
        }
    }
}
