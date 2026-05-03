package com.myapp.familycode.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.Delete
import com.myapp.familycode.ui.components.DeviceListSection
import com.myapp.familycode.ui.components.OtpListItem
import com.myapp.familycode.viewmodel.OtpViewModel
import com.myapp.familycode.viewmodel.ThemeMode
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: OtpViewModel = koinViewModel(),
    onSettingsClick: () -> Unit
) {
    val otpList    by viewModel.otpList.collectAsState()
    val isLoading  by viewModel.isLoading.collectAsState()
    val deviceCount by viewModel.deviceCount.collectAsState()
    val deviceList by viewModel.deviceList.collectAsState()
    val themeMode  by viewModel.themeMode.collectAsState()
    val error      by viewModel.error.collectAsState()
    val tick       by viewModel.tick.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val pullState = rememberPullToRefreshState()

    LaunchedEffect(error) {
        error?.let { 
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "FamilyCode",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black
                        )
                        AnimatedContent(
                            targetState = deviceCount,
                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                            label = "DeviceCount"
                        ) { count ->
                            Text(
                                text = if (count > 0) "$count device${if (count > 1) "s" else ""} linked"
                                       else "Setting up…",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    }
                },
                actions = {
                    // Theme toggle icon button
                    val themeIcon: ImageVector = when (themeMode) {
                        ThemeMode.LIGHT  -> Icons.Default.LightMode
                        ThemeMode.DARK   -> Icons.Default.DarkMode
                        ThemeMode.SYSTEM -> Icons.Default.WbSunny
                    }
                    IconButton(onClick = { viewModel.toggleTheme() }) {
                        Icon(
                            imageVector = themeIcon,
                            contentDescription = "Toggle Theme",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    // Settings / re-setup
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.refreshData() },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = RoundedCornerShape(24.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh Data")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->

        PullToRefreshBox(
            state = pullState,
            isRefreshing = isLoading,
            onRefresh = { viewModel.refreshData() },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (otpList.isEmpty() && !isLoading) {
                EmptyState(onRefresh = { viewModel.refreshData() })
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Device list section at the top
                item(key = "devices") {
                    DeviceListSection(
                        deviceList = deviceList,
                        deviceCount = deviceCount
                    )
                }

                // Section label
                if (otpList.isNotEmpty()) {
                    item(key = "otp_header") {
                        Text(
                            text = "Recent OTPs",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                        )
                    }
                }

                items(otpList, key = { it.timestamp }) { otp ->
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = { dismissValue ->
                            if (dismissValue == SwipeToDismissBoxValue.EndToStart || dismissValue == SwipeToDismissBoxValue.StartToEnd) {
                                viewModel.deleteOtp(otp.timestamp)
                                true
                            } else {
                                false
                            }
                        }
                    )

                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn() + slideInVertically { it / 2 }
                    ) {
                        SwipeToDismissBox(
                            state = dismissState,
                            backgroundContent = {
                                val color by animateColorAsState(
                                    when (dismissState.targetValue) {
                                        SwipeToDismissBoxValue.Settled -> MaterialTheme.colorScheme.surface
                                        else -> MaterialTheme.colorScheme.errorContainer
                                    }, label = "dismissColor"
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(24.dp))
                                        .background(color)
                                        .padding(horizontal = 20.dp),
                                    contentAlignment = Alignment.CenterEnd
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Delete OTP",
                                        tint = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        ) {
                            OtpListItem(otp, tick = tick)
                        }
                    }
                }

                // Bottom spacer so FAB doesn't overlap last item
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
private fun EmptyState(onRefresh: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Surface(
                modifier = Modifier.size(96.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Sms,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    )
                }
            }
            Text(
                "No OTPs Yet",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Text(
                "OTPs received on any linked family device will appear here. Pull down or tap refresh.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )
            OutlinedButton(
                onClick = onRefresh,
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Refresh", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
