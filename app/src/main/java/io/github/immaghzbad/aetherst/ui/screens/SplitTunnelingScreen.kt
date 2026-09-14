package io.github.immaghzbad.aetherst.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.immaghzbad.aetherst.model.AppInfo

private val IosCardBg = Color(0xFF1C1C1E)
private val IosSecondaryLabel = Color(0xFF8E8E93)
private val IosActiveBlue = Color(0xFF007AFF)

@Composable
fun SplitTunnelingScreen(
    apps: List<AppInfo>,
    excludedPackages: Set<String>,
    blockedPackages: Set<String>,
    onUpdateMode: (String, Int) -> Unit,
    onBack: () -> Unit,
    scaleFactor: Float = 1f
) {
    val focusManager = LocalFocusManager.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(0) }
    var showHelpDialog by remember { mutableStateOf(false) }
    var animateDialog by remember { mutableStateOf(false) }

    BackHandler(onBack = {
        if (showHelpDialog) {
            animateDialog = false
        } else {
            onBack()
        }
    })

    if (showHelpDialog) {
        SplitTunnelHelpDialog(
            visible = animateDialog,
            onDismiss = { animateDialog = false },
            onTransitionEnd = { 
                showHelpDialog = false 
            },
            scaleFactor = scaleFactor
        )
    }

    LaunchedEffect(showHelpDialog) {
        if (showHelpDialog) {
            animateDialog = true
        }
    }

    val filteredApps = remember(apps, searchQuery, selectedTab, excludedPackages, blockedPackages) {
        apps.filter { app ->
            val matchesTab = if (selectedTab == 0) !app.isSystemApp else app.isSystemApp
            val matchesSearch = searchQuery.isEmpty() || 
                               app.name.contains(searchQuery, ignoreCase = true) || 
                               app.packageName.contains(searchQuery, ignoreCase = true)
            matchesTab && matchesSearch
        }.sortedWith(
            compareByDescending<AppInfo> { excludedPackages.contains(it.packageName) || blockedPackages.contains(it.packageName) }
                .thenBy { it.name.lowercase() }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { focusManager.clearFocus() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = (8 * scaleFactor).dp, end = (16 * scaleFactor).dp, top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size((40 * scaleFactor).dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White, modifier = Modifier.size((24 * scaleFactor).dp))
            }
            Spacer(modifier = Modifier.width((4 * scaleFactor).dp))
            Text(
                text = "Split Tunneling",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.weight(1f),
                fontSize = (26 * scaleFactor).sp,
                lineHeight = (30 * scaleFactor).sp
            )
            IconButton(onClick = { showHelpDialog = true }, modifier = Modifier.size((40 * scaleFactor).dp)) {
                Icon(Icons.Default.Info, null, tint = IosActiveBlue, modifier = Modifier.size((24 * scaleFactor).dp))
            }
        }

        Box(modifier = Modifier.padding(horizontal = (16 * scaleFactor).dp, vertical = (8 * scaleFactor).dp)) {
            BasicTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height((44 * scaleFactor).dp)
                    .background(IosCardBg, RoundedCornerShape(12.dp)),
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White, fontSize = (14 * scaleFactor).sp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                singleLine = true,
                cursorBrush = SolidColor(IosActiveBlue),
                decorationBox = { innerTextField ->
                    Row(
                        modifier = Modifier.padding(horizontal = (12 * scaleFactor).dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Search, null, tint = IosSecondaryLabel, modifier = Modifier.size((20 * scaleFactor).dp))
                        Spacer(modifier = Modifier.width((8 * scaleFactor).dp))
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (searchQuery.isEmpty()) {
                                Text("Search applications...", color = IosSecondaryLabel, fontSize = (13 * scaleFactor).sp)
                            }
                            innerTextField()
                        }
                    }
                }
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = (16 * scaleFactor).dp, vertical = (12 * scaleFactor).dp)
                .background(IosCardBg, RoundedCornerShape(10.dp))
                .padding(2.dp)
        ) {
            listOf("User Apps", "System Apps").forEachIndexed { index, title ->
                val isSelected = selectedTab == index
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) IosActiveBlue else Color.Transparent)
                        .clickable { selectedTab = index }
                        .padding(vertical = (8 * scaleFactor).dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) Color.White else IosSecondaryLabel,
                        fontSize = (12 * scaleFactor).sp
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = (16 * scaleFactor).dp,
                end = (16 * scaleFactor).dp,
                bottom = (24 * scaleFactor).dp
            ),
            verticalArrangement = Arrangement.spacedBy((12 * scaleFactor).dp)
        ) {
            items(filteredApps, key = { it.packageName }) { app ->
                val mode = when {
                    excludedPackages.contains(app.packageName) -> 1
                    blockedPackages.contains(app.packageName) -> 2
                    else -> 0
                }
                AppLineItem(
                    app = app,
                    mode = mode,
                    onUpdateMode = { modeIndex ->
                        onUpdateMode(app.packageName, modeIndex)
                    },
                    scaleFactor = scaleFactor
                )
            }
        }
    }
}

