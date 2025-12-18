package com.example.filemanager.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/**
 * A reusable Composable for handling storage permissions required by the file manager.
 *
 * This handler abstracts the logic for different Android versions:
 * - For Android 11 (R) and above, it checks for `MANAGE_EXTERNAL_STORAGE`.
 * - For Android 10 (Q) and below, it requests `READ_EXTERNAL_STORAGE` and `WRITE_EXTERNAL_STORAGE`.
 *
 * @param onPermissionGranted A Composable lambda to be executed when all required permissions are granted.
 * @param onPermissionDenied A Composable lambda to be executed when any permission is denied. It receives
 * a `requestPermission` function that can be called to trigger the permission request flow again.
 */
@Composable
fun StoragePermissionHandler(
    onPermissionGranted: @Composable () -> Unit,
    onPermissionDenied: @Composable (requestPermission: () -> Unit) -> Unit
) {
    val context = LocalContext.current

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        // Android 11+ (R) and above: MANAGE_EXTERNAL_STORAGE
        var hasManagerPermission by remember { mutableStateOf(Environment.isExternalStorageManager()) }
        val lifecycleOwner = LocalLifecycleOwner.current

        // Listen for lifecycle RESUME event to re-check permission after returning from settings
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    hasManagerPermission = Environment.isExternalStorageManager()
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }

        if (hasManagerPermission) {
            onPermissionGranted()
        } else {
            val requestPermission: () -> Unit = {
                // Navigate user to the system settings screen for the app
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.addCategory("android.intent.category.DEFAULT")
                    intent.data = Uri.fromParts("package", context.packageName, null)
                    context.startActivity(intent)
                } catch (_: Exception) {
                    // Fallback for devices that may not handle the specific intent
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    context.startActivity(intent)
                }
            }
            onPermissionDenied(requestPermission)
        }
    } else {
        // Android 10 (Q) and below: READ/WRITE_EXTERNAL_STORAGE
        val permissions = arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )

        var permissionsGranted by remember {
            mutableStateOf(
                permissions.all {
                    ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
                }
            )
        }

        val launcher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions()
        ) { permissionResults: Map<String, Boolean> ->
            // Update state based on whether all permissions were granted
            permissionsGranted = permissionResults.values.all { it }
        }

        if (permissionsGranted) {
            onPermissionGranted()
        } else {
            val requestPermission: () -> Unit = {
                launcher.launch(permissions)
            }
            onPermissionDenied(requestPermission)
        }
    }
}
