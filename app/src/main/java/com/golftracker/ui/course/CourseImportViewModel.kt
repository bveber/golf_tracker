package com.golftracker.ui.course

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.golftracker.data.api.model.NetworkCourseSummary
import com.golftracker.data.repository.RemoteCourseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CourseImportViewModel @Inject constructor(
    private val remoteCourseRepository: RemoteCourseRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<NetworkCourseSummary>>(emptyList())
    val searchResults: StateFlow<List<NetworkCourseSummary>> = _searchResults.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _importStatus = MutableStateFlow<ImportStatus>(ImportStatus.Idle)
    val importStatus: StateFlow<ImportStatus> = _importStatus.asStateFlow()

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun searchCourses() {
        if (_searchQuery.value.isBlank()) return
        viewModelScope.launch {
            _isLoading.value = true
            _importStatus.value = ImportStatus.Idle
            val results = remoteCourseRepository.searchCourses(_searchQuery.value)
            _searchResults.value = results
            _isLoading.value = false
        }
    }

    fun fetchCourseTees(courseId: Int) {
        viewModelScope.launch {
            _importStatus.value = ImportStatus.Importing
            try {
                val details = remoteCourseRepository.fetchCourseDetails(courseId)
                val allTees = (details.tees?.male ?: emptyList()) + (details.tees?.female ?: emptyList())
                if (allTees.isEmpty()) {
                    // No tees, just import the shell course
                    remoteCourseRepository.saveSelectedCourseTees(details, emptyList())
                    _importStatus.value = ImportStatus.Success
                } else {
                    _importStatus.value = ImportStatus.TeeSelection(details, allTees)
                }
            } catch (e: Exception) {
                _importStatus.value = ImportStatus.Error(e.message ?: "Failed to fetch course details")
            }
        }
    }

    fun confirmImport(courseDetails: com.golftracker.data.api.model.NetworkCourseDetails, selectedTees: List<com.golftracker.data.api.model.NetworkScorecard>) {
        viewModelScope.launch {
            _importStatus.value = ImportStatus.Importing
            try {
                remoteCourseRepository.saveSelectedCourseTees(courseDetails, selectedTees)
                _importStatus.value = ImportStatus.Success
            } catch (e: Exception) {
                _importStatus.value = ImportStatus.Error(e.message ?: "Failed to save course")
            }
        }
    }
    
    fun resetImportStatus() {
        _importStatus.value = ImportStatus.Idle
    }
}

sealed class ImportStatus {
    object Idle : ImportStatus()
    object Importing : ImportStatus()
    data class TeeSelection(
        val courseDetails: com.golftracker.data.api.model.NetworkCourseDetails,
        val availableTees: List<com.golftracker.data.api.model.NetworkScorecard>
    ) : ImportStatus()
    object Success : ImportStatus()
    data class Error(val message: String) : ImportStatus()
}
