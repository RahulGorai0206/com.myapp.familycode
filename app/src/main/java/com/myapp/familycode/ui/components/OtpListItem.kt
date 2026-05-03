package com.myapp.familycode.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myapp.familycode.data.model.OtpItem
import java.time.ZonedDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val OTP_EXPIRY_MS = 5 * 60 * 1000L // 5 minutes

@Composable
fun OtpListItem(
    otpItem: OtpItem,
    tick: Long = 0L   // Passed from ViewModel ticker so recomposition fires every second
) {
    val context = LocalContext.current

    val ageMs = remember(otpItem.timestamp, tick) { getAgeMs(otpItem.timestamp) }
    val isExpired = ageMs >= OTP_EXPIRY_MS
    val remainingMs = (OTP_EXPIRY_MS - ageMs).coerceAtLeast(0)

    // Pulse animation for fresh OTPs (< 60 s old)
    val isBrandNew = ageMs < 60_000L
    val pulseAlpha by rememberInfiniteTransition(label = "Pulse").animateFloat(
        initialValue = 0.08f,
        targetValue = if (isBrandNew) 0.22f else 0.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseAlpha"
    )

    val containerColor by animateColorAsState(
        targetValue = if (isExpired)
            MaterialTheme.colorScheme.surfaceContainerLow
        else
            MaterialTheme.colorScheme.surfaceContainer,
        label = "ContainerColor"
    )

    var showDialog by remember { mutableStateOf(false) }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = {
                Text(
                    text = otpItem.bankName,
                    fontWeight = FontWeight.ExtraBold,
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "OTP CODE",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = otpItem.otpCode,
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 4.sp
                            )
                        }
                    }

                    Text(
                        text = otpItem.fullMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 22.sp
                    )

                    Text(
                        text = "Received at: ${formatTimestamp(otpItem.timestamp)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        copyToClipboard(context, otpItem.otpCode)
                        showDialog = false
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Copy OTP")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Close")
                }
            },
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    }

    Surface(
        onClick = { showDialog = true },
        color = containerColor,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                shadowElevation = if (isExpired) 0f else 4f
                shape = RoundedCornerShape(24.dp)
                clip = true
            }
    ) {
        // Glow overlay for fresh OTPs
        if (!isExpired) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(88.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = pulseAlpha))
            )
        }

        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon container
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (isExpired)
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                        else
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Sms,
                    contentDescription = null,
                    tint = if (isExpired)
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    else
                        MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                // Bank name + device badge
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = otpItem.bankName,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isExpired)
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        else
                            MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    otpItem.deviceName?.let { name ->
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(
                                alpha = if (isExpired) 0.15f else 0.35f
                            ),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                name.uppercase(),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                                color = if (isExpired)
                                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)
                                else
                                    MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(3.dp))

                // Timestamp row
                Text(
                    text = formatTimestamp(otpItem.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                        alpha = if (isExpired) 0.4f else 0.65f
                    ),
                    letterSpacing = 0.5.sp
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(horizontalAlignment = Alignment.End) {
                // OTP code or Expired badge
                if (isExpired) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            "EXPIRED",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            letterSpacing = 1.sp
                        )
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = otpItem.otpCode,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp),
                            letterSpacing = 2.sp
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(
                            onClick = { copyToClipboard(context, otpItem.otpCode) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = "Copy OTP",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Countdown timer
                    val countdownText = formatCountdown(remainingMs)
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = countdownText,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun formatCountdown(remainingMs: Long): String {
    val totalSeconds = (remainingMs / 1000).toInt()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return if (minutes > 0) "${minutes}m ${seconds}s" else "${seconds}s"
}

private fun getAgeMs(timestamp: String): Long {
    return try {
        val zdt = ZonedDateTime.parse(timestamp)
        System.currentTimeMillis() - zdt.toInstant().toEpochMilli()
    } catch (e: Exception) { Long.MAX_VALUE }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("OTP Code", text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "OTP Copied!", Toast.LENGTH_SHORT).show()
}

private fun formatTimestamp(timestamp: String): String {
    return try {
        val zdt = ZonedDateTime.parse(timestamp)
        val ist = zdt.withZoneSameInstant(ZoneId.of("Asia/Kolkata"))
        val formatter = DateTimeFormatter.ofPattern("dd MMM, hh:mm a", Locale.getDefault())
        ist.format(formatter)
    } catch (e: Exception) { timestamp }
}
