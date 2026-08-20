package io.github.immaghzbad.aetherst.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.immaghzbad.aetherst.data.IpInfo
import io.github.immaghzbad.aetherst.data.PingState
import io.github.immaghzbad.aetherst.model.*
import kotlinx.coroutines.launch
import java.util.Locale

private val IosCardBg = Color(0xFF1C1C1E)
private val IosGroupBg = Color(0xFF2C2C2E)
private val IosSecondaryLabel = Color(0xFF8E8E93)
private val IosActiveGreen = Color(0xFF34C759)
private val IosActiveBlue = Color(0xFF007AFF)
private val IosScanningAmber = Color(0xFFFF9500)
private val IosErrorRed = Color(0xFFFF3B30)

@Composable
fun DashboardScreen(
    config: AetherConfig,
    connectionStatus: ConnectionStatus,
    elapsedSeconds: Long,
    sessionTraffic: SessionTraffic,
    ipInfo: IpInfo = IpInfo(),
    pingState: PingState = PingState(),
    onToggleVpn: () -> Unit,
    onUpdateProtocol: (AetherProtocol) -> Unit,
    onRefreshIpInfo: () -> Unit = {},
    onRefreshPing: () -> Unit = {},
    onShowToast: (String, Boolean) -> Unit = { _, _ -> },
    bottomContentPadding: Dp = 0.dp
) {
    var showProxyOverlay by remember { mutableStateOf(true) }

    LaunchedEffect(connectionStatus) {
        if (connectionStatus != ConnectionStatus.RUNNING) {
            showProxyOverlay = true
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val screenWidth = this.maxWidth
        val screenHeight = this.maxHeight
        val scaleFactor = (screenWidth.value / 411f).coerceIn(0.7f, 1.1f)
        val isCompactHeight = screenHeight < 640.dp
        val isVeryCompactHeight = screenHeight < 580.dp
        val horizontalPadding = if (screenWidth < 360.dp) 12.dp else 16.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = horizontalPadding,
                    end = horizontalPadding,
                    bottom = bottomContentPadding + 12.dp
                ),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.statusBarsPadding().padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy((14 * scaleFactor).dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column {
                        Text(
                            text = "AetherST Tunnel",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = (26 * scaleFactor).sp,
                            lineHeight = (30 * scaleFactor).sp
                        )
                        Text(
                            text = if (config.connectionMode == ConnectionMode.TUNNEL) "Secure & Private Tunneling" else "High-Performance Local Proxy",
                            style = MaterialTheme.typography.bodySmall,
                            color = IosSecondaryLabel,
                            fontSize = (12 * scaleFactor).sp,
                            lineHeight = (16 * scaleFactor).sp
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (config.connectionMode == ConnectionMode.PROXY_ONLY && connectionStatus == ConnectionStatus.RUNNING) {
                            IconButton(
                                onClick = { showProxyOverlay = true },
                                modifier = Modifier.size((32 * scaleFactor).dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "Proxy Info",
                                    tint = IosActiveBlue,
                                    modifier = Modifier.size((22 * scaleFactor).dp)
                                )
                            }
                            Spacer(modifier = Modifier.width((8 * scaleFactor).dp))
                        }
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = IosGroupBg
                        ) {
                            Text(
                                text = "v1.4.2",
                                modifier = Modifier.padding(horizontal = (12 * scaleFactor).dp, vertical = (6 * scaleFactor).dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = IosActiveBlue,
                                fontSize = (10 * scaleFactor).sp
                            )
                        }
                    }
                }

                IosStatusHeroCard(
                    connectionStatus = connectionStatus,
                    elapsedSeconds = elapsedSeconds,
                    sessionTraffic = sessionTraffic,
                    config = config,
                    ipInfo = ipInfo,
                    pingState = pingState,
                    onRefreshIpInfo = onRefreshIpInfo,
                    onRefreshPing = onRefreshPing,
                    onShowToast = onShowToast,
                    hideConfigChips = isCompactHeight,
                    scaleFactor = scaleFactor
                )

                if (!isVeryCompactHeight && connectionStatus == ConnectionStatus.ERROR) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = IosErrorRed.copy(alpha = 0.1f))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Refresh, null, tint = IosErrorRed, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Connection failed. Please try reconnecting.",
                                color = IosErrorRed,
                                fontSize = (11 * scaleFactor).sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                val minDim = if (screenWidth < screenHeight) screenWidth else screenHeight
                val buttonSize = (minDim * 0.35f).coerceIn(100.dp, 160.dp)
                
                IosPowerButton(
                    connectionStatus = connectionStatus,
                    onToggle = onToggleVpn,
                    size = buttonSize
                )
            }

            if (!isVeryCompactHeight) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp)
                ) {
                    IosProtocolSegmentedControl(
                        selectedProtocol = config.protocol,
                        onProtocolSelected = onUpdateProtocol,
                        enabled = connectionStatus == ConnectionStatus.STOPPED || connectionStatus == ConnectionStatus.ERROR,
                        scaleFactor = scaleFactor
                    )
                }
            }
        }

        val offsetY = remember { Animatable(0f) }
        val scope = rememberCoroutineScope()

        LaunchedEffect(showProxyOverlay) {
            if (showProxyOverlay) {
                offsetY.snapTo(0f)
            }
        }

        AnimatedVisibility(
            visible = config.connectionMode == ConnectionMode.PROXY_ONLY && connectionStatus == ConnectionStatus.RUNNING && showProxyOverlay,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 12.dp)
                .graphicsLayer { translationY = offsetY.value }
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                if (offsetY.value < -100f) {
                                    showProxyOverlay = false
                                } else {
                                    offsetY.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                                }
                            }
                        },
                        onVerticalDrag = { _, dragAmount ->
                            scope.launch {
                                offsetY.snapTo((offsetY.value + dragAmount).coerceAtMost(20f))
                            }
                        }
                    )
                }
        ) {
            ProxyOverlayPill(
                host = config.socksHost,
                socksPort = config.socksPort,
                httpPort = config.httpPort,
                onHide = { showProxyOverlay = false },
                onCopy = { 
                    onShowToast("Address copied: $it", false)
                },
                scaleFactor = scaleFactor
            )
        }
    }
}

