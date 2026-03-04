package com.golftracker.util

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.FileContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleDriveService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun uploadFile(localFile: java.io.File, mimeType: String): String? = withContext(Dispatchers.IO) {
        val account = GoogleSignIn.getLastSignedInAccount(context) ?: return@withContext null
        
        val credential = GoogleAccountCredential.usingOAuth2(
            context, listOf(DriveScopes.DRIVE_FILE)
        ).apply {
            selectedAccount = account.account
        }

        val driveService = Drive.Builder(
            NetHttpTransport(),
            GsonFactory(),
            credential
        ).setApplicationName("Golf Tracker").build()

        val googleFile = File().apply {
            name = localFile.name
            parents = listOf("root")
        }

        val mediaContent = FileContent(mimeType, localFile)
        
        try {
            val uploadedFile = driveService.files().create(googleFile, mediaContent)
                .setFields("id")
                .execute()
            uploadedFile.id
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
