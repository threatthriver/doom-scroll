package com.securemessage.app.ui.nav

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
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
import com.securemessage.app.ui.common.AppViewModelFactory
import com.securemessage.app.ui.conversations.ConversationsViewModel
import com.securemessage.app.ui.profile.SettingsScreen
import com.securemessage.app.ui.users.UserSearchScreen
import com.securemessage.app.ui.weave.AssistantScreen
import com.securemessage.app.ui.weave.CirclesScreen
import com.securemessage.app.ui.weave.CreateEventScreen
import com.securemessage.app.ui.weave.HomeViewModel
import com.securemessage.app.ui.weave.MomentScreen
import com.securemessage.app.ui.weave.NearbyScreen
import com.securemessage.app.ui.weave.OnboardingScreen
import com.securemessage.app.ui.weave.SessionScreen
import com.securemessage.app.ui.weave.SplashScreen
import com.securemessage.app.ui.weave.WeaveMainScreen
import com.securemessage.app.ui.weave.WeaveNav
import com.securemessage.app.ui.weave.WeavePrefs
import com.securemessage.app.ui.weave.friendUidsOf

/** Navigate and clear the entire back stack. */
private fun NavHostController.navigateClearing(route: String) =
    navigate(route) { popUpTo(graph.id) { inclusive = true } }

private val enterSlide = slideInHorizontally(tween(280)) { it } + fadeIn(tween(220))
private val exitSlide = slideOutHorizontally(tween(260)) { -it / 3 } + fadeOut(tween(200))
private val popEnterSlide = slideInHorizontally(tween(260)) { -it / 3 } + fadeIn(tween(200))
private val popExitSlide = slideOutHorizontally(tween(280)) { it } + fadeOut(tween(220))

