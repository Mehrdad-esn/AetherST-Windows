package io.github.immaghzbad.aetherst.ui.screens

import android.net.Uri
import java.io.File
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Block
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.immaghzbad.aetherst.model.RoutingMode
import io.github.immaghzbad.aetherst.model.RoutingRule

private val IosCardBg = Color(0xFF1C1C1E)
private val IosSecondaryLabel = Color(0xFF8E8E93)
private val IosActiveBlue = Color(0xFF007AFF)

@Composable
fun RoutingRulesScreen(
    rules: List<RoutingRule>,
    importConflictRules: List<RoutingRule>?,
    importErrorMessage: String?,
    onAddRule: (String, RoutingMode) -> Unit,
    onRemoveRule: (String) -> Unit,
    onUpdateMode: (String, RoutingMode) -> Unit,
    onClearAllRules: () -> Unit,
    onCleanPattern: (String) -> String,
    onValidatePattern: (String) -> Boolean,
    onExportRules: () -> Unit,
    onImportRules: (Uri) -> Unit,
    onResolveConflict: (List<RoutingRule>, Boolean) -> Unit,
    onCancelImport: () -> Unit,
    onClearImportError: () -> Unit,
    onShowToast: (String, Boolean) -> Unit = { _, _ -> },
    onBack: () -> Unit,
    scaleFactor: Float = 1f
) {
    val focusManager = LocalFocusManager.current

    BackHandler(onBack = onBack)

    var searchQuery by remember { mutableStateOf("") }
    var editingRule by remember { mutableStateOf<RoutingRule?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }
    var showInternalButton by remember { mutableStateOf(false) }
    var showInternalRulesDialog by remember { mutableStateOf(false) }
    
    val context = LocalContext.current
    val importInternalAsset = { assetName: String ->
        try {
            val cacheFile = File(context.cacheDir, assetName)
            context.assets.open(assetName).use { input ->
                cacheFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            onImportRules(Uri.fromFile(cacheFile))
            onShowToast("Rules imported from internal storage", false)
            showInternalRulesDialog = false
        } catch (e: Exception) {
            onShowToast("Error: ${e.message}", true)
        }
    }
    
    var ruleToDelete by remember { mutableStateOf<String?>(null) }
    var showClearAllConfirmation by remember { mutableStateOf(false) }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { onImportRules(it) }
    }

    LaunchedEffect(importErrorMessage) {
        if (importErrorMessage != null) {
            onShowToast(importErrorMessage, true)
            onClearImportError()
        }
    }

    if (showAddDialog || editingRule != null) {
        RuleEditDialog(
            initialRule = editingRule,
            onDismiss = { 
                showAddDialog = false
                editingRule = null
            },
            onConfirm = { pattern, mode ->
                if (editingRule != null) {
                    onRemoveRule(editingRule!!.pattern)
                }
                onAddRule(pattern, mode)
                showAddDialog = false
                editingRule = null
            },
            onCleanPattern = onCleanPattern,
            onValidatePattern = onValidatePattern,
            onShowToast = onShowToast,
            scaleFactor = scaleFactor
        )
    }

    if (showHelpDialog) {
        RoutingRulesHelpDialog(
            onDismiss = { showHelpDialog = false },
            scaleFactor = scaleFactor
        )
    }

    if (showInternalRulesDialog) {
        InternalRulesDialog(
            onDismiss = { showInternalRulesDialog = false },
            onImport = importInternalAsset,
            scaleFactor = scaleFactor
        )
    }

    if (importConflictRules != null) {
        RoutingImportConflictDialog(
            onReplace = { onResolveConflict(importConflictRules, true) },
            onMerge = { onResolveConflict(importConflictRules, false) },
            onCancel = onCancelImport,
            scaleFactor = scaleFactor
        )
    }

    if (ruleToDelete != null) {
        DeleteConfirmationDialog(
            title = "Delete Rule",
            message = "Are you sure you want to remove the routing rule for '${ruleToDelete}'?",
            confirmText = "Delete",
            onConfirm = {
                onRemoveRule(ruleToDelete!!)
                ruleToDelete = null
            },
            onDismiss = { ruleToDelete = null },
            scaleFactor = scaleFactor
        )
    }

    if (showClearAllConfirmation) {
        DeleteConfirmationDialog(
            title = "Clear All Rules",
            message = "This will remove all custom domain and IP routing rules. This action cannot be undone.",
            confirmText = "Clear All",
            onConfirm = {
                onClearAllRules()
                showClearAllConfirmation = false
            },
            onDismiss = { showClearAllConfirmation = false },
            scaleFactor = scaleFactor
        )
    }

    val filteredRules = remember(rules, searchQuery) {
        if (searchQuery.isEmpty()) rules else {
            rules.filter { it.pattern.contains(searchQuery, ignoreCase = true) }
        }
    }.sortedBy { it.pattern }

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
                .padding(start = (8 * scaleFactor).dp, end = (12 * scaleFactor).dp, top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size((40 * scaleFactor).dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White, modifier = Modifier.size((24 * scaleFactor).dp))
            }
            Spacer(modifier = Modifier.width((4 * scaleFactor).dp))
            Text(
                text = "Routing Rules",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.weight(1f),
                fontSize = (26 * scaleFactor).sp,
                lineHeight = (30 * scaleFactor).sp
            )
            IconButton(onClick = { showAddDialog = true }, modifier = Modifier.size((40 * scaleFactor).dp)) {
                Icon(Icons.Default.Add, null, tint = IosActiveBlue, modifier = Modifier.size((28 * scaleFactor).dp))
            }
            Box {
                IconButton(onClick = { showMenu = true }, modifier = Modifier.size((40 * scaleFactor).dp)) {
                    Icon(Icons.Default.MoreVert, null, tint = Color.White, modifier = Modifier.size((24 * scaleFactor).dp))
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier
                        .width((220 * scaleFactor).dp)
                        .background(Color(0xFF1C1C1E).copy(alpha = 0.95f), RoundedCornerShape(14.dp))
                        .border(0.5.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(14.dp))
                ) {
                    DropdownMenuItem(
                        text = { 
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, null, tint = IosActiveBlue, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("Export Backup (.astb)", color = Color.White, fontSize = 14.sp)
                            }
                        },
                        onClick = {
                            showMenu = false
                            if (rules.isEmpty()) {
                                onShowToast("No rules to export", true)
                            } else {
                                onExportRules()
                                onShowToast("Routing rules exported successfully", false)
                            }
                        }
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = 0.05f), thickness = 0.5.dp)
                    DropdownMenuItem(
                        text = { 
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Search, null, tint = IosActiveBlue, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("Import Backup (.astb)", color = Color.White, fontSize = 14.sp)
                            }
                        },
                        onClick = {
                            showMenu = false
                            filePicker.launch("*/*")
                        }
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = 0.05f), thickness = 0.5.dp)
                    DropdownMenuItem(
                        text = { 
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Info, null, tint = IosActiveBlue, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("Help & Instructions", color = Color.White, fontSize = 14.sp)
                            }
                        },
                        onClick = {
                            showMenu = false
                            showHelpDialog = true
                        }
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = 0.05f), thickness = 0.5.dp)
                    DropdownMenuItem(
                        text = { 
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Add, null, tint = IosActiveBlue, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("Show More", color = Color.White, fontSize = 14.sp)
                            }
                        },
                        onClick = {
                            showMenu = false
                            showInternalButton = true
                        }
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = 0.05f), thickness = 0.5.dp)
                    DropdownMenuItem(
                        text = { 
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Delete, null, tint = Color(0xFFFF3B30), modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("Delete All Rules", color = Color(0xFFFF3B30), fontSize = 14.sp)
                            }
                        },
                        onClick = {
                            showMenu = false
                            if (rules.isNotEmpty()) {
                                showClearAllConfirmation = true
                            } else {
                                onShowToast("List is already empty", true)
                            }
                        }
                    )
                }
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
                                Text("Search rules...", color = IosSecondaryLabel, fontSize = (13 * scaleFactor).sp)
                            }
                            innerTextField()
                        }
                    }
                }
            )
        }

        if (showInternalButton) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = (16 * scaleFactor).dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(IosActiveBlue.copy(alpha = 0.15f))
                    .clickable { showInternalRulesDialog = true }
                    .padding(vertical = (10 * scaleFactor).dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Add Internal App Rules", color = IosActiveBlue, fontWeight = FontWeight.Bold, fontSize = (14 * scaleFactor).sp)
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = (16 * scaleFactor).dp,
                end = (16 * scaleFactor).dp,
                bottom = (24 * scaleFactor).dp,
                top = 8.dp
            ),
            verticalArrangement = Arrangement.spacedBy((12 * scaleFactor).dp)
        ) {
            items(filteredRules, key = { it.pattern }) { rule ->
                RuleLineItem(
                    rule = rule,
                    onUpdateMode = { onUpdateMode(rule.pattern, it) },
                    onEdit = { editingRule = rule },
                    onDelete = { ruleToDelete = rule.pattern },
                    scaleFactor = scaleFactor
                )
            }
        }
    }
}

