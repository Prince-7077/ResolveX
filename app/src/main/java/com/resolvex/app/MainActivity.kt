package com.resolvex.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.resolvex.app.ui.AdminDashboardScreen
import com.resolvex.app.ui.CitizenDashboardScreen
import com.resolvex.app.ui.LoginScreen
import com.resolvex.app.ui.OnboardingScreen
import com.resolvex.app.ui.RaiseComplaintScreen
import com.resolvex.app.ui.SplashScreen
import com.resolvex.app.ui.CivicMapScreen
import com.resolvex.app.ui.SchemeAssistantScreen
import com.resolvex.app.ui.TrackComplaintScreen
import com.resolvex.app.ui.theme.ResolvexTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ResolvexTheme {
                val navController = rememberNavController()

                NavHost(
                    navController = navController,
                    startDestination = "splash"
                ) {
                    composable("splash") {
                        SplashScreen(navController = navController)
                    }
                    composable("onboarding") {
                        OnboardingScreen(navController = navController)
                    }
                    composable("login") {
                        LoginScreen(navController = navController)
                    }
                    composable("citizen_dashboard") {
                        CitizenDashboardScreen(navController = navController)
                    }
                    composable("raise_complaint") {
                        RaiseComplaintScreen(navController = navController)
                    }
                    composable("admin_dashboard") {
                        AdminDashboardScreen(navController = navController)
                    }
                    composable(
                        route = "track_complaint/{complaintId}",
                        arguments = listOf(navArgument("complaintId") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val complaintId = backStackEntry.arguments?.getString("complaintId") ?: ""
                        TrackComplaintScreen(navController = navController, initialComplaintId = complaintId)
                    }
                    composable("track_complaint") {
                        TrackComplaintScreen(navController = navController, initialComplaintId = "")
                    }
                    composable("civic_map") {
                        CivicMapScreen(navController = navController)
                    }
                    composable("scheme_assistant") {
                        SchemeAssistantScreen(navController = navController)
                    }
                    // Compatibility redirect
                    composable("home") {
                        CitizenDashboardScreen(navController = navController)
                    }
                }
            }
        }
    }
}
