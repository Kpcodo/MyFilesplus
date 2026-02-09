package com.mfp.filemanager.ui.fragments

import android.os.Bundle
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.mfp.filemanager.R
import com.mfp.filemanager.data.SettingsRepository
import com.mfp.filemanager.databinding.FragmentSettingsBinding
import android.widget.ProgressBar
import android.widget.LinearLayout
import android.widget.TextView

import com.mfp.filemanager.ui.viewmodels.SettingsViewModel
import com.mfp.filemanager.ui.viewmodels.SettingsViewModelFactory
import com.mfp.filemanager.ui.viewmodels.UpdateCheckState
import kotlinx.coroutines.launch

class SettingsFragment : Fragment() {

    override fun onCreateAnimation(transit: Int, enter: Boolean, nextAnim: Int): android.view.animation.Animation? {
        val isMovingRight = com.mfp.filemanager.data.cache.AppCache.getData<Boolean>("isMovingRight") ?: true
        val animRes = if (enter) {
            if (isMovingRight) R.anim.slide_in_right else R.anim.slide_in_left
        } else {
            if (isMovingRight) R.anim.slide_out_left else R.anim.slide_out_right
        }
        return android.view.animation.AnimationUtils.loadAnimation(context, animRes)
    }

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SettingsViewModel by viewModels {
        SettingsViewModelFactory(
            SettingsRepository(requireContext().applicationContext)
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupListeners()
        setupObservers()
        
        // Refresh cache size on entry
        viewModel.calculateCacheSize(requireContext())
    }


    private fun setupListeners() {
        binding.layoutTheme.setOnClickListener {
            showThemeSelectionDialog()
        }

        binding.switchHiddenFiles.setOnCheckedChangeListener { _, isChecked ->
            viewModel.toggleShowHiddenFiles(isChecked)
        }

        binding.switchSwipeNav.setOnCheckedChangeListener { _, isChecked ->
            viewModel.toggleSwipeNavigation(isChecked)
        }
        
        binding.layoutClearCache.setOnClickListener {
             viewModel.clearThumbnailsCache(requireContext())
        }

        binding.layoutTrashRetention.setOnClickListener {
            showTrashRetentionDialog()
        }



        binding.switchAutoUpdate.setOnCheckedChangeListener { _, isChecked ->
            viewModel.toggleAutoUpdateEnabled(isChecked)
        }

        binding.layoutCheckUpdate.setOnClickListener {
            val versionName = try {
                requireContext().packageManager.getPackageInfo(requireContext().packageName, 0).versionName
            } catch (e: Exception) { "1.3.1" } ?: "1.3.1"
            
            viewModel.checkForUpdates(versionName)
            Snackbar.make(binding.root, getString(R.string.settings_checking_updates), Snackbar.LENGTH_SHORT).show()
        }

        binding.btnChangelog.setOnClickListener {
            val versionName = try {
                requireContext().packageManager.getPackageInfo(requireContext().packageName, 0).versionName
            } catch (e: Exception) { "1.3.1" } ?: "1.3.1"
            
            viewModel.fetchChangelog(versionName)
            Snackbar.make(binding.root, "Fetching changelog...", Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.settingsState.collect { state ->
                        // Update Theme Text
                        binding.textCurrentTheme.text = when (state.themeMode) {
                            1 -> getString(R.string.settings_theme_light)
                            2 -> getString(R.string.settings_theme_dark)
                            3 -> getString(R.string.settings_theme_amoled)
                            else -> getString(R.string.settings_theme_system)
                        }
                        
                        if (binding.switchHiddenFiles.isChecked != state.showHiddenFiles) {
                            binding.switchHiddenFiles.isChecked = state.showHiddenFiles
                        }
                        
                        if (binding.switchSwipeNav.isChecked != state.isSwipeNavigationEnabled) {
                             binding.switchSwipeNav.isChecked = state.isSwipeNavigationEnabled
                        }

                        if (binding.switchAutoUpdate.isChecked != state.autoUpdateEnabled) {
                            binding.switchAutoUpdate.isChecked = state.autoUpdateEnabled
                        }
                        
                        // Retention & Speed Texts
                        binding.textTrashDays.text = getString(R.string.settings_trash_retention_days, state.trashRetentionDays)

                        
                        // Cache Size
                        binding.textCacheSize.text = Formatter.formatFileSize(requireContext(), state.currentCacheSize)
                    }
                }
                
                launch {
                    viewModel.updateState.collect { state ->
                        when (state) {
                            is UpdateCheckState.UpdateAvailable -> {
                                showUpdateDialog(state.release)
                            }
                            is UpdateCheckState.UpToDate -> {
                                Snackbar.make(binding.root, getString(R.string.settings_up_to_date), Snackbar.LENGTH_SHORT).show()
                                viewModel.resetUpdateState()
                            }
                            is UpdateCheckState.Downloading -> {
                                showProgressDialog(state.progress)
                            }
                            is UpdateCheckState.DownloadFinished -> {
                                hideProgressDialog()
                                viewModel.resetUpdateState()
                            }
                            is UpdateCheckState.Error -> {
                                hideProgressDialog()
                                Snackbar.make(binding.root, state.message, Snackbar.LENGTH_SHORT).show()
                                viewModel.resetUpdateState()
                            }
                            else -> {}
                        }
                    }
                }

                launch {
                    viewModel.changelogState.collect { state ->
                        when(state) {
                            is com.mfp.filemanager.ui.viewmodels.ChangelogState.Loading -> {
                                Snackbar.make(binding.root, "Fetching changelog...", Snackbar.LENGTH_SHORT).show()
                            }
                            is com.mfp.filemanager.ui.viewmodels.ChangelogState.Success -> {
                                showChangelogDialog(state.release)
                                viewModel.resetChangelogState()
                            }
                            is com.mfp.filemanager.ui.viewmodels.ChangelogState.Error -> {
                                Snackbar.make(binding.root, state.message, Snackbar.LENGTH_SHORT).show()
                                viewModel.resetChangelogState()
                            }
                            else -> {}
                        }
                    }
                }
            }
        }
    }

