package com.mfp.filemanager.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.mfp.filemanager.data.FileRepository
import com.mfp.filemanager.data.FileType
import com.mfp.filemanager.data.FileUtils
import com.mfp.filemanager.data.SettingsRepository
import com.mfp.filemanager.databinding.FragmentMusicBinding
import com.mfp.filemanager.ui.adapters.FileListAdapter
import com.mfp.filemanager.ui.viewmodels.AudioViewModel
import com.mfp.filemanager.ui.viewmodels.HomeViewModel
import com.mfp.filemanager.ui.viewmodels.HomeViewModelFactory
import com.mfp.filemanager.R
import java.io.File
import kotlinx.coroutines.launch

class MusicFragment : Fragment() {

    override fun onCreateAnimation(transit: Int, enter: Boolean, nextAnim: Int): android.view.animation.Animation? {
        // Force animation based on Main Activity's calculated direction
        val isMovingRight = com.mfp.filemanager.data.cache.AppCache.getData<Boolean>("isMovingRight") ?: true
        val animRes = if (enter) {
            if (isMovingRight) R.anim.slide_in_right else R.anim.slide_in_left
        } else {
            if (isMovingRight) R.anim.slide_out_left else R.anim.slide_out_right
        }
        return android.view.animation.AnimationUtils.loadAnimation(context, animRes)
    }

    private var _binding: FragmentMusicBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HomeViewModel by viewModels {
        HomeViewModelFactory(
            FileRepository(requireContext().applicationContext),
            SettingsRepository(requireContext().applicationContext)
        )
    }
    
    private val audioViewModel: AudioViewModel by activityViewModels()

    private lateinit var musicAdapter: FileListAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMusicBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupRecyclerView()
        setupObservers()
        setupSwipeRefresh()
        
        viewModel.loadFilesByCategory(FileType.AUDIO)
        audioViewModel.loadMusicFiles()
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.loadFilesByCategory(FileType.AUDIO)
            viewLifecycleOwner.lifecycleScope.launch {
                kotlinx.coroutines.delay(1000)
                binding.swipeRefresh.isRefreshing = false
            }
        }
    }

    private fun setupRecyclerView() {
        musicAdapter = FileListAdapter(
            onItemClick = { file ->
                if (viewModel.isBrowserSelectionMode.value) {
                    viewModel.toggleBrowserSelection(file.path)
                } else {
                    audioViewModel.playFile(File(file.path))
                }
            },
            onLongClick = { file ->
                // Long click disabled via flag, but we pass listener anyway
                viewModel.toggleBrowserSelection(file.path)
            },
            onActionClick = { file, action ->
                handleFileAction(file, action)
            },
            allowSelection = false,
            showItemMenu = false
        )
        binding.recyclerMusic.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = musicAdapter
        }
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.categoryFiles.collect { files ->
                        musicAdapter.submitList(files)
                        binding.textEmpty.visibility = if (files.isEmpty()) View.VISIBLE else View.GONE
                    }
                }
                launch {
                    viewModel.isBrowserSelectionMode.collect { isSelectionMode ->
                        updateToolbarForSelection(isSelectionMode)
                    }
                }
                launch {
                    viewModel.selectedBrowserFiles.collect { selected ->
                        if (viewModel.isBrowserSelectionMode.value) {
                            binding.toolbar.title = "${selected.size} selected"
                        }
                    }
                }
            }
        }
    }

    private fun updateToolbarForSelection(isSelectionMode: Boolean) {
        if (isSelectionMode) {
            binding.toolbar.menu.clear()
            binding.toolbar.inflateMenu(R.menu.menu_selection)
            binding.toolbar.setNavigationIcon(R.drawable.ic_close_24)
            binding.toolbar.setNavigationOnClickListener {
                viewModel.exitBrowserSelectionMode()
            }
            binding.toolbar.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_copy -> {
                        viewModel.copySelectedBrowserFiles()
                        true
                    }
                    R.id.action_move -> {
                        viewModel.moveSelectedBrowserFiles()
                        true
                    }
                    R.id.action_delete -> {
                        showMultiDeleteDialog()
                        true
                    }
                    R.id.action_select_all -> {
                        viewModel.selectAllBrowserFiles()
                        true
                    }
                    else -> false
                }
            }
        } else {
            binding.toolbar.menu.clear()
            binding.toolbar.title = ""
            binding.toolbar.navigationIcon = null
            binding.toolbar.setOnMenuItemClickListener(null)
        }
    }

    private fun showMultiDeleteDialog() {
        val selectedCount = viewModel.selectedBrowserFiles.value.size
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete $selectedCount items?")
            .setMessage("Are you sure you want to move these music files to Bin?")
            .setPositiveButton("Delete") { _, _ ->
                viewModel.deleteSelectedBrowserFiles("")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun handleFileAction(file: com.mfp.filemanager.data.FileModel, action: String) {
        when (action) {
            "copy" -> viewModel.copyFileToClipboard(file)
            "move" -> viewModel.moveFileToClipboard(file)
            "share" -> FileUtils.shareFile(requireContext(), file)
            "rename" -> showRenameDialog(file) // need to implement or move from FileBrowser
            "info" -> showDetailsDialog(file)
            "delete" -> showDeleteDialog(file)
        }
    }

    private fun showRenameDialog(file: com.mfp.filemanager.data.FileModel) {
        // Reuse dialog logic or delegate to viewModel
    }

    private fun showDetailsDialog(file: com.mfp.filemanager.data.FileModel) {
        // Reuse dialog logic
    }

    private fun showDeleteDialog(file: com.mfp.filemanager.data.FileModel) {
        // Reuse dialog logic
    }
}
