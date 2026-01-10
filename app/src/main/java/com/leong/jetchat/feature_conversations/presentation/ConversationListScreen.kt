package com.leong.jetchat.feature_conversations.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel

data class ConversationUi(
    val id: String,
    val title: String,
    val lastMessage: String,
    val time: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationListScreen(
    onOpenChat: (String) -> Unit,
    onNewChat: () -> Unit,
    onOpenProfile: () -> Unit,
    onSignOut: () -> Unit,
    vm: ConversationListViewModel = hiltViewModel()
) {
    val ui by vm.ui.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chats") },
                actions = {
                    IconButton(onClick = onOpenProfile) {
                        Icon(Icons.Default.Person, contentDescription = "Profile")
                    }
                    TextButton(onClick = onSignOut) { Text("Sign out") }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNewChat) {
                Icon(Icons.Default.Add, contentDescription = "New chat")
            }
        }
    ) { padding ->
        when {
            ui.isLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            ui.error != null -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                Text(ui.error!!, color = MaterialTheme.colorScheme.error)
            }
            ui.items.isEmpty() -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("No chats yet. Tap + to start a new one.")
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding)
            ) {
                items(ui.items.size) { idx ->
                    val c = ui.items[idx]
                    ListItem(
                        headlineContent = { Text(c.title) },
                        supportingContent = {
                            Text(c.last, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        trailingContent = { Text(c.time, maxLines = 1) },
                        modifier = Modifier.clickable { onOpenChat(c.id) }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}