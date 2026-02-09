package com.mfp.filemanager.ui.adapters

import android.graphics.Color
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.graphics.toColorInt
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.request.videoFrameMicros
import coil.size.Scale
import com.mfp.filemanager.R
import com.mfp.filemanager.data.FileModel
import com.mfp.filemanager.data.FileType
import com.mfp.filemanager.databinding.ItemRecentFileBinding
import java.io.File
import kotlin.math.abs

class RecentFilesAdapter(
    private val onItemClick: (FileModel) -> Unit,
    private val onLongClick: (FileModel) -> Unit,
    private val isSelectionModeActive: () -> Boolean
) : ListAdapter<RecentsListItem, RecyclerView.ViewHolder>(DiffCallback()) {

    var thumbnailSeed: Long = 0L

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_FILE = 1
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is RecentsListItem.Header -> TYPE_HEADER
            is RecentsListItem.FileItem -> TYPE_FILE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_HEADER) {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_recent_header, parent, false)
            HeaderViewHolder(view)
        } else {
            val binding = ItemRecentFileBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            FileViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is RecentsListItem.Header -> (holder as HeaderViewHolder).bind(item.title)
            is RecentsListItem.FileItem -> (holder as FileViewHolder).bind(item.file)
        }
    }

    class HeaderViewHolder(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
        private val textView: android.widget.TextView = itemView as android.widget.TextView
        fun bind(title: String) {
            textView.text = title
        }
    }

    inner class FileViewHolder(private val binding: ItemRecentFileBinding) :
        RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val item = getItem(position)
                    if (item is RecentsListItem.FileItem) {
                        onItemClick(item.file)
                    }
                }
            }
            binding.root.setOnLongClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val item = getItem(position)
                    if (item is RecentsListItem.FileItem) {
                        onLongClick(item.file)
                    }
                }
                true
            }
        }

        fun bind(file: FileModel) {
            binding.root.isSelected = file.isSelected
            binding.root.alpha = if (file.isSelected) 0.6f else 1.0f
            
            binding.textFileName.text = file.name
            binding.textFileSize.text = Formatter.formatFileSize(binding.root.context, file.size)
            
            if (isSelectionModeActive()) {
                binding.checkbox.visibility = android.view.View.VISIBLE
                binding.checkbox.isChecked = file.isSelected
            } else {
                binding.checkbox.visibility = android.view.View.GONE
            }

            binding.imgThumbnail.colorFilter = null
            binding.imgThumbnail.setPadding(0, 0, 0, 0)
            binding.imgThumbnail.setBackgroundColor(Color.TRANSPARENT)
            binding.imgThumbnail.scaleType = android.widget.ImageView.ScaleType.CENTER

            when (file.type) {
                FileType.IMAGE, FileType.VIDEO, FileType.AUDIO, FileType.APK -> {
                    binding.imgThumbnail.load(File(file.path)) {
                        // Optimization: Downsample to thumbnail size
                        size(144, 144) 
                        bitmapConfig(android.graphics.Bitmap.Config.RGB_565)
                        
                        val signatureKey = "${file.path}_${file.dateModified}"
                        memoryCacheKey(signatureKey)
                        diskCacheKey(signatureKey)
                        memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                        diskCachePolicy(coil.request.CachePolicy.ENABLED)
                        crossfade(false)

                        scale(Scale.FILL)
                        if (file.type == FileType.VIDEO) {
                            // Randomize frame based on file hash and rotating seed (wraps ~60s)
                            val micros = (file.hashCode().toLong() + thumbnailSeed)
                            val frameTime = abs(micros) % 60_000_000 // 0 to 60 seconds
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
                        
                        listener(
                            onError = { _, _ ->
                                binding.imgThumbnail.scaleType = android.widget.ImageView.ScaleType.CENTER
                                when (file.type) {
                                    FileType.AUDIO -> {
                                        binding.imgThumbnail.setColorFilter("#00ACC1".toColorInt())
                                    }
                                    FileType.APK -> {
                                        binding.imgThumbnail.setColorFilter("#4CAF50".toColorInt())
                                    }
                                    else -> {}
                                }
                            },
                            onSuccess = { _, _ ->
                                binding.imgThumbnail.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                                binding.imgThumbnail.setBackgroundColor(Color.TRANSPARENT)
                                binding.imgThumbnail.colorFilter = null
                                binding.imgThumbnail.setPadding(0, 0, 0, 0)
                            }
                        )
                    }
                }
                FileType.DOCUMENT -> {
                    val ext = file.path.substringAfterLast('.', "")
                    if (ext.equals("pdf", ignoreCase = true)) {
                        // Load PDF Thumbnail
                        binding.imgThumbnail.load(File(file.path)) {
                            fetcherFactory(PdfFetcher.Factory())
                            size(144, 144)
                            bitmapConfig(android.graphics.Bitmap.Config.RGB_565)
                            
                            val signatureKey = "${file.path}_${file.dateModified}"
                            memoryCacheKey(signatureKey)
                            diskCacheKey(signatureKey)
                            memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                            diskCachePolicy(coil.request.CachePolicy.ENABLED)
                            
                            scale(Scale.FIT)
                            val placeholder = R.drawable.ic_description_24
                            placeholder(placeholder)
                            error(placeholder)
                            crossfade(true)
                            
                            listener(
                                onStart = { _ ->
                                    binding.imgThumbnail.scaleType = android.widget.ImageView.ScaleType.CENTER
                                    binding.imgThumbnail.setColorFilter("#F44336".toColorInt())
                                },
                                onSuccess = { _, _ ->
                                    binding.imgThumbnail.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                                    binding.imgThumbnail.colorFilter = null
                                    binding.imgThumbnail.setBackgroundColor(Color.TRANSPARENT)
                                },
                                onError = { _, _ ->
                                    binding.imgThumbnail.scaleType = android.widget.ImageView.ScaleType.CENTER
                                    binding.imgThumbnail.setColorFilter("#F44336".toColorInt())
                                }
                            )
                        }
                    } else {
                        // Other Documents
                        binding.imgThumbnail.setImageResource(R.drawable.ic_description_24)
                        val color = when (ext.lowercase()) {
                            "doc", "docx" -> "#2b579a" // Blue (Word)
                            "xls", "xlsx" -> "#217346" // Green (Excel)
                            "ppt", "pptx" -> "#d24726" // Orange (PowerPoint)
                            "txt" -> "#757575"         // Grey (Text)
                            else -> "#4285F4"          // Default Blue
                        }
                        binding.imgThumbnail.setColorFilter(color.toColorInt())
                    }
                }
                FileType.ARCHIVE -> {
                    binding.imgThumbnail.setImageResource(R.drawable.ic_archive_24)
                    binding.imgThumbnail.setColorFilter("#FF7043".toColorInt())
                }
                else -> {
                    binding.imgThumbnail.setImageResource(R.drawable.ic_description_24)
                    binding.imgThumbnail.setColorFilter("#4285F4".toColorInt())
                }
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<RecentsListItem>() {
        override fun areItemsTheSame(oldItem: RecentsListItem, newItem: RecentsListItem): Boolean {
            return if (oldItem is RecentsListItem.FileItem && newItem is RecentsListItem.FileItem) {
                oldItem.file.path == newItem.file.path
            } else if (oldItem is RecentsListItem.Header && newItem is RecentsListItem.Header) {
                oldItem.title == newItem.title
            } else {
                false
            }
        }

        override fun areContentsTheSame(oldItem: RecentsListItem, newItem: RecentsListItem): Boolean {
            return oldItem == newItem
        }
    }
}
