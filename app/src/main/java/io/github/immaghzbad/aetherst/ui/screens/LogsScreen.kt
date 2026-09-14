package io.github.immaghzbad.aetherst.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.immaghzbad.aetherst.model.AetherLogLevel
import io.github.immaghzbad.aetherst.model.LogEntry
import io.github.immaghzbad.aetherst.model.LogLevel
import io.github.immaghzbad.aetherst.ui.AetherViewModel

private val IosCardBg = Color(0xFF1C1C1E)
private val IosSecondaryLabel = Color(0xFF8E8E93)
private val IosActiveBlue = Color(0xFF007AFF)
private val IosActiveGreen = Color(0xFF34C759)
private val IosWarnAmber = Color(0xFFFF9500)
private val IosErrorRed = Color(0xFFFF3B30)
private val IosDebugCyan = Color(0xFF64D2FF)

@Composable
fun LogsScreen(
    viewModel: AetherViewModel,
    onShowToast: (String, Boolean) -> Unit = { _, _ -> },
    bottomContentPadding: Dp = 0.dp
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        val screenWidth = this.maxWidth
        val scaleFactor = (screenWidth.value / 411f).coerceIn(0.7f, 1.1f)
        val horizontalPadding = if (screenWidth < 360.dp) 10.dp else 16.dp

        val context = LocalContext.current
        val config by viewModel.config.collectAsState()
        val logs by viewModel.logs.collectAsState()

        var selectedLevelFilter by remember { mutableStateOf<LogLevel?>(null) }
        var searchQuery by remember { mutableStateOf("") }
        var isSearchFocused by remember { mutableStateOf(false) }
        val listState = rememberLazyListState()

        val filteredLogs = remember(logs, selectedLevelFilter, searchQuery) {
            logs.filter { entry ->
                val levelMatches = selectedLevelFilter == null || entry.level == selectedLevelFilter
                val searchMatches = searchQuery.isEmpty() || entry.message.contains(searchQuery, ignoreCase = true) || entry.tag.contains(searchQuery, ignoreCase = true)
                levelMatches && searchMatches
            }
        }

        LaunchedEffect(filteredLogs.size) {
            if (filteredLogs.isNotEmpty()) {
                try {
                    listState.scrollToItem(filteredLogs.size - 1)
                } catch (_: Exception) {
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = horizontalPadding,
                    top = 0.dp,
                    end = horizontalPadding,
                    bottom = bottomContentPadding + 10.dp
                )
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(top = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "AetherST Logs",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = (26 * scaleFactor).sp
                    )
                    Text(
                        text = "Live Aether Core Logs",
                        style = MaterialTheme.typography.bodySmall,
                        color = IosSecondaryLabel,
                        fontSize = (11 * scaleFactor).sp
                    )
                }

                Row {
                    IconButton(
                        onClick = {
                            viewModel.copyLogs(context)
                            onShowToast("Logs copied to clipboard", false)
                        },
                        modifier = Modifier.size((40 * scaleFactor).dp).testTag("copy_logs_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy Logs",
                            tint = IosActiveBlue,
                            modifier = Modifier.size((20 * scaleFactor).dp)
                        )
                    }

                    IconButton(
                        onClick = { viewModel.clearLogs() },
                        modifier = Modifier.size((40 * scaleFactor).dp).testTag("clear_logs_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Clear Logs",
                            tint = IosErrorRed,
                            modifier = Modifier.size((20 * scaleFactor).dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height((10 * scaleFactor).dp))

            if (config.coreLogLevel == AetherLogLevel.OFF) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = IosCardBg),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier.padding((12 * scaleFactor).dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Info",
                            tint = IosActiveBlue,
                            modifier = Modifier.size((18 * scaleFactor).dp)
                        )
                        Spacer(modifier = Modifier.width((10 * scaleFactor).dp))
                        Text(
                            text = "Core logging is OFF to eliminate RAM overhead. Set Log Level in Settings to record engine events.",
                            style = MaterialTheme.typography.bodySmall,
                            color = IosSecondaryLabel,
                            fontSize = (10 * scaleFactor).sp,
                            lineHeight = (14 * scaleFactor).sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height((10 * scaleFactor).dp))
            }

            BasicTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height((44 * scaleFactor).dp)
                    .background(IosCardBg, RoundedCornerShape(10.dp))
                    .border(
                        width = 1.dp,
                        color = if (isSearchFocused) IosActiveBlue else Color.Transparent,
                        shape = RoundedCornerShape(10.dp)
                    )
                    .onFocusChanged { isSearchFocused = it.isFocused }
                    .testTag("search_logs_field"),
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White, fontSize = (14 * scaleFactor).sp),
                singleLine = true,
                cursorBrush = SolidColor(IosActiveBlue),
                decorationBox = { innerTextField ->
                    Row(
                        modifier = Modifier.padding(horizontal = (12 * scaleFactor).dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = IosSecondaryLabel,
                            modifier = Modifier.size((18 * scaleFactor).dp)
                        )
                        Spacer(modifier = Modifier.width((8 * scaleFactor).dp))
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (searchQuery.isEmpty()) {
                                Text("Search console logs...", color = IosSecondaryLabel, fontSize = (13 * scaleFactor).sp)
                            }
                            innerTextField()
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height((10 * scaleFactor).dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(IosCardBg)
                    .padding(2.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val filters = listOf(
                    "ALL" to null,
                    "INFO" to LogLevel.INFO,
                    "WARN" to LogLevel.WARN,
                    "ERROR" to LogLevel.ERROR,
                    "DEBUG" to LogLevel.DEBUG
                )

                filters.forEach { (label, level) ->
                    val selected = selectedLevelFilter == level
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) IosActiveBlue else Color.Transparent)
                            .clickable { selectedLevelFilter = level }
                            .padding(vertical = (8 * scaleFactor).dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) Color.White else IosSecondaryLabel,
                            fontSize = (9 * scaleFactor).sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height((10 * scaleFactor).dp))

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(14.dp),
                color = IosCardBg
            ) {
                if (filteredLogs.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        val msg = if (config.coreLogLevel == AetherLogLevel.OFF && config.appLogLevel == AetherLogLevel.OFF) {
                            "Logging disabled in Config"
                        } else {
                            "No log records found"
                        }
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodySmall,
                            color = IosSecondaryLabel,
                            fontSize = (11 * scaleFactor).sp
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding((10 * scaleFactor).dp)
                    ) {
                        items(
                        items = filteredLogs.distinctBy { it.id },
                        key = { it.id }
                    ) { entry ->
                        IosLogLineItem(entry = entry, onShowToast = onShowToast, scaleFactor = scaleFactor)
                    }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun IosLogLineItem(
    entry: LogEntry,
    onShowToast: (String, Boolean) -> Unit = { _, _ -> },
    scaleFactor: Float = 1f
) {
    val clipboardManager = LocalClipboardManager.current
    val levelColor = when (entry.level) {
        LogLevel.INFO -> IosActiveGreen
        LogLevel.WARN -> IosWarnAmber
        LogLevel.ERROR -> IosErrorRed
        LogLevel.DEBUG -> IosDebugCyan
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = (4 * scaleFactor).dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF141416))
            .combinedClickable(
                onClick = {},
                onLongClick = {
                    val logText = "[${entry.timestamp}] [${entry.level.name}] [${entry.tag}] ${entry.message}"
                    clipboardManager.setText(AnnotatedString(logText))
                    onShowToast("Log line copied to clipboard", false)
                }
            )
            .height(IntrinsicSize.Min)
    ) {
        Box(
            modifier = Modifier
                .width((4 * scaleFactor).dp)
                .fillMaxHeight()
                .background(levelColor)
        )

        Column(
            modifier = Modifier
                .padding(horizontal = (12 * scaleFactor).dp, vertical = (10 * scaleFactor).dp)
                .weight(1f)
        ) {
            Text(
                text = entry.message,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                color = Color(0xFFF2F2F7),
                fontSize = (12 * scaleFactor).sp
            )

            Spacer(modifier = Modifier.height((6 * scaleFactor).dp))

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "📅 ${entry.timestamp}",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = IosSecondaryLabel,
                    fontSize = (9 * scaleFactor).sp
                )

                Spacer(modifier = Modifier.width((10 * scaleFactor).dp))

                Text(
                    text = "•  ${entry.tag}",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = IosSecondaryLabel.copy(alpha = 0.7f),
                    fontSize = (9 * scaleFactor).sp
                )
            }
        }
    }
}
