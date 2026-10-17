package com.zai.native_app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.zai.native_app.data.TokenManager
import com.zai.native_app.ui.chat.ChatScreen
import com.zai.native_app.ui.conversation_list.ConversationListScreen
import com.zai.native_app.ui.login.LoginScreen
import com.zai.native_app.ui.settings.SettingsScreen

@Composable
fun Navigation() {
    val navController = rememberNavController()
    val context = androidx.compose.ui.platform.LocalContext.current
    val tokenManager = remember { TokenManager(context) }
    val token by tokenManager.token.collectAsState(initial = null)

    val startDestination = if (token != null) "chat_list" else "login"

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable("login") {
            LoginScreen(
                tokenManager = tokenManager,
                onLoginSuccess = {
                    navController.navigate("chat_list") {
                        popUpTo("login") { inclusive = true }
                    }
                }
            )
        }
        composable("chat_list") {
            ConversationListScreen(navController = navController)
        }
        composable("chat") {
            ChatScreen(
                navController = navController,
                tokenManager = tokenManager,
                chatId = null
            )
        }
        composable(
            route = "chat/{chatId}",
            arguments = listOf(navArgument("chatId") { type = NavType.StringType })
        ) { backStackEntry ->
            val chatId = backStackEntry.arguments?.getString("chatId")
            ChatScreen(
                navController = navController,
                tokenManager = tokenManager,
                chatId = chatId
            )
        }
        composable("settings") {
            SettingsScreen(
                navController = navController,
                tokenManager = tokenManager
            )
        }
    }
}
