package com.leong.jetchat.feature_chat.presentation

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
    val composerMode by vm.composerMode.collectAsState()
    val actionMenuFor by vm.actionMenuFor.collectAsState()
    val confirmDeleteFor by vm.confirmDeleteFor.collectAsState()

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

    // Delete confirm dialog
    if (confirmDeleteFor != null) {
        AlertDialog(
            onDismissRequest = vm::cancelDelete,
            title = { Text("Delete message?") },
            text = { Text("This will replace the message with \"Message deleted\".") },
            confirmButton = {
                TextButton(onClick = vm::confirmDelete) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = vm::cancelDelete) { Text("Cancel") }
            }
        )
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
                .consumeWindowInsets(padding) // Here we will consume the windowInsets. And hence there won't be any padding when keyboard is open.
                .imePadding(), // This is very important as well, otherwise the scroll won't work when the keyboard is shown
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
                            val canAct = m.isMine && !m.isDeleted

                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = if (m.isMine) Alignment.TopEnd else Alignment.TopStart
                            ) {
                                Box {
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        tonalElevation = 1.dp,
                                        modifier = Modifier
                                            .combinedClickable(
                                                onClick = { /* no-op */ },
                                                onLongClick = {
                                                    if (canAct) vm.openActions(m.id)
                                                }
                                            )
                                    ) {
                                        Column(
                                            Modifier.padding(
                                                horizontal = 12.dp,
                                                vertical = 8.dp
                                            )
                                        ) {
                                            Text(
                                                m.sender,
                                                style = MaterialTheme.typography.labelMedium
                                            )
                                            Spacer(Modifier.height(4.dp))

                                            if (m.isDeleted) {
                                                Text(
                                                    "Message deleted",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            } else {
                                                Text(m.text)
                                            }

                                            Spacer(Modifier.height(4.dp))

                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    m.time,
                                                    style = MaterialTheme.typography.labelSmall
                                                )
                                                if (!m.isDeleted && m.isEdited) {
                                                    Spacer(Modifier.width(8.dp))
                                                    Text(
                                                        "edited",
                                                        style = MaterialTheme.typography.labelSmall
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    DropdownMenu(
                                        expanded = actionMenuFor == m.id,
                                        onDismissRequest = vm::dismissActions
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Edit") },
                                            onClick = { vm.startEdit(m.id) }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Delete") },
                                            onClick = { vm.requestDelete(m.id) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider()

                    // Editing Banner
                    if (composerMode is ComposerMode.Editing) {
                        Surface(tonalElevation = 1.dp) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Editing message",
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = vm::cancelEdit) { Text("Cancel") }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        OutlinedTextField(
                            value = draft,
                            onValueChange = vm::onDraftChange,
                            placeholder = {
                                Text(
                                    if (composerMode is ComposerMode.Editing) "Edit message"
                                    else "Message"
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = SingleLineFieldMinHeight),
                            maxLines = 4,
                        )

                        Spacer(Modifier.width(8.dp))

                        IconButton(
                            onClick = vm::sendOrSave,
                            enabled = draft.isNotBlank(),
                            modifier = Modifier.heightIn(min = SingleLineFieldMinHeight)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = if (composerMode is ComposerMode.Editing) "Save" else "Send"
                            )
                        }
                    }
                }
            }
        }
    }
}