@Composable
private fun RuleLineItem(
    rule: RoutingRule,
    onUpdateMode: (RoutingMode) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
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
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = rule.pattern,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontSize = (16 * scaleFactor).sp,
                modifier = Modifier.weight(1f),
                maxLines = 1
            )
            Row {
                IconButton(onClick = onEdit, modifier = Modifier.size((32 * scaleFactor).dp)) {
                    Icon(Icons.Default.Edit, null, tint = IosActiveBlue.copy(alpha = 0.8f), modifier = Modifier.size((18 * scaleFactor).dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size((32 * scaleFactor).dp)) {
                    Icon(Icons.Default.Delete, null, tint = Color(0xFFFF3B30).copy(alpha = 0.8f), modifier = Modifier.size((20 * scaleFactor).dp))
                }
            }
        }

        Spacer(modifier = Modifier.height((12 * scaleFactor).dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape((10 * scaleFactor).dp))
                .padding(2.dp)
        ) {
            RoutingMode.entries.forEach { mode ->
                val isSelected = rule.mode == mode
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape((8 * scaleFactor).dp))
                        .background(if (isSelected) IosActiveBlue else Color.Transparent)
                        .clickable { onUpdateMode(mode) }
                        .padding(vertical = (8 * scaleFactor).dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = mode.name.lowercase().replaceFirstChar { it.uppercase() },
                        fontSize = (12 * scaleFactor).sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                        color = if (isSelected) Color.White else IosSecondaryLabel
                    )
                }
            }
        }
    }
}

