package io.github.immaghzbad.aetherst.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import java.io.File

@Composable
fun CrashReportScreen(
    crashLog: String,
    onRestart: () -> Unit,
    onShowToast: (String) -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    
    val manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
    val model = Build.MODEL
    val deviceName = if (model.startsWith(manufacturer, ignoreCase = true)) {
        model.replaceFirstChar { it.uppercase() }
    } else {
        "$manufacturer $model"
    }
    
    val systemInfo = """
        Device: $deviceName
        Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})
        App Version: 1.4.2 (Code 1)
    """.trimIndent()
    
    val fullReport = "$systemInfo\n\n--- Crash Log ---\n$crashLog"

    fun shareLogFile() {
        try {
            val file = File(context.cacheDir, "AetherST_Crash_Report.txt")
            file.writeText(fullReport)
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "AetherST Crash Report")
                putExtra(Intent.EXTRA_TEXT, "Please find the attached crash report for AetherST.")
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share Crash Report"))
        } catch (_: Exception) {
            onShowToast("Failed to generate share file")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(20.dp)
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier.size(64.dp),
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFFFF3B30).copy(alpha = 0.12f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.WarningAmber,
                        contentDescription = null,
                        tint = Color(0xFFFF3B30),
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "System Interruption",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
            )
            Text(
                text = "AetherST recovered from a critical exception",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF8E8E93),
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "DIAGNOSTICS",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF8E8E93),
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                InfoRow(
                    label = "Device Info", 
                    value = deviceName,
                    onCopy = {
                        clipboardManager.setText(AnnotatedString(deviceName))
                        onShowToast("Device info copied")
                    }
                )
                HorizontalDivider(color = Color(0xFF2C2C2E), modifier = Modifier.padding(vertical = 10.dp))
                InfoRow(label = "System Version", value = "Android ${Build.VERSION.RELEASE}")
                HorizontalDivider(color = Color(0xFF2C2C2E), modifier = Modifier.padding(vertical = 10.dp))
                InfoRow(label = "AetherST Build", value = "1.4.2 (1)")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "STACK TRACE",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF8E8E93),
                letterSpacing = 1.sp
            )
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    onClick = { shareLogFile() },
                    shape = RoundedCornerShape(100.dp),
                    color = Color(0xFF34C759).copy(alpha = 0.12f),
                    contentColor = Color(0xFF34C759)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Share, null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Share Log", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Surface(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(fullReport))
                        onShowToast("Full report copied")
                    },
                    shape = RoundedCornerShape(100.dp),
                    color = Color(0xFF007AFF).copy(alpha = 0.12f),
                    contentColor = Color(0xFF007AFF)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copy Full", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.4f))
                    .padding(12.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = crashLog,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 10.sp,
                    lineHeight = 16.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onRestart,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF007AFF),
                contentColor = Color.White
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
        ) {
            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text("Clear & Restart App", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        Spacer(modifier = Modifier.navigationBarsPadding())
    }
}

@Composable
private fun InfoRow(label: String, value: String, onCopy: (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = Color(0xFF8E8E93), fontSize = 13.sp)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable(enabled = onCopy != null) { onCopy?.invoke() }
        ) {
            Text(text = value, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            if (onCopy != null) {
                Spacer(modifier = Modifier.width(6.dp))
                Icon(Icons.Default.ContentCopy, null, tint = Color(0xFF007AFF).copy(alpha = 0.6f), modifier = Modifier.size(12.dp))
            }
        }
    }
}