@Composable
private fun AppLineItem(
    app: AppInfo,
    mode: Int,
    onUpdateMode: (Int) -> Unit,
    scaleFactor: Float
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(IosCardBg, RoundedCornerShape((16 * scaleFactor).dp))
            .padding((12 * scaleFactor).dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val bitmap = remember(app.packageName) { app.icon?.toBitmap()?.asImageBitmap() }
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    modifier = Modifier
                        .size((44 * scaleFactor).dp)
                        .clip(RoundedCornerShape((10 * scaleFactor).dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size((44 * scaleFactor).dp)
                        .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape((10 * scaleFactor).dp))
                )
            }
            
            Spacer(modifier = Modifier.width((14 * scaleFactor).dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = (16 * scaleFactor).sp,
                    maxLines = 1
                )
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.labelSmall,
                    color = IosSecondaryLabel,
                    fontSize = (11 * scaleFactor).sp,
                    maxLines = 1
                )
            }
        }

        Spacer(modifier = Modifier.height((12 * scaleFactor).dp))

        ThreeStateSelector(
            currentMode = mode,
            onModeSelected = onUpdateMode,
            scaleFactor = scaleFactor
        )
    }
}

@Composable
private fun ThreeStateSelector(
    currentMode: Int,
    onModeSelected: (Int) -> Unit,
    scaleFactor: Float
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape((10 * scaleFactor).dp))
            .padding(2.dp)
    ) {
        listOf("Tunnel", "Bypass", "Blocked").forEachIndexed { index, label ->
            val isSelected = currentMode == index
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape((8 * scaleFactor).dp))
                    .background(if (isSelected) IosActiveBlue else Color.Transparent)
                    .clickable { onModeSelected(index) }
                    .padding(vertical = (8 * scaleFactor).dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    fontSize = (12 * scaleFactor).sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (isSelected) Color.White else IosSecondaryLabel
                )
            }
        }
    }
}

