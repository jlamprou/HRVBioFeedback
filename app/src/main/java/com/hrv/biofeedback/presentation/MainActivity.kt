package com.hrv.biofeedback.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import com.hrv.biofeedback.presentation.navigation.NavGraph
import com.hrv.biofeedback.presentation.theme.HrvBiofeedbackTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HrvBiofeedbackTheme {
                val navController = rememberNavController()
                NavGraph(navController = navController)
            }
        }
    }
}
