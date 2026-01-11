package com.leong.jetchat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.leong.jetchat.feature_auth.presentation.AuthState
import com.leong.jetchat.feature_auth.presentation.AuthViewModel
import com.leong.jetchat.feature_auth.presentation.LoginScreen
import com.leong.jetchat.feature_auth.presentation.RegisterScreen
import com.leong.jetchat.feature_chat.presentation.ChatScreen
import com.leong.jetchat.feature_conversations.presentation.ConversationListScreen
import com.leong.jetchat.feature_conversations.presentation.NewChatScreen
import com.leong.jetchat.feature_profile.presentation.ProfileScreen

private object Routes {
    const val SPLASH = "splash"
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
    val authState by authVm.authState.collectAsState()

    fun popNavBackStackSafe() {
        if (nav.currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) {
            nav.popBackStack()
        }
    }

    LaunchedEffect(authState) {
        val route = nav.currentBackStackEntry?.destination?.route

        val isSplash = route == Routes.SPLASH
        val isLogin = route == Routes.LOGIN
        val isRegister = route == Routes.REGISTER
        val inAuth = isSplash || isLogin || isRegister

        when (authState) {
            AuthState.Loading -> {
                // Keep user on splash while resolving auth
                if (!isSplash) {
                    nav.navigate(Routes.SPLASH) {
                        popUpTo(nav.graph.startDestinationId) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }

            AuthState.SignedOut -> {
                // If signed out, ensure user is on LOGIN (from splash or any app screen)
                if (!isLogin) {
                    nav.navigate(Routes.LOGIN) {
                        popUpTo(nav.graph.startDestinationId) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }

            is AuthState.SignedIn -> {
                // If signed in, only redirect if we're in auth section (prevents resetting chat/profile)
                if (inAuth) {
                    nav.navigate(Routes.CONVERSATIONS) {
                        popUpTo(nav.graph.startDestinationId) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }
        }
    }

    NavHost(navController = nav, startDestination = Routes.SPLASH) {

        composable(Routes.SPLASH) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        composable(Routes.LOGIN) {
            LoginScreen(onGoRegister = { nav.navigate(Routes.REGISTER) })
        }

        composable(Routes.REGISTER) {
            RegisterScreen(onGoLogin = { popNavBackStackSafe() })
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
                onBack = { popNavBackStackSafe() },
                onOpenChat = { convoId ->
                    nav.navigate(Routes.chat(convoId)) {
                        popUpTo(Routes.CONVERSATIONS) { inclusive = false }
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(Routes.PROFILE) {
            ProfileScreen(onBack = { popNavBackStackSafe() })
        }

        composable(
            route = Routes.CHAT,
            arguments = listOf(navArgument("conversationId") { type = NavType.StringType })
        ) {
            ChatScreen(onBack = { popNavBackStackSafe() })
        }
    }
}
