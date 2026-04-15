package com.hrv.biofeedback.presentation.navigation

sealed class NavRoutes(val route: String) {
    data object Home : NavRoutes("home")
    data object DeviceConnection : NavRoutes("device_connection")
    data object RfAssessment : NavRoutes("rf_assessment")
    data object Training : NavRoutes("training")
    data object SessionReport : NavRoutes("session_report/{sessionId}") {
        fun createRoute(sessionId: Long) = "session_report/$sessionId"
    }
    data object History : NavRoutes("history")
    data object LiveMetrics : NavRoutes("live_metrics")
    data object MorningCheck : NavRoutes("morning_check")
    data object Evaluation : NavRoutes("evaluation")
    data object Settings : NavRoutes("settings")
}
