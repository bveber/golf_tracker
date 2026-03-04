package com.golftracker.ui.course

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.golftracker.data.entity.Course
import com.golftracker.data.entity.Hole
import com.golftracker.data.entity.HoleTeeYardage
import com.golftracker.data.entity.TeeSet
import com.golftracker.data.repository.CourseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CourseEditUiState(
    val name: String = "",
    val city: String = "",
    val state: String = "",
    val holeCount: Int = 18,
    val teeSets: List<TeeSet> = emptyList(), // ID=0 means new
    val holes: List<Hole> = emptyList(), // ID=0 means new
    // Yardages mapped by (holeIndex, teeSetIndex/Id) -> Yardage
    // Key: "holeIndex_teeSetIndex" (for new) or "holeId_teeSetId" (for existing)
    // Actually simpler: Map<TeeSetId/Index, Map<HoleIndex, Int>>
    // For new course, we rely on list index. For existing, we rely on ID.
    // Let's use a simpler structure: List<HoleEditData> where each has yardages per tee set.
    val yardages: Map<String, Int> = emptyMap(), // Key: "holeIndex_teeSetIndex", Value: Yardage
    val yardageIds: Map<String, Int> = emptyMap(), // Key: "holeIndex_teeSetIndex", Value: Yardage ID
    val duplicateHandicaps: List<Int> = emptyList(), // List of handicap values that are used more than once
    val isLoading: Boolean = false,
    val isSaved: Boolean = false
) {
    fun getTotalDistanceForTeeSet(teeSetIndex: Int): Int {
        return (0 until holes.size).sumOf { holeIndex ->
            yardages["${holeIndex}_${teeSetIndex}"] ?: 0
        }
    }
}

