package com.golftracker.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.golftracker.data.model.GoogleUser
import com.golftracker.data.repository.AuthRepository
import com.golftracker.data.repository.RoundRepository
import com.golftracker.util.JsonExporter
import com.golftracker.util.GoogleDriveService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val roundRepository: RoundRepository,
    private val jsonExporter: JsonExporter,
    private val googleDriveService: GoogleDriveService,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val currentUser: StateFlow<GoogleUser?> = authRepository.currentUser

    private val _exportEvent = MutableSharedFlow<ExportResult>()
    val exportEvent: SharedFlow<ExportResult> = _exportEvent.asSharedFlow()

    fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
        }
    }

    fun updateCurrentUser(user: GoogleUser?) {
        authRepository.updateCurrentUser(user)
    }

    fun exportLocally() {
        viewModelScope.launch {
            val allRounds = roundRepository.finalizedRoundsWithDetails.first()
            val file = jsonExporter.exportAllDataToCache(context, allRounds)
            if (file != null) {
                _exportEvent.emit(ExportResult.LocalSuccess(file))
            } else {
                _exportEvent.emit(ExportResult.Error("Failed to generate JSON file."))
            }
        }
    }

    fun exportToGoogleDrive() {
        viewModelScope.launch {
            _exportEvent.emit(ExportResult.Loading("Uploading to Google Drive..."))
            val allRounds = roundRepository.finalizedRoundsWithDetails.first()
            val file = jsonExporter.exportAllDataToCache(context, allRounds)
            if (file != null) {
                val fileId = googleDriveService.uploadFile(file, "application/json")
                if (fileId != null) {
                    _exportEvent.emit(ExportResult.DriveSuccess(fileId))
                } else {
                    _exportEvent.emit(ExportResult.Error("Failed to upload to Google Drive. Ensure you have granted Drive permissions."))
                }
            } else {
                _exportEvent.emit(ExportResult.Error("Failed to generate JSON file."))
            }
        }
    }

    sealed class ExportResult {
        data class LocalSuccess(val file: File) : ExportResult()
        data class DriveSuccess(val fileId: String) : ExportResult()
        data class Loading(val message: String) : ExportResult()
        data class Error(val message: String) : ExportResult()
    }
}