@Composable
private fun RuleEditDialog(
    initialRule: RoutingRule?,
    onDismiss: () -> Unit,
    onConfirm: (String, RoutingMode) -> Unit,
    onCleanPattern: (String) -> String,
    onValidatePattern: (String) -> Boolean,
    onShowToast: (String, Boolean) -> Unit,
    scaleFactor: Float
) {
    var rawPattern by remember { mutableStateOf(initialRule?.pattern ?: "") }
    var selectedMode by remember {
        mutableStateOf(initialRule?.mode ?: RoutingMode.TUNNEL)
    }
    var error by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF1C1C1E))
                    .clickable(enabled = false) { }
                    .padding(24.dp)
            ) {
                Text(
                    text = if (initialRule == null) "Add Routing Rule" else "Edit Routing Rule",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = (18 * scaleFactor).sp
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    "Define how Aether handles specific traffic. Rules match by domain, IP, or CIDR. Use prefixes like 'keyword:' for partial matches.",
                    fontSize = (13 * scaleFactor).sp,
                    color = IosSecondaryLabel,
                    lineHeight = (18 * scaleFactor).sp
                )
                
                Spacer(modifier = Modifier.height(20.dp))
                
                BasicTextField(
                    value = rawPattern,
                    onValueChange = { 
                        rawPattern = it
                        error = null
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height((48 * scaleFactor).dp)
                        .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White, fontSize = (15 * scaleFactor).sp),
                    singleLine = true,
                    cursorBrush = SolidColor(IosActiveBlue),
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (rawPattern.isEmpty()) {
                                Text("google.com, 1.1.1.1, or keyword:ads", color = IosSecondaryLabel, fontSize = (14 * scaleFactor).sp)
                            }
                            innerTextField()
                        }
                    }
                )

                AnimatedVisibility(visible = error != null) {
                    Text(
                        text = error ?: "",
                        color = Color(0xFFFF3B30),
                        fontSize = (11 * scaleFactor).sp,
                        modifier = Modifier.padding(top = 6.dp, start = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height((24 * scaleFactor).dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .padding(2.dp)
                ) {
                    RoutingMode.entries.forEach { mode ->
                        val isSelected = selectedMode == mode
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) IosActiveBlue else Color.Transparent)
                                .clickable { selectedMode = mode }
                                .padding(vertical = (10 * scaleFactor).dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = mode.name.lowercase().replaceFirstChar { it.uppercase() },
                                fontSize = (13 * scaleFactor).sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                color = if (isSelected) Color.White else IosSecondaryLabel
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.textButtonColors(contentColor = IosSecondaryLabel)
                    ) {
                        Text("Cancel", fontWeight = FontWeight.Medium)
                    }
                    Button(
                        onClick = {
                            val cleaned = onCleanPattern(rawPattern)
                            if (onValidatePattern(cleaned)) {
                                onConfirm(cleaned, selectedMode)
                            } else {
                                error = "Invalid format or characters detected"
                                onShowToast("Please enter valid English pattern", true)
                            }
                        },
                        modifier = Modifier.weight(1f).height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = IosActiveBlue, contentColor = Color.White)
                    ) {
                        Text(if (initialRule == null) "Add" else "Save", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun RoutingImportConflictDialog(
    onReplace: () -> Unit,
    onMerge: () -> Unit,
    onCancel: () -> Unit,
    scaleFactor: Float
) {
    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onCancel
                )
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF1C1C1E))
                    .clickable(enabled = false) { }
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Import Conflict",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = (18 * scaleFactor).sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Some imported rules already exist in your list. How would you like to proceed?",
                    color = IosSecondaryLabel,
                    fontSize = (14 * scaleFactor).sp,
                    textAlign = TextAlign.Center,
                    lineHeight = (20 * scaleFactor).sp
                )
                Spacer(modifier = Modifier.height(28.dp))
                
                Button(
                    onClick = onReplace,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3B30), contentColor = Color.White)
                ) {
                    Text("Delete Old & Replace", fontWeight = FontWeight.Bold)
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Button(
                    onClick = onMerge,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = IosActiveBlue, contentColor = Color.White)
                ) {
                    Text("Merge (Keep Both)", fontWeight = FontWeight.Bold)
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                TextButton(
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    Text("Cancel Import", color = IosSecondaryLabel)
                }
            }
        }
    }
}

