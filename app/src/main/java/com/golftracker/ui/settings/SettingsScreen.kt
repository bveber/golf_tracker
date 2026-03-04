package com.golftracker.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.hilt.navigation.compose.hiltViewModel
import com.golftracker.data.model.GoogleUser
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val currentUser by viewModel.currentUser.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            viewModel.updateCurrentUser(
                GoogleUser(
                    id = account.id ?: "",
                    displayName = account.displayName,
                    email = account.email,
                    photoUrl = account.photoUrl?.toString()
                )
            )
        } catch (e: ApiException) {
            scope.launch {
                val errorMsg = com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes.getStatusCodeString(e.statusCode)
                snackbarHostState.showSnackbar("Sign-in failed: ${e.statusCode} ($errorMsg)")
            }
        }
    }

    // Local Export (Create Document) Launcher
    var tempFileToExport by remember { mutableStateOf<java.io.File?>(null) }
    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            tempFileToExport?.let { file ->
                context.contentResolver.openOutputStream(it)?.use { output ->
                    file.inputStream().use { input ->
                        input.copyTo(output)
                    }
                }
                tempFileToExport = null
                // Show success
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.exportEvent.collect { result ->
            when (result) {
                is SettingsViewModel.ExportResult.LocalSuccess -> {
                    tempFileToExport = result.file
                    createDocumentLauncher.launch("golf_tracker_export.json")
                }
                is SettingsViewModel.ExportResult.DriveSuccess -> {
                    snackbarHostState.showSnackbar("Successfully exported to Google Drive!")
                }
                is SettingsViewModel.ExportResult.Error -> {
                    snackbarHostState.showSnackbar(result.message)
                }
                is SettingsViewModel.ExportResult.Loading -> {
                    // Could show a progress indicator
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Google Account Section
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Google Account", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    if (currentUser != null) {
                        Text("Signed in as: ${currentUser?.displayName ?: currentUser?.email}")
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { viewModel.signOut() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.Logout, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Sign Out")
                        }
                    } else {
                        Button(onClick = {
                            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                                .requestEmail()
                                .requestScopes(com.google.android.gms.common.api.Scope(com.google.api.services.drive.DriveScopes.DRIVE_FILE))
                                .build()
                            val client = GoogleSignIn.getClient(context, gso)
                            // Clear any previous sign-in to ensure account picker and permission prompt
                            client.signOut().addOnCompleteListener {
                                signInLauncher.launch(client.signInIntent)
                            }
                        }) {
                            Icon(Icons.Default.Login, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Sign in with Google")
                        }
                    }
                }
            }

            // Export Section
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Data Export", style = MaterialTheme.typography.titleMedium)
                    
                    Button(
                        onClick = { viewModel.exportLocally() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Export All Data (Local)")
                    }

                    Button(
                        onClick = {
                            val account = GoogleSignIn.getLastSignedInAccount(context)
                            val driveScope = com.google.android.gms.common.api.Scope(com.google.api.services.drive.DriveScopes.DRIVE_FILE)
                            if (account != null && GoogleSignIn.hasPermissions(account, driveScope)) {
                                viewModel.exportToGoogleDrive()
                            } else {
                                scope.launch {
                                    snackbarHostState.showSnackbar("Drive permission missing. Please Sign Out and Sign In again to grant it.")
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = currentUser != null
                    ) {
                        Icon(Icons.Default.CloudUpload, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Export All Data to Google Drive")
                    }
                    
                    if (currentUser == null) {
                        Text(
                            "Sign in to enable Google Drive export",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
        }
    }
}
