package com.mfp.filemanager.ui.adapters
import android.graphics.Bitmap
import android.graphics.Color
import android.text.format.DateFormat
import android.text.format.Formatter
import android.transition.ChangeBounds
import android.transition.Slide
import android.transition.TransitionManager
import android.transition.TransitionSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import androidx.core.graphics.toColorInt
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import coil.load
import coil.request.CachePolicy
import coil.request.videoFrameMicros
import coil.size.Scale
import com.mfp.filemanager.R
import com.mfp.filemanager.data.FileModel
import com.mfp.filemanager.data.FileType
import com.mfp.filemanager.databinding.ItemFileListBinding
import java.io.File
import java.util.Date

class FileListAdapter(
    private val onItemClick: (FileModel) -> Unit,
    private val onLongClick: (FileModel) -> Unit,
    private val onActionClick: (FileModel, String) -> Unit, // "copy", "move", etc.
    private val allowSelection: Boolean = true,
    private val showItemMenu: Boolean = true
) : ListAdapter<FileModel, FileListAdapter.ViewHolder>(DiffCallback()) {

    var thumbnailSeed: Long = 0L

    private var expandedPath: String? = null
    private var recyclerView: RecyclerView? = null

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        this.recyclerView = recyclerView
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        this.recyclerView = null
    }

    var isSelectionMode: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                recyclerView?.let { rv ->
                    // Disable default fade animations so TransitionManager takes full control
                    (rv.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false

                    val transition = TransitionSet()
                        .addTransition(ChangeBounds())
                        .addTransition(Slide(Gravity.START).addTarget(R.id.checkbox_select))
                        .setDuration(200)
                        .setInterpolator(LinearInterpolator())
                    TransitionManager.beginDelayedTransition(rv, transition)
                }
                notifyItemRangeChanged(0, itemCount)
            }
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemFileListBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    private fun toggleExpansion(path: String) {
        val oldPath = expandedPath
        expandedPath = if (expandedPath == path) null else path
        
        currentList.indexOfFirst { it.path == oldPath }.takeIf { it != -1 }?.let { notifyItemChanged(it) }
        currentList.indexOfFirst { it.path == expandedPath }.takeIf { it != -1 }?.let { notifyItemChanged(it) }
    }

    inner class ViewHolder(private val binding: ItemFileListBinding) :
        RecyclerView.ViewHolder(binding.root) {

        init {
            binding.layoutMain.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(getItem(position))
                }
            }
            
            if (allowSelection) {
                binding.layoutMain.setOnLongClickListener {
                    val position = bindingAdapterPosition
                    if (position != RecyclerView.NO_POSITION) {
                        onLongClick(getItem(position))
                    }
                    true
                }
            }

            if (showItemMenu) {
                binding.btnMore.setOnClickListener {
                    val position = bindingAdapterPosition
                    if (position != RecyclerView.NO_POSITION) {
                        val file = getItem(position)
                        TransitionManager.beginDelayedTransition(binding.root as ViewGroup)
                        toggleExpansion(file.path)
                    }
                }
            } else {
                binding.btnMore.visibility = View.GONE
                binding.btnMore.setOnClickListener(null)
            }

            // Action Buttons
            binding.btnCopy.setOnClickListener { onActionClick(getItem(bindingAdapterPosition), "copy") }
            binding.btnMove.setOnClickListener { onActionClick(getItem(bindingAdapterPosition), "move") }
            binding.btnShare.setOnClickListener { onActionClick(getItem(bindingAdapterPosition), "share") }
            binding.btnRename.setOnClickListener { onActionClick(getItem(bindingAdapterPosition), "rename") }
            binding.btnInfo.setOnClickListener { onActionClick(getItem(bindingAdapterPosition), "info") }
            binding.btnDelete.setOnClickListener { onActionClick(getItem(bindingAdapterPosition), "delete") }
        }

        fun bind(file: FileModel) {
            val isExpanded = file.path == expandedPath
            binding.layoutMenu.visibility = if (isExpanded && !isSelectionMode && showItemMenu) View.VISIBLE else View.GONE
            
            if (showItemMenu) {
                binding.btnMore.setImageResource(if (isExpanded) R.drawable.ic_more_horiz_24 else R.drawable.ic_more_vert_24)
                binding.btnMore.visibility = if (isSelectionMode) View.INVISIBLE else View.VISIBLE
            } else {
                binding.btnMore.visibility = View.GONE
            }

            binding.checkboxSelect.visibility = if (isSelectionMode && allowSelection) View.VISIBLE else View.GONE
            binding.checkboxSelect.isChecked = file.isSelected

            binding.layoutMain.isSelected = file.isSelected
            binding.layoutMain.setBackgroundColor(
                if (file.isSelected) 
                    "#334285F4".toColorInt() // 20% alpha blue
                else 
                    Color.TRANSPARENT
            )
            
            binding.textName.text = file.name
            
            // Date formatting
            val date = Date(file.dateModified)
            val dateFormat = DateFormat.getMediumDateFormat(binding.root.context)
            binding.textDate.text = dateFormat.format(date)

            // Size or Item count
            if (file.isDirectory) {
               // If we have item count in FileModel, use it, otherwise just show nothing or "dir"
               // Assuming FileModel has size for files only usually, but let's check.
               // For now, if directory, we might not show size unless calculated.
               binding.textSize.text = binding.root.context.getString(R.string.label_directory)
            } else {
               binding.textSize.text = Formatter.formatFileSize(binding.root.context, file.size)
            }

            // Icon & Color Logic
            binding.imgIcon.colorFilter = null
            binding.imgIcon.setPadding(0, 0, 0, 0)
            binding.imgIcon.setBackgroundColor(Color.TRANSPARENT)
            binding.imgIcon.scaleType = ImageView.ScaleType.CENTER

            when {
                file.isDirectory -> {
                    binding.imgIcon.setImageResource(R.drawable.ic_folder_24)
                    binding.imgIcon.setColorFilter("#4285F4".toColorInt())
                }
                file.type == FileType.IMAGE || file.type == FileType.VIDEO || file.type == FileType.APK || file.type == FileType.AUDIO -> {
                    binding.imgIcon.load(File(file.path)) {
                        // Optimization: Downsample to thumbnail size
                        size(144, 144) 
                        
                        // Optimization: Use RGB_565 to reduce memory consumption by 50%
                        bitmapConfig(Bitmap.Config.RGB_565)
                        
                        // Cache Management: Use file stats as signature to ensure fresh load only on change
                        val signatureKey = "${file.path}_${file.dateModified}"
                        memoryCacheKey(signatureKey)
                        diskCacheKey(signatureKey)
                        
                        memoryCachePolicy(CachePolicy.ENABLED)
                        diskCachePolicy(CachePolicy.ENABLED)
                        
                        scale(Scale.FILL)
                        if (file.type == FileType.VIDEO) {
                            // Randomize frame based on file hash and rotating seed
                            val micros = (file.hashCode().toLong() + thumbnailSeed)
                            val frameTime = Math.abs(micros) % 60_000_000 // 0 to 60 seconds
                            videoFrameMicros(frameTime)
                        }
                        
                        val placeholder = when (file.type) {
                            FileType.IMAGE -> R.drawable.ic_image_24
                            FileType.VIDEO -> R.drawable.ic_movie_24
                            FileType.AUDIO -> R.drawable.ic_music_note_24
                            else -> R.drawable.ic_apk_24
                        }
                        placeholder(placeholder)
                        error(placeholder)
                        
                        // Disable crossfade for smoother scrolling in large lists, or keep it very fast
                        crossfade(false) 

                        // Apply styling only for Audio if it's using the placeholder
                        listener(
                            onError = { _, _ ->
                                binding.imgIcon.scaleType = ImageView.ScaleType.CENTER
                                if (file.type == FileType.AUDIO) {
                                    binding.imgIcon.setColorFilter("#00ACC1".toColorInt())
                                }
                            },
                            onSuccess = { _, _ ->
                                // Success - clear background/tint if it was previously set
                                binding.imgIcon.scaleType = ImageView.ScaleType.CENTER_CROP
                                binding.imgIcon.setBackgroundColor(Color.TRANSPARENT)
                                binding.imgIcon.colorFilter = null
                                binding.imgIcon.setPadding(0, 0, 0, 0)
                            }
                        )
                    }
                }
                file.type == FileType.DOCUMENT -> {
                    val ext = file.path.substringAfterLast('.', "")
                    if (ext.equals("pdf", ignoreCase = true)) {
                        // Load PDF Thumbnail
                        binding.imgIcon.load(File(file.path)) {
                            fetcherFactory(PdfFetcher.Factory())
                            size(144, 144)
                            memoryCacheKey("${file.path}_${file.dateModified}")
                            diskCacheKey("${file.path}_${file.dateModified}")
                            scale(Scale.FIT)
                            placeholder(R.drawable.ic_description_24)
                            error(R.drawable.ic_description_24)
                            crossfade(true)
                            listener(
                                onStart = { _ ->
                                     binding.imgIcon.scaleType = ImageView.ScaleType.CENTER
                                     binding.imgIcon.setColorFilter("#F44336".toColorInt()) // Red tint for placeholder
                                },
                                onSuccess = { _, _ ->
                                     binding.imgIcon.scaleType = ImageView.ScaleType.CENTER_CROP
                                     binding.imgIcon.colorFilter = null
                                     binding.imgIcon.setBackgroundColor(Color.TRANSPARENT)
                                },
                                onError = { _, _ ->
                                     binding.imgIcon.scaleType = ImageView.ScaleType.CENTER
                                     binding.imgIcon.setColorFilter("#F44336".toColorInt())
                                }
                            )
                        }
                    } else {
                        // Other Documents - Color Code
                        binding.imgIcon.setImageResource(R.drawable.ic_description_24)
                        val color = when (ext.lowercase()) {
                            "doc", "docx" -> "#2b579a" // Blue (Word)
                            "xls", "xlsx" -> "#217346" // Green (Excel)
                            "ppt", "pptx" -> "#d24726" // Orange (PowerPoint)
                            "txt" -> "#757575"         // Grey (Text)
                            else -> "#4285F4"          // Default Blue
                        }
                        binding.imgIcon.setColorFilter(color.toColorInt())
                    }
                }
                file.type == FileType.ARCHIVE -> {
                    binding.imgIcon.setImageResource(R.drawable.ic_archive_24)
                    binding.imgIcon.setColorFilter("#FF7043".toColorInt())
                }
                else -> {
                    binding.imgIcon.setImageResource(R.drawable.ic_description_24)
                    binding.imgIcon.setColorFilter("#4285F4".toColorInt())
                }
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<FileModel>() {
        override fun areItemsTheSame(oldItem: FileModel, newItem: FileModel): Boolean {
            return oldItem.path == newItem.path
        }

        override fun areContentsTheSame(oldItem: FileModel, newItem: FileModel): Boolean {
            return oldItem == newItem
        }
    }
}

// PdfFetcher moved to separate file: PdfFetcher.kt
