package com.mfp.filemanager.ui.fragments

import android.os.Bundle
import android.text.format.Formatter
import android.view.*
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.mfp.filemanager.R
import androidx.core.view.WindowCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.mfp.filemanager.data.FileRepository
import com.mfp.filemanager.data.SettingsRepository
import com.mfp.filemanager.databinding.FragmentStorageForecastBinding
import com.mfp.filemanager.ui.viewmodels.StorageForecastViewModel
import com.mfp.filemanager.ui.viewmodels.StorageForecastViewModelFactory
import kotlinx.coroutines.launch

class StorageForecastFragment : Fragment() {

    private var _binding: FragmentStorageForecastBinding? = null
    private val binding get() = _binding!!

    private val viewModel: StorageForecastViewModel by viewModels {
        StorageForecastViewModelFactory(
            FileRepository(requireContext().applicationContext),
            SettingsRepository(requireContext().applicationContext)
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStorageForecastBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupStatusBar()
        setupInsets()

        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        setupObservers()
        setupSwipeRefresh()
        viewModel.loadData()
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.loadData()
            viewLifecycleOwner.lifecycleScope.launch {
                kotlinx.coroutines.delay(1200)
                binding.swipeRefresh.isRefreshing = false
            }
        }
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                
                launch {
                    viewModel.storageInfo.collect { info ->
                        if (info.totalBytes == 0L) return@collect

                        val totalStr = Formatter.formatFileSize(requireContext(), info.totalBytes)
                        val freeStr = Formatter.formatFileSize(requireContext(), info.freeBytes)
                        val usedStr = Formatter.formatFileSize(requireContext(), info.usedBytes)

                        binding.textTotalStorage.text = "Total: $totalStr"
                        binding.textNowUsage.text = "Now: $usedStr"
                        binding.textFreeSpaceValue.text = "$freeStr / $totalStr"

                        // Category Sizes
                        binding.textAppsSize.text = Formatter.formatFileSize(requireContext(), info.appBytes)
                        binding.textVideosSize.text = Formatter.formatFileSize(requireContext(), info.videoBytes)
                        binding.textImagesSize.text = Formatter.formatFileSize(requireContext(), info.imageBytes)
                        binding.textDocsSize.text = Formatter.formatFileSize(requireContext(), info.documentBytes)
                        binding.textAudioSize.text = Formatter.formatFileSize(requireContext(), info.audioBytes)
                        binding.textOthersSize.text = Formatter.formatFileSize(requireContext(), info.otherBytes + info.archiveBytes)

                        // Progress Bar
                        val total = info.totalBytes.toFloat()
                        setWeight(binding.barApps, info.appBytes / total)
                        setWeight(binding.barVideos, info.videoBytes / total)
                        setWeight(binding.barImages, info.imageBytes / total)
                        setWeight(binding.barDocs, info.documentBytes / total)
                        setWeight(binding.barAudio, info.audioBytes / total)
                        setWeight(binding.barOthers, (info.otherBytes + info.archiveBytes) / total)
                        setWeight(binding.barFree, info.freeBytes / total)

                        // Legend Indicator Lines (Proportional to total storage)
                        setWeight(binding.lineApps, info.appBytes / total)
                        setWeight(binding.lineVideos, info.videoBytes / total)
                        setWeight(binding.lineImages, info.imageBytes / total)
                        setWeight(binding.lineDocs, info.documentBytes / total)
                        setWeight(binding.lineAudio, info.audioBytes / total)
                        setWeight(binding.lineOthers, (info.otherBytes + info.archiveBytes) / total)
                    }
                }

                launch {
                    viewModel.dailyUsageRate.collect { rate ->
                        val rateStr = Formatter.formatFileSize(requireContext(), rate)
                        binding.textDailyUsage.text = "You're using about $rateStr per day."
                    }
                }

                launch {
                    viewModel.estimatedFullDate.collect { date ->
                        binding.textFullPrediction.text = date
                        
                        // Dynamic text scaling for clean display
                        when {
                            date.contains("ANALYZING") || date.contains("NEVER") -> {
                                binding.textFullPrediction.textSize = 28f
                            }
                            date.length > 20 -> {
                                binding.textFullPrediction.textSize = 24f
                            }
                            else -> {
                                binding.textFullPrediction.textSize = 32f
                            }
                        }
                    }
                }

                launch {
                    viewModel.projectionPoints.collect { points ->
                        if (points.isNotEmpty()) {
                            binding.chartProjection.setData(points, 8) // 8 is "Now" (middle of 0-16)
                        }
                    }
                }

                launch {
                    viewModel.suggestions.collect { list ->
                        if (list.isNotEmpty()) {
                            binding.cardSuggestions.visibility = View.VISIBLE
                            val suggestion = list.first()
                            binding.textSuggestionsTitle.text = suggestion.title
                            binding.textSuggestionsSubtitle.text = suggestion.subtitle
                            binding.btnClearSuggestions.text = suggestion.actionText
                            
                            binding.btnClearSuggestions.setOnClickListener {
                                // Simple action - in a real app this would navigate to Cleanup or trigger task
                                com.google.android.material.snackbar.Snackbar.make(binding.root, "Feature coming soon!", com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show()
                            }
                        } else {
                            binding.cardSuggestions.visibility = View.GONE
                        }
                    }
                }
            }
        }
    }

    private fun setupStatusBar() {
        val window = requireActivity().window
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        // Use theme background color for status bar
        val typedValue = android.util.TypedValue()
        requireContext().theme.resolveAttribute(android.R.attr.colorBackground, typedValue, true)
        window.statusBarColor = typedValue.data
        
        // Determine if we are in dark mode to set icon contrast
        val isDarkTheme = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == 
                android.content.res.Configuration.UI_MODE_NIGHT_YES
        
        val controller = WindowInsetsControllerCompat(window, binding.root)
        controller.isAppearanceLightStatusBars = !isDarkTheme
    }

    private fun setupInsets() {
        // Handle Top Inset (Status Bar) for the Toolbar
        ViewCompat.setOnApplyWindowInsetsListener(binding.toolbar) { v, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            v.setPadding(0, statusBarHeight, 0, 0)
            insets
        }

        // Handle Bottom Inset for the content container to stay above Bottom Nav / Taskbar
        // We add a minimum of 80dp padding as requested to ensure no overlap
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val navBarHeight = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            val density = resources.displayMetrics.density
            val minPadding = (80 * density).toInt()
            
            // Apply the larger of navigation bar height or our 80dp "taskbar" padding
            binding.scrollContainer.setPadding(0, 0, 0, maxOf(navBarHeight, minPadding))
            insets
        }
    }

    private fun setWeight(view: View, weight: Float) {
        val params = view.layoutParams as LinearLayout.LayoutParams
        params.weight = weight.coerceAtLeast(0.01f) // Ensure minimum visibility
        view.layoutParams = params
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Restore default behavior for other fragments
        WindowCompat.setDecorFitsSystemWindows(requireActivity().window, true)
        _binding = null
    }
}
