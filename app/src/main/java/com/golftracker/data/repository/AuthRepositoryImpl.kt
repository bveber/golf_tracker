package com.golftracker.data.repository

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.golftracker.data.model.GoogleUser
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : AuthRepository {

    private val _currentUser = MutableStateFlow<GoogleUser?>(null)
    override val currentUser: StateFlow<GoogleUser?> = _currentUser.asStateFlow()

    init {
        val account = GoogleSignIn.getLastSignedInAccount(context)
        _currentUser.value = account?.toGoogleUser()
    }

    override suspend fun signOut() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .build()
        val googleSignInClient = GoogleSignIn.getClient(context, gso)
        googleSignInClient.signOut().addOnCompleteListener {
            _currentUser.value = null
        }
    }

    override fun updateCurrentUser(user: GoogleUser?) {
        _currentUser.value = user
    }

    private fun GoogleSignInAccount.toGoogleUser(): GoogleUser {
        return GoogleUser(
            id = id ?: "",
            displayName = displayName,
            email = email,
            photoUrl = photoUrl?.toString()
        )
    }
}
