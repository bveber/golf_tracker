package com.golftracker.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.rememberNavController
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.golftracker.ui.home.HomeScreen

@Composable
fun GolfTrackerNavGraph(
    navController: NavHostController = rememberNavController(),
    startDestination: String = "home"
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable("home") {
            val viewModel: com.golftracker.ui.home.HomeViewModel = androidx.hilt.navigation.compose.hiltViewModel()
            val activeRound = viewModel.activeRound.collectAsState(initial = null).value

            var showDeleteDialog by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

            if (showDeleteDialog && activeRound != null) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { showDeleteDialog = false },
                    title = { androidx.compose.material3.Text("Delete In-Progress Round?") },
                    text = { androidx.compose.material3.Text("Are you sure you want to discard this round data? This cannot be undone.") },
                    confirmButton = {
                        androidx.compose.material3.TextButton(
                            onClick = { 
                                viewModel.deleteRound(activeRound)
                                showDeleteDialog = false
                            }
                        ) {
                             androidx.compose.material3.Text("Delete", color = androidx.compose.material3.MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(onClick = { showDeleteDialog = false }) {
                            androidx.compose.material3.Text("Cancel")
                        }
                    }
                )
            }

            HomeScreen(
                activeRound = activeRound,
                onResumeRound = { roundId -> navController.navigate("holeTracking/$roundId") },
                onNavigateToCourseList = { navController.navigate("courseList") },
                onNavigateToRoundSetup = { navController.navigate("roundSetup") },
                onNavigateToHistory = { navController.navigate("roundHistory") },
                onNavigateToStats = { navController.navigate("stats") },
                onNavigateToBag = { navController.navigate("bag") },
                onNavigateToHandicap = { navController.navigate("handicap") },
                onDeleteActiveRound = { showDeleteDialog = true },
                onNavigateToSettings = { navController.navigate("settings") }
            )
        }
        composable("courseList") {
            com.golftracker.ui.course.CourseListScreen(
                onAddCourse = { navController.navigate("courseEdit") },
                onImportCourse = { navController.navigate("courseImport") },
                onCourseClick = { courseId -> navController.navigate("courseEdit?courseId=$courseId") },
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable("courseImport") {
            com.golftracker.ui.course.CourseImportScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(
            route = "courseEdit?courseId={courseId}",
            arguments = listOf(androidx.navigation.navArgument("courseId") {
                type = androidx.navigation.NavType.StringType // Passed as string/null, VM parses it or default value
                nullable = true
                defaultValue = null
            })
        ) {
            com.golftracker.ui.course.CourseEditScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable("bag") {
            com.golftracker.ui.bag.BagScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        // Round Tracking
        composable("roundSetup") {
            com.golftracker.ui.round.RoundSetupScreen(
                onNavigateBack = { navController.popBackStack() },
                onRoundCreated = { roundId -> 
                    navController.navigate("holeTracking/$roundId") {
                        popUpTo("roundSetup") { inclusive = true }
                    }
                }
            )
        }
        composable(
            route = "holeTracking/{roundId}?initialHole={initialHole}",
            arguments = listOf(
                androidx.navigation.navArgument("roundId") { type = androidx.navigation.NavType.StringType },
                androidx.navigation.navArgument("initialHole") {
                    type = androidx.navigation.NavType.IntType
                    defaultValue = -1
                }
            )
        ) {
            com.golftracker.ui.round.HoleTrackingScreen(
                onNavigateBack = { navController.popBackStack() },
                onFinishRound = { 
                    // When finishing, we can go to summary. 
                    // Ideally we pass roundId, but VM already has it. 
                    // However, we need to extract roundId from current backstack entry to pass to next if distinct VM,
                    // but since we rely on Hilt VM scoping (which is per generic navigation entry usually),
                    // passing ID ensures correct VM init.
                    val roundId = it.arguments?.getString("roundId") ?: "0"
                    navController.navigate("roundSummary/$roundId")
                }
            )
        }
        composable(
            route = "roundSummary/{roundId}",
            arguments = listOf(androidx.navigation.navArgument("roundId") { type = androidx.navigation.NavType.StringType })
        ) { backStackEntry ->
             val roundId = backStackEntry.arguments?.getString("roundId") ?: "0"
            com.golftracker.ui.round.RoundSummaryScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToHole = { holeIndex -> 
                    // Navigate to hole tracking with specific hole index
                    navController.navigate("holeTracking/$roundId?initialHole=$holeIndex")
                },
                onFinishRound = {
                    navController.navigate("home") {
                        popUpTo("home") { inclusive = true }
                    }
                }
            )
        }
        composable("roundHistory") {
            com.golftracker.ui.round.RoundHistoryScreen(
                onNavigateBack = { navController.popBackStack() },
                onRoundClick = { roundId -> navController.navigate("roundSummary/$roundId") }
            )
        }
        composable("stats") {
            com.golftracker.ui.stats.StatsDashboardScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable("handicap") {
            com.golftracker.ui.handicap.HandicapScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable("settings") {
            com.golftracker.ui.settings.SettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
