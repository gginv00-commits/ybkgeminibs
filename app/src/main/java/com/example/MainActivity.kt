package com.example

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.navigation.AdminRoute
import com.example.navigation.DashboardRoute
import com.example.navigation.LandingRoute
import com.example.navigation.RoomRoute
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.LandingScreen
import com.example.ui.screens.MainViewModel
import com.example.ui.screens.RoomScreen
import com.example.ui.screens.AdminScreen
import com.example.ui.theme.AppTheme
import com.example.ui.theme.SquadBackground

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        setContent {
            AppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = SquadBackground
                ) {
                    val navController = rememberNavController()

                    NavHost(
                        navController = navController,
                        startDestination = LandingRoute
                    ) {
                        composable<LandingRoute> {
                            LandingScreen(
                                viewModel = viewModel,
                                onNavigateToRoom = { roomId ->
                                    navController.navigate(RoomRoute(roomId)) {
                                        popUpTo(LandingRoute) { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable<DashboardRoute> {
                            DashboardScreen(
                                viewModel = viewModel,
                                onNavigateToRoom = { roomId ->
                                    navController.navigate(RoomRoute(roomId))
                                },
                                onNavigateToAdmin = {
                                    navController.navigate(AdminRoute)
                                }
                            )
                        }
                        composable<RoomRoute> { backStackEntry ->
                            val route = backStackEntry.toRoute<RoomRoute>()
                            RoomScreen(
                                roomId = route.roomId,
                                viewModel = viewModel,
                                onNavigateBack = {
                                    navController.navigate(LandingRoute) {
                                        popUpTo(0) { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable<AdminRoute> {
                            AdminScreen(
                                viewModel = viewModel,
                                onNavigateBack = {
                                    navController.popBackStack()
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent != null && intent.action == Intent.ACTION_SEND) {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!text.isNullOrBlank()) {
                viewModel.handleIncomingShare(text)
            }
        }
    }
}
