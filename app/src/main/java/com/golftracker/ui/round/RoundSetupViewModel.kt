package com.golftracker.ui.round

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.golftracker.data.entity.Course
import com.golftracker.data.entity.Hole
import com.golftracker.data.entity.Round
import com.golftracker.data.entity.TeeSet
import com.golftracker.data.repository.CourseRepository
import com.golftracker.data.repository.RoundRepository
import com.golftracker.data.repository.WeatherData
import com.golftracker.data.repository.WeatherRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

data class RoundSetupUiState(
    val courses: List<Course> = emptyList(),
    val selectedCourse: Course? = null,
    val teeSets: List<TeeSet> = emptyList(),
    val selectedTeeSet: TeeSet? = null,
    val holes: List<Hole> = emptyList(),
    val date: Date = Date(),
    val notes: String = "",
    val teeYardages: Map<Int, Int> = emptyMap(), // teeSetId to totalYardage
    val holesToPlay: Int = 18, // 9 or 18
    val startingHole: Int = 1, // 1 or 10
    val isPractice: Boolean = false,
    val isLoading: Boolean = false,
    val createdRoundId: Int? = null,
    val weather: WeatherData? = null,
    val weatherLoading: Boolean = false,
    val weatherError: String? = null
)

@HiltViewModel
class RoundSetupViewModel @Inject constructor(
    private val courseRepository: CourseRepository,
    private val roundRepository: RoundRepository,
    private val weatherRepository: WeatherRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RoundSetupUiState())
    val uiState: StateFlow<RoundSetupUiState> = _uiState.asStateFlow()

    init {
        loadCourses()
    }

    private fun loadCourses() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val courses = courseRepository.allCourses.first()
            _uiState.update { it.copy(courses = courses, isLoading = false) }
        }
    }

    fun selectCourse(course: Course) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, selectedCourse = course, teeSets = emptyList(), selectedTeeSet = null, teeYardages = emptyMap()) }
            val teeSets = courseRepository.getTeeSets(course.id).first()

            val yardagesMap = mutableMapOf<Int, Int>()
            for (teeSet in teeSets) {
                val yardages = courseRepository.getYardagesForTeeSet(teeSet.id).first()
                yardagesMap[teeSet.id] = yardages.sumOf { it.yardage }
            }

            _uiState.update {
                it.copy(
                    teeSets = teeSets,
                    selectedTeeSet = teeSets.firstOrNull(), // Default to first
                    teeYardages = yardagesMap,
                    isLoading = false
                )
            }

            // Auto-fetch weather: prefer hole 1 tee coords, then course-level coords, then geocode city/state
            val holes = courseRepository.getHoles(course.id).first().sortedBy { it.holeNumber }
            _uiState.update { it.copy(holes = holes) }
            val hole1 = holes.firstOrNull { it.holeNumber == 1 }
            val lat = hole1?.teeLat ?: course.latitude
            val lon = hole1?.teeLng ?: course.longitude
            if (lat != null && lon != null) {
                fetchWeather(lat, lon)
            } else if (course.city.isNotBlank()) {
                fetchWeatherByCity(course.city, course.state)
            }
        }
    }

    fun fetchWeather(lat: Double, lon: Double) {
        viewModelScope.launch {
            _uiState.update { it.copy(weatherLoading = true, weatherError = null) }
            try {
                val weather = weatherRepository.fetchWeather(lat, lon)
                _uiState.update { it.copy(weather = weather, weatherLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(weatherLoading = false, weatherError = "Unable to fetch weather") }
            }
        }
    }

    fun selectTeeSet(teeSet: TeeSet) {
        _uiState.update { it.copy(selectedTeeSet = teeSet) }
    }

    fun updateDate(date: Date) {
        _uiState.update { it.copy(date = date) }
    }
    
    fun updateNotes(notes: String) {
        _uiState.update { it.copy(notes = notes) }
    }

    fun updateHolesToPlay(holes: Int) {
        _uiState.update { it.copy(holesToPlay = holes) }
    }

    fun updateStartingHole(hole: Int) {
        _uiState.update { it.copy(startingHole = hole) }
    }

    fun togglePracticeRound() {
        _uiState.update { it.copy(isPractice = !it.isPractice) }
    }

    fun startRound() {
        val state = uiState.value
        if (state.selectedCourse != null && state.selectedTeeSet != null) {
            viewModelScope.launch {
                val newRound = Round(
                    courseId = state.selectedCourse.id,
                    teeSetId = state.selectedTeeSet.id,
                    date = state.date,
                    notes = state.notes,
                    totalHoles = state.holesToPlay,
                    startHole = state.startingHole,
                    isPractice = state.isPractice,
                    weatherCondition = state.weather?.condition,
                    temperatureFahrenheit = state.weather?.temperatureFahrenheit,
                    windSpeedMph = state.weather?.windSpeedMph,
                    windDirection = state.weather?.windDirection,
                    humidityPercent = state.weather?.humidityPercent,
                    pressureInHg = state.weather?.pressureInHg
                )
                val roundId = roundRepository.insertRound(newRound).toInt()

                // Initialize hole stats
                val allHoles = courseRepository.getHoles(state.selectedCourse.id).first()
                val holesToInitialize = if (state.holesToPlay == 18) {
                    allHoles
                } else if (state.startingHole == 1) {
                    allHoles.filter { it.holeNumber in 1..9 }
                } else {
                    allHoles.filter { it.holeNumber in 10..18 }
                }

                holesToInitialize.forEach { hole ->
                    // Correctly initialize with roundId
                    roundRepository.insertHoleStat(
                        com.golftracker.data.entity.HoleStat(
                            roundId = roundId,
                            holeId = hole.id
                        )
                    )
                }

                _uiState.update { it.copy(createdRoundId = roundId, isLoading = false) }
            }
        }
    }
    
    fun retryWeatherFetch() {
        viewModelScope.launch {
            val course = uiState.value.selectedCourse ?: return@launch
            val holes = courseRepository.getHoles(course.id).first()
            val hole1 = holes.firstOrNull { it.holeNumber == 1 }
            val lat = hole1?.teeLat ?: course.latitude
            val lon = hole1?.teeLng ?: course.longitude
            if (lat != null && lon != null) {
                fetchWeather(lat, lon)
            } else if (course.city.isNotBlank()) {
                fetchWeatherByCity(course.city, course.state)
            } else {
                _uiState.update { it.copy(weatherError = "No GPS coordinates available for this course") }
            }
        }
    }

    private fun fetchWeatherByCity(city: String, state: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(weatherLoading = true, weatherError = null) }
            try {
                val weather = weatherRepository.fetchWeatherForCity(city, state)
                _uiState.update { it.copy(weather = weather, weatherLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(weatherLoading = false, weatherError = "Unable to fetch weather") }
            }
        }
    }

    fun updateHoleStrategyNotes(hole: Hole, notes: String) {
        viewModelScope.launch {
            val updated = hole.copy(strategyNotes = notes)
            courseRepository.updateHole(updated)
            _uiState.update { state ->
                state.copy(holes = state.holes.map { if (it.id == hole.id) updated else it })
            }
        }
    }

    fun resetCreatedRoundId() {
        _uiState.update { it.copy(createdRoundId = null) }
    }
}
