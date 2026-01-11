package com.leong.jetchat.feature_chat.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel

private val SingleLineFieldMinHeight = 56.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onBack: () -> Unit,
    vm: ChatViewModel = hiltViewModel()
) {
    val draft by vm.draft.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    val error by vm.error.collectAsState()
    val messages by vm.messagesUi.collectAsState()

    val listState = rememberLazyListState()

    // In reverseLayout=true, bottom == item index 0.
    // "At bottom" should mean: item 0 is visible (not necessarily the first visible item).
    val isAtBottom by remember(listState) {
        derivedStateOf {
            listState.layoutInfo.visibleItemsInfo.any { it.index == 0 }
        }
    }

    // Track newest message id (changes when a new message arrives)
    val newestId = messages.lastOrNull()?.id
    val newestIsMine = messages.lastOrNull()?.isMine == true

    // Scroll to bottom when new messages arrive, but only if user is already at bottom
    LaunchedEffect(newestId) {
        if (newestId == null) return@LaunchedEffect

        // Scroll if user is at bottom OR the newest message is mine (I just sent)
        if (isAtBottom || newestIsMine) {
            listState.animateScrollToItem(0)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chat") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(error!!, color = MaterialTheme.colorScheme.error)
                }
                else -> {
                    val reversedMessages = messages.asReversed()

                    LazyColumn(
                        state = listState,
                        reverseLayout = true,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(
                            items = reversedMessages,
                            key = { it.id }
                        ) { m ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = if (m.isMine) Arrangement.End else Arrangement.Start
                            ) {
                                Surface(shape = RoundedCornerShape(16.dp), tonalElevation = 1.dp) {
                                    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                        Text(m.sender, style = MaterialTheme.typography.labelMedium)
                                        Spacer(Modifier.height(4.dp))
                                        Text(m.text)
                                        Spacer(Modifier.height(4.dp))
                                        Text(m.time, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider()

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .imePadding()
                            .padding(8.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        OutlinedTextField(
                            value = draft,
                            onValueChange = vm::onDraftChange,
                            placeholder = { Text("Message") },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = SingleLineFieldMinHeight),
                            maxLines = 4,
                        )
                        Spacer(Modifier.width(8.dp))
                        IconButton(
                            onClick = vm::send,
                            enabled = draft.isNotBlank(),
                            modifier = Modifier
                                .heightIn(min = SingleLineFieldMinHeight)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                        }
                    }
                }
            }
        }
    }
}
