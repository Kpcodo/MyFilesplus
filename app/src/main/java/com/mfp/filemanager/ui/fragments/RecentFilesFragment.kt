package com.mfp.filemanager.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.mfp.filemanager.data.FileRepository
import com.mfp.filemanager.data.SettingsRepository
import com.mfp.filemanager.databinding.FragmentFileBrowserBinding
import com.mfp.filemanager.ui.adapters.RecentFilesAdapter
import com.mfp.filemanager.ui.adapters.RecentsListItem
import com.mfp.filemanager.ui.viewmodels.HomeViewModel
import com.mfp.filemanager.ui.viewmodels.HomeViewModelFactory
import kotlinx.coroutines.launch
import com.mfp.filemanager.R

class RecentFilesFragment : Fragment() {

    private var _binding: FragmentFileBrowserBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HomeViewModel by viewModels {
        HomeViewModelFactory(
            FileRepository(requireContext().applicationContext),
            SettingsRepository(requireContext().applicationContext)
        )
    }

    private lateinit var recentFilesAdapter: RecentFilesAdapter
    private var allGroupedItems: List<RecentsListItem> = emptyList()
    private var displayedItemCount: Int = 20
    private val pageSize = 20

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Reuse FileBrowser layout as it has toolbar and recycler
        _binding = FragmentFileBrowserBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setupRecyclerView()
        setupObservers()
        setupSwipeRefresh()

