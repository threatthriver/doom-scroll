package com.securemessage.app.ui.users

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.securemessage.app.ui.common.AppViewModelFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserSearchScreen(
    onBack: () -> Unit,
    onOpenChat: (String) -> Unit,
    vm: UserSearchViewModel = viewModel(factory = AppViewModelFactory.Factory),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.navigateToChatId) {
        state.navigateToChatId?.let {
            vm.navigationHandled()
            onOpenChat(it)
        }
    }
    LaunchedEffect(state.userMessage) {
        state.userMessage?.let {
            snackbar.showSnackbar(it)
            vm.userMessageShown()
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Find People") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                )
                OutlinedTextField(
                    value = state.query,
                    onValueChange = vm::onQueryChange,
                    placeholder = { Text("Username or email") },
                    label = { Text("Search") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val center = Modifier.align(Alignment.Center).padding(24.dp)
            when {
                state.isLoading -> CircularProgressIndicator(center)
                state.searchError != null -> Text(state.searchError!!, color = MaterialTheme.colorScheme.error, modifier = center)
                state.hasSearched && state.results.isEmpty() -> Text("No users found", modifier = center)
                !state.hasSearched -> Text("Search by username prefix or exact email.", modifier = center)
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(state.results, key = { it.uid }) { user ->
                        ListItem(
                            headlineContent = { Text(user.displayName) },
                            supportingContent = { Text("@${user.username}") },
                            modifier = Modifier.clickable { vm.onUserClick(user) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
