package com.mfp.filemanager.ui.viewmodels

import android.graphics.PointF
import com.mfp.filemanager.R
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mfp.filemanager.data.FileRepository
import com.mfp.filemanager.data.StorageInfo
import com.mfp.filemanager.data.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import java.text.SimpleDateFormat
import java.util.*

import kotlinx.coroutines.delay

class StorageForecastViewModel(
    private val repository: FileRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _storageInfo = MutableStateFlow(StorageInfo.EMPTY)
    val storageInfo: StateFlow<StorageInfo> = _storageInfo.asStateFlow()

    private val _dailyUsageRate = MutableStateFlow(0L)
    val dailyUsageRate: StateFlow<Long> = _dailyUsageRate.asStateFlow()

    private val _forecastStatus = MutableStateFlow("Initializing AI...")
    val estimatedFullDate: StateFlow<String> = _forecastStatus.asStateFlow()

    private val _projectionPoints = MutableStateFlow<List<PointF>>(emptyList())
    val projectionPoints: StateFlow<List<PointF>> = _projectionPoints.asStateFlow()

    fun loadData() {
        viewModelScope.launch {
            // Start all tasks in parallel
            val infoDef = async { repository.getStorageInfo() }
            // Await basic info
            val info = infoDef.await()
            _storageInfo.value = info
            
            // 1. Record Snapshot for AI Training
            repository.recordStorageSnapshot(info)
            
            // 2. Fetch History & Train AI
            val history = repository.getStorageHistory()
            
            // AI Analysis
            if (history.size < 3) {
                // Cold Start / Insufficient Data
                _forecastStatus.value = "AI Learning..."
                _dailyUsageRate.value = 0
                generateProjectionPoints(info, emptyList(), 0, null)
                
                // For demo purposes, if it's truly empty, we can create fake data 
                // so the user sees something immediately.
                if (history.isEmpty()) { 
                     repository.debugInjectFakeHistory()
                     // Retry load once
                     loadData()
                     return@launch
                }
            } else {
                trainLinearRegressionModel(history, info.totalBytes)
            }
        }
    }

    private fun trainLinearRegressionModel(history: List<com.mfp.filemanager.data.StorageSnapshot>, totalBytes: Long) {
        // Linear Regression: y = mx + b
        // x = time (days from start), y = used bytes
        
        val sortedHistory = history.sortedBy { it.timestamp }
        val startTime = sortedHistory.first().timestamp
        
        val n = sortedHistory.size
        var sumX = 0.0
        var sumY = 0.0
        var sumXY = 0.0
        var sumX2 = 0.0

        val xValues = mutableListOf<Double>()
        val yValues = mutableListOf<Long>()

        for (snapshot in sortedHistory) {
            val x = (snapshot.timestamp - startTime) / (1000.0 * 60 * 60 * 24) // Days
            val y = snapshot.usedBytes
            
            xValues.add(x)
            yValues.add(y)
            
            sumX += x
            sumY += y.toDouble()
            sumXY += x * y
            sumX2 += x * x
        }

            val denominator = (n * sumX2) - (sumX * sumX)
        var slope = 0.0
        
        if (denominator != 0.0) {
            slope = ((n * sumXY) - (sumX * sumY)) / denominator
        }

        // Fallback if slope is non-positive (stable or deleting files): Use 50MB/day default
        if (slope <= 0) {
             slope = (50L * 1024 * 1024).toDouble()
        }

        _dailyUsageRate.value = slope.toLong()

        // Calculate Days until Full
        val currentUsed = storageInfo.value.usedBytes
        val remainingBytes = totalBytes - currentUsed
        val daysLeft = (remainingBytes / slope).toLong()
        
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, daysLeft.toInt())
        
        if (daysLeft > 365 * 5) {
            _forecastStatus.value = "More than 5 years"
        } else {
            val dateFormat = SimpleDateFormat("MMM yyyy", Locale.getDefault())
            _forecastStatus.value = "Full by ${dateFormat.format(calendar.time)}"
        }
        
        // Use the calculated positive slope for the chart
        generateProjectionPoints(storageInfo.value, yValues, slope.toLong(), null)
    }



    private fun generateProjectionPoints(info: StorageInfo, historyCounts: List<Long>, rate: Long, ignored: Any?) {
        if (info.totalBytes == 0L) return

        val points = mutableListOf<PointF>()
        val total = info.totalBytes.toFloat()
        
        // 1. Plot History (Last 8 points or fits)
        // We take the last few points of our actual history for the chart
        val recentHistory = historyCounts.takeLast(8)
        val currentUsagePercent = info.usedBytes.toFloat() / total
        
        // Fill up to 8 slots for history
        for (i in 0..7) {
            // Inverse mapping: 7 is Now, 0 is past
            val historyIdx = recentHistory.size - 1 - (7 - i)
            if (historyIdx >= 0) {
                 points.add(PointF(i / 16f, 1f - (recentHistory[historyIdx] / total)))
            } else {
                 // Pre-pad with approximate regression if missing data? 
                 // Or just skip? better to skip visually or line will drop to 0.
                 // Let's use current - rate * days
                 val daysBack = 7 - i
                 val approxUsed = info.usedBytes - (rate * daysBack)
                 points.add(PointF(i / 16f, 1f - (approxUsed.toFloat() / total).coerceIn(0f, 1f)))
            }
        }

        // 2. Plot Forecast (Next 8 points)
        // Project 1 year (or relevant scale)
        // Let's project next 30 days for visual clarity? Or 6 months?
        // Code maps 0..16 X to standard width.
        val dailyRatePercent = rate.toFloat() / total
        
        // We project out 6 months for the chart 
        val projectDays = 180f 
        
        for (i in 8..16) {
            val t = (i - 8) / 8f // 0..1
            val addedUsage = dailyRatePercent * (projectDays * t)
            val predicted = currentUsagePercent + addedUsage
            points.add(PointF(i / 16f, 1f - predicted.coerceIn(0f, 1f)))
        }

        _projectionPoints.value = points
    }

}

class StorageForecastViewModelFactory(
    private val repository: FileRepository,
    private val settingsRepository: SettingsRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(StorageForecastViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return StorageForecastViewModel(repository, settingsRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
