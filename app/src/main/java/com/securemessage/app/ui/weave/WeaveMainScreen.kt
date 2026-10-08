package com.securemessage.app.ui.weave

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.securemessage.app.data.weave.Moment
import com.securemessage.app.data.weave.Session
import com.securemessage.app.ui.conversations.ConversationsViewModel

/** Navigation callbacks out of the tab shell. */
class WeaveNav(
    val openChat: (String) -> Unit,
    val newChat: () -> Unit,
    val openMoment: (Moment) -> Unit,
    val openSession: (String) -> Unit,
    val openNearby: () -> Unit,
    val openCircles: () -> Unit,
    val openAssistant: () -> Unit,
    val createEvent: () -> Unit,
    val openSettings: () -> Unit,
    val needsProfile: () -> Unit,
    val needsVerification: () -> Unit,
)

/** People you chat with, for Home's "Your circle" row. */
fun peopleOf(vm: ConversationsViewModel, chats: List<com.securemessage.app.data.model.Chat>): List<PersonChip> =
    chats.filterNot { vm.isArchived(it) }.map { PersonChip(it.id, vm.titleFor(it)) }

fun friendUidsOf(vm: ConversationsViewModel, chats: List<com.securemessage.app.data.model.Chat>): Set<String> =
    chats.flatMap { it.participants }.filter { it != vm.myUid }.toSet()

/** WEAVE shell: tabs Home · Explore · + · Chats · Me. */
@Composable
fun WeaveMainScreen(nav: WeaveNav, conversationsVm: ConversationsViewModel, homeVm: HomeViewModel) {
    var tab by rememberSaveable { mutableStateOf(WeaveTab.HOME) }
    var sheet by remember { mutableStateOf<CreateChoice?>(null) }
    var chooser by remember { mutableStateOf(false) }
    val ctx = LocalContext.current

    val convState by conversationsVm.state.collectAsStateWithLifecycle()
    val homeState by homeVm.state.collectAsStateWithLifecycle()
    LaunchedEffect(homeState.message) {
        homeState.message?.let { Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show(); homeVm.messageShown() }
    }
    val unread = remember(convState.chats) {
        val uid = conversationsVm.myUid ?: return@remember 0
        convState.chats.filterNot { it.archived[uid] == true }.sumOf { it.unreadCount[uid] ?: 0 }
    }

    WeaveBackground {
        Column(Modifier.fillMaxSize()) {
            val holder = rememberSaveableStateHolder()
            AnimatedContent(
                targetState = tab,
                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) },
                label = "weave_tab",
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) { current ->
                holder.SaveableStateProvider(current.name) {
                    Box(Modifier.fillMaxSize().then(if (current == WeaveTab.ME) Modifier else Modifier.statusBarsPadding())) {
                        when (current) {
                            WeaveTab.HOME -> WeaveHomeScreen(
                                vm = homeVm,
                                people = peopleOf(conversationsVm, convState.chats),
                                onOpenSession = { s: Session -> nav.openSession(s.id) },
                                onOpenMoment = nav.openMoment,
                                onOpenChat = nav.openChat,
                                onOpenCircles = nav.openCircles,
                                onOpenNearby = nav.openNearby,
                                onOpenAssistant = nav.openAssistant,
                            )
                            WeaveTab.EXPLORE -> ExploreScreen(homeVm)
                            WeaveTab.MESSAGES -> MessagesScreen(
                                vm = conversationsVm,
                                circles = homeState.circles,
                                onOpenChat = nav.openChat,
                                onNewChat = nav.newChat,
                                onOpenCircles = nav.openCircles,
                                onNeedsProfile = nav.needsProfile,
                                onNeedsVerification = nav.needsVerification,
                            )
                            WeaveTab.ME -> WeaveProfileScreen(
                                vm = homeVm,
                                peopleCount = friendUidsOf(conversationsVm, convState.chats).size,
                                onOpenSettings = nav.openSettings,
                                onOpenCircles = nav.openCircles,
                            )
                        }
                    }
                }
            }
            WeaveNavBar(selected = tab, onSelect = { tab = it }, onCompose = { chooser = true }, badgeCount = unread)
        }
    }

    if (chooser) {
        CreateSheet(onDismiss = { chooser = false }) { choice ->
            chooser = false
            if (choice == CreateChoice.EVENT) nav.createEvent() else sheet = choice
        }
    }
    when (sheet) {
        CreateChoice.MOMENT -> MomentComposer(
            isPosting = homeState.posting,
            onDismiss = { sheet = null },
            onPost = { body, intent -> homeVm.postMoment(body, intent) { ok -> if (ok) sheet = null } },
        )
        CreateChoice.SESSION -> StartSessionSheet(homeState.posting, onDismiss = { sheet = null }) { title, kind, topics ->
            homeVm.startSession(title, kind, topics) { id -> if (id != null) { sheet = null; nav.openSession(id) } }
        }
        else -> Unit
    }
}
