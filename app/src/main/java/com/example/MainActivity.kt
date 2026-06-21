package com.example

import android.os.Bundle
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
                                onNavigateToDashboard = {
                                    navController.navigate(DashboardRoute) {
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
                                    navController.popBackStack()
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
}
