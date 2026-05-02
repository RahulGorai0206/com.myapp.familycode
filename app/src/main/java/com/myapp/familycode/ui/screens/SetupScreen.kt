package com.myapp.familycode.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myapp.familycode.R
import com.myapp.familycode.viewmodel.OtpViewModel
import kotlinx.coroutines.launch

@Composable
fun SetupScreen(viewModel: OtpViewModel, onSetupComplete: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var deviceName by remember { mutableStateOf("") }
    var sheetUrl by remember { mutableStateOf("") }
    var scriptUrl by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }
    var currentStep by remember { mutableIntStateOf(0) }
    var isTestingConnection by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AnimatedContent(
                targetState = currentStep,
                transitionSpec = {
                    if (targetState > initialState) {
                        slideInHorizontally { it } + fadeIn() togetherWith slideOutHorizontally { -it } + fadeOut()
                    } else {
                        slideInHorizontally { -it } + fadeIn() togetherWith slideOutHorizontally { it } + fadeOut()
                    }
                },
                label = "SetupTransition",
                modifier = Modifier.weight(1f)
            ) { step ->
                when (step) {
                    0 -> WelcomeStep()
                    1 -> FeaturesStep()
                    2 -> DeviceNameStep(
                        value = deviceName,
                        isError = showError && deviceName.isEmpty(),
                        onValueChange = { 
                            deviceName = it
                            if (it.isNotEmpty()) showError = false
                        }
                    )
                    3 -> CloudSyncStep(
                        sheetUrl = sheetUrl,
                        onSheetUrlChange = { sheetUrl = it },
                        scriptUrl = scriptUrl,
                        onUrlChange = { scriptUrl = it },
                        apiKey = apiKey,
                        onKeyChange = { apiKey = it }
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Progress Indicators
            Row(
                modifier = Modifier.padding(bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                repeat(4) { index ->
                    val width by animateDpAsState(
                        targetValue = if (currentStep == index) 24.dp else 8.dp,
                        label = "DotWidth"
                    )
                    val color by animateColorAsState(
                        targetValue = if (currentStep == index) MaterialTheme.colorScheme.primary 
                                     else MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                        label = "DotColor"
                    )
                    Box(
                        modifier = Modifier
                            .size(width, 8.dp)
                            .clip(CircleShape)
                            .background(color)
                    )
                }
            }

            Button(
                onClick = {
                    if (isTestingConnection) return@Button
                    when (currentStep) {
                        0 -> currentStep = 1
                        1 -> currentStep = 2
                        2 -> {
                            if (deviceName.isEmpty()) {
                                showError = true
                            } else {
                                showError = false
                                currentStep = 3
                            }
                        }
                        3 -> {
                            if (scriptUrl.isBlank() || apiKey.isBlank()) {
                                Toast.makeText(context, "Please fill all cloud fields", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            
                            scope.launch {
                                isTestingConnection = true
                                val error = viewModel.testConnection(scriptUrl, apiKey)
                                if (error == null) {
                                    viewModel.saveSetup(scriptUrl, apiKey, deviceName) {
                                        onSetupComplete()
                                    }
                                } else {
                                    isTestingConnection = false
                                    Toast.makeText(context, error.toString(), Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                AnimatedContent(
                    targetState = isTestingConnection,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "ButtonContent"
                ) { testing ->
                    if (testing) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Checking connection...", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = when (currentStep) {
                                    0 -> stringResource(R.string.setup_get_started)
                                    1, 2 -> stringResource(R.string.setup_continue)
                                    else -> stringResource(R.string.setup_finish)
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WelcomeStep() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.verticalScroll(rememberScrollState())
    ) {
        Surface(
            modifier = Modifier.size(120.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.FamilyRestroom,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = stringResource(R.string.setup_welcome_title),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.setup_welcome_description),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            lineHeight = 24.sp
        )
    }
}

@Composable
fun DeviceNameStep(value: String, isError: Boolean, onValueChange: (String) -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.verticalScroll(rememberScrollState())
    ) {
        Text(
            stringResource(R.string.setup_device_name_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            stringResource(R.string.setup_device_name_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(48.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(stringResource(R.string.setup_device_name_label)) },
            placeholder = { Text(stringResource(R.string.setup_device_name_placeholder)) },
            isError = isError,
            supportingText = {
                if (isError) {
                    Text(stringResource(R.string.setup_device_name_error), color = MaterialTheme.colorScheme.error)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            textStyle = LocalTextStyle.current.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold)
        )
    }
}

@Composable
fun FeaturesStep() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.verticalScroll(rememberScrollState())
    ) {
        Text(
            stringResource(R.string.setup_features_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(32.dp))
        
        FeatureItem(
            icon = Icons.Default.Sms,
            title = stringResource(R.string.setup_feature_sms_title),
            description = stringResource(R.string.setup_feature_sms_desc)
        )
        Spacer(modifier = Modifier.height(24.dp))
        FeatureItem(
            icon = Icons.Default.Devices,
            title = stringResource(R.string.setup_feature_insights_title),
            description = stringResource(R.string.setup_feature_insights_desc)
        )
        Spacer(modifier = Modifier.height(24.dp))
        FeatureItem(
            icon = Icons.Default.CloudSync,
            title = stringResource(R.string.setup_feature_cloud_title),
            description = stringResource(R.string.setup_feature_cloud_desc)
        )
    }
}

@Composable
fun CloudSyncStep(
    sheetUrl: String,
    onSheetUrlChange: (String) -> Unit,
    scriptUrl: String,
    onUrlChange: (String) -> Unit,
    apiKey: String,
    onKeyChange: (String) -> Unit
) {
    val context = LocalContext.current
    val extractedSheetId = remember(sheetUrl) {
        val pattern = "/spreadsheets/d/([a-zA-Z0-9-_]+)".toRegex()
        pattern.find(sheetUrl)?.groupValues?.get(1) ?: "YOUR_SHEET_ID_HERE"
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.verticalScroll(rememberScrollState())
    ) {
        Surface(
            modifier = Modifier.size(80.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.CloudSync,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.secondary
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            stringResource(R.string.setup_cloud_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(12.dp))
        
        // Instructions Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    stringResource(R.string.setup_instructions_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                listOf(
                    R.string.setup_step_1, R.string.setup_step_2, R.string.setup_step_3,
                    R.string.setup_step_4, R.string.setup_step_5, R.string.setup_step_6,
                    R.string.setup_step_9, R.string.setup_step_10, R.string.setup_step_11
                ).forEach { stepRes ->
                    Text(
                        stringResource(stepRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        OutlinedTextField(
            value = sheetUrl,
            onValueChange = onSheetUrlChange,
            label = { Text(stringResource(R.string.setup_sheet_url_label)) },
            placeholder = { Text(stringResource(R.string.setup_sheet_url_placeholder)) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            singleLine = true
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(
            onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Apps Script Code", "/* Copy Code.gs from project root */")
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, "Instructions copied", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.ContentCopy, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.setup_copy_code))
        }

        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedTextField(
            value = apiKey,
            onValueChange = onKeyChange,
            label = { Text(stringResource(R.string.setup_cloud_key_label)) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            trailingIcon = {
                TextButton(onClick = { 
                    val charPool = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
                    val randomKey = (1..32).map { charPool.random() }.joinToString("")
                    onKeyChange(randomKey)
                }) {
                    Text(stringResource(R.string.setup_cloud_generate_key))
                }
            },
            singleLine = true
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedTextField(
            value = scriptUrl,
            onValueChange = onUrlChange,
            label = { Text(stringResource(R.string.setup_cloud_url_label)) },
            placeholder = { Text(stringResource(R.string.setup_cloud_url_placeholder)) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            singleLine = true
        )
    }
}

@Composable
fun FeatureItem(icon: ImageVector, title: String, description: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(48.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
