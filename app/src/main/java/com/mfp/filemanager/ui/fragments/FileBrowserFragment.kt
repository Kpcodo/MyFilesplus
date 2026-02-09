package com.mfp.filemanager.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.addCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.mfp.filemanager.data.FileRepository
import com.mfp.filemanager.data.SettingsRepository
import com.mfp.filemanager.data.FileUtils
import com.mfp.filemanager.databinding.FragmentFileBrowserBinding
import com.mfp.filemanager.ui.adapters.FileListAdapter
import com.mfp.filemanager.ui.viewmodels.HomeViewModel
import com.mfp.filemanager.ui.viewmodels.HomeViewModelFactory
import kotlinx.coroutines.launch
import androidx.fragment.app.activityViewModels
import com.mfp.filemanager.ui.viewmodels.AudioViewModel
import com.mfp.filemanager.data.FileType
import com.mfp.filemanager.R
import java.io.File
import java.net.URLDecoder

class FileBrowserFragment : Fragment() {

    private var _binding: FragmentFileBrowserBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HomeViewModel by viewModels {
        HomeViewModelFactory(
            FileRepository(requireContext().applicationContext),
            SettingsRepository(requireContext().applicationContext)
        )
    }
    
    private val audioViewModel: AudioViewModel by activityViewModels()

    private lateinit var fileAdapter: FileListAdapter
    // Using Safe Args if possible, or manual bundle
    // Assuming argument "path" is passed as string
    private var currentPath: String = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFileBrowserBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val args = arguments
        val rawPath = args?.getString("path") ?: ""
        // Decode logic if you passed encoded path from Compose logic, but Navigation Component handles strings better.
        // If we migrated to standard Nav, we can pass raw strings usually.
        // But let's assume raw string for now.
        currentPath = rawPath

        setupToolbar()
        setupRecyclerView()
        setupObservers()
        setupSwipeRefresh()
        
        // Initial Load
        if (currentPath.isNotEmpty()) {
            viewModel.loadFiles(currentPath)
            binding.toolbar.subtitle = null
        } else {
            // Error or Root?
            Toast.makeText(context, "Invalid path", Toast.LENGTH_SHORT).show()
            findNavController().popBackStack()
        }