@Composable
fun ProxyOverlayPill(
    host: String,
    socksPort: String,
    httpPort: String,
    onHide: () -> Unit,
    onCopy: (String) -> Unit,
    scaleFactor: Float
) {
    val clipboardManager = LocalClipboardManager.current
    val socksAddress = "$host:$socksPort"
    val httpAddress = "$host:$httpPort"

    Surface(
        modifier = Modifier
            .widthIn(max = 400.dp)
            .padding(horizontal = 8.dp)
            .shadow(24.dp, RoundedCornerShape(20.dp), spotColor = IosActiveBlue.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF1C1C1E).copy(alpha = 0.95f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(IosActiveBlue.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Dns, null, tint = IosActiveBlue, modifier = Modifier.size(20.dp))
            }
            
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ProxyCopyRow(
                    label = "SOCKS5",
                    address = socksAddress,
                    onCopy = {
                        clipboardManager.setText(AnnotatedString(socksAddress))
                        onCopy(socksAddress)
                    },
                    scaleFactor = scaleFactor
                )
                ProxyCopyRow(
                    label = "HTTP",
                    address = httpAddress,
                    onCopy = {
                        clipboardManager.setText(AnnotatedString(httpAddress))
                        onCopy(httpAddress)
                    },
                    scaleFactor = scaleFactor
                )
            }

            VerticalDivider(modifier = Modifier.height(36.dp), thickness = 1.dp, color = Color.White.copy(alpha = 0.1f))

            IconButton(
                onClick = onHide,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Default.Close, null, tint = IosSecondaryLabel, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun ProxyCopyRow(
    label: String,
    address: String,
    onCopy: () -> Unit,
    scaleFactor: Float
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onCopy() }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Text(
                text = "$label:",
                style = MaterialTheme.typography.labelSmall,
                color = IosActiveBlue,
                fontWeight = FontWeight.ExtraBold,
                fontSize = (9 * scaleFactor).sp
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = address,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = (12 * scaleFactor).sp,
                maxLines = 1
            )
        }
        Icon(
            imageVector = Icons.Default.ContentCopy,
            contentDescription = "Copy",
            tint = Color.White.copy(alpha = 0.6f),
            modifier = Modifier.size((14 * scaleFactor).dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun IosStatusHeroCard(
    connectionStatus: ConnectionStatus,
    elapsedSeconds: Long,
    sessionTraffic: SessionTraffic,
    config: AetherConfig,
    ipInfo: IpInfo = IpInfo(),
    pingState: PingState = PingState(),
    onRefreshIpInfo: () -> Unit = {},
    onRefreshPing: () -> Unit = {},
    onShowToast: (String, Boolean) -> Unit = { _, _ -> },
    hideConfigChips: Boolean = false,
    scaleFactor: Float = 1f
) {
    val statusColor by animateColorAsState(
        targetValue = when (connectionStatus) {
            ConnectionStatus.RUNNING -> IosActiveGreen
            ConnectionStatus.STARTING, ConnectionStatus.VALIDATING, ConnectionStatus.RECONNECTING, ConnectionStatus.STOPPING -> IosScanningAmber
            ConnectionStatus.ERROR -> IosErrorRed
            ConnectionStatus.STOPPED -> IosSecondaryLabel
        },
        label = "statusColor"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("status_hero_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = IosCardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            statusColor.copy(alpha = 0.12f),
                            Color.Transparent
                        )
                    )
                )
                .padding((14 * scaleFactor).dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size((7 * scaleFactor).dp)
                                .clip(CircleShape)
                                .background(statusColor)
                        )
                        Spacer(modifier = Modifier.width((5 * scaleFactor).dp))
                                Text(
                                    text = when (connectionStatus) {
                                        ConnectionStatus.RUNNING -> if (config.connectionMode == ConnectionMode.TUNNEL) "PROTECTED & CONNECTED" else "PROXY ACTIVE"
                                        ConnectionStatus.STARTING -> "FINDING SERVERS..."
                                        ConnectionStatus.VALIDATING -> "ESTABLISHING LINK..."
                                        ConnectionStatus.RECONNECTING -> "RECONNECTING..."
                                        ConnectionStatus.STOPPING -> "STOPPING..."
                                        ConnectionStatus.ERROR -> "CONNECTION ERROR"
                                        ConnectionStatus.STOPPED -> "READY TO CONNECT"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.8.sp,
                                    color = statusColor,
                                    fontSize = (8.5 * scaleFactor).sp
                                )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = IosGroupBg
                    ) {
                        val protocolText = if (config.protocol == AetherProtocol.MASQUE) {
                            if (config.h2Mode) "MASQUE (H2)" else "MASQUE (H3)"
                        } else {
                            config.protocol.displayName
                        }
                        Text(
                            text = protocolText,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = IosActiveBlue,
                            fontSize = (8.5 * scaleFactor).sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height((10 * scaleFactor).dp))

                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Text(
                            text = formatTime(elapsedSeconds),
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = (28 * scaleFactor).sp
                        )
                    }

                    if (connectionStatus == ConnectionStatus.RUNNING) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onRefreshPing() }
                                .padding(2.dp)
                        ) {
                            if (pingState.isPinging) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size((11 * scaleFactor).dp),
                                    color = IosActiveBlue,
                                    strokeWidth = 1.5.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Speed,
                                    contentDescription = "Ping",
                                    tint = if (pingState.error != null) IosErrorRed else IosActiveBlue,
                                    modifier = Modifier.size((15 * scaleFactor).dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = when {
                                    pingState.isPinging -> "..."
                                    pingState.error != null -> "TIMEOUT"
                                    pingState.ms >= 0 -> "${pingState.ms}ms"
                                    else -> "PING"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (pingState.error != null) IosErrorRed else IosActiveBlue,
                                fontSize = (12 * scaleFactor).sp
                            )
                        }
                    } else {
                        Text(
                            text = if (connectionStatus == ConnectionStatus.RECONNECTING) "RETRY" else "NO UPLINK",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (connectionStatus == ConnectionStatus.RECONNECTING) IosScanningAmber else IosSecondaryLabel,
                            modifier = Modifier.clickable { onRefreshPing() },
                            fontSize = (10 * scaleFactor).sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height((8 * scaleFactor).dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(IosGroupBg)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TrafficValue(
                        label = "UPLOAD",
                        value = formatTrafficBytes(sessionTraffic.uploadedBytes),
                        color = IosActiveBlue,
                        alignment = Alignment.Start,
                        modifier = Modifier.weight(1f),
                        scaleFactor = scaleFactor
                    )
                    TrafficValue(
                        label = "DOWNLOAD",
                        value = formatTrafficBytes(sessionTraffic.downloadedBytes),
                        color = IosActiveGreen,
                        alignment = Alignment.End,
                        modifier = Modifier.weight(1f),
                        scaleFactor = scaleFactor
                    )
                }

                Spacer(modifier = Modifier.height((8 * scaleFactor).dp))

                val clipboardManager = LocalClipboardManager.current

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .combinedClickable(
                            onClick = { onRefreshIpInfo() },
                            onLongClick = {
                                if (ipInfo.ip.isNotEmpty()) {
                                    clipboardManager.setText(AnnotatedString(ipInfo.ip))
                                    onShowToast("IP address copied to clipboard", false)
                                }
                            }
                        ),
                    color = IosGroupBg
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = ipInfo.flagEmoji.ifEmpty { "🌐" },
                                fontSize = (16 * scaleFactor).sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = when {
                                        ipInfo.country.isNotEmpty() -> if (ipInfo.countryCode.isNotEmpty()) "${ipInfo.country} (${ipInfo.countryCode})" else ipInfo.country
                                        ipInfo.isLoading -> "Wait..."
                                        ipInfo.error != null -> "Error"
                                        else -> "Unknown"
                                    },
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = (11 * scaleFactor).sp
                                )
                            Text(
                                text = when {
                                    ipInfo.ip.isNotEmpty() -> ipInfo.ip
                                    ipInfo.isLoading -> "LOCATING YOUR IP..."
                                    ipInfo.error != null -> "COULD NOT FIND IP"
                                    else -> "SHOW PUBLIC IP"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = when {
                                    ipInfo.error != null -> IosErrorRed
                                    ipInfo.isLoading -> IosScanningAmber
                                    else -> IosSecondaryLabel
                                },
                                fontSize = (9 * scaleFactor).sp
                            )
                            }
                        }

                        if (ipInfo.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size((12 * scaleFactor).dp),
                                color = IosActiveBlue,
                                strokeWidth = 1.5.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh",
                                tint = IosSecondaryLabel,
                                modifier = Modifier.size((12 * scaleFactor).dp)
                            )
                        }
                    }
                }

                if (!hideConfigChips) {
                    Spacer(modifier = Modifier.height((10 * scaleFactor).dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(IosGroupBg)
                            .padding(6.dp),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        IosConfigChip(label = "BYPASS", value = config.noise.displayName.split(" ")[0], scaleFactor = scaleFactor)
                        IosConfigChip(label = "SPEED", value = config.scanMode.name.take(6), scaleFactor = scaleFactor)
                        IosConfigChip(label = "NETWORK", value = config.ipMode.rawValue, scaleFactor = scaleFactor)
                    }
                }
            }
        }
    }
}