@Composable
private fun RoutingRulesHelpDialog(
    onDismiss: () -> Unit,
    scaleFactor: Float
) {
    val scrollState = rememberScrollState()
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.85f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF1C1C1E))
                    .clickable(enabled = false) { }
                    .padding(24.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Rule,
                        null,
                        tint = IosActiveBlue,
                        modifier = Modifier.size((22 * scaleFactor).dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        "Routing Instructions",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = (18 * scaleFactor).sp
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    HelpSection(
                        title = "What is Domain & IP Routing?",
                        desc = "Control internet destinations globally. Unlike app-based Split Tunneling, these rules apply to all traffic regardless of which app sends it.",
                        icon = Icons.Default.Info,
                        color = IosActiveBlue,
                        scaleFactor = scaleFactor
                    )

                    HelpSection(
                        title = "Routing Modes",
                        desc = "• Tunnel: Route through Aether (Default).\n• Direct: Bypass Aether and use the device network.\n• Block: Kill the connection (Ad-blocking).",
                        icon = Icons.Default.Security,
                        color = Color(0xFF34C759),
                        scaleFactor = scaleFactor
                    )

                    HelpSection(
                        title = "Smart Prefixes",
                        desc = "• domain: Matches exact domain or subdomains.\n• ip: Matches specific IP or CIDR ranges.\n• keyword: Matches if the URL contains this text.\n• regexp: Advanced Regular Expression matching.",
                        icon = Icons.Default.Public,
                        color = Color(0xFF5856D6),
                        scaleFactor = scaleFactor
                    )

                    HelpSection(
                        title = "Formatting Tips",
                        desc = "• Do not include http:// or https://\n• Use only English characters and symbols\n• Port matching is supported: example.com:443",
                        icon = Icons.Default.Block,
                        color = Color(0xFFFF3B30),
                        scaleFactor = scaleFactor
                    )
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                            .padding(16.dp)
                    ) {
                        Text(
                            "Note: Protocols (http:// or https://) are automatically removed upon saving. The engine routes based on the core domain or IP identity.",
                            style = MaterialTheme.typography.bodySmall,
                            color = IosSecondaryLabel,
                            lineHeight = 16.sp,
                            fontSize = (11 * scaleFactor).sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = IosActiveBlue, contentColor = Color.White)
                ) {
                    Text("Got it", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun HelpSection(
    title: String,
    desc: String,
    icon: ImageVector,
    color: Color,
    scaleFactor: Float
) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size((36 * scaleFactor).dp)
                .background(color.copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size((20 * scaleFactor).dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(title, color = color, fontWeight = FontWeight.Bold, fontSize = (15 * scaleFactor).sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(desc, color = IosSecondaryLabel, fontSize = (13 * scaleFactor).sp, lineHeight = 18.sp)
        }
    }
}

@Composable
private fun DeleteConfirmationDialog(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    scaleFactor: Float
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF1C1C1E))
                    .clickable(enabled = false) { }
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.Delete,
                    null,
                    tint = Color(0xFFFF3B30),
                    modifier = Modifier.size((32 * scaleFactor).dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = (18 * scaleFactor).sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    message,
                    color = IosSecondaryLabel,
                    fontSize = (14 * scaleFactor).sp,
                    textAlign = TextAlign.Center,
                    lineHeight = (20 * scaleFactor).sp
                )
                Spacer(modifier = Modifier.height(28.dp))
                
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3B30), contentColor = Color.White)
                ) {
                    Text(confirmText, fontWeight = FontWeight.Bold)
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    Text("Cancel", color = IosSecondaryLabel)
                }
            }
        }
    }
}

