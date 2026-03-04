package com.golftracker.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class UserPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {

    private object PreferencesKeys {
        val ESTIMATED_HANDICAP = doublePreferencesKey("estimated_handicap")
    }

    val estimatedHandicapFlow: Flow<Double?> = dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.ESTIMATED_HANDICAP]
        }

    suspend fun setEstimatedHandicap(handicap: Double?) {
        dataStore.edit { preferences ->
            if (handicap == null) {
                preferences.remove(PreferencesKeys.ESTIMATED_HANDICAP)
            } else {
                preferences[PreferencesKeys.ESTIMATED_HANDICAP] = handicap
            }
        }
    }
}
