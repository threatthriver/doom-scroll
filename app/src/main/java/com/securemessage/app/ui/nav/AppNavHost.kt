package com.securemessage.app.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.securemessage.app.di.AppContainer
import com.securemessage.app.ui.auth.CompleteProfileScreen
import com.securemessage.app.ui.auth.SignInScreen
import com.securemessage.app.ui.auth.SignUpScreen
import com.securemessage.app.ui.chat.ChatScreen
import com.securemessage.app.ui.conversations.ConversationsScreen
import com.securemessage.app.ui.main.MainScreen
import com.securemessage.app.ui.users.UserSearchScreen

/** Navigate and clear the entire back stack. */
private fun NavHostController.navigateClearing(route: String) =
    navigate(route) { popUpTo(graph.id) { inclusive = true } }

@Composable
fun AppNavHost(container: AppContainer) {
    val nav = rememberNavController()
    val start = remember {
        if (container.firebaseAuth.currentUser != null) Routes.CONVERSATIONS else Routes.SIGN_IN
    }

    NavHost(navController = nav, startDestination = start) {
        composable(Routes.SIGN_IN) {
            SignInScreen(
                onAuthenticated = { nav.navigateClearing(Routes.CONVERSATIONS) },
                onNeedsProfile = { nav.navigateClearing(Routes.completeProfile()) },
                onCreateAccount = { nav.navigate(Routes.SIGN_UP) },
            )
        }
        composable(Routes.SIGN_UP) {
            SignUpScreen(
                onAuthenticated = { nav.navigateClearing(Routes.CONVERSATIONS) },
                onNeedsProfile = { reason -> nav.navigateClearing(Routes.completeProfile(reason)) },
                onBack = { nav.popBackStack() },
            )
        }
        composable(
            Routes.COMPLETE_PROFILE,
            arguments = listOf(
                navArgument("reason") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) {
            CompleteProfileScreen(
                onAuthenticated = { nav.navigateClearing(Routes.CONVERSATIONS) },
                onSignedOut = { nav.navigateClearing(Routes.SIGN_IN) },
            )
        }
        composable(Routes.CONVERSATIONS) {
            MainScreen(
                onOpenChat = { id -> nav.navigate(Routes.chat(id)) },
                onNeedsProfile = { nav.navigateClearing(Routes.completeProfile()) },
                onSignedOut = { nav.navigateClearing(Routes.SIGN_IN) },
            )
        }
        composable(Routes.USERS) {
            UserSearchScreen(
                onBack = { nav.popBackStack() },
                onOpenChat = { id ->
                    nav.navigate(Routes.chat(id)) { popUpTo(Routes.USERS) { inclusive = true } }
                },
            )
        }
        composable(
            Routes.CHAT,
            arguments = listOf(navArgument("chatId") { type = NavType.StringType }),
        ) {
            ChatScreen(onBack = { nav.popBackStack() })
        }
    }
}