@Composable
private fun InternalRulesDialog(
    onDismiss: () -> Unit,
    onImport: (String) -> Unit,
    scaleFactor: Float
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF1C1C1E))
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Internal Routing Rules",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = (18 * scaleFactor).sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                InternalRuleButton(
                    title = "Iran Direct Rules",
                    desc = "Bypass tunnel for Iranian domains and IP ranges to improve speed and local access.",
                    onClick = { onImport("iran-direct-domains-ipv4-ipv6.astb") },
                    scaleFactor = scaleFactor
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                InternalRuleButton(
                    title = "Adult Content Block",
                    desc = "Restrict access to adult-oriented domains for a safer browsing experience.",
                    onClick = { onImport("adult-content-block.astb") },
                    scaleFactor = scaleFactor
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                InternalRuleButton(
                    title = "Ads & DNS Block",
                    desc = "Block common advertisement domains and public DNS trackers for better privacy.",
                    onClick = { onImport("ads-and-public-dns-block.astb") },
                    scaleFactor = scaleFactor
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    Text("Close", color = IosActiveBlue, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun InternalRuleButton(
    title: String,
    desc: String,
    onClick: () -> Unit,
    scaleFactor: Float
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .clickable { onClick() }
            .padding(16.dp)
    ) {
        Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = (15 * scaleFactor).sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text(desc, color = IosSecondaryLabel, fontSize = (12 * scaleFactor).sp, lineHeight = 16.sp)
    }
}
