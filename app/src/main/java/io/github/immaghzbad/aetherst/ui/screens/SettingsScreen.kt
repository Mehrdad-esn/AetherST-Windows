package io.github.immaghzbad.aetherst.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.immaghzbad.aetherst.core.NetworkUtils
import io.github.immaghzbad.aetherst.model.*

private val IosCardBackground = Color(0xFF1C1C1E)
private val IosGroupBackground = Color(0xFF2C2C2E)
private val IosSecondaryLabel = Color(0xFF8E8E93)
private val IosActiveBlue = Color(0xFF007AFF)
private val IosDividerColor = Color(0xFF2C2C2E)
private val IosActiveSwitchGreen = Color(0xFF34C759)
private val IosInactiveSwitchTrack = Color(0xFF3A3A3C)

@Composable
fun SettingsScreen(
    config: AetherConfig,
    isBatteryOptimized: Boolean,
    scrollToSection: Boolean = false,
    onSectionScrolled: () -> Unit = {},
    onUpdateConfig: (AetherConfig) -> Unit,
    onUpdateTunnelEngine: (TunnelEngine) -> Unit,
    onApplyPreset: (String) -> Unit,
    onOpenSplitTunneling: () -> Unit,
    onOpenRoutingRules: () -> Unit,
    onRequestBatteryOptimization: () -> Unit,
    onResetAll: () -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: (android.net.Uri) -> Unit,
    onOptimizeMtu: () -> Unit,
    isOptimizingMtu: Boolean = false,
    onShowToast: (String, Boolean) -> Unit = { _, _ -> },
    bottomContentPadding: Dp = 0.dp,
) {
    val focusManager = LocalFocusManager.current
    var searchQuery by remember { mutableStateOf("") }
    var showAdvancedZeroTrust by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    
    val fullBackupPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent(),
    ) { uri -> uri?.let { onImportBackup(it) } }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null
            ) { focusManager.clearFocus() }
    ) {
        val screenWidth = this.maxWidth
        val scaleFactor = (screenWidth.value / 411f).coerceIn(0.7f, 1.1f)
        val horizontalPadding = 16.dp
        val lazyListState = rememberLazyListState()

        LaunchedEffect(scrollToSection) {
            if (scrollToSection) {
                lazyListState.animateScrollToItem(4)
                onSectionScrolled()
            }
        }

        LazyColumn(
            state = lazyListState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = horizontalPadding,
                top = 0.dp,
                end = horizontalPadding,
                bottom = bottomContentPadding + 12.dp
            ),
            verticalArrangement = Arrangement.spacedBy((18 * scaleFactor).dp)
        ) {
            item {
                Column(modifier = Modifier.statusBarsPadding().padding(top = 12.dp)) {
                    Text(
                        text = "AetherST Settings",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = (26 * scaleFactor).sp,
                        lineHeight = (30 * scaleFactor).sp
                    )
                    Text(
                        text = "Configure engine protocols, obfuscation & transport parameters",
                        style = MaterialTheme.typography.bodySmall,
                        color = IosSecondaryLabel,
                        fontSize = (12 * scaleFactor).sp,
                        lineHeight = (16 * scaleFactor).sp
                    )
                }
            }

            item {
                BasicTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height((44 * scaleFactor).dp)
                        .background(IosCardBackground, RoundedCornerShape(12.dp)),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White, fontSize = (14 * scaleFactor).sp),
                    singleLine = true,
                    cursorBrush = SolidColor(IosActiveBlue),
                    decorationBox = { innerTextField ->
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Search, null, tint = IosSecondaryLabel, modifier = Modifier.size((20 * scaleFactor).dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (searchQuery.isEmpty()) {
                                    Text("Search settings...", color = IosSecondaryLabel, fontSize = (13 * scaleFactor).sp)
                                }
                                innerTextField()
                            }
                        }
                    }
                )
            }

            if (searchQuery.isEmpty() || "Preset Profiles Custom Manual Tweaks Bypass UDP TLS Ironclad Stealth Turbo Speed".contains(searchQuery, ignoreCase = true)) {
                item {
                    IosSectionHeader(title = "PRESET CONFIGURATION PROFILES", scaleFactor = scaleFactor)
                    IosGroupCard {
                        Column {
                            IosPresetItem(
                                icon = Icons.Default.Tune,
                                iconBg = Color(0xFF8E8E93),
                                title = "Custom Manual Tweaks",
                                subtitle = "Your own independent manual configuration",
                                isActive = config.presetId == "custom",
                                onClick = { 
                                    onApplyPreset("custom")
                                    onShowToast("Applied manual configuration", false)
                                },
                                scaleFactor = scaleFactor
                            )
                            HorizontalDivider(color = IosDividerColor, thickness = 0.5.dp, modifier = Modifier.padding(start = (50 * scaleFactor).dp))
                            IosPresetItem(
                                icon = Icons.Default.Lock,
                                iconBg = Color(0xFF5856D6),
                                title = "Bypass UDP / TLS",
                                subtitle = "MASQUE + H2 Fallback + Packet Fragmentation",
                                isActive = config.presetId == "bypass_udp",
                                onClick = { 
                                    onApplyPreset("bypass_udp")
                                    onShowToast("Applied UDP/TLS Bypass preset", false)
                                },
                                scaleFactor = scaleFactor
                            )
                            HorizontalDivider(color = IosDividerColor, thickness = 0.5.dp, modifier = Modifier.padding(start = (50 * scaleFactor).dp))
                            IosPresetItem(
                                icon = Icons.Default.Shield,
                                iconBg = Color(0xFF007AFF),
                                title = "Ironclad Stealth",
                                subtitle = "MASQUE + GFW Noise + Ironclad Probe Scan",
                                isActive = config.presetId == "ironclad_stealth",
                                onClick = { 
                                    onApplyPreset("ironclad_stealth")
                                    onShowToast("Applied Ironclad Stealth preset", false)
                                },
                                scaleFactor = scaleFactor
                            )
                            HorizontalDivider(color = IosDividerColor, thickness = 0.5.dp, modifier = Modifier.padding(start = (50 * scaleFactor).dp))
                            IosPresetItem(
                                icon = Icons.Default.Bolt,
                                iconBg = Color(0xFFFF9500),
                                title = "Turbo Speed",
                                subtitle = "WireGuard + Balanced Noise + Turbo Scan",
                                isActive = config.presetId == "turbo_wg",
                                onClick = { 
                                    onApplyPreset("turbo_wg")
                                    onShowToast("Applied Turbo Speed preset", false)
                                },
                                scaleFactor = scaleFactor
                            )
                        }
                    }
                }
            }


            if (searchQuery.isEmpty() || "Engine Transport Protocol Bypass Obfuscation Speed Strategy Network Stack Whole Device Split Tunneling Domain Routing VPN Tunnel Mode SOCKS5 HTTP Host Port MTU Keepalive Peer".contains(searchQuery, ignoreCase = true)) {
                item {
                    IosSectionHeader(title = "CONNECTION & ROUTING", scaleFactor = scaleFactor)
                    IosGroupCard {
                        Column {
                            IosPickerRow(
                                icon = Icons.Default.VpnLock,
                                iconBg = Color(0xFF34C759),
                                title = "Connection Mode",
                                value = if (config.connectionMode == ConnectionMode.TUNNEL) "Tunnel" else "Proxy Only",
                                options = listOf("Tunnel", "Proxy Only"),
                                onOptionSelected = { index -> 
                                    onUpdateConfig(config.copy(connectionMode = if (index == 0) ConnectionMode.TUNNEL else ConnectionMode.PROXY_ONLY))
                                },
                                scaleFactor = scaleFactor
                            )
                            if (config.connectionMode == ConnectionMode.TUNNEL) {
                                HorizontalDivider(color = IosDividerColor, thickness = 0.5.dp, modifier = Modifier.padding(start = (50 * scaleFactor).dp))
                                IosPickerRow(
                                    icon = Icons.Default.VpnLock,
                                    iconBg = Color(0xFF5856D6),
                                    title = "Tunnel Engine",
                                    value = config.tunnelEngine.displayName,
                                    options = TunnelEngine.entries.map { it.displayName },
                                    onOptionSelected = { index -> onUpdateTunnelEngine(TunnelEngine.entries[index]) },
                                    scaleFactor = scaleFactor
                                )
                            }
                            HorizontalDivider(color = IosDividerColor, thickness = 0.5.dp, modifier = Modifier.padding(start = (50 * scaleFactor).dp))
                            IosPickerRow(
                                icon = Icons.Default.VpnLock,
                                iconBg = Color(0xFF007AFF),
                                title = "Transport Protocol",
                                value = config.protocol.displayName,
                                options = AetherProtocol.entries.map { it.displayName },
                                onOptionSelected = { index -> onUpdateConfig(config.copy(protocol = AetherProtocol.entries[index])) },
                                scaleFactor = scaleFactor
                            )
                            HorizontalDivider(color = IosDividerColor, thickness = 0.5.dp, modifier = Modifier.padding(start = (50 * scaleFactor).dp))
                            if (config.protocol == AetherProtocol.MASQUE) {
                                IosSwitchRow(
                                    icon = Icons.Default.Http,
                                    iconBg = Color(0xFF007AFF),
                                    title = "HTTP/2 Fallback Mode",
                                    subtitle = "Force MASQUE over TCP/TLS instead of QUIC",
                                    checked = config.h2Mode,
                                    onCheckedChange = { onUpdateConfig(config.copy(h2Mode = it)) },
                                    testTag = "switch_h2_mode",
                                    scaleFactor = scaleFactor
                                )
                                HorizontalDivider(color = IosDividerColor, thickness = 0.5.dp, modifier = Modifier.padding(start = (50 * scaleFactor).dp))
                                IosSwitchRow(
                                    icon = Icons.Default.VerticalSplit,
                                    iconBg = Color(0xFF5856D6),
                                    title = "Packet Fragmentation",
                                    subtitle = "Bypass SNI filters (H2 mode only)",
                                    checked = config.h2Fragment,
                                    onCheckedChange = { onUpdateConfig(config.copy(h2Fragment = it)) },
                                    testTag = "switch_fragment",
                                    scaleFactor = scaleFactor
                                )
                                AnimatedVisibility(visible = config.h2Fragment) {
                                    Column(modifier = Modifier.background(IosGroupBackground.copy(alpha = 0.3f))) {
                                        IosInputFieldRow(
                                            icon = Icons.Default.Straighten,
                                            iconBg = Color(0xFF8E8E93),
                                            label = "Fragment Size (Bytes)",
                                            value = config.fragmentSize,
                                            onValueChange = { onUpdateConfig(config.copy(fragmentSize = it)) },
                                            placeholder = "16-32",
                                            testTag = "fragment_size_input",
                                            scaleFactor = scaleFactor
                                        )
                                        HorizontalDivider(color = IosDividerColor, thickness = 0.5.dp, modifier = Modifier.padding(start = (50 * scaleFactor).dp))
                                        IosInputFieldRow(
                                            icon = Icons.Default.Timer,
                                            iconBg = Color(0xFF8E8E93),
                                            label = "Fragment Delay (ms)",
                                            value = config.fragmentDelay,
                                            onValueChange = { onUpdateConfig(config.copy(fragmentDelay = it)) },
                                            placeholder = "2-10",
                                            testTag = "fragment_delay_input",
                                            scaleFactor = scaleFactor
                                        )
                                    }
                                }
                                HorizontalDivider(color = IosDividerColor, thickness = 0.5.dp, modifier = Modifier.padding(start = (50 * scaleFactor).dp))
                            }
                            IosSwitchRow(
                                icon = Icons.AutoMirrored.Filled.Rule,
                                iconBg = Color(0xFF8E8E93),
                                title = "Skip Data Plane Check",
                                subtitle = "Trust gateway after handshake only",
                                checked = config.noDataCheck,
                                onCheckedChange = { onUpdateConfig(config.copy(noDataCheck = it)) },
                                testTag = "switch_no_data_check",
                                scaleFactor = scaleFactor
                            )
                            HorizontalDivider(color = IosDividerColor, thickness = 0.5.dp, modifier = Modifier.padding(start = (50 * scaleFactor).dp))
                            IosSwitchRow(
                                icon = Icons.Default.FlashOn,
                                iconBg = Color(0xFFFF9500),
                                title = "Quick Gateway Reconnect",
                                subtitle = "Reuse last working endpoint on start",
                                checked = config.quickReconnect,
                                onCheckedChange = { onUpdateConfig(config.copy(quickReconnect = it)) },
                                testTag = "switch_quick_reconnect",
                                scaleFactor = scaleFactor
                            )
                            HorizontalDivider(color = IosDividerColor, thickness = 0.5.dp, modifier = Modifier.padding(start = (50 * scaleFactor).dp))
                            IosSwitchRow(
                                icon = Icons.Default.Refresh,
                                iconBg = Color(0xFF8E8E93),
                                title = "No Profile Retry",
                                subtitle = "Do not fallback to other noise profiles",
                                checked = config.noProfileRetry,
                                onCheckedChange = { onUpdateConfig(config.copy(noProfileRetry = it)) },
                                testTag = "switch_no_profile_retry",
                                scaleFactor = scaleFactor
                            )
                            HorizontalDivider(color = IosDividerColor, thickness = 0.5.dp, modifier = Modifier.padding(start = (50 * scaleFactor).dp))
                            val availableNoise = if (config.protocol == AetherProtocol.MASQUE) {
                                listOf(AetherNoise.FIREWALL, AetherNoise.GFW, AetherNoise.OFF)
                            } else {
                                listOf(AetherNoise.BALANCED, AetherNoise.AGGRESSIVE, AetherNoise.LIGHT, AetherNoise.OFF)
                            }
                            IosPickerRow(
                                icon = Icons.Default.Tune,
                                iconBg = Color(0xFFAF52DE),
                                title = "Bypass Obfuscation",
                                value = config.noise.displayName.substringBefore(" ("),
                                options = availableNoise.map { it.displayName },
                                onOptionSelected = { index -> onUpdateConfig(config.copy(noise = availableNoise[index])) },
                                scaleFactor = scaleFactor
                            )
                            HorizontalDivider(color = IosDividerColor, thickness = 0.5.dp, modifier = Modifier.padding(start = (50 * scaleFactor).dp))
                            IosPickerRow(
                                icon = Icons.Default.NetworkCheck,
                                iconBg = Color(0xFFFF9500),
                                title = "Speed Strategy",
                                value = config.scanMode.name.lowercase().replaceFirstChar { it.uppercase() },
                                options = AetherScanMode.entries.map { mode -> "${mode.name.lowercase().replaceFirstChar { it.uppercase() }} (${mode.description})" },
                                onOptionSelected = { index -> onUpdateConfig(config.copy(scanMode = AetherScanMode.entries[index])) },
                                scaleFactor = scaleFactor
                            )
                            HorizontalDivider(color = IosDividerColor, thickness = 0.5.dp, modifier = Modifier.padding(start = (50 * scaleFactor).dp))
                            IosPickerRow(
                                icon = Icons.AutoMirrored.Filled.AltRoute,
                                iconBg = Color(0xFF5856D6),
                                title = "Network Stack",
                                value = config.ipMode.rawValue,
                                options = AetherIpMode.entries.map { it.displayName },
                                onOptionSelected = { index -> onUpdateConfig(config.copy(ipMode = AetherIpMode.entries[index])) },
                                scaleFactor = scaleFactor
                            )
                            if (config.connectionMode == ConnectionMode.TUNNEL) {
                                HorizontalDivider(color = IosDividerColor, thickness = 0.5.dp, modifier = Modifier.padding(start = (50 * scaleFactor).dp))
                                IosSwitchRow(
                                    icon = Icons.Default.AllInclusive,
                                    iconBg = Color(0xFF007AFF),
                                    title = "Tunnel Whole Device",
                                    subtitle = "Route all application traffic through VPN",
                                    checked = config.tunnelAllApps,
                                    onCheckedChange = { onUpdateConfig(config.copy(tunnelAllApps = it)) },
                                    testTag = "switch_tunnel_all",
                                    scaleFactor = scaleFactor
                                )
                                HorizontalDivider(color = IosDividerColor, thickness = 0.5.dp, modifier = Modifier.padding(start = (50 * scaleFactor).dp))
                                IosPickerRow(
                                    icon = Icons.Default.Tune,
                                    iconBg = Color(0xFF5856D6),
                                    title = "Split Tunneling",
                                    value = if (config.tunnelAllApps) "All Apps Tunneled" else "${config.excludedPackages.size + config.blockedPackages.size} Apps",
                                    options = emptyList(),
                                    onOptionSelected = { },
                                    scaleFactor = scaleFactor,
                                    onClickOverride = onOpenSplitTunneling
                                )
                                HorizontalDivider(color = IosDividerColor, thickness = 0.5.dp, modifier = Modifier.padding(start = (50 * scaleFactor).dp))
                                IosPickerRow(
                                    icon = Icons.AutoMirrored.Filled.AltRoute,
                                    iconBg = Color(0xFF007AFF),
                                    title = "Domain & IP Routing",
                                    value = "${config.routingRules.size} Rules",
                                    options = emptyList(),
                                    onOptionSelected = { },
                                    scaleFactor = scaleFactor,
                                    onClickOverride = onOpenRoutingRules
                                )
                            }
                            HorizontalDivider(color = IosDividerColor, thickness = 0.5.dp, modifier = Modifier.padding(start = (50 * scaleFactor).dp))
                            IosSwitchRow(
                                icon = Icons.Default.Share,
                                iconBg = Color(0xFFAF52DE),
                                title = "Share via Hotspot",
                                subtitle = "Allow other devices to connect to proxy",
                                checked = config.shareHotspot,
                                onCheckedChange = { onUpdateConfig(config.copy(shareHotspot = it)) },
                                testTag = "switch_share_hotspot",
                                scaleFactor = scaleFactor
                            )
                            if (config.shareHotspot) {
                                HorizontalDivider(color = IosDividerColor, thickness = 0.5.dp, modifier = Modifier.padding(start = (50 * scaleFactor).dp))
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(IosGroupBackground.copy(alpha = 0.4f))
                                        .padding((14 * scaleFactor).dp)
                                ) {
                                    var localIp by remember { mutableStateOf<String?>(null) }
                                    val clipboardManager = LocalClipboardManager.current
                                    
                                    LaunchedEffect(Unit) {
                                        localIp = NetworkUtils.getLocalIpAddress()
                                    }
                                    
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            modifier = Modifier.weight(1f),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Info,
                                                contentDescription = null,
                                                tint = if (localIp != null) IosActiveBlue else Color(0xFFFF3B30),
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = if (localIp != null) "LAN IP: $localIp" else "Hotspot is off",
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = (13 * scaleFactor).sp
                                            )
                                        }
                                        
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(
                                                onClick = {
                                                    val newIp = NetworkUtils.getLocalIpAddress()
                                                    localIp = newIp
                                                    if (newIp != null) {
                                                        onShowToast("Hotspot IP detected!", false)
                                                    } else {
                                                        onShowToast("Hotspot is off. Please enable it and test again.", true)
                                                    }
                                                },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(Icons.Default.Refresh, null, tint = IosActiveBlue, modifier = Modifier.size(18.dp))
                                            }
                                            
                                            if (localIp != null) {
                                                Spacer(modifier = Modifier.width(4.dp))
                                                IconButton(
                                                    onClick = {
                                                        clipboardManager.setText(AnnotatedString(localIp!!))
                                                        onShowToast("IP address copied!", false)
                                                    },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(Icons.Default.ContentCopy, null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = if (localIp != null) 
                                            "Devices on your Hotspot can use this IP with the ports below."
                                            else "Please enable your Hotspot and tap the refresh button.",
                                        color = IosSecondaryLabel,
                                        fontSize = (11 * scaleFactor).sp,
                                        lineHeight = 16.sp
                                    )
                                }
                            }
                            HorizontalDivider(color = IosDividerColor, thickness = 0.5.dp, modifier = Modifier.padding(start = (50 * scaleFactor).dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = (16 * scaleFactor).dp, vertical = (12 * scaleFactor).dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IosIconBadge(icon = Icons.Default.Language, backgroundColor = Color(0xFF007AFF), scaleFactor = scaleFactor)
                                Spacer(modifier = Modifier.width((12 * scaleFactor).dp))
                                IosInputField(
                                    label = "SOCKS5 Host",
                                    value = config.socksHost,
                                    onValueChange = { onUpdateConfig(config.copy(socksHost = it)) },
                                    modifier = Modifier.weight(1f),
                                    placeholder = "127.0.0.1",
                                    testTag = "socks_host_input",
                                    scaleFactor = scaleFactor
                                )
                                Spacer(modifier = Modifier.width((10 * scaleFactor).dp))
                                IosInputField(
                                    label = "SOCKS Port",
                                    value = config.socksPort,
                                    onValueChange = { onUpdateConfig(config.copy(socksPort = it)) },
                                    modifier = Modifier.width((75 * scaleFactor).dp),
                                    placeholder = "1819",
                                    keyboardType = KeyboardType.Number,
                                    testTag = "socks_port_input",
                                    scaleFactor = scaleFactor
                                )
                                Spacer(modifier = Modifier.width((8 * scaleFactor).dp))
                                IosInputField(
                                    label = "HTTP Port",
                                    value = config.httpPort,
                                    onValueChange = { onUpdateConfig(config.copy(httpPort = it)) },
                                    modifier = Modifier.width((75 * scaleFactor).dp),
                                    placeholder = "1820",
                                    keyboardType = KeyboardType.Number,
                                    testTag = "http_port_input",
                                    scaleFactor = scaleFactor
                                )
                            }
                            HorizontalDivider(color = IosDividerColor, thickness = 0.5.dp, modifier = Modifier.padding(start = (50 * scaleFactor).dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = (16 * scaleFactor).dp, vertical = (12 * scaleFactor).dp),
                                verticalAlignment = Alignment.Bottom,
                                horizontalArrangement = Arrangement.spacedBy((10 * scaleFactor).dp)
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IosIconBadge(icon = Icons.Default.Tune, backgroundColor = Color(0xFF34C759), scaleFactor = scaleFactor)
                                    Spacer(modifier = Modifier.width((12 * scaleFactor).dp))
                                    IosInputField(
                                        label = "Custom MTU Size",
                                        value = config.mtu.toString(),
                                        onValueChange = { onUpdateConfig(config.copy(mtu = it.toIntOrNull() ?: 1100)) },
                                        modifier = Modifier.weight(1f),
                                        placeholder = "1100",
                                        keyboardType = KeyboardType.Number,
                                        testTag = "mtu_input",
                                        scaleFactor = scaleFactor
                                    )
                                }
                                Button(
                                    onClick = onOptimizeMtu,
                                    enabled = !isOptimizingMtu,
                                    modifier = Modifier.height((46 * scaleFactor).dp),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = IosActiveBlue.copy(alpha = 0.15f),
                                        contentColor = IosActiveBlue,
                                        disabledContainerColor = IosActiveBlue.copy(alpha = 0.05f),
                                        disabledContentColor = IosActiveBlue.copy(alpha = 0.3f)
                                    ),
                                    contentPadding = PaddingValues(horizontal = (16 * scaleFactor).dp)
                                ) {
                                    if (isOptimizingMtu) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size((18 * scaleFactor).dp),
                                            color = IosActiveBlue,
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Text("Optimize", fontSize = (13 * scaleFactor).sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            HorizontalDivider(color = IosDividerColor, thickness = 0.5.dp, modifier = Modifier.padding(start = (50 * scaleFactor).dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = (16 * scaleFactor).dp, vertical = (12 * scaleFactor).dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                    IosIconBadge(icon = Icons.Default.Bolt, backgroundColor = Color(0xFFFF9500), scaleFactor = scaleFactor)
                                    Spacer(modifier = Modifier.width((12 * scaleFactor).dp))
                                    IosInputField(
                                        label = "Keepalive (Secs)",
                                        value = config.keepalive.toString(),
                                        onValueChange = { onUpdateConfig(config.copy(keepalive = it.toIntOrNull() ?: 5)) },
                                        placeholder = "5",
                                        keyboardType = KeyboardType.Number,
                                        testTag = "keepalive_input",
                                        scaleFactor = scaleFactor
                                    )
                                }
                                Spacer(modifier = Modifier.width((12 * scaleFactor).dp))
                                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                    IosInputField(
                                        label = "Validation (Secs)",
                                        value = config.validateSecs.toString(),
                                        onValueChange = { onUpdateConfig(config.copy(validateSecs = it.toIntOrNull() ?: 10)) },
                                        placeholder = "10",
                                        keyboardType = KeyboardType.Number,
                                        testTag = "validate_secs_input",
                                        scaleFactor = scaleFactor
                                    )
                                }
                            }
                            HorizontalDivider(color = IosDividerColor, thickness = 0.5.dp, modifier = Modifier.padding(start = (50 * scaleFactor).dp))
                            IosInputFieldRow(
                                icon = Icons.Default.Code,
                                iconBg = Color(0xFF8E8E93),
                                label = "TLS Key Groups",
                                value = config.tlsGroups,
                                onValueChange = { onUpdateConfig(config.copy(tlsGroups = it)) },
                                placeholder = "P-256:X25519:P-384",
                                testTag = "tls_groups_input",
                                scaleFactor = scaleFactor
                            )
                            if (config.connectionMode == ConnectionMode.TUNNEL) {
                                HorizontalDivider(color = IosDividerColor, thickness = 0.5.dp, modifier = Modifier.padding(start = (50 * scaleFactor).dp))
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = (16 * scaleFactor).dp, vertical = (12 * scaleFactor).dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IosIconBadge(icon = Icons.Default.Dns, backgroundColor = Color(0xFF007AFF), scaleFactor = scaleFactor)
                                        Spacer(modifier = Modifier.width((12 * scaleFactor).dp))
                                        IosInputField(
                                            label = "Tunnel DNS Servers",
                                            value = config.dnsList,
                                            onValueChange = { 
                                                val cleaned = it.replace(Regex("\\s*,\\s*"), ",")
                                                onUpdateConfig(config.copy(dnsList = cleaned)) 
                                            },
                                            modifier = Modifier.weight(1f),
                                            placeholder = "1.1.1.1,1.0.0.1",
                                            testTag = "dns_list_input",
                                            scaleFactor = scaleFactor
                                        )
                                    }
                                    Text(
                                        text = "Separate multiple DNS IPs with a comma (e.g. 1.1.1.1,8.8.8.8) - no spaces required.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.Yellow.copy(alpha = 0.8f),
                                        fontSize = (10 * scaleFactor).sp,
                                        modifier = Modifier.padding(start = (42 * scaleFactor).dp, top = 4.dp)
                                    )
                                }
                            }
                            HorizontalDivider(color = IosDividerColor, thickness = 0.5.dp, modifier = Modifier.padding(start = (50 * scaleFactor).dp))
                            IosInputFieldRow(
                                icon = Icons.AutoMirrored.Filled.AltRoute,
                                iconBg = Color(0xFF5856D6),
                                label = "Forced Peer IP",
                                value = config.peer,
                                onValueChange = { onUpdateConfig(config.copy(peer = it)) },
                                placeholder = "e.g. 1.2.3.4:443",
                                testTag = "peer_input",
                                scaleFactor = scaleFactor
                            )
                        }
                    }
                }
            }

            if ((config.protocol == AetherProtocol.ZERO_TRUST) && (searchQuery.isEmpty() || "Cloudflare Zero Trust Team Access Gateway ID Secret Token".contains(searchQuery, ignoreCase = true))) {
                item {
                    Column {
                        IosSectionHeader(title = "CLOUDFLARE ZERO TRUST", scaleFactor = scaleFactor)
                        IosGroupCard {
                            Column {
                                IosInputFieldRow(
                                    icon = Icons.Default.Business,
                                    iconBg = Color(0xFF5856D6),
                                    label = "Organization Team Name",
                                    value = config.teamName,
                                    onValueChange = { onUpdateConfig(config.copy(teamName = it)) },
                                    placeholder = "e.g. my-org",
                                    testTag = "zt_team_input",
                                    scaleFactor = scaleFactor
                                )
                                HorizontalDivider(color = IosDividerColor, thickness = 0.5.dp, modifier = Modifier.padding(start = (50 * scaleFactor).dp))
                                IosInputFieldRow(
                                    icon = Icons.Default.Language,
                                    iconBg = Color(0xFF007AFF),
                                    label = "Cloudflare Access Email",
                                    value = config.accessEmail,
                                    onValueChange = { onUpdateConfig(config.copy(accessEmail = it)) },
                                    placeholder = "user@example.com",
                                    testTag = "zt_email_input",
                                    scaleFactor = scaleFactor
                                )
                                HorizontalDivider(color = IosDividerColor, thickness = 0.5.dp, modifier = Modifier.padding(start = (50 * scaleFactor).dp))
                                IosSwitchRow(
                                    icon = Icons.Default.Shield,
                                    iconBg = Color(0xFF34C759),
                                    title = "Gateway Filtering Proxy",
                                    subtitle = "Route via org Gateway for filtering & logs",
                                    checked = config.useGateway,
                                    onCheckedChange = { onUpdateConfig(config.copy(useGateway = it)) },
                                    testTag = "switch_zt_gateway",
                                    scaleFactor = scaleFactor
                                )
                                HorizontalDivider(color = IosDividerColor, thickness = 0.5.dp, modifier = Modifier.padding(start = (50 * scaleFactor).dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { showAdvancedZeroTrust = !showAdvancedZeroTrust }
                                        .padding(horizontal = (16 * scaleFactor).dp, vertical = (14 * scaleFactor).dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IosIconBadge(icon = Icons.Default.Lock, backgroundColor = Color(0xFF8E8E93), scaleFactor = scaleFactor)
                                        Spacer(modifier = Modifier.width((12 * scaleFactor).dp))
                                        Text(
                                            text = "Advanced Authentication",
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Medium,
                                            color = Color.White,
                                            fontSize = (15 * scaleFactor).sp
                                        )
                                    }
                                    Icon(
                                        imageVector = if (showAdvancedZeroTrust) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = null,
                                        tint = IosSecondaryLabel,
                                        modifier = Modifier.size((18 * scaleFactor).dp)
                                    )
                                }

                                AnimatedVisibility(
                                    visible = showAdvancedZeroTrust,
                                    enter = fadeIn() + expandVertically(),
                                    exit = fadeOut() + shrinkVertically()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(IosGroupBackground.copy(alpha = 0.4f))
                                            .padding((14 * scaleFactor).dp),
                                        verticalArrangement = Arrangement.spacedBy((12 * scaleFactor).dp)
                                    ) {
                                        IosInputField(
                                            label = "Access Client ID",
                                            value = config.accessId,
                                            onValueChange = { onUpdateConfig(config.copy(accessId = it)) },
                                            placeholder = "Required for Service Tokens",
                                            testTag = "zt_access_id",
                                            scaleFactor = scaleFactor
                                        )
                                        IosInputField(
                                            label = "Access Client Secret",
                                            value = config.accessSecret,
                                            onValueChange = { onUpdateConfig(config.copy(accessSecret = it)) },
                                            placeholder = "Required for Service Tokens",
                                            testTag = "zt_access_secret",
                                            scaleFactor = scaleFactor
                                        )
                                        IosInputField(
                                            label = "Manual JWT Access Token",
                                            value = config.accessToken,
                                            onValueChange = { onUpdateConfig(config.copy(accessToken = it)) },
                                            placeholder = "Optional overrides auth",
                                            testTag = "zt_access_token",
                                            scaleFactor = scaleFactor
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (config.connectionMode == ConnectionMode.TUNNEL && (searchQuery.isEmpty() || "Advanced Security Kill Switch IPv6 Leak Protection Smart Reconnect".contains(searchQuery, ignoreCase = true))) {
                item {
                    IosSectionHeader(title = "ADVANCED SECURITY & AUTO-RECOVERY", scaleFactor = scaleFactor)
                    IosGroupCard {
                        Column {
                            IosSwitchRow(
                                icon = Icons.Default.VpnLock,
                                iconBg = Color(0xFF5856D6),
                                title = "Strict Kill Switch",
                                subtitle = "Prevent any leak even during manual stop",
                                checked = config.strictKillSwitch,
                                onCheckedChange = { onUpdateConfig(config.copy(strictKillSwitch = it)) },
                                testTag = "switch_strict_kill_switch",
                                scaleFactor = scaleFactor
                            )
                            HorizontalDivider(color = IosDividerColor, thickness = 0.5.dp, modifier = Modifier.padding(start = (50 * scaleFactor).dp))
                            IosSwitchRow(
                                icon = Icons.Default.Lock,
                                iconBg = Color(0xFFFF3B30),
                                title = "Kill Switch",
                                subtitle = "Block traffic when VPN is disconnected",
                                checked = config.killSwitch,
                                onCheckedChange = { onUpdateConfig(config.copy(killSwitch = it)) },
                                testTag = "switch_kill_switch",
                                scaleFactor = scaleFactor
                            )
                            HorizontalDivider(color = IosDividerColor, thickness = 0.5.dp, modifier = Modifier.padding(start = (50 * scaleFactor).dp))
                            IosSwitchRow(
                                icon = Icons.Default.Security,
                                iconBg = Color(0xFF5856D6),
                                title = "IPv6 Leak Protection",
                                subtitle = "Force all IPv6 traffic through tunnel",
                                checked = config.ipv6Leak,
                                onCheckedChange = { onUpdateConfig(config.copy(ipv6Leak = it)) },
                                testTag = "switch_ipv6_leak",
                                scaleFactor = scaleFactor
                            )
                            HorizontalDivider(color = IosDividerColor, thickness = 0.5.dp, modifier = Modifier.padding(start = (50 * scaleFactor).dp))
                            IosSwitchRow(
                                icon = Icons.Default.Restore,
                                iconBg = Color(0xFF34C759),
                                title = "Smart Reconnect",
                                subtitle = "Attempt auto-recovery on network failure",
                                checked = config.smartReconnect,
                                onCheckedChange = { onUpdateConfig(config.copy(smartReconnect = it)) },
                                testTag = "switch_smart_reconnect",
                                scaleFactor = scaleFactor
                            )
                            if (config.smartReconnect) {
                                HorizontalDivider(color = IosDividerColor, thickness = 0.5.dp, modifier = Modifier.padding(start = (50 * scaleFactor).dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = (16 * scaleFactor).dp, vertical = (12 * scaleFactor).dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                        IosIconBadge(icon = Icons.Default.Repeat, backgroundColor = Color(0xFF8E8E93), scaleFactor = scaleFactor)
                                        Spacer(modifier = Modifier.width((12 * scaleFactor).dp))
                                        IosInputField(
                                            label = "Max Retries",
                                            value = config.reconnectRetryLimit.toString(),
                                            onValueChange = { onUpdateConfig(config.copy(reconnectRetryLimit = it.toIntOrNull() ?: 10)) },
                                            placeholder = "10",
                                            keyboardType = KeyboardType.Number,
                                            testTag = "reconnect_limit_input",
                                            scaleFactor = scaleFactor
                                        )
                                    }
                                    Spacer(modifier = Modifier.width((12 * scaleFactor).dp))
                                    Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                        IosInputField(
                                            label = "Delay (Secs)",
                                            value = config.reconnectSecs.toString(),
                                            onValueChange = { onUpdateConfig(config.copy(reconnectSecs = it.toIntOrNull() ?: 2)) },
                                            placeholder = "2",
                                            keyboardType = KeyboardType.Number,
                                            testTag = "reconnect_secs_input",
                                            scaleFactor = scaleFactor
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (searchQuery.isEmpty() || "Logging System Logs App Core Level Diagnostics".contains(searchQuery, ignoreCase = true)) {
                item {
                    IosSectionHeader(title = "DIAGNOSTICS & SYSTEM LOGS", scaleFactor = scaleFactor)
                    IosGroupCard {
                        Column {
                            IosPickerRow(
                                icon = Icons.Default.BugReport,
                                iconBg = Color(0xFF64D2FF),
                                title = "App System Logging",
                                value = config.appLogLevel.displayName.substringBefore(" ("),
                                options = AetherLogLevel.entries.map { it.displayName },
                                onOptionSelected = { index ->
                                    onUpdateConfig(config.copy(appLogLevel = AetherLogLevel.entries[index]))
                                },
                                scaleFactor = scaleFactor
                            )
                            HorizontalDivider(color = IosDividerColor, thickness = 0.5.dp, modifier = Modifier.padding(start = (50 * scaleFactor).dp))
                            IosPickerRow(
                                icon = Icons.Default.VpnLock,
                                iconBg = Color(0xFF8E8E93),
                                title = "Aether Core Logging",
                                value = config.coreLogLevel.displayName.substringBefore(" ("),
                                options = AetherLogLevel.entries.map { it.displayName },
                                onOptionSelected = { index ->
                                    onUpdateConfig(config.copy(coreLogLevel = AetherLogLevel.entries[index]))
                                },
                                scaleFactor = scaleFactor
                            )
                        }
                    }
                }
            }

            if (searchQuery.isEmpty() || "Backup Restore Reset System Defaults".contains(searchQuery, ignoreCase = true)) {
                item {
                    IosSectionHeader(title = "BACKUP & SYSTEM MAINTENANCE", scaleFactor = scaleFactor)
                    IosGroupCard {
                        Column {
                            IosActionRow(
                                icon = Icons.Default.CloudUpload,
                                iconBg = Color(0xFF5856D6),
                                title = "Full Configuration Backup",
                                subtitle = "Export all settings to .astf file",
                                onClick = onExportBackup,
                                scaleFactor = scaleFactor
                            )
                            HorizontalDivider(color = IosDividerColor, thickness = 0.5.dp, modifier = Modifier.padding(start = (50 * scaleFactor).dp))
                            IosActionRow(
                                icon = Icons.Default.CloudDownload,
                                iconBg = Color(0xFF34C759),
                                title = "Restore from Backup",
                                subtitle = "Import settings from an .astf file",
                                onClick = { fullBackupPicker.launch("*/*") },
                                scaleFactor = scaleFactor
                            )
                            HorizontalDivider(color = IosDividerColor, thickness = 0.5.dp, modifier = Modifier.padding(start = (50 * scaleFactor).dp))
                            IosActionRow(
                                icon = Icons.Default.DeleteForever,
                                iconBg = Color(0xFFFF3B30),
                                title = "Reset to Factory Defaults",
                                subtitle = "Wipe all custom tweaks and restart",
                                onClick = { showResetDialog = true },
                                scaleFactor = scaleFactor,
                                titleColor = Color(0xFFFF3B30)
                            )
                        }
                    }
                }
            }

            item {
                IosSectionHeader(title = "SYSTEM STABILITY", scaleFactor = scaleFactor)
                IosGroupCard {
                    Column {
                        IosSwitchRow(
                            icon = Icons.Default.BatteryAlert,
                            iconBg = Color(0xFFFF3B30),
                            title = "Battery Optimization",
                            subtitle = "Allow AetherST to run without restrictions",
                            checked = isBatteryOptimized,
                            enabled = !isBatteryOptimized,
                            onCheckedChange = { if (it) onRequestBatteryOptimization() },
                            testTag = "switch_battery_opt",
                            scaleFactor = scaleFactor
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }

        if (showResetDialog) {
            IosConfirmationDialog(
                title = "Reset All Settings?",
                message = "This will restore all protocols, engine tweaks, and security settings to their factory defaults. This action cannot be undone.",
                confirmText = "Reset Everything",
                confirmColor = Color(0xFFFF3B30),
                onConfirm = {
                    onResetAll()
                    showResetDialog = false
                    onShowToast("System restored to defaults", false)
                },
                onDismiss = { showResetDialog = false },
                scaleFactor = scaleFactor
            )
        }
    }
}

@Composable
fun IosConfirmationDialog(
    title: String,
    message: String,
    confirmText: String,
    confirmColor: Color = Color.White,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    scaleFactor: Float = 1f
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = false) { },
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E).copy(alpha = 0.95f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = (18 * scaleFactor).sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = IosSecondaryLabel,
                        fontSize = (14 * scaleFactor).sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .weight(1f)
                                .height((50 * scaleFactor).dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
                        ) {
                            Text(
                                text = "Cancel",
                                fontWeight = FontWeight.Medium,
                                fontSize = (14 * scaleFactor).sp,
                                maxLines = 1,
                                textAlign = TextAlign.Center
                            )
                        }
                        Button(
                            onClick = onConfirm,
                            modifier = Modifier
                                .weight(1f)
                                .height((50 * scaleFactor).dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = confirmColor, contentColor = Color.White),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            Text(
                                text = confirmText,
                                fontWeight = FontWeight.Bold,
                                fontSize = (14 * scaleFactor).sp,
                                maxLines = 1,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun IosPresetItem(
    icon: ImageVector,
    iconBg: Color,
    title: String,
    subtitle: String,
    isActive: Boolean,
    onClick: () -> Unit,
    scaleFactor: Float = 1f
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = (16 * scaleFactor).dp, vertical = (14 * scaleFactor).dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IosIconBadge(icon = icon, backgroundColor = iconBg, scaleFactor = scaleFactor)
            Spacer(modifier = Modifier.width((12 * scaleFactor).dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    fontSize = (15 * scaleFactor).sp
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = IosSecondaryLabel,
                    fontSize = (11 * scaleFactor).sp
                )
            }
        }

        if (isActive) {
            Text(
                text = "Active",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = IosActiveSwitchGreen,
                fontSize = (11 * scaleFactor).sp
            )
        } else {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = IosSecondaryLabel,
                modifier = Modifier.size((18 * scaleFactor).dp)
            )
        }
    }
}

@Composable
fun IosSectionHeader(title: String, scaleFactor: Float = 1f) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = IosSecondaryLabel,
        fontSize = (11 * scaleFactor).sp,
        letterSpacing = 0.5.sp,
        modifier = Modifier.padding(start = 8.dp, bottom = (6 * scaleFactor).dp)
    )
}

@Composable
fun IosGroupCard(
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = IosCardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        content()
    }
}

@Composable
fun IosIconBadge(
    icon: ImageVector,
    backgroundColor: Color,
    scaleFactor: Float = 1f
) {
    Box(
        modifier = Modifier
            .size((30 * scaleFactor).dp)
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size((18 * scaleFactor).dp)
        )
    }
}

@Composable
fun IosSwitchRow(
    icon: ImageVector,
    iconBg: Color,
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String,
    scaleFactor: Float = 1f
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = (16 * scaleFactor).dp, vertical = (12 * scaleFactor).dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IosIconBadge(icon = icon, backgroundColor = iconBg, scaleFactor = scaleFactor)
            Spacer(modifier = Modifier.width((12 * scaleFactor).dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = if (enabled) 1f else 0.5f),
                    fontSize = (15 * scaleFactor).sp
                )
                if (!subtitle.isNullOrEmpty()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = IosSecondaryLabel.copy(alpha = if (enabled) 1f else 0.5f),
                        fontSize = (11 * scaleFactor).sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            modifier = Modifier
                .testTag(testTag)
                .graphicsLayer {
                    scaleX = scaleFactor * 0.9f
                    scaleY = scaleFactor * 0.9f
                },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = IosActiveSwitchGreen,
                checkedBorderColor = Color.Transparent,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = IosInactiveSwitchTrack,
                uncheckedBorderColor = Color.Transparent,
                disabledCheckedTrackColor = IosActiveSwitchGreen.copy(alpha = 0.5f),
                disabledCheckedThumbColor = Color.White.copy(alpha = 0.8f)
            )
        )
    }
}

@Composable
fun IosPickerRow(
    icon: ImageVector,
    iconBg: Color,
    title: String,
    value: String,
    options: List<String>,
    onOptionSelected: (Int) -> Unit,
    scaleFactor: Float = 1f,
    onClickOverride: (() -> Unit)? = null
) {
    var expanded by remember { mutableStateOf(value = false) }

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { 
                    if (onClickOverride != null) onClickOverride() else expanded = true 
                }
                .padding(horizontal = (16 * scaleFactor).dp, vertical = (14 * scaleFactor).dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IosIconBadge(icon = icon, backgroundColor = iconBg, scaleFactor = scaleFactor)
                Spacer(modifier = Modifier.width((12 * scaleFactor).dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    fontSize = (15 * scaleFactor).sp
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = IosSecondaryLabel,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                    fontSize = (13 * scaleFactor).sp
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = IosSecondaryLabel,
                    modifier = Modifier.size((18 * scaleFactor).dp)
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(IosGroupBackground)
        ) {
            options.forEachIndexed { index, option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = option,
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                            fontSize = (14 * scaleFactor).sp
                        )
                    },
                    onClick = {
                        onOptionSelected(index)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun IosActionRow(
    icon: ImageVector,
    iconBg: Color,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
    scaleFactor: Float = 1f,
    titleColor: Color = Color.White
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = (16 * scaleFactor).dp, vertical = (14 * scaleFactor).dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IosIconBadge(icon = icon, backgroundColor = iconBg, scaleFactor = scaleFactor)
            Spacer(modifier = Modifier.width((12 * scaleFactor).dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = titleColor,
                    fontSize = (15 * scaleFactor).sp
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = IosSecondaryLabel,
                        fontSize = (11 * scaleFactor).sp
                    )
                }
            }
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = IosSecondaryLabel,
            modifier = Modifier.size((18 * scaleFactor).dp)
        )
    }
}

@Composable
fun IosInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    keyboardType: KeyboardType = KeyboardType.Text,
    scaleFactor: Float = 1f
) {
    val focusManager = LocalFocusManager.current
    var isFocused by remember { mutableStateOf(false) }
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = IosSecondaryLabel,
            fontSize = (10 * scaleFactor).sp,
            modifier = Modifier.padding(bottom = 2.dp)
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .height((46 * scaleFactor).dp)
                .background(IosGroupBackground, RoundedCornerShape(10.dp))
                .border(
                    width = 1.dp,
                    color = if (isFocused) IosActiveBlue else Color.Transparent,
                    shape = RoundedCornerShape(10.dp)
                )
                .onFocusChanged { isFocused = it.isFocused }
                .testTag(testTag),
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White, fontSize = (14 * scaleFactor).sp),
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = { focusManager.clearFocus() }
            ),
            singleLine = true,
            cursorBrush = SolidColor(IosActiveBlue),
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier.padding(horizontal = (12 * scaleFactor).dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (value.isEmpty()) {
                        Text(placeholder, color = IosSecondaryLabel, fontSize = (13 * scaleFactor).sp)
                    }
                    innerTextField()
                }
            }
        )
    }
}

@Composable
fun IosInputFieldRow(
    icon: ImageVector,
    iconBg: Color,
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    keyboardType: KeyboardType = KeyboardType.Text,
    testTag: String,
    scaleFactor: Float = 1f
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = (16 * scaleFactor).dp, vertical = (12 * scaleFactor).dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IosIconBadge(icon = icon, backgroundColor = iconBg, scaleFactor = scaleFactor)
        Spacer(modifier = Modifier.width((12 * scaleFactor).dp))
        IosInputField(
            label = label,
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            placeholder = placeholder,
            keyboardType = keyboardType,
            testTag = testTag,
            scaleFactor = scaleFactor
        )
    }
}
