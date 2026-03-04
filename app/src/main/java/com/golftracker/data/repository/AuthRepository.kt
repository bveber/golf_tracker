package com.golftracker.data.repository

import com.golftracker.data.model.GoogleUser
import kotlinx.coroutines.flow.StateFlow

interface AuthRepository {
    val currentUser: StateFlow<GoogleUser?>
    suspend fun signOut()
    // Sign-in is usually handled via ActivityResultLauncher in the UI, 
    // so the repository might just handle the intentional update or token exchange.
    fun updateCurrentUser(user: GoogleUser?)
}
