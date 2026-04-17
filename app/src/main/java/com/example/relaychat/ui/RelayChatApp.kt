package com.example.relaychat.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.relaychat.app.RelayChatViewModel
import com.example.relaychat.R
import com.example.relaychat.ui.chat.ChatScreen
import com.example.relaychat.ui.components.RelayChatBackdrop
import com.example.relaychat.ui.components.RelayGlassCard
import com.example.relaychat.ui.settings.SettingsScreen
import com.example.relaychat.ui.theme.RelayChatTheme

@Composable
fun RelayChatApp(
    viewModel: RelayChatViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val clipboardManager = LocalClipboardManager.current
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val imeVisible = WindowInsets.ime.asPaddingValues().calculateBottomPadding() > 0.dp

    RelayChatTheme(themeMode = uiState.settings.themeMode) {
        Box(modifier = Modifier.fillMaxSize()) {
            RelayChatBackdrop()

            Scaffold(
                containerColor = androidx.compose.ui.graphics.Color.Transparent,
                bottomBar = {
                    AnimatedVisibility(visible = !imeVisible) {
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
                                        label = { Text(stringResource(R.string.nav_chat)) },
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
                                        label = { Text(stringResource(R.string.nav_settings)) },
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
                    }
                },
            ) { innerPadding ->
                when (selectedTab) {
                    0 -> ChatScreen(
                        uiState = uiState,
                        viewModel = viewModel,
                        onCopyTranscript = { transcript ->
                            clipboardManager.setText(AnnotatedString(transcript))
                        },
                        modifier = Modifier
                            .padding(innerPadding)
                            .consumeWindowInsets(innerPadding),
                    )

                    else -> SettingsScreen(
                        uiState = uiState,
                        viewModel = viewModel,
                        modifier = Modifier
                            .padding(innerPadding)
                            .consumeWindowInsets(innerPadding),
                    )
                }
            }
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
                title = { Text(stringResource(R.string.error_request_failed_title)) },
                text = { Text(message) },
                confirmButton = {
                    TextButton(onClick = viewModel::dismissError) {
                        Text(stringResource(R.string.action_dismiss))
                    }
                },
            )
        }
    }
}