@Composable
private fun SplitTunnelHelpDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    onTransitionEnd: () -> Unit,
    scaleFactor: Float
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(300)) + scaleIn(tween(300), initialScale = 0.9f),
            exit = fadeOut(tween(250)) + scaleOut(tween(250), targetScale = 0.9f)
        ) {
            DisposableEffect(Unit) {
                onDispose {
                    onTransitionEnd()
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding((12 * scaleFactor).dp)
                        .clip(RoundedCornerShape((24 * scaleFactor).dp))
                        .background(Color(0xFF1C1C1E).copy(alpha = 0.98f))
                        .clickable(enabled = false) {}
                        .padding((24 * scaleFactor).dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Default.Info,
                            null,
                            tint = IosActiveBlue,
                            modifier = Modifier.size((22 * scaleFactor).dp)
                        )
                        Spacer(modifier = Modifier.width((8 * scaleFactor).dp))
                        Text(
                            "Split Tunnel Modes",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = (20 * scaleFactor).sp
                        )
                    }

                    Spacer(modifier = Modifier.height((24 * scaleFactor).dp))

                    Column(verticalArrangement = Arrangement.spacedBy((18 * scaleFactor).dp)) {
                        HelpItem(
                            title = "Tunnel",
                            desc = "Full protection. All traffic for this app is encrypted and routed through the Aether secure tunnel.",
                            icon = Icons.Default.Security,
                            color = IosActiveBlue,
                            scaleFactor = scaleFactor
                        )
                        HelpItem(
                            title = "Bypass",
                            desc = "Direct access (Default). Uses your local network for maximum speed and compatibility with local apps.",
                            icon = Icons.Default.Public,
                            color = Color(0xFF34C759),
                            scaleFactor = scaleFactor
                        )
                        HelpItem(
                            title = "Blocked",
                            desc = "Total isolation. This app will have no internet access, ensuring zero data leakage.",
                            icon = Icons.Default.Block,
                            color = Color(0xFFFF3B30),
                            scaleFactor = scaleFactor
                        )
                    }

                    Spacer(modifier = Modifier.height((20 * scaleFactor).dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFFFD60A).copy(alpha = 0.1f), RoundedCornerShape((12 * scaleFactor).dp))
                            .padding((12 * scaleFactor).dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Default.Info,
                            null,
                            tint = Color(0xFFFFD60A),
                            modifier = Modifier.size((18 * scaleFactor).dp)
                        )
                        Spacer(modifier = Modifier.width((10 * scaleFactor).dp))
                        Column {
                            Text(
                                "Engine Requirement",
                                color = Color(0xFFFFD60A),
                                fontWeight = FontWeight.Bold,
                                fontSize = (14 * scaleFactor).sp
                            )
                            Text(
                                "Blocked mode is enforced by the compatible packet engine automatically.",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = (12 * scaleFactor).sp,
                                lineHeight = (17 * scaleFactor).sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height((12 * scaleFactor).dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFFFD60A).copy(alpha = 0.1f), RoundedCornerShape((12 * scaleFactor).dp))
                            .padding((12 * scaleFactor).dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Default.Info,
                            null,
                            tint = Color(0xFFFFD60A),
                            modifier = Modifier.size((18 * scaleFactor).dp)
                        )
                        Spacer(modifier = Modifier.width((10 * scaleFactor).dp))
                        Column {
                            Text(
                                "System Requirement",
                                color = Color(0xFFFFD60A),
                                fontWeight = FontWeight.Bold,
                                fontSize = (14 * scaleFactor).sp
                            )
                            Text(
                                "Android 10 and newer use the platform connection-owner API. Android 8 and 9 use the compatibility resolver.",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = (12 * scaleFactor).sp,
                                lineHeight = (17 * scaleFactor).sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height((28 * scaleFactor).dp))

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height((50 * scaleFactor).dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = IosActiveBlue,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape((14 * scaleFactor).dp)
                    ) {
                        Text(
                            "Got it",
                            fontWeight = FontWeight.Bold,
                            fontSize = (16 * scaleFactor).sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HelpItem(
    title: String,
    desc: String,
    icon: ImageVector,
    color: Color,
    scaleFactor: Float
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size((40 * scaleFactor).dp)
                .background(color.copy(alpha = 0.15f), RoundedCornerShape((12 * scaleFactor).dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size((22 * scaleFactor).dp)
            )
        }
        
        Spacer(modifier = Modifier.width((16 * scaleFactor).dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = color,
                fontWeight = FontWeight.Bold,
                fontSize = (16 * scaleFactor).sp
            )
            Spacer(modifier = Modifier.height((4 * scaleFactor).dp))
            Text(
                text = desc,
                color = Color.White.copy(alpha = 0.6f),
                fontSize = (13 * scaleFactor).sp,
                lineHeight = (19 * scaleFactor).sp
            )
        }
    }
}
