package com.mfp.filemanager.ui.adapters

import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.size.Scale
import com.mfp.filemanager.R
import com.mfp.filemanager.data.FileType
import com.mfp.filemanager.data.trash.TrashedFile
import com.mfp.filemanager.databinding.ItemFileListBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TrashAdapter(
    private val onItemClick: (TrashedFile) -> Unit,
    private val onLongClick: (TrashedFile) -> Unit,
    private val onMenuClick: (TrashedFile) -> Unit
) : ListAdapter<TrashedFile, TrashAdapter.ViewHolder>(DiffCallback()) {

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

    inner class ViewHolder(private val binding: ItemFileListBinding) :
        RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(getItem(position))
                }
            }
            binding.root.setOnLongClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onLongClick(getItem(position))
                }
                true
            }
            binding.btnMore.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onMenuClick(getItem(position))
                }
            }
        }

        fun bind(file: TrashedFile) {
            binding.root.isSelected = file.isSelected
            binding.root.setBackgroundColor(
                if (file.isSelected) 
                    android.graphics.Color.parseColor("#334285F4") 
                else 
                    android.graphics.Color.TRANSPARENT
            )
            binding.btnMore.visibility = android.view.View.GONE

            binding.textName.text = file.name
            
            // Show deletion date
            val date = Date(file.dateDeleted)
            val dateFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
            binding.textDate.text = "Deleted: ${dateFormat.format(date)}"
            binding.textSize.text = Formatter.formatFileSize(binding.root.context, file.size)

            // Icon & Color Logic (Matching FileListAdapter)
            binding.imgIcon.colorFilter = null
            binding.imgIcon.setPadding(0, 0, 0, 0)
            binding.imgIcon.setBackgroundColor(android.graphics.Color.TRANSPARENT)

            when {
                file.type == FileType.IMAGE || file.type == FileType.VIDEO || file.type == FileType.APK -> {
                    binding.imgIcon.load(File(file.trashPath)) {
                        size(144, 144)
                        bitmapConfig(android.graphics.Bitmap.Config.RGB_565)
                        val signatureKey = "${file.trashPath}_${file.dateDeleted}"
                        memoryCacheKey(signatureKey)
                        diskCacheKey(signatureKey)
                        memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                        diskCachePolicy(coil.request.CachePolicy.ENABLED)
                        crossfade(false)
                        
                        scale(Scale.FILL)
                        val placeholder = when (file.type) {
                            FileType.IMAGE -> R.drawable.ic_image_24
                            FileType.VIDEO -> R.drawable.ic_movie_24
                            else -> R.drawable.ic_apk_24
                        }
                        placeholder(placeholder)
                        error(placeholder)
                    }
                }
                file.type == FileType.AUDIO -> {
                    binding.imgIcon.setImageResource(R.drawable.ic_music_note_24)
                    binding.imgIcon.setBackgroundColor(android.graphics.Color.parseColor("#1A2C2E"))
                    binding.imgIcon.setColorFilter(android.graphics.Color.parseColor("#00ACC1"))
                    binding.imgIcon.setPadding(8, 8, 8, 8)
                }
                file.type == FileType.DOCUMENT -> {
                    binding.imgIcon.setImageResource(R.drawable.ic_description_24)
                    binding.imgIcon.setBackgroundColor(android.graphics.Color.parseColor("#2E1A1A"))
                    binding.imgIcon.setColorFilter(android.graphics.Color.parseColor("#F44336"))
                    binding.imgIcon.setPadding(8, 8, 8, 8)
                }
                file.type == FileType.ARCHIVE -> {
                    binding.imgIcon.setImageResource(R.drawable.ic_archive_24)
                    binding.imgIcon.setBackgroundColor(android.graphics.Color.parseColor("#2E231A"))
                    binding.imgIcon.setColorFilter(android.graphics.Color.parseColor("#FF7043"))
                    binding.imgIcon.setPadding(8, 8, 8, 8)
                }
                else -> {
                    binding.imgIcon.setImageResource(R.drawable.ic_description_24)
                    binding.imgIcon.setBackgroundColor(android.graphics.Color.parseColor("#1A202E"))
                    binding.imgIcon.setColorFilter(android.graphics.Color.parseColor("#4285F4"))
                    binding.imgIcon.setPadding(8, 8, 8, 8)
                }
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<TrashedFile>() {
        override fun areItemsTheSame(oldItem: TrashedFile, newItem: TrashedFile): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: TrashedFile, newItem: TrashedFile): Boolean {
            return oldItem == newItem
        }
    }
}
