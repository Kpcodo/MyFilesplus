package com.mfp.filemanager.security


import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import com.mfp.filemanager.ui.screens.OnboardingScreen
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/**
 * A reusable Composable for handling permissions required by the file manager.
 * Checks for:
 * 1. Storage Permissions (MANAGE_EXTERNAL_STORAGE for A11+, READ/WRITE for A10-)
 * 2. Usage Stats Permission (for app size metrics)
 */
@Composable
fun AppPermissionHandler(
    onPermissionGranted: @Composable () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // State for permissions
    var hasStoragePermission by remember { mutableStateOf(checkStoragePermission(context)) }
    var hasUsagePermission by remember { mutableStateOf(checkUsageStatsPermission(context)) }

    // Listen for lifecycle RESUME event to re-check permissions after returning from settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasStoragePermission = checkStoragePermission(context)
                hasUsagePermission = checkUsageStatsPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    if (hasStoragePermission && hasUsagePermission) {
        onPermissionGranted()
    } else {
        // Show Onboarding Screen if any permission is missing
        val requestStorage: () -> Unit = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                 try {
                     val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                     intent.addCategory("android.intent.category.DEFAULT")
                     intent.data = Uri.fromParts("package", context.packageName, null)
                     context.startActivity(intent)
                 } catch (_: Exception) {
                     val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                     context.startActivity(intent)
                 }
            } else {
                 val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                 intent.data = Uri.fromParts("package", context.packageName, null)
                 context.startActivity(intent)
            }
        }

        val requestUsage: () -> Unit = {
             val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
             context.startActivity(intent)
        }

        OnboardingScreen(
            hasStoragePermission = hasStoragePermission,
            hasUsagePermission = hasUsagePermission,
            onRequestStorage = requestStorage,
            onRequestUsage = requestUsage,
            onContinue = {
                // Double check permissions before proceeding
                hasStoragePermission = checkStoragePermission(context)
                hasUsagePermission = checkUsageStatsPermission(context)
            }
        )
    }
}

enum class PermissionType(val displayName: String) {
    STORAGE("Storage Access"),
    USAGE_STATS("Usage Access")
}

private fun checkStoragePermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        val read = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE)
        val write = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        read == PackageManager.PERMISSION_GRANTED && write == PackageManager.PERMISSION_GRANTED
    }
}

private fun checkUsageStatsPermission(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName
        )
    } else {
        @Suppress("DEPRECATION")
        appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName
        )
    }
    return mode == AppOpsManager.MODE_ALLOWED
}