@Composable
fun AppNavHost(
    container: AppContainer,
    @Suppress("UNUSED_PARAMETER") openUpdates: Boolean = false,
    // Chat to open from a message notification tap. Ignored when signed out.
    openChatId: String? = null,
) {
    val nav = rememberNavController()
    val ctx = LocalContext.current

    // WEAVE is dark everywhere: keep status-bar icons light.
    val view = LocalView.current
    LaunchedEffect(Unit) {
        (view.context as? android.app.Activity)?.window?.let {
            WindowCompat.getInsetsController(it, view).isAppearanceLightStatusBars = false
            WindowCompat.getInsetsController(it, view).isAppearanceLightNavigationBars = false
        }
    }

    // Where a signed-in, verified user lands: the intent picker the first time, then Home.
    val home: () -> String = { if (WeavePrefs.isOnboarded(ctx)) Routes.CONVERSATIONS else Routes.ONBOARDING }
    fun AuthDestination.route(): String = when (this) {
        AuthDestination.SIGN_IN -> Routes.SPLASH
        AuthDestination.VERIFY_EMAIL -> Routes.verifyEmail()
        AuthDestination.COMPLETE_PROFILE -> Routes.completeProfile()
        AuthDestination.CONVERSATIONS -> home()
    }

    val start = remember {
        val user = container.firebaseAuth.currentUser
        // The profile isn't known yet at launch; Home redirects to Complete Profile if it's missing.
        AuthGate.route(user != null, user?.isEmailVerified == true, hasProfile = true).route()
    }

    LaunchedEffect(openChatId, start) {
        if (openChatId != null && start == Routes.CONVERSATIONS) nav.navigate(Routes.chat(openChatId))
    }

    /** View models shared with the tab shell, scoped to the Home back-stack entry. */
    @Composable
    fun shared(entry: NavBackStackEntry): Pair<HomeViewModel, ConversationsViewModel> {
        val owner = remember(entry) { runCatching { nav.getBackStackEntry(Routes.CONVERSATIONS) }.getOrDefault(entry) }
        return viewModel<HomeViewModel>(owner, factory = AppViewModelFactory.Factory) to
            viewModel<ConversationsViewModel>(owner, factory = AppViewModelFactory.Factory)
    }

    NavHost(
        navController = nav,
        startDestination = start,
        enterTransition = { enterSlide },
        exitTransition = { exitSlide },
        popEnterTransition = { popEnterSlide },
        popExitTransition = { popExitSlide },
    ) {
        composable(Routes.SPLASH, enterTransition = { fadeIn() }, exitTransition = { fadeOut() }) {
            SplashScreen(onGetStarted = { nav.navigate(Routes.SIGN_UP) }, onHaveAccount = { nav.navigate(Routes.SIGN_IN) })
        }
        composable(Routes.ONBOARDING) {
            var name by remember { mutableStateOf("") }
            LaunchedEffect(Unit) {
                container.firebaseAuth.currentUser?.uid?.let { uid ->
                    container.userRepository.getUser(uid).onSuccess { name = it.displayName }
                }
            }
            OnboardingScreen(name) { nav.navigateClearing(Routes.CONVERSATIONS) }
        }
        composable(Routes.SIGN_IN) {
            SignInScreen(
                onAuthenticated = { nav.navigateClearing(home()) },
                onNeedsProfile = { nav.navigateClearing(Routes.completeProfile()) },
                onNeedsVerification = { nav.navigateClearing(Routes.verifyEmail()) },
                onCreateAccount = { nav.navigate(Routes.SIGN_UP) { popUpTo(Routes.SPLASH) } },
            )
        }
        composable(Routes.SIGN_UP) {
            SignUpScreen(
                onAuthenticated = { nav.navigateClearing(home()) },
                onNeedsProfile = { reason -> nav.navigateClearing(Routes.completeProfile(reason)) },
                onNeedsVerification = { nav.navigateClearing(Routes.verifyEmail(sent = true)) },
                onBack = { nav.popBackStack() },
            )
        }
        composable(
            Routes.VERIFY_EMAIL,
            arguments = listOf(navArgument("sent") { type = NavType.BoolType; defaultValue = false }),
        ) {
            VerifyEmailScreen(
                onVerified = { dest -> nav.navigateClearing(dest.route()) },
                onSignedOut = { nav.navigateClearing(Routes.SPLASH) },
            )
        }
        composable(
            Routes.COMPLETE_PROFILE,
            arguments = listOf(navArgument("reason") { type = NavType.StringType; nullable = true; defaultValue = null }),
        ) {
            CompleteProfileScreen(
                onAuthenticated = { nav.navigateClearing(home()) },
                onNeedsVerification = { nav.navigateClearing(Routes.verifyEmail()) },
                onSignedOut = { nav.navigateClearing(Routes.SPLASH) },
            )
        }
        composable(Routes.CONVERSATIONS, enterTransition = { fadeIn() }, popEnterTransition = { fadeIn() }) { entry ->
            val (homeVm, convVm) = shared(entry)
            WeaveMainScreen(
                nav = WeaveNav(
                    openChat = { nav.navigate(Routes.chat(it)) },
                    newChat = { nav.navigate(Routes.USERS) },
                    openMoment = { nav.navigate(Routes.moment(it.id)) },
                    openSession = { nav.navigate(Routes.session(it)) },
                    openNearby = { nav.navigate(Routes.NEARBY) },
                    openCircles = { nav.navigate(Routes.CIRCLES) },
                    openAssistant = { nav.navigate(Routes.ASSISTANT) },
                    createEvent = { nav.navigate(Routes.CREATE_EVENT) },
                    openSettings = { nav.navigate(Routes.SETTINGS) },
                    needsProfile = { nav.navigateClearing(Routes.completeProfile()) },
                    needsVerification = { nav.navigateClearing(Routes.verifyEmail()) },
                ),
                conversationsVm = convVm,
                homeVm = homeVm,
            )
        }
        composable(Routes.MOMENT, arguments = listOf(navArgument("momentId") { type = NavType.StringType })) {
            MomentScreen(onBack = { nav.popBackStack() }, onOpenChat = { nav.navigate(Routes.chat(it)) })
        }
        composable(Routes.SESSION, arguments = listOf(navArgument("sessionId") { type = NavType.StringType })) {
            SessionScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.NEARBY) { entry ->
            val (homeVm, convVm) = shared(entry)
            val conv by convVm.state.collectAsStateWithLifecycle()
            NearbyScreen(homeVm, friendUidsOf(convVm, conv.chats), onBack = { nav.popBackStack() }) { nav.navigate(Routes.session(it.id)) }
        }
        composable(Routes.CIRCLES) { entry ->
            CirclesScreen(shared(entry).first, onBack = { nav.popBackStack() })
        }
        composable(Routes.CREATE_EVENT) { entry ->
            CreateEventScreen(shared(entry).first, onBack = { nav.popBackStack() }) { id ->
                nav.navigate(Routes.session(id)) { popUpTo(Routes.CREATE_EVENT) { inclusive = true } }
            }
        }
        composable(Routes.ASSISTANT) { entry ->
            AssistantScreen(
                vm = shared(entry).first,
                onBack = { nav.popBackStack() },
                onOpenSession = { nav.navigate(Routes.session(it.id)) },
                onCreateEvent = { nav.navigate(Routes.CREATE_EVENT) },
                onOpenExplore = { nav.popBackStack() },
                onNewChat = { nav.navigate(Routes.USERS) },
            )
        }
        composable(Routes.SETTINGS) { entry ->
            val convVm = shared(entry).second
            SettingsScreen(
                onSignOut = { convVm.signOut { nav.navigateClearing(Routes.SPLASH) } },
                onBack = { nav.popBackStack() },
            )
        }
        composable(Routes.USERS) {
            UserSearchScreen(
                onBack = { nav.popBackStack() },
                onOpenChat = { id -> nav.navigate(Routes.chat(id)) { popUpTo(Routes.USERS) { inclusive = true } } },
            )
        }
        composable(Routes.CHAT, arguments = listOf(navArgument("chatId") { type = NavType.StringType })) {
            ChatScreen(onBack = { nav.popBackStack() })
        }
    }
}
