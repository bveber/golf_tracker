package com.golftracker.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.golftracker.data.entity.Round
import com.golftracker.data.repository.RoundRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import android.content.Context
import com.golftracker.util.JsonExporter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import java.io.File
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val roundRepository: RoundRepository,
    private val jsonExporter: JsonExporter,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val activeRound: StateFlow<Round?> = roundRepository.activeRound
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun deleteRound(round: Round) {
        viewModelScope.launch {
            roundRepository.deleteRound(round)
        }
    }

    private val _exportFileEvent = MutableSharedFlow<File?>()
    val exportFileEvent: SharedFlow<File?> = _exportFileEvent.asSharedFlow()

    fun exportAllData() {
        viewModelScope.launch {
            val allRounds = roundRepository.finalizedRoundsWithDetails.first()
            val file = jsonExporter.exportAllDataToCache(context, allRounds)
            _exportFileEvent.emit(file)
        }
    }
}
