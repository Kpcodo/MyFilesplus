package com.mfp.filemanager

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.mfp.filemanager.databinding.ActivityOnboardingBinding
import com.mfp.filemanager.security.PermissionHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        val repo = com.mfp.filemanager.data.SettingsRepository(applicationContext)
        val themeMode = runBlocking<Int> { repo.themeMode.first() }
        when (themeMode) {
            1 -> setTheme(R.style.Theme_FileManager) // Light
            2 -> setTheme(R.style.Theme_FileManager_Dark) // Dark (Grey)
            3 -> setTheme(R.style.Theme_FileManager_Amoled) // Amoled (Black)
            else -> {
                // System Default
                val isNight = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
                if (isNight) {
                    setTheme(R.style.Theme_FileManager_Dark)
                } else {
                    setTheme(R.style.Theme_FileManager)
                }
            }
        }

        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnGrantStorage.setOnClickListener {
            PermissionHelper.requestStoragePermission(this)
        }

        binding.btnGrantUsage.setOnClickListener {
            PermissionHelper.requestUsageStatsPermission(this)
        }


    }

    override fun onResume() {
        super.onResume()
        checkPermissions()
    }

    private fun checkPermissions(): Boolean {
        val storage = PermissionHelper.hasStoragePermission(this)
        val usage = PermissionHelper.hasUsageStatsPermission(this)

        binding.btnGrantStorage.isEnabled = !storage
        binding.btnGrantStorage.text = if (storage) "Storage Access Granted" else "Grant Storage Access"
        
        binding.btnGrantUsage.isEnabled = !usage
        binding.btnGrantUsage.text = if (usage) "Usage Access Granted" else "Grant Usage Access"

        val allGranted = storage && usage
        if (allGranted) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
        return allGranted
    }
}
