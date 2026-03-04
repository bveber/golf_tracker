package com.golftracker.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SportsGolf
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    activeRound: com.golftracker.data.entity.Round?,
    onResumeRound: (Int) -> Unit,
    onNavigateToCourseList: () -> Unit,
    onNavigateToRoundSetup: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToStats: () -> Unit,
    onNavigateToBag: () -> Unit,
    onNavigateToHandicap: () -> Unit,
    onDeleteActiveRound: (com.golftracker.data.entity.Round) -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: HomeViewModel = androidx.hilt.navigation.compose.hiltViewModel()
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.LaunchedEffect(Unit) {
        viewModel.exportFileEvent.collect { file ->
            if (file != null) {
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    context, 
                    "${context.packageName}.fileprovider", 
                    file
                )
                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "application/json"
                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(android.content.Intent.createChooser(intent, "Share All Golf Data"))
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Golf Tracker") },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(androidx.compose.material.icons.Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Welcome Back!",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 32.dp)
            )

            if (activeRound != null) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                DashboardButton(
                    text = "Resume Round",
                    icon = Icons.Filled.SportsGolf, // Or a "Play" icon if available
                    onClick = { onResumeRound(activeRound.id) },
                    modifier = Modifier.weight(1f).height(80.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = { onDeleteActiveRound(activeRound) },
                    modifier = Modifier.size(80.dp) // Make it square-ish matching height
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Delete Round",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(32.dp)
                    )
                }
                }
                
                DashboardButton(
                    text = "Start New Round",
                    icon = Icons.Filled.Add,
                    onClick = onNavigateToRoundSetup,
                    modifier = Modifier.fillMaxWidth().height(60.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                )
            } else {
                DashboardButton(
                    text = "Start Round",
                    icon = Icons.Filled.SportsGolf,
                    onClick = onNavigateToRoundSetup,
                    modifier = Modifier.fillMaxWidth().height(80.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                DashboardButton(
                    text = "History",
                    icon = Icons.Filled.History,
                    onClick = onNavigateToHistory,
                    modifier = Modifier.weight(1f).height(100.dp)
                )
                DashboardButton(
                    text = "Stats",
                    icon = Icons.Filled.Star,
                    onClick = onNavigateToStats,
                    modifier = Modifier.weight(1f).height(100.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                DashboardButton(
                    text = "Courses",
                    icon = Icons.Filled.List,
                    onClick = onNavigateToCourseList,
                    modifier = Modifier.weight(1f).height(100.dp)
                )
                DashboardButton(
                    text = "My Bag",
                    icon = Icons.Filled.Person,
                    onClick = onNavigateToBag,
                    modifier = Modifier.weight(1f).height(100.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            DashboardButton(
                text = "Handicap",
                icon = Icons.Filled.Timeline,
                onClick = onNavigateToHandicap,
                modifier = Modifier.fillMaxWidth().height(60.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun DashboardButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    colors: androidx.compose.material3.CardColors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = colors
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.height(4.dp))
            Text(text, style = MaterialTheme.typography.titleMedium)
        }
    }
}
