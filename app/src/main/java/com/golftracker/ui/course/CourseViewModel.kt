package com.golftracker.ui.course

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.golftracker.data.entity.Course
import com.golftracker.data.repository.CourseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CourseViewModel @Inject constructor(
    private val courseRepository: CourseRepository
) : ViewModel() {

    val allCourses: StateFlow<List<Course>> = courseRepository.allCourses
        .stateIn(
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