@HiltViewModel
class CourseEditViewModel @Inject constructor(
    private val courseRepository: CourseRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val courseId: Int? = savedStateHandle.get<String>("courseId")?.toIntOrNull()

    private val _uiState = MutableStateFlow(CourseEditUiState())
    val uiState: StateFlow<CourseEditUiState> = _uiState.asStateFlow()

    init {
        if (courseId != null && courseId != -1) {
            loadCourse(courseId)
        } else {
            initializeNewCourse()
        }
    }

    private fun initializeNewCourse() {
        _uiState.update { 
            it.copy(
                holes = List(18) { index -> 
                    Hole(courseId = 0, holeNumber = index + 1, par = 4, handicapIndex = null) 
                },
                teeSets = listOf(
                    TeeSet(courseId = 0, name = "White", slope = 113, rating = 72.0)
                )
            )
        }
    }

    private fun loadCourse(id: Int) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val course = courseRepository.getCourse(id)
            if (course != null) {
                val holes = courseRepository.getHoles(id).first()
                val teeSets = courseRepository.getTeeSets(id).first()
                
                // Build yardage map from all tee sets
                val yardageMap = mutableMapOf<String, Int>()
                val yardageIdMap = mutableMapOf<String, Int>()
                teeSets.forEachIndexed { teeSetIndex, teeSet ->
                    val yardages = courseRepository.getYardagesForTeeSet(teeSet.id).first()
                    yardages.forEach { yardage ->
                        // Find hole index by matching holeId
                        val holeIndex = holes.indexOfFirst { it.id == yardage.holeId }
                        if (holeIndex >= 0) {
                            yardageMap["${holeIndex}_${teeSetIndex}"] = yardage.yardage
                            yardageIdMap["${holeIndex}_${teeSetIndex}"] = yardage.id
                        }
                    }
                }
                
                _uiState.update { 
                    it.copy(
                        name = course.name,
                        city = course.city,
                        state = course.state,
                        holeCount = course.holeCount,
                        holes = if (holes.isNotEmpty()) holes else it.holes,
                        teeSets = if (teeSets.isNotEmpty()) teeSets else it.teeSets,
                        yardages = yardageMap,
                        yardageIds = yardageIdMap,
                        isLoading = false
                    ) 
                }
            }
        }
    }

    fun updateName(name: String) { _uiState.update { it.copy(name = name) } }
    fun updateCity(city: String) { _uiState.update { it.copy(city = city) } }
    fun updateState(state: String) { _uiState.update { it.copy(state = state) } }
    
    fun toggleHoleCount() {
        _uiState.update { 
            val newCount = if (it.holeCount == 18) 9 else 18
            val currentHoles = it.holes.toMutableList()
            if (newCount > currentHoles.size) {
                 for (i in currentHoles.size until newCount) {
                     currentHoles.add(Hole(courseId = 0, holeNumber = i + 1, par = 4, handicapIndex = null))
                 }
            } else {
                while (currentHoles.size > newCount) {
                    currentHoles.removeAt(currentHoles.lastIndex)
                }
            }
            it.copy(holeCount = newCount, holes = currentHoles)
        }
    }

    /**
     * Returns the available handicap indices for a given hole position.
     * We no longer filter out already-assigned values here, allowing the user to select duplicates
     * and simply prompting them with a warning on save.
     */
    fun getAvailableHandicaps(holeIndex: Int): List<Int> {
        val state = _uiState.value
        val maxHandicap = state.holeCount // 9 or 18
        return (1..maxHandicap).toList()
    }

    fun validateAndSave() {
        val state = _uiState.value
        val assignedHandicaps = state.holes.mapNotNull { it.handicapIndex }
        val duplicates = assignedHandicaps.groupingBy { it }.eachCount().filter { it.value > 1 }.keys.toList()

        if (duplicates.isNotEmpty()) {
            _uiState.update { it.copy(duplicateHandicaps = duplicates) }
        } else {
            proceedWithSave()
        }
    }

    fun dismissHandicapWarning() {
        _uiState.update { it.copy(duplicateHandicaps = emptyList()) }
    }

    fun proceedWithSave() {
        _uiState.update { it.copy(isLoading = true, duplicateHandicaps = emptyList()) }
        viewModelScope.launch {
            val state = uiState.value
            val newCourse = Course(
                id = courseId?.takeIf { it != -1 } ?: 0,
                name = state.name,
                city = state.city,
                state = state.state,
                holeCount = state.holeCount
            )
            
            val savedId = if (newCourse.id == 0) {
                courseRepository.insertCourse(newCourse).toInt()
            } else {
                courseRepository.updateCourse(newCourse)
                newCourse.id
            }

            // Save tee sets
            val savedTeeSets = mutableListOf<TeeSet>()
            state.teeSets.forEach { teeSet ->
                val ts = teeSet.copy(courseId = savedId)
                val insertResult = courseRepository.insertTeeSet(ts).toInt()
                val finalTeeSetId = if (ts.id == 0) insertResult else ts.id
                savedTeeSets.add(ts.copy(id = finalTeeSetId))
            }
            
            // Save holes and yardages
            state.holes.forEachIndexed { holeIndex, hole ->
                val h = hole.copy(courseId = savedId)
                val insertHoleResult = courseRepository.insertHole(h).toInt()
                val finalHoleId = if (h.id == 0) insertHoleResult else h.id
                
                // Save yardages for this hole across all tee sets
                savedTeeSets.forEachIndexed { teeSetIndex, teeSet ->
                    val yardageVal = state.yardages["${holeIndex}_${teeSetIndex}"] ?: 0
                    val existingYardageId = state.yardageIds["${holeIndex}_${teeSetIndex}"] ?: 0
                    if (yardageVal > 0) {
                        val yardage = HoleTeeYardage(
                            id = existingYardageId,
                            holeId = finalHoleId,
                            teeSetId = teeSet.id,
                            yardage = yardageVal
                        )
                        courseRepository.insertYardage(yardage)
                    }
                }
            }
            
            _uiState.update { it.copy(isSaved = true) }
        }
    }
    
    fun addTeeSet() {
         _uiState.update { 
             it.copy(teeSets = it.teeSets + TeeSet(courseId = 0, name = "New Tee", slope = 113, rating = 72.0))
         }
    }

    fun removeTeeSet(index: Int) {
        _uiState.update { 
            val sets = it.teeSets.toMutableList()
            if (index in sets.indices) sets.removeAt(index)
            it.copy(teeSets = sets)
        }
    }
    
    fun updateTeeSet(index: Int, teeSet: TeeSet) {
        _uiState.update {
            val sets = it.teeSets.toMutableList()
            if (index in sets.indices) sets[index] = teeSet
            it.copy(teeSets = sets)
        }
    }
    
    fun updateHole(index: Int, hole: Hole) {
        _uiState.update {
            val holes = it.holes.toMutableList()
            if (index in holes.indices) holes[index] = hole
            it.copy(holes = holes)
        }
    }

    fun updateYardage(holeIndex: Int, teeSetIndex: Int, yardage: Int) {
        _uiState.update {
            val key = "${holeIndex}_${teeSetIndex}"
            val newMap = it.yardages.toMutableMap()
            newMap[key] = yardage
            it.copy(yardages = newMap)
        }
    }
}