    private fun showChangelogDialog(release: com.mfp.filemanager.data.GitHubRelease) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("What's New in ${release.tagName}")
            .setMessage(release.body ?: "No release notes available.") // Basic markdown support via TextView default or adding RichText later if needed
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showTrashRetentionDialog() {
        val options = arrayOf("7 Days", "15 Days", "30 Days", "60 Days")
        val values = arrayOf(7, 15, 30, 60)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Trash Retention")
            .setItems(options) { _, which ->
                viewModel.setTrashRetentionDays(values[which])
            }
            .setNegativeButton(R.string.settings_action_later) { _, _ ->
                viewModel.resetUpdateState()
            }
            .show()
    }

    private var progressDialog: androidx.appcompat.app.AlertDialog? = null
    private var progressBar: ProgressBar? = null
    private var progressText: TextView? = null

    private fun showProgressDialog(progress: Float) {
        if (progressDialog == null) {
            val context = requireContext()
            val layout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(50, 40, 50, 40)
            }

            progressBar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
                isIndeterminate = false
                max = 100
            }
            
            progressText = TextView(context).apply {
                text = "0%"
                gravity = android.view.Gravity.CENTER_HORIZONTAL
                setPadding(0, 20, 0, 0)
            }

            layout.addView(progressBar)
            layout.addView(progressText)

            progressDialog = MaterialAlertDialogBuilder(context)
                .setTitle("Downloading Update")
                .setView(layout)
                .setCancelable(false)
                .create()
            progressDialog?.show()
        }
        
        val percent = (progress * 100).toInt()
        progressBar?.progress = percent
        progressText?.text = "$percent%"
    }

    private fun hideProgressDialog() {
        progressDialog?.dismiss()
        progressDialog = null
        progressBar = null
        progressText = null
    }



    private fun showUpdateDialog(release: com.mfp.filemanager.data.GitHubRelease) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Version ${release.tagName}")
            .setMessage(release.releaseDescription ?: release.body ?: "No release notes available.")
            .setPositiveButton("Download") { _, _ ->
                viewModel.downloadUpdate(release, requireContext())
            }
            .setNegativeButton("Later") { _, _ ->
                viewModel.resetUpdateState()
            }
            .setOnCancelListener {
                 viewModel.resetUpdateState()
            }
            .show()
    }

    private fun showThemeSelectionDialog() {
        val themes = arrayOf(
            getString(R.string.settings_theme_system),
            getString(R.string.settings_theme_light),
            getString(R.string.settings_theme_dark),
            getString(R.string.settings_theme_amoled)
        )
        val values = arrayOf(0, 1, 2, 3)
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Select Theme")
            .setItems(themes) { _, which ->
                val newMode = values[which]
                
                // Use lifecycleScope to ensure the write is finished before recreating
                lifecycleScope.launch {
                    viewModel.setThemeMode(newMode)
                    
                    // For immediate effect:
                    val appCompatMode = when (newMode) {
                        1 -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
                        2 -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
                        3 -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
                        else -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                    }
                    
                    val oldNightMode = androidx.appcompat.app.AppCompatDelegate.getDefaultNightMode()
                    androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(appCompatMode)
                    
                    // If the night mode value itself didn't change (e.g. FOLLOW_SYSTEM to YES while system is dark),
                    // AppCompat might not recreate the activity. We must force it.
                    // Even if it did change, we wait for DataStore propagation to be safe.
                    kotlinx.coroutines.delay(400) // Slightly longer to be absolutely sure DataStore is ready
                    
                    if (activity?.isFinishing == false && activity?.isDestroyed == false) {
                        activity?.recreate()
                    }
                }
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
