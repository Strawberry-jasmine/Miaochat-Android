package com.example.relaychat.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.relaychat.app.RelayChatViewModel
import com.example.relaychat.ui.chat.ChatScreen
import com.example.relaychat.ui.components.RelayChatBackdrop
import com.example.relaychat.ui.components.RelayGlassCard
import com.example.relaychat.ui.history.HistorySheet
import com.example.relaychat.ui.settings.SettingsScreen
import com.example.relaychat.ui.theme.RelayChatTheme

@Composable
fun RelayChatApp(
    viewModel: RelayChatViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val clipboardManager = LocalClipboardManager.current
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var historyVisible by rememberSaveable { mutableStateOf(false) }

    RelayChatTheme(themeMode = uiState.settings.themeMode) {
        Box(modifier = Modifier.fillMaxSize()) {
            RelayChatBackdrop()

            Scaffold(
                containerColor = androidx.compose.ui.graphics.Color.Transparent,
                bottomBar = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        RelayGlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            accent = if (selectedTab == 0) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.secondary
                            },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                horizontal = 10.dp,
                                vertical = 8.dp,
                            ),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                NavigationBarItem(
                                    selected = selectedTab == 0,
                                    onClick = { selectedTab = 0 },
                                    icon = { Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = null) },
                                    label = { Text("Chat") },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    ),
                                )
                                NavigationBarItem(
                                    selected = selectedTab == 1,
                                    onClick = { selectedTab = 1 },
                                    icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                                    label = { Text("Settings") },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    ),
                                )
                            }
                        }
                    }
                },
            ) { innerPadding ->
                when (selectedTab) {
                    0 -> ChatScreen(
                        uiState = uiState,
                        viewModel = viewModel,
                        onShowHistory = { historyVisible = true },
                        onCopyTranscript = { transcript ->
                            clipboardManager.setText(AnnotatedString(transcript))
                        },
                        modifier = Modifier.padding(innerPadding),
                    )

                    else -> SettingsScreen(
                        uiState = uiState,
                        viewModel = viewModel,
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }

        if (historyVisible) {
            HistorySheet(
                uiState = uiState,
                viewModel = viewModel,
                onDismiss = { historyVisible = false },
            )
        }

        uiState.errorMessage?.let { message ->
            AlertDialog(
                onDismissRequest = viewModel::dismissError,
                icon = {
                    Icon(
                        imageVector = Icons.Outlined.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                },
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                shape = RoundedCornerShape(28.dp),
                title = { Text("Request didn't finish") },
                text = { Text(message) },
                confirmButton = {
                    TextButton(onClick = viewModel::dismissError) {
                        Text("Dismiss")
                    }
                },
            )
        }
    }
}
