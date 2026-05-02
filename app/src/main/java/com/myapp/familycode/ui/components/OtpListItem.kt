package com.myapp.familycode.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun OtpListItem(otpItem: OtpItem) {
    val context = LocalContext.current
    
    Surface(
        onClick = { copyToClipboard(context, otpItem.otpCode) },
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                shadowElevation = 2f
                shape = RoundedCornerShape(24.dp)
                clip = true
            }
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Sms, 
                    null, 
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        otpItem.bankName, 
                        fontWeight = FontWeight.ExtraBold, 
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    otpItem.deviceName?.let { name ->
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                name.uppercase(),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Text(
                    formatTimestamp(otpItem.timestamp), 
                    style = MaterialTheme.typography.labelSmall, 
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    letterSpacing = 1.sp
                )
            }
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    otpItem.otpCode,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
                    modifier = Modifier.padding(end = 8.dp)
                )
                
                IconButton(onClick = { copyToClipboard(context, otpItem.otpCode) }) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "Copy OTP",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("OTP Code", text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "OTP Copied!", Toast.LENGTH_SHORT).show()
}

private fun formatTimestamp(timestamp: String): String {
    return try {
        // GAS new Date().toISOString() returns format: 2026-05-02T22:30:10.000Z
        val zdt = ZonedDateTime.parse(timestamp)
        // Convert to IST (Asia/Kolkata)
        val istZone = java.time.ZoneId.of("Asia/Kolkata")
        val istDateTime = zdt.withZoneSameInstant(istZone)
        
        val formatter = DateTimeFormatter.ofPattern("dd MMM, hh:mm a", Locale.getDefault())
        istDateTime.format(formatter)
    } catch (e: Exception) {
        timestamp
    }
}
