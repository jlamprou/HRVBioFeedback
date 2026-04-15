package com.hrv.biofeedback.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.hrv.biofeedback.presentation.assessment.RfAssessmentScreen
import com.hrv.biofeedback.presentation.device.DeviceConnectionScreen
import com.hrv.biofeedback.presentation.history.HistoryScreen
import com.hrv.biofeedback.presentation.home.HomeScreen
import com.hrv.biofeedback.presentation.live.LiveMetricsScreen
import com.hrv.biofeedback.presentation.evaluation.EvaluationScreen
import com.hrv.biofeedback.presentation.morning.MorningCheckScreen
import com.hrv.biofeedback.presentation.report.SessionReportScreen
import com.hrv.biofeedback.presentation.settings.SettingsScreen
import com.hrv.biofeedback.presentation.training.TrainingSessionScreen

@Composable
fun NavGraph(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = NavRoutes.Home.route
    ) {
        composable(NavRoutes.Home.route) {
            HomeScreen(
                onNavigateToDevice = { navController.navigate(NavRoutes.DeviceConnection.route) },
                onNavigateToAssessment = { navController.navigate(NavRoutes.RfAssessment.route) },
                onNavigateToTraining = { navController.navigate(NavRoutes.Training.route) },
                onNavigateToLive = { navController.navigate(NavRoutes.LiveMetrics.route) },
                onNavigateToMorningCheck = { navController.navigate(NavRoutes.MorningCheck.route) },
                onNavigateToEvaluation = { navController.navigate(NavRoutes.Evaluation.route) },
                onNavigateToHistory = { navController.navigate(NavRoutes.History.route) },
                onNavigateToSettings = { navController.navigate(NavRoutes.Settings.route) },
                onNavigateToReport = { sessionId ->
                    navController.navigate(NavRoutes.SessionReport.createRoute(sessionId))
                }
            )
        }

        composable(NavRoutes.DeviceConnection.route) {
            DeviceConnectionScreen(onBack = { navController.popBackStack() })
        }

        composable(NavRoutes.LiveMetrics.route) {
            LiveMetricsScreen(onBack = { navController.popBackStack() })
        }

        composable(NavRoutes.MorningCheck.route) {
            MorningCheckScreen(onBack = { navController.popBackStack() })
        }

        composable(NavRoutes.Evaluation.route) {
            EvaluationScreen(onBack = { navController.popBackStack() })
        }

        composable(NavRoutes.RfAssessment.route) {
            RfAssessmentScreen(
                onBack = { navController.popBackStack() },
                onComplete = { sessionId ->
                    navController.popBackStack()
                    navController.navigate(NavRoutes.SessionReport.createRoute(sessionId))
                }
            )
        }

        composable(NavRoutes.Training.route) {
            TrainingSessionScreen(
                onBack = { navController.popBackStack() },
                onComplete = { sessionId ->
                    navController.popBackStack()
                    navController.navigate(NavRoutes.SessionReport.createRoute(sessionId))
                }
            )
        }

        composable(
            route = NavRoutes.SessionReport.route,
            arguments = listOf(navArgument("sessionId") { type = NavType.LongType })
        ) {
            SessionReportScreen(onBack = { navController.popBackStack() })
        }

        composable(NavRoutes.History.route) {
            HistoryScreen(
                onBack = { navController.popBackStack() },
                onNavigateToReport = { sessionId ->
                    navController.navigate(NavRoutes.SessionReport.createRoute(sessionId))
                }
            )
        }

        composable(NavRoutes.Settings.route) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
