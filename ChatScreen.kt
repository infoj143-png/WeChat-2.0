package com.infoj143.wechat20

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.infoj143.wechat20.navigation.ChatRoutes
import com.infoj143.wechat20.ui.screens.*
import com.infoj143.wechat20.ui.theme.MyApplicationTheme
import com.infoj143.wechat20.viewmodel.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Explicitly initialize FirebaseApp to guarantee it is ready on startup
        // 
        // --- PRODUCTION NOTE FOR FIREBASE PHONE AUTHENTICATION ---
        // For real-time Phone OTP verification to succeed on a physical device or external build:
        // 1. Ensure the 'Phone' Sign-in provider is enabled in Firebase Console -> Build -> Authentication -> Sign-in method.
        // 2. You MUST add your app's SHA-1 and SHA-256 fingerprints to your Firebase Console project settings.
        //    - Under Project settings -> General -> Your apps -> SHA certificate fingerprints.
        //    - These fingerprints are derived from your debug/release keystore (e.g. debug.keystore or my-upload-key.jks).
        // 3. Make sure 'com.infoj143.wechat20' is verified as the Package Name matching the google-services.json configuration.
        try {
            com.google.firebase.FirebaseApp.initializeApp(this)
            android.util.Log.d("MainActivity", "FirebaseApp initialized successfully on startup.")
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Failed to initialize FirebaseApp on startup", e)
        }

        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0)
                ) { innerPadding ->
                    AppNavigation(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun AppNavigation(modifier: Modifier = Modifier) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = ChatRoutes.SPLASH,
        modifier = modifier
    ) {
        // 1. SPLASH SCREEN
        composable(ChatRoutes.SPLASH) {
            SplashScreen(onNavigate = { route ->
                navController.navigate(route) {
                    popUpTo(ChatRoutes.SPLASH) { inclusive = true }
                }
            })
        }

        // 2. AUTHENTICATION & OTP
        composable(ChatRoutes.AUTH) {
            val authViewModel = remember { AuthViewModel() }
            AuthScreen(
                viewModel = authViewModel,
                onNavigateToProfileSetup = {
                    navController.navigate(ChatRoutes.PROFILE_SETUP) {
                        popUpTo(ChatRoutes.AUTH) { inclusive = true }
                    }
                },
                onNavigateToHome = {
                    navController.navigate(ChatRoutes.HOME) {
                        popUpTo(ChatRoutes.AUTH) { inclusive = true }
                    }
                }
            )
        }

        // 3. PROFILE SETUP
        composable(ChatRoutes.PROFILE_SETUP) {
            val profileViewModel = remember { ProfileViewModel() }
            ProfileSetupScreen(
                viewModel = profileViewModel,
                onComplete = {
                    navController.navigate(ChatRoutes.HOME) {
                        popUpTo(ChatRoutes.PROFILE_SETUP) { inclusive = true }
                    }
                }
            )
        }

        // 4. MAIN CHAT DASHBOARD HUB
        composable(ChatRoutes.HOME) {
            val homeViewModel = remember { HomeViewModel() }
            HomeScreen(
                viewModel = homeViewModel,
                onNavigateToChat = { peerId ->
                    navController.navigate(ChatRoutes.chatRoute(peerId))
                },
                onNavigateToSettings = {
                    navController.navigate(ChatRoutes.SETTINGS)
                },
                onNavigateToVoiceCall = { peerId, isIncoming ->
                    navController.navigate(ChatRoutes.voiceCallRoute(peerId, isIncoming))
                },
                onNavigateToVideoCall = { peerId, isIncoming ->
                    navController.navigate(ChatRoutes.videoCallRoute(peerId, isIncoming))
                }
            )
        }

        // 5. ONE-TO-ONE CHAT ROOM SCREEN
        composable(
            route = ChatRoutes.CHAT,
            arguments = listOf(navArgument("peerId") { type = NavType.StringType })
        ) { backStackEntry ->
            val peerId = backStackEntry.arguments?.getString("peerId") ?: ""
            val chatViewModel = remember(peerId) { ChatViewModel(peerId) }
            ChatScreen(
                viewModel = chatViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToVoiceCall = { targetId, isIncoming ->
                    navController.navigate(ChatRoutes.voiceCallRoute(targetId, isIncoming))
                },
                onNavigateToVideoCall = { targetId, isIncoming ->
                    navController.navigate(ChatRoutes.videoCallRoute(targetId, isIncoming))
                }
            )
        }

        // 6. VOICE CALLING SCREEN
        composable(
            route = ChatRoutes.VOICE_CALL,
            arguments = listOf(
                navArgument("peerId") { type = NavType.StringType },
                navArgument("isIncoming") { type = NavType.BoolType }
            )
        ) { backStackEntry ->
            val peerId = backStackEntry.arguments?.getString("peerId") ?: ""
            val isIncoming = backStackEntry.arguments?.getBoolean("isIncoming") ?: false
            val callViewModel = remember { CallViewModel() }
            CallScreen(
                viewModel = callViewModel,
                peerId = peerId,
                isIncoming = isIncoming,
                isVideo = false,
                onCallEnded = { navController.popBackStack() }
            )
        }

        // 7. VIDEO CALLING SCREEN
        composable(
            route = ChatRoutes.VIDEO_CALL,
            arguments = listOf(
                navArgument("peerId") { type = NavType.StringType },
                navArgument("isIncoming") { type = NavType.BoolType }
            )
        ) { backStackEntry ->
            val peerId = backStackEntry.arguments?.getString("peerId") ?: ""
            val isIncoming = backStackEntry.arguments?.getBoolean("isIncoming") ?: false
            val callViewModel = remember { CallViewModel() }
            CallScreen(
                viewModel = callViewModel,
                peerId = peerId,
                isIncoming = isIncoming,
                isVideo = true,
                onCallEnded = { navController.popBackStack() }
            )
        }

        // 8. SETTINGS PREFERENCES SCREEN
        composable(ChatRoutes.SETTINGS) {
            val settingsViewModel = remember { SettingsViewModel() }
            SettingsScreen(
                viewModel = settingsViewModel,
                onNavigateBack = { navController.popBackStack() },
                onLogoutComplete = {
                    navController.navigate(ChatRoutes.AUTH) {
                        popUpTo(ChatRoutes.HOME) { inclusive = true }
                    }
                }
            )
        }
    }
}
