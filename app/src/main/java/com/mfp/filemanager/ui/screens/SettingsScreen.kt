package com.mfp.filemanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import com.mfp.filemanager.ui.viewmodels.SettingsViewModel
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.alpha
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.remember
import android.widget.Toast
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    showTopBar: Boolean = true,
    onBack: () -> Unit
) {
    val state by viewModel.settingsState.collectAsState()
    val updateState by viewModel.updateState.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current

    // Handle Side Effects for Toast messages
    androidx.compose.runtime.LaunchedEffect(updateState) {
        when (val s = updateState) {
            is com.mfp.filemanager.ui.viewmodels.UpdateCheckState.UpToDate -> {
                Toast.makeText(context, "You are up to date!", Toast.LENGTH_SHORT).show()
                viewModel.resetUpdateState()
            }
            is com.mfp.filemanager.ui.viewmodels.UpdateCheckState.Error -> {
                Toast.makeText(context, s.message, Toast.LENGTH_LONG).show()
                viewModel.resetUpdateState()
            }
            else -> {}
        }
    }

    when (val s = updateState) {
        is com.mfp.filemanager.ui.viewmodels.UpdateCheckState.UpdateAvailable -> {
            UpdateAvailableDialog(
                release = s.release,
                onDismiss = { viewModel.resetUpdateState() },
                onUpdate = {
                    viewModel.downloadUpdate(s.release, context)
                }
            )
        }
        is com.mfp.filemanager.ui.viewmodels.UpdateCheckState.Downloading -> {
            DownloadProgressDialog(progress = s.progress)
        }
        is com.mfp.filemanager.ui.viewmodels.UpdateCheckState.DownloadFinished -> {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { viewModel.resetUpdateState() },
                title = { Text("Update Ready") },
                text = { Text("The update has been downloaded and is ready to install.") },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = { viewModel.installApk(context, s.file) }) {
                        Text("Install Now")
                    }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = { viewModel.resetUpdateState() }) {
                        Text("Cancel")
                    }
                }
            )
        }
        else -> {}
    }

    Scaffold(
        topBar = {
            if (showTopBar) {
                TopAppBar(
                    title = { Text("Settings") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Appearance Section
            SettingsSection(title = "Appearance") {
                Text("Theme", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.themeMode == 0,
                        onClick = { viewModel.setThemeMode(0) },
                        label = { Text("System") },
                        leadingIcon = if (state.themeMode == 0) { { Icon(Icons.Default.Check, null) } } else null
                    )
                    FilterChip(
                        selected = state.themeMode == 1,
                        onClick = { viewModel.setThemeMode(1) },
                        label = { Text("Light") },
                        leadingIcon = if (state.themeMode == 1) { { Icon(Icons.Default.Check, null) } } else null
                    )
                    FilterChip(
                        selected = state.themeMode == 2,
                        onClick = { viewModel.setThemeMode(2) },
                        label = { Text("Dark") },
                        leadingIcon = if (state.themeMode == 2) { { Icon(Icons.Default.Check, null) } } else null
                    )
                    FilterChip(
                        selected = state.themeMode == 3,
                        onClick = { viewModel.setThemeMode(3) },
                        label = { Text("AMOLED") },
                        leadingIcon = if (state.themeMode == 3) { { Icon(Icons.Default.Check, null) } } else null
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text("Accent Color", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(8.dp))
                ColorPicker(
                    selectedColor = state.accentColor,
                    onColorSelected = { viewModel.setAccentColor(it) }
                )


                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Use Background Blur", style = MaterialTheme.typography.bodyLarge)
                    Switch(
                        checked = state.isBlurEnabled,
                        onCheckedChange = { viewModel.toggleBlurEnabled(it) }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val speedLabel = when(state.animationSpeed) {
                       0.5f -> "Slow (0.5x)"
                       1.0f -> "Normal (1.0x)"
                       1.5f -> "Fast (1.5x)"
                       2.0f -> "Very Fast (2.0x)"
                       else -> "${state.animationSpeed}x"
                    }
                    Text("Animation Speed: $speedLabel", style = MaterialTheme.typography.bodyMedium)
                    if (state.animationSpeed != 1.0f) {
                        IconButton(onClick = { viewModel.setAnimationSpeed(1.0f) }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Reset Speed")
                        }
                    }
                }
                Slider(
                    value = state.animationSpeed,
                    onValueChange = { viewModel.setAnimationSpeed(it) },
                    valueRange = 0.1f..10.0f,
                    steps = 98 
                )

                Spacer(modifier = Modifier.height(16.dp))
                val isIconSizeEditable = state.viewMode == 0 || state.viewMode == 2 
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Icon Size: ${(state.iconSize * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (state.iconSize != 1.0f && isIconSizeEditable) {
                        IconButton(onClick = { viewModel.setIconSize(1.0f) }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Reset Icon Size")
                        }
                    }
                }
                Slider(
                    value = state.iconSize,
                    onValueChange = { viewModel.setIconSize(it) },
                    valueRange = 0.5f..1.5f,
                    steps = 9,
                    enabled = isIconSizeEditable
                )

                Spacer(modifier = Modifier.height(16.dp))
                Text("Layout Mode", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = state.viewMode == 0,
                        onClick = { viewModel.setViewMode(0) },
                        label = { Text("List") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.List, "List") },
                        trailingIcon = if (state.viewMode == 0) { { Icon(Icons.Default.Check, null) } } else null
                    )
                    FilterChip(
                        selected = state.viewMode == 2,
                        onClick = { viewModel.setViewMode(2) },
                        label = { Text("Compact") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.ViewList, "Compact") },
                        trailingIcon = if (state.viewMode == 2) { { Icon(Icons.Default.Check, null) } } else null
                    )
                    FilterChip(
                        selected = state.viewMode == 1,
                        onClick = { viewModel.setViewMode(1) },
                        label = { Text("Grid") },
                        leadingIcon = { Icon(Icons.Default.GridView, "Grid") },
                        trailingIcon = if (state.viewMode == 1) { { Icon(Icons.Default.Check, null) } } else null
                    )
                    FilterChip(
                        selected = state.viewMode == 3,
                        onClick = { viewModel.setViewMode(3) },
                        label = { Text("Large Grid") },
                        leadingIcon = { Icon(Icons.Default.ViewModule, "Large Grid") },
                        trailingIcon = if (state.viewMode == 3) { { Icon(Icons.Default.Check, null) } } else null
                    )
                }
            }

            HorizontalDivider()

            // View Options
            SettingsSection(title = "View Options") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Show Hidden Files", style = MaterialTheme.typography.bodyLarge)
                    Switch(
                        checked = state.showHiddenFiles,
                        onCheckedChange = { viewModel.toggleShowHiddenFiles(it) }
                    )
                }
            }

            HorizontalDivider()

            // Navigation Section
            SettingsSection(title = "Navigation") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Enable Swipe Navigation", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Swipe between screens/ tabs",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = state.isSwipeNavigationEnabled,
                        onCheckedChange = { viewModel.toggleSwipeNavigation(it) }
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                // Swipe to Delete Config
                val isSwipeDeleteBlocked = state.isSwipeNavigationEnabled
                Row(
                    modifier = Modifier.fillMaxWidth().alpha(if (isSwipeDeleteBlocked) 0.5f else 1f),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Swipe to Delete", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (isSwipeDeleteBlocked) "Disabled by Swipe Navigation" else "Swipe items to move them to Bin",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = state.swipeDeleteEnabled,
                        onCheckedChange = { viewModel.toggleSwipeDelete(it) },
                        enabled = !isSwipeDeleteBlocked
                    )
                }

                if (state.swipeDeleteEnabled) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Swipe Direction", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        FilterChip(
                            selected = state.swipeDeleteDirection == 0, // Left
                            onClick = { viewModel.setSwipeDeleteDirection(0) },
                            label = { Text("Swipe Left") },
                            leadingIcon = if (state.swipeDeleteDirection == 0) { { Icon(Icons.Default.Check, null) } } else null
                        )
                        FilterChip(
                            selected = state.swipeDeleteDirection == 1, // Right
                            onClick = { viewModel.setSwipeDeleteDirection(1) },
                            label = { Text("Swipe Right") },
                            leadingIcon = if (state.swipeDeleteDirection == 1) { { Icon(Icons.Default.Check, null) } } else null
                        )
                    }
                }
            }
            
            HorizontalDivider()

            // Trash Bin Policy
            SettingsSection(title = "Trash Bin Policy") {
                Text(
                    "Remove deleted files after:", 
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val days = state.trashRetentionDays
                    val presetValues = listOf(-1, 30, 180, 365)
                    val isCustom = days !in presetValues

                    FilterChip(
                        selected = days == -1,
                        onClick = { viewModel.setTrashRetentionDays(-1) },
                        label = { Text("Off") },
                        leadingIcon = if (days == -1) { { Icon(Icons.Default.Check, null) } } else null
                    )
                    FilterChip(
                        selected = days == 30,
                        onClick = { viewModel.setTrashRetentionDays(30) },
                        label = { Text("1 Month") },
                        leadingIcon = if (days == 30) { { Icon(Icons.Default.Check, null) } } else null
                    )
                    FilterChip(
                        selected = days == 180,
                        onClick = { viewModel.setTrashRetentionDays(180) },
                        label = { Text("6 Months") },
                        leadingIcon = if (days == 180) { { Icon(Icons.Default.Check, null) } } else null
                    )
                    FilterChip(
                        selected = days == 365,
                        onClick = { viewModel.setTrashRetentionDays(365) },
                        label = { Text("12 Months") },
                        leadingIcon = if (days == 365) { { Icon(Icons.Default.Check, null) } } else null
                    )
                    FilterChip(
                        selected = isCustom,
                        onClick = { if (!isCustom) viewModel.setTrashRetentionDays(90) },
                        label = { Text("Custom") },
                        leadingIcon = if (isCustom) { { Icon(Icons.Default.Check, null) } } else null
                    )
                }
                
                if (state.trashRetentionDays != -1 && state.trashRetentionDays !in listOf(30, 180, 365)) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Custom Duration: ${state.trashRetentionDays} days", 
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Slider(
                        value = state.trashRetentionDays.toFloat(),
                        onValueChange = { viewModel.setTrashRetentionDays(it.toInt()) },
                        valueRange = 1f..730f, 
                        steps = 0 
                    )
                } else {
                     Spacer(modifier = Modifier.height(8.dp))
                     val infoText = if (state.trashRetentionDays == -1) {
                         "Auto-deletion is disabled. Files will stay in Bin until manually deleted."
                     } else {
                         "Files will be permanently deleted after ${state.trashRetentionDays} days."
                     }
                     Text(
                        infoText, 
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            HorizontalDivider()

            // App Updates Section
            SettingsSection(title = "App Updates") {
                val packageInfo = remember(context) {
                    try {
                        context.packageManager.getPackageInfo(context.packageName, 0)
                    } catch (e: Exception) {
                        null
                    }
                }
                val versionName = packageInfo?.versionName ?: "Unknown"

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Current Version: $versionName", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Check for latest updates",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = { viewModel.checkForUpdates(versionName) },
                        enabled = updateState !is com.mfp.filemanager.ui.viewmodels.UpdateCheckState.Checking
                    ) {
                        if (updateState is com.mfp.filemanager.ui.viewmodels.UpdateCheckState.Checking) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Check")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun UpdateAvailableDialog(
    release: com.mfp.filemanager.data.GitHubRelease,
    onDismiss: () -> Unit,
    onUpdate: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Update Available") },
        text = {
            Column {
                Text(
                    text = "A new version ${release.tagName} is available.",
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Release Notes:",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = release.body,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 5,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onUpdate) {
                Text("Update")
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text("Not Now")
            }
        }
    )
}

@Composable
fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(16.dp))
        content()
    }
}

@Composable
fun ColorPicker(selectedColor: Int, onColorSelected: (Int) -> Unit) {
    val colors = listOf(
        0xFF6650a4, // Purple
        0xFF006C4C, // Green
        0xFFB3261E, // Red
        0xFFEDC200, // Yellow/Amber
        0xFF2196F3, // Blue
        0xFF00BCD4, // Cyan
        0xFFE91E63  // Pink
    )

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        colors.forEach { colorLong ->
            val colorInt = colorLong.toInt()
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(colorInt))
                    .clickable { onColorSelected(colorInt) }
            ) {
                if (selectedColor == colorInt) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = Color.White,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }
    }
}
@Composable
fun DownloadProgressDialog(progress: Float) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = { },
        title = { Text("Downloading Update") },
        text = {
            Column {
                Text("Please wait while the update is downloaded...")
                Spacer(modifier = Modifier.height(16.dp))
                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "${(progress * 100).toInt()}%",
                    modifier = Modifier.align(Alignment.End),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {}
    )
}
