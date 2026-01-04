package com.mfp.filemanager.utils

import android.content.Context
import android.content.pm.PackageInfo
import android.os.Build
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CrashHandler(private val context: Context) : Thread.UncaughtExceptionHandler {

    private val defaultHandler: Thread.UncaughtExceptionHandler? = Thread.getDefaultUncaughtExceptionHandler()

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        saveCrashLog(throwable)
        
        // Delegate to the system's default handler to handle the crash (e.g., show "App Stopped" dialog)
        defaultHandler?.uncaughtException(thread, throwable)
    }

    private fun saveCrashLog(throwable: Throwable) {
        try {
            val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
            val fileName = "crash_log_$timestamp.txt"
            
            val logDir = File(context.getExternalFilesDir(null), "crash_logs")
            if (!logDir.exists()) {
                logDir.mkdirs()
            }
            
            val logFile = File(logDir, fileName)
            val printWriter = PrintWriter(FileWriter(logFile))
            
            // Write Header
            printWriter.println("---- MyFiles+ Crash Log ----")
            printWriter.println("Timestamp: $timestamp")
            
            // Write Device Info
            printWriter.println("\n---- Device Info ----")
            printWriter.println("Brand: ${Build.BRAND}")
            printWriter.println("Device: ${Build.DEVICE}")
            printWriter.println("Model: ${Build.MODEL}")
            printWriter.println("SDK: ${Build.VERSION.SDK_INT}")
            printWriter.println("Manufacturer: ${Build.MANUFACTURER}")
            
            // App Info
            try {
                val pInfo: PackageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                printWriter.println("App Version Name: ${pInfo.versionName}")
                printWriter.println("App Version Code: ${pInfo.longVersionCode}")
            } catch (e: Exception) {
                printWriter.println("App Info: Unknown")
            }

            // Write Stack Trace
            printWriter.println("\n---- Stack Trace ----")
            val stringWriter = StringWriter()
            throwable.printStackTrace(PrintWriter(stringWriter))
            printWriter.println(stringWriter.toString())
            
            // Close
            printWriter.close()
            
            Log.e("CrashHandler", "Crash log saved to ${logFile.absolutePath}")
        } catch (e: Exception) {
            Log.e("CrashHandler", "Failed to save crash log", e)
        }
    }

    companion object {
        fun init(context: Context) {
            Thread.setDefaultUncaughtExceptionHandler(CrashHandler(context))
        }
    }
}