@Composable
private fun TrafficValue(label: String, value: String, color: Color, alignment: Alignment.Horizontal, modifier: Modifier = Modifier, scaleFactor: Float = 1f) {
    Column(modifier = modifier, horizontalAlignment = alignment) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = IosSecondaryLabel,
            fontSize = (8 * scaleFactor).sp
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color,
            fontSize = (12 * scaleFactor).sp
        )
    }
}

@Composable
fun IosConfigChip(label: String, value: String, scaleFactor: Float = 1f) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = IosSecondaryLabel, fontSize = (8 * scaleFactor).sp, fontWeight = FontWeight.Bold)
        Text(text = value, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color.White, fontSize = (10 * scaleFactor).sp)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun IosPowerButton(
    connectionStatus: ConnectionStatus,
    onToggle: () -> Unit,
    size: Dp = 140.dp
) {
    val isConnected = connectionStatus == ConnectionStatus.RUNNING
    val isWorking = connectionStatus == ConnectionStatus.STARTING ||
                    connectionStatus == ConnectionStatus.VALIDATING ||
                    connectionStatus == ConnectionStatus.RECONNECTING ||
                    connectionStatus == ConnectionStatus.STOPPING
    val isError = connectionStatus == ConnectionStatus.ERROR
    val canToggle = connectionStatus != ConnectionStatus.STOPPING

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scope = rememberCoroutineScope()

    val shakeOffset = remember { Animatable(0f) }
    LaunchedEffect(connectionStatus) {
        if (isError) {
            shakeOffset.animateTo(
                targetValue = 0f,
                animationSpec = keyframes {
                    durationMillis = 500
                    20f at 100
                    20f at 200
                    20f at 300
                    20f at 400
                }
            )
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "refinedGlow")

    val breathingScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isWorking) 1.12f else 1f,
        animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breathingScale"
    )

    val buttonScale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else if (isWorking) breathingScale else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "buttonScale"
    )

    val cornerRadiusPercent by animateFloatAsState(
        targetValue = if (isConnected || isWorking) 0.28f else 0.5f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "cornerRadius"
    )

    val buttonColor by animateColorAsState(
        targetValue = when {
            isConnected -> IosActiveGreen
            isWorking -> IosScanningAmber
            isError -> IosErrorRed
            else -> IosActiveBlue
        },
        animationSpec = tween(durationMillis = 600),
        label = "buttonColor"
    )

    val glowScale by infiniteTransition.animateFloat(
        initialValue = 1.2f,
        targetValue = if (isConnected) 1.8f else 1.5f,
        animationSpec = infiniteRepeatable(tween(2500, easing = LinearEasing), RepeatMode.Reverse),
        label = "glowScale"
    )

    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.02f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Reverse),
        label = "glowAlpha"
    )

    Box(
        modifier = Modifier
            .size(size * 2.5f)
            .graphicsLayer { translationX = shakeOffset.value },
        contentAlignment = Alignment.Center
    ) {
        if (isWorking || isConnected) {
            val pulseColor = buttonColor.copy(alpha = 0.45f)
            val glowShape = RoundedCornerShape(size * cornerRadiusPercent)

            Box(
                modifier = Modifier
                    .size(size)
                    .graphicsLayer {
                        scaleX = glowScale
                        scaleY = glowScale
                        alpha = glowAlpha
                    }
                    .background(pulseColor, glowShape)
            )

            if (isConnected) {
                Box(
                    modifier = Modifier
                        .size(size)
                        .graphicsLayer {
                            scaleX = glowScale * 0.75f
                            scaleY = glowScale * 0.75f
                            alpha = glowAlpha * 1.8f
                        }
                        .background(pulseColor, glowShape)
                )
            }
        }

        Surface(
            modifier = Modifier
                .size(size)
                .graphicsLayer {
                    scaleX = buttonScale
                    scaleY = buttonScale
                }
                .shadow(
                    elevation = if (isPressed) 6.dp else 24.dp,
                    shape = RoundedCornerShape(size * cornerRadiusPercent),
                    ambientColor = buttonColor.copy(alpha = 0.6f),
                    spotColor = buttonColor
                )
                .clip(RoundedCornerShape(size * cornerRadiusPercent))
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = canToggle,
                    onClick = {
                        scope.launch { onToggle() }
                    }
                ),
            color = buttonColor,
            tonalElevation = 14.dp
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.35f),
                                    Color.White.copy(alpha = 0.05f),
                                    Color.Transparent
                                )
                            )
                        )
                )

                Icon(
                    imageVector = Icons.Default.PowerSettingsNew,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(size * 0.45f)
                )
            }
        }
    }
}

