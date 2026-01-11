package com.leong.jetchat.feature_profile.presentation

import android.content.ClipData
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    vm: ProfileViewModel = hiltViewModel()
) {
    val ui = vm.ui

    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboard.current
    val scroll = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile") },
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
                .padding(16.dp)
                .verticalScroll(scroll),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ListItem(
                        headlineContent = { Text("Email") },
                        supportingContent = {
                            Text(ui.email ?: "-")
                        },
                        trailingContent = {
                            IconButton(
                                onClick = {
                                    val text = ui.email ?: "-"
                                    val clipData = ClipData.newPlainText(text, text)
                                    scope.launch { clipboard.setClipEntry(clipData.toClipEntry()) }
                                },
                                enabled = ui.email != null
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy email")
                            }
                        }
                    )

                    ListItem(
                        headlineContent = { Text("UID") },
                        supportingContent = {
                            Text(ui.uid ?: "-")
                        },
                        trailingContent = {
                            IconButton(
                                onClick = {
                                    val text = ui.uid ?: "-"
                                    val clipData = ClipData.newPlainText(text, text)
                                    scope.launch { clipboard.setClipEntry(clipData.toClipEntry()) }
                                },
                                enabled = ui.uid != null
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy UID")
                            }
                        }
                    )

                }
            }

            Button(
                onClick = vm::signOut,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Sign out")
            }

            Text(
                "Tip: Share your email with friends so they can start a chat with you.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
