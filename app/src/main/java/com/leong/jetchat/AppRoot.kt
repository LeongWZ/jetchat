package com.leong.jetchat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.leong.jetchat.feature_auth.presentation.AuthViewModel
import com.leong.jetchat.feature_auth.presentation.LoginScreen
import com.leong.jetchat.feature_auth.presentation.RegisterScreen
import com.leong.jetchat.feature_chat.presentation.ChatScreen
import com.leong.jetchat.feature_conversations.presentation.ConversationListScreen
import com.leong.jetchat.feature_conversations.presentation.NewChatScreen
import com.leong.jetchat.feature_profile.presentation.ProfileScreen

private object Routes {
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val CONVERSATIONS = "conversations"
    const val NEW_CHAT = "new_chat"
    const val PROFILE = "profile"
    const val CHAT = "chat/{conversationId}"
    fun chat(conversationId: String) = "chat/$conversationId"
}

@Composable
fun AppRoot() {
    val nav = rememberNavController()
    val authVm: AuthViewModel = hiltViewModel()

    val user by authVm.currentUser.collectAsState(initial = null)
    val userId = user?.uid

    LaunchedEffect(userId) {
        val target = if (userId == null) Routes.LOGIN else Routes.CONVERSATIONS
        val current = nav.currentBackStackEntry?.destination?.route

        if (current == target) return@LaunchedEffect

        nav.navigate(target) {
            // Clear back stack so you can't back-navigate into auth screens after login (and vice versa)
            popUpTo(nav.graph.findStartDestination().id) { inclusive = true }
            launchSingleTop = true
        }
    }

    NavHost(navController = nav, startDestination = Routes.LOGIN) {

        composable(Routes.LOGIN) {
            LoginScreen(onGoRegister = { nav.navigate(Routes.REGISTER) } )
        }

        composable(Routes.REGISTER) {
            RegisterScreen(onGoLogin = { nav.popBackStack() } )
        }

        composable(Routes.CONVERSATIONS) {
            ConversationListScreen(
                onOpenChat = { convoId -> nav.navigate(Routes.chat(convoId)) },
                onNewChat = { nav.navigate(Routes.NEW_CHAT) },
                onOpenProfile = { nav.navigate(Routes.PROFILE) },
                onSignOut = { authVm.signOut() }
            )
        }

        composable(Routes.NEW_CHAT) {
            NewChatScreen(
                onBack = { nav.popBackStack() },
                onOpenChat = { convoId ->
                    nav.navigate(Routes.chat(convoId)) {
                        popUpTo(Routes.CONVERSATIONS) { inclusive = false }
                    }
                }
            )
        }

        composable(Routes.PROFILE) {
            ProfileScreen(onBack = { nav.popBackStack() })
        }

        composable(
            route = Routes.CHAT,
            arguments = listOf(navArgument("conversationId") { type = NavType.StringType } )
        ) {
            ChatScreen(onBack = { nav.popBackStack() })
        }
    }
}
