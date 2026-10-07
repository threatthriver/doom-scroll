package com.securemessage.app.ui.nav

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.securemessage.app.data.AuthDestination
import com.securemessage.app.data.AuthGate
import com.securemessage.app.di.AppContainer
import com.securemessage.app.ui.auth.CompleteProfileScreen
import com.securemessage.app.ui.auth.SignInScreen
import com.securemessage.app.ui.auth.SignUpScreen
import com.securemessage.app.ui.auth.VerifyEmailScreen
import com.securemessage.app.ui.chat.ChatScreen
import com.securemessage.app.ui.conversations.ConversationsScreen
import com.securemessage.app.ui.main.MainScreen
import com.securemessage.app.ui.users.UserSearchScreen

/** Navigate and clear the entire back stack. */
private fun NavHostController.navigateClearing(route: String) =
    navigate(route) { popUpTo(graph.id) { inclusive = true } }

private fun AuthDestination.route(): String = when (this) {
    AuthDestination.SIGN_IN -> Routes.SIGN_IN
    AuthDestination.VERIFY_EMAIL -> Routes.verifyEmail()
    AuthDestination.COMPLETE_PROFILE -> Routes.completeProfile()
    AuthDestination.CONVERSATIONS -> Routes.CONVERSATIONS
}

@Composable
fun AppNavHost(
    container: AppContainer,
    openUpdates: Boolean = false,
    // Chat to open from a message notification tap. Ignored when signed out.
    openChatId: String? = null,
) {
    val nav = rememberNavController()
    val start = remember {
        val user = container.firebaseAuth.currentUser
        // The profile isn't known yet at launch; Conversations redirects to Complete Profile if it's missing.
        AuthGate.route(user != null, user?.isEmailVerified == true, hasProfile = true).route()
    }

    // Deep-link from the tray: jump straight into the chat once the graph is ready.
    LaunchedEffect(openChatId, start) {
        if (openChatId != null && start == Routes.CONVERSATIONS) {
            nav.navigate(Routes.chat(openChatId))
        }
    }

    NavHost(navController = nav, startDestination = start) {
        composable(Routes.SIGN_IN) {
            SignInScreen(
                onAuthenticated = { nav.navigateClearing(Routes.CONVERSATIONS) },
                onNeedsProfile = { nav.navigateClearing(Routes.completeProfile()) },
                onNeedsVerification = { nav.navigateClearing(Routes.verifyEmail()) },
                onCreateAccount = { nav.navigate(Routes.SIGN_UP) },
            )
        }
        composable(Routes.SIGN_UP) {
            SignUpScreen(
                onAuthenticated = { nav.navigateClearing(Routes.CONVERSATIONS) },
                onNeedsProfile = { reason -> nav.navigateClearing(Routes.completeProfile(reason)) },
                onNeedsVerification = { nav.navigateClearing(Routes.verifyEmail(sent = true)) },
                onBack = { nav.popBackStack() },
            )
        }
        composable(
            Routes.VERIFY_EMAIL,
            arguments = listOf(
                navArgument("sent") {
                    type = NavType.BoolType
                    defaultValue = false
                },
            ),
        ) {
            VerifyEmailScreen(
                onVerified = { dest -> nav.navigateClearing(dest.route()) },
                onSignedOut = { nav.navigateClearing(Routes.SIGN_IN) },
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
                onNeedsVerification = { nav.navigateClearing(Routes.verifyEmail()) },
                onSignedOut = { nav.navigateClearing(Routes.SIGN_IN) },
            )
        }
        composable(Routes.CONVERSATIONS) {
            MainScreen(
                onOpenChat = { id -> nav.navigate(Routes.chat(id)) },
                onNeedsProfile = { nav.navigateClearing(Routes.completeProfile()) },
                onNeedsVerification = { nav.navigateClearing(Routes.verifyEmail()) },
                onSignedOut = { nav.navigateClearing(Routes.SIGN_IN) },
                openUpdates = openUpdates,
                onEditProfile = { displayName, bio ->
                    // Profile editing is handled within the ProfileScreen
                    // This callback can be used to trigger a snackbar or other UI feedback
                },
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
            enterTransition = {
                slideInHorizontally(
                    initialOffsetX = { it },
                    animationSpec = tween(280)
                ) + fadeIn(animationSpec = tween(220))
            },
            exitTransition = {
                slideOutHorizontally(
                    targetOffsetX = { it / 3 },
                    animationSpec = tween(260)
                ) + fadeOut(animationSpec = tween(200))
            },
            popEnterTransition = {
                slideInHorizontally(
                    initialOffsetX = { -it / 3 },
                    animationSpec = tween(260)
                ) + fadeIn(animationSpec = tween(200))
            },
            popExitTransition = {
                slideOutHorizontally(
                    targetOffsetX = { it },
                    animationSpec = tween(280)
                ) + fadeOut(animationSpec = tween(220))
            },
        ) {
            ChatScreen(onBack = { nav.popBackStack() })
        }
    }
}
