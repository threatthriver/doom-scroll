package com.securemessage.app.ui.conversations

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.securemessage.app.ui.common.AppViewModelFactory
import com.securemessage.app.ui.common.formatTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationsScreen(
    onNewChat: () -> Unit,
    onOpenChat: (String) -> Unit,
    onNeedsProfile: () -> Unit,
    onSignedOut: () -> Unit,
    vm: ConversationsViewModel = viewModel(factory = AppViewModelFactory.Factory),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.needsProfile) { if (state.needsProfile) onNeedsProfile() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chats") },
                actions = { TextButton(onClick = { vm.signOut(); onSignedOut() }) { Text("Sign out") } },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewChat,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("New chat") },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.error != null -> Text(
                    state.error!!,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
                state.chats.isEmpty() -> Text(
                    "No conversations yet. Tap New chat to find people.",
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(state.chats, key = { it.id }) { chat ->
                        ListItem(
                            headlineContent = { Text(vm.titleFor(chat)) },
                            supportingContent = {
                                Text(chat.lastMessage, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            trailingContent = { Text(formatTime(chat.lastMessageAt)) },
                            modifier = Modifier.clickable { onOpenChat(chat.id) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