        viewModel.loadRecentFiles()
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.loadRecentFiles()
            viewLifecycleOwner.lifecycleScope.launch {
                kotlinx.coroutines.delay(1000)
                binding.swipeRefresh.isRefreshing = false
            }
        }
    }

    private fun setupToolbar() {
        binding.toolbar.title = "Recent Files"
        binding.toolbar.setNavigationOnClickListener {
            findNavController().popBackStack()
        }
    }

    private fun setupRecyclerView() {
        recentFilesAdapter = RecentFilesAdapter(
            onItemClick = { file ->
                if (viewModel.isRecentSelectionMode.value) {
                    viewModel.toggleRecentSelection(file)
                } else {
                    if (file.type == com.mfp.filemanager.data.FileType.IMAGE || file.type == com.mfp.filemanager.data.FileType.VIDEO) {
                        // Filter only FileItems from the mixed list
                        val fileItems = recentFilesAdapter.currentList.filterIsInstance<RecentsListItem.FileItem>().map { it.file }
                        
                        val mediaFiles = fileItems.filter { 
                            it.type == com.mfp.filemanager.data.FileType.IMAGE || it.type == com.mfp.filemanager.data.FileType.VIDEO 
                        }
                        
                        val mediaItems = ArrayList<com.mfp.filemanager.data.MediaItem>()
                        var startIndex = 0
                        
                        mediaFiles.forEachIndexed { index, f ->
                            val uri = android.net.Uri.fromFile(java.io.File(f.path))
                            if (f.type == com.mfp.filemanager.data.FileType.IMAGE) {
                                mediaItems.add(com.mfp.filemanager.data.MediaItem.Image(uri, f.name))
                            } else {
                                mediaItems.add(com.mfp.filemanager.data.MediaItem.Video(uri, f.name))
                            }
                            if (f.path == file.path) startIndex = index
                        }
                        
                        val intent = android.content.Intent(requireContext(), com.mfp.filemanager.ui.activities.MediaViewerActivity::class.java).apply {
                            putParcelableArrayListExtra("media_items", mediaItems)
                            putExtra("start_index", startIndex)
                        }
                        startActivity(intent)
                    } else {
                        com.mfp.filemanager.data.FileUtils.openFile(requireContext(), file)
                    }
                }
            },
            onLongClick = { file ->
                viewModel.toggleRecentSelection(file)
            },
            isSelectionModeActive = { viewModel.isRecentSelectionMode.value }
        )
        binding.recyclerFiles.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = recentFilesAdapter
            
            addOnScrollListener(object : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: androidx.recyclerview.widget.RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    if (!recyclerView.canScrollVertically(1)) { // Scrolled to bottom
                        loadMoreItems()
                    }
                }
            })
        }
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.recentFiles.collect { files ->
                        allGroupedItems = groupFilesByDate(files)
                        displayedItemCount = pageSize // Reset on new data
                        updateDisplayedList()
                        binding.textEmpty.visibility = if (files.isEmpty()) View.VISIBLE else View.GONE
                    }
                }
                launch {
                    viewModel.isRecentSelectionMode.collect { isSelectionMode ->
                        updateToolbarForSelection(isSelectionMode)
                        recentFilesAdapter.notifyDataSetChanged()
                    }
                }
                launch {
                    viewModel.selectedRecentFiles.collect { selected ->
                        if (viewModel.isRecentSelectionMode.value) {
                            binding.toolbar.title = "${selected.size} selected"
                        }
                    }
                }
                launch {
                    viewModel.thumbnailSeed.collect { seed ->
                        recentFilesAdapter.thumbnailSeed = seed
                    }
                }
                launch {
                    com.mfp.filemanager.data.cache.AppCache.cacheClearEvents.collect {
                        recentFilesAdapter.notifyDataSetChanged()
                        viewModel.loadRecentFiles()
                    }
                }
            }
        }
    }

    private fun updateDisplayedList() {
        if (::recentFilesAdapter.isInitialized) {
            recentFilesAdapter.submitList(allGroupedItems.take(displayedItemCount))
        }
    }

    private fun loadMoreItems() {
        if (displayedItemCount < allGroupedItems.size) {
            displayedItemCount += pageSize
            updateDisplayedList()
        }
    }

    private fun groupFilesByDate(files: List<com.mfp.filemanager.data.FileModel>): List<RecentsListItem> {
        val grouped = ArrayList<RecentsListItem>()
        val calendar = java.util.Calendar.getInstance()
        val today = calendar.get(java.util.Calendar.DAY_OF_YEAR)
        val year = calendar.get(java.util.Calendar.YEAR)
        
        var lastHeader = ""
        
        // Ensure files are sorted by date descending (should be already, but just in case)
        val sortedFiles = files.sortedByDescending { it.dateModified }

        for (file in sortedFiles) {
            calendar.timeInMillis = file.dateModified
            val fileDay = calendar.get(java.util.Calendar.DAY_OF_YEAR)
            val fileYear = calendar.get(java.util.Calendar.YEAR)
            
            val header = when {
                fileYear == year && fileDay == today -> "Today"
                fileYear == year && fileDay == today - 1 -> "Yesterday"
                else -> {
                    val format = java.text.SimpleDateFormat("dd MMM, yyyy", java.util.Locale.getDefault())
                    format.format(calendar.time)
                }
            }
            
            if (header != lastHeader) {
                grouped.add(RecentsListItem.Header(header))
                lastHeader = header
            }
            grouped.add(RecentsListItem.FileItem(file))
        }
        return grouped
    }

    private fun updateToolbarForSelection(isSelectionMode: Boolean) {
        if (isSelectionMode) {
            binding.toolbar.menu.clear()
            binding.toolbar.inflateMenu(R.menu.menu_selection)
            binding.toolbar.setNavigationIcon(R.drawable.ic_close_24)
            binding.toolbar.setNavigationOnClickListener {
                viewModel.exitRecentSelectionMode()
            }
            binding.toolbar.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_copy -> {
                        copySelectedRecentFiles()
                        true
                    }
                    R.id.action_move -> {
                        moveSelectedRecentFiles()
                        true
                    }
                    R.id.action_delete -> {
                        showMultiDeleteDialog()
                        true
                    }
                    R.id.action_select_all -> {
                        viewModel.selectAllRecentFiles()
                        true
                    }
                    else -> false
                }
            }
        } else {
            binding.toolbar.menu.clear()
            binding.toolbar.title = "Recent Files"
            binding.toolbar.setNavigationIcon(R.drawable.ic_arrow_back)
            binding.toolbar.setNavigationOnClickListener {
                findNavController().popBackStack()
            }
        }
    }

    private fun copySelectedRecentFiles() {
        viewModel.copySelectedRecentFiles()
    }

    private fun moveSelectedRecentFiles() {
        viewModel.moveSelectedRecentFiles()
    }

    private fun showMultiDeleteDialog() {
        val selectedCount = viewModel.selectedRecentFiles.value.size
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete $selectedCount items?")
            .setMessage("Are you sure you want to move these items to Bin?")
            .setPositiveButton("Delete") { _, _ ->
                viewModel.deleteSelectedRecentFiles()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