@Composable
fun IosProtocolSegmentedControl(
    selectedProtocol: AetherProtocol,
    onProtocolSelected: (AetherProtocol) -> Unit,
    enabled: Boolean = true,
    scaleFactor: Float = 1f
) {
    Column {
        Text(
            text = "CONNECTION PROTOCOL",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = IosSecondaryLabel,
            fontSize = (9 * scaleFactor).sp,
            letterSpacing = 0.5.sp,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(IosCardBg)
                .padding(2.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            AetherProtocol.entries.forEach { proto ->
                val selected = proto == selectedProtocol
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (selected) IosActiveBlue else Color.Transparent
                        )
                        .clickable(enabled = enabled) { onProtocolSelected(proto) }
                        .padding(vertical = (6 * scaleFactor).dp)
                        .graphicsLayer { alpha = if (enabled || selected) 1f else 0.5f }
                        .testTag("protocol_${proto.rawValue}"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (proto == AetherProtocol.ZERO_TRUST) "Z-TRUST" else proto.displayName.split(" ")[0].uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = if (selected) Color.White else IosSecondaryLabel,
                        fontSize = (9 * scaleFactor).sp
                    )
                }
            }
        }
    }
}

private fun formatTime(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return String.format(Locale.ROOT, "%02d:%02d:%02d", h, m, s)
}

private fun formatTrafficBytes(bytes: Long): String {
    val safeBytes = bytes.coerceAtLeast(0)
    val units = arrayOf("B", "KB", "MB", "GB", "TB", "PB")
    var value = safeBytes.toDouble()
    var unitIndex = 0
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex += 1
    }
    return if (unitIndex == 0) {
        "$safeBytes ${units[unitIndex]}"
    } else {
        String.format(Locale.ROOT, "%.2f %s", value, units[unitIndex])
    }
}