        // Custom Back Press Login to go up directories?
        // Usually View System relies on Fragment Back Stack.
        // If we want "Up" navigation within the same fragment (reloading list), we handle it manually.
        // But "Navigation Component" philosophy usually pushes new fragment instance for new path?
        // OR we stay in same fragment and update data.
        // "Single Fragment" for browsing is more efficient.
        // Let's implement Back Press to go UP directory if allowed, else pop.
        
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            handleBackPress(this)
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            if (currentPath.isNotEmpty()) {
                viewModel.loadFiles(currentPath, true)
            }
            // Observe isLoading instead of arbitrary delay
            viewLifecycleOwner.lifecycleScope.launch {
                // Wait for the ViewModel to signal loading is complete
                viewModel.isRefreshing.collect { isRefreshing ->
                     if (!isRefreshing) {
                         binding.swipeRefresh.isRefreshing = false
                     }
                }
            }
        }
    }

    private fun handleBackPress(callback: androidx.activity.OnBackPressedCallback) {
        // Decide if we pop or go up.
        // If we strictly follow Navigation Component, every folder click pushes a new Fragment to stack.
        // That makes Back button generic (pop stack).
        // It's the standard way and easiest for transition animations.
        // SO: We will stick to "Push new Fragment" strategy if possible, 
        // OR "Single Fragment updating data".
        
        // Given `viewModel.loadFiles(path)` updates the State for the UI...
        // If we share the SAME ViewModel instance (scoped to Activity), then modifying it modifies it for EVERYONE.
        // But `by viewModels` here creates a Fragment-scoped ViewModel unless we say `by activityViewModels`.
        // The factory creates a new one each time currently? No, ViewModelStoreOwner is the Fragment.
        // So each Fragment has its OWN ViewModel instance. This is good for "Push new Fragment stack".
        // Each fragment holds its own state of files.
        // So, standard Back Press (popBackStack) is correct.
        
        // DISABLE custom callback to let default behavior happen
        callback.isEnabled = false
        requireActivity().onBackPressedDispatcher.onBackPressed()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            findNavController().popBackStack()
        }
    }

    private fun setupRecyclerView() {
        fileAdapter = FileListAdapter(
            onItemClick = { file ->
                if (viewModel.isBrowserSelectionMode.value) {
                    viewModel.toggleBrowserSelection(file.path)
                } else {
                    if (file.isDirectory) {
                        navigateToFolder(file.path)
                    } else {
                        openFile(file)
                    }
                }
            },
            onLongClick = { file ->
                viewModel.toggleBrowserSelection(file.path)
            },
            onActionClick = { file, action ->
                handleFileAction(file, action)
            }
        )
        binding.recyclerFiles.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = fileAdapter
        }
    }

    private fun updateToolbarForSelection(isSelectionMode: Boolean) {
        if (isSelectionMode) {
            binding.toolbar.menu.clear()
            binding.toolbar.inflateMenu(R.menu.menu_selection)
            binding.toolbar.setNavigationIcon(R.drawable.ic_close_24)
            binding.toolbar.subtitle = null
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
            binding.toolbar.inflateMenu(R.menu.menu_file_browser)
            // Fix: Show "Internal Storage" for root path
            val title = if (currentPath == "/storage/emulated/0") "Internal Storage" else File(currentPath).name
            binding.toolbar.title = if (title.isEmpty()) "Internal Storage" else title
            
            // Fix: User-friendly subtitle path
            binding.toolbar.subtitle = null
            binding.toolbar.setNavigationIcon(R.drawable.ic_arrow_back)
            binding.toolbar.setNavigationOnClickListener {
                findNavController().popBackStack()
            }
            binding.toolbar.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_paste -> {
                        viewModel.pasteClipboardFiles(currentPath)
                        true
                    }
                    R.id.action_search -> {
                        // handled by SearchView
                        true
                    }
                    else -> false
                }
            }
            val searchItem = binding.toolbar.menu.findItem(R.id.action_search)
            val searchView = searchItem?.actionView as? androidx.appcompat.widget.SearchView
            searchView?.queryHint = "Search in ${if (currentPath == "/storage/emulated/0") "Internal Storage" else File(currentPath).name}"
            searchView?.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean {
                    searchView.clearFocus()
                    return true
                }
                override fun onQueryTextChange(newText: String?): Boolean {
                    viewModel.updateSearchQuery(newText ?: "")
                    return true
                }
            })
            
            // Clear search when closed
            searchItem?.setOnActionExpandListener(object : android.view.MenuItem.OnActionExpandListener {
                 override fun onMenuItemActionExpand(item: android.view.MenuItem): Boolean {
                     return true
                 }
                 override fun onMenuItemActionCollapse(item: android.view.MenuItem): Boolean {
                     viewModel.updateSearchQuery("")
                     return true
                 }
            })

            // Update paste visibility immediately if needed
            val hasClipboard = (viewModel.clipboardFiles.value.isNotEmpty())
            val pasteItem = binding.toolbar.menu.findItem(R.id.action_paste)
            pasteItem?.isVisible = hasClipboard
            
            // Fix: Immediately set correct icon based on current operation state
            val currentOp = viewModel.clipboardOperation.value
            if (currentOp == com.mfp.filemanager.data.clipboard.ClipboardOperation.MOVE) {
                pasteItem?.setIcon(R.drawable.ic_move_to_24)
                pasteItem?.title = "Move Here"
            } else {
                pasteItem?.setIcon(R.drawable.ic_content_paste_24)
                pasteItem?.title = "Paste"
            }
        }
    }

    private fun showMultiDeleteDialog() {
        val selectedCount = viewModel.selectedBrowserFiles.value.size
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete $selectedCount items?")
            .setMessage("Are you sure you want to move these items to Bin?")
            .setPositiveButton("Delete") { _, _ ->
                viewModel.deleteSelectedBrowserFiles(currentPath)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showMessage(msg: String) {
        com.google.android.material.snackbar.Snackbar.make(binding.root, msg, com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show()
    }

    private fun showRenameDialog(file: com.mfp.filemanager.data.FileModel) {
        val input = android.widget.EditText(requireContext())
        input.setText(file.name)
        val container = android.widget.FrameLayout(requireContext())
        val params = android.widget.FrameLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT, 
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.leftMargin = 50 // dp to px conversion needed ideally, but keeping simple
        params.rightMargin = 50
        container.addView(input, params)

        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("Rename")
            .setView(container)
            .setPositiveButton("Rename") { _, _ ->
                val newName = input.text.toString()
                if (newName.isNotEmpty() && newName != file.name) {
                    viewModel.renameFile(file, newName) {
                        viewModel.loadFiles(currentPath, true)
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDeleteDialog(file: com.mfp.filemanager.data.FileModel) {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete File?")
            .setMessage("Are you sure you want to move '${file.name}' to Bin?")
            .setPositiveButton("Delete") { _, _ ->
                viewModel.deleteFile(file.path, currentPath)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDetailsDialog(file: com.mfp.filemanager.data.FileModel) {
        val details = """
            Name: ${file.name}
            Path: ${file.path}
            Size: ${com.mfp.filemanager.data.FileUtils.formatSize(file.size)}
            Modified: ${com.mfp.filemanager.data.FileUtils.formatDate(file.dateModified)}
        """.trimIndent()
        
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("Details")
            .setMessage(details)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun navigateToFolder(path: String) {
        // Recursive navigation
        // Navigate to SELF with new argument
        val bundle = Bundle().apply {
            putString("path", path)
        }
        val navOptions = androidx.navigation.NavOptions.Builder()
            .setEnterAnim(R.anim.pop_enter)
            .setExitAnim(R.anim.pop_exit)
            .setPopEnterAnim(R.anim.pop_pop_enter)
            .setPopExitAnim(R.anim.pop_pop_exit)
            .build()
        findNavController().navigate(com.mfp.filemanager.R.id.nav_file_browser, bundle, navOptions)
    }

    private fun openFile(file: com.mfp.filemanager.data.FileModel) {
        if (file.type == FileType.AUDIO) {
            audioViewModel.playFile(File(file.path))
        } else if (file.type == FileType.IMAGE || file.type == FileType.VIDEO) {
             val mediaFiles = fileAdapter.currentList.filter { 
                 it.type == FileType.IMAGE || it.type == FileType.VIDEO 
             }
             
             val mediaItems = ArrayList<com.mfp.filemanager.data.MediaItem>()
             var startIndex = 0
             
             mediaFiles.forEachIndexed { index, f ->
                 val uri = android.net.Uri.fromFile(File(f.path))
                 if (f.type == FileType.IMAGE) {
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

    private fun handleFileAction(file: com.mfp.filemanager.data.FileModel, action: String) {
        when (action) {
            "copy" -> {
                viewModel.copyFileToClipboard(file)
            }
            "move" -> {
                viewModel.moveFileToClipboard(file)
            }
            "share" -> {
                FileUtils.shareFile(requireContext(), file)
            }
            "rename" -> showRenameDialog(file)
            "info" -> showDetailsDialog(file)
            "delete" -> showDeleteDialog(file)
        }
    }

    private fun setupObservers() {
        // Observe Files
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    // Combine files and search results based on query
                    kotlinx.coroutines.flow.combine(
                        viewModel.files,
                        viewModel.searchResults,
                        viewModel.searchQuery
                    ) { files, searchResults, query ->
                        if (query.isEmpty()) {
                            files 
                        } else {
                            // Filter search results by current path to keep context
                            searchResults.filter { it.path.startsWith(currentPath) }
                        }
                    }.collect { displayedFiles ->
                        fileAdapter.submitList(displayedFiles)
                        binding.textEmpty.visibility = if (displayedFiles.isEmpty()) View.VISIBLE else View.GONE
                        binding.textEmpty.text = if (viewModel.searchQuery.value.isNotEmpty()) "No results found" else "No files found"
                    }
                }
                launch {
                    viewModel.isLoading.collect { isLoading ->
                        binding.progressLoading.visibility = if (isLoading) View.VISIBLE else View.GONE
                    }
                }
                launch {
                    viewModel.userMessage.collect { message ->
                        if (message.isNotEmpty()) {
                             com.google.android.material.snackbar.Snackbar.make(binding.root, message, com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show()
                        }
                    }
                }
                launch {
                    viewModel.isBrowserSelectionMode.collect { isSelectionMode ->
                        fileAdapter.isSelectionMode = isSelectionMode
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
                launch {
                    viewModel.clipboardFiles.collect { clipboard ->
                        if (!viewModel.isBrowserSelectionMode.value) {
                             binding.toolbar.menu.findItem(R.id.action_paste)?.isVisible = clipboard.isNotEmpty()
                        }
                    }
                }
                launch {
                    viewModel.clipboardOperation.collect { operation ->
                        val pasteItem = binding.toolbar.menu.findItem(R.id.action_paste)
                        if (operation == com.mfp.filemanager.data.clipboard.ClipboardOperation.MOVE) {
                            pasteItem?.setIcon(R.drawable.ic_move_to_24)
                            pasteItem?.title = "Move Here"
                        } else {
                            pasteItem?.setIcon(R.drawable.ic_content_paste_24)
                            pasteItem?.title = "Paste"
                        }
                    }
                }
                launch {
                    viewModel.thumbnailSeed.collect { seed ->
                        fileAdapter.thumbnailSeed = seed
                    }
                }
                launch {
                    com.mfp.filemanager.data.cache.AppCache.cacheClearEvents.collect {
                        fileAdapter.notifyDataSetChanged()
                        if (currentPath.isNotEmpty()) {
                            viewModel.loadFiles(currentPath, true)
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
