package com.mfp.filemanager.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import coil.load
import com.mfp.filemanager.data.MediaItem
import com.mfp.filemanager.databinding.FragmentMediaImageBinding
import com.mfp.filemanager.ui.activities.MediaViewerActivity

class MediaImageFragment : Fragment() {

    private var _binding: FragmentMediaImageBinding? = null
    private val binding get() = _binding!!

    companion object {
        private const val ARG_MEDIA_ITEM = "media_item"

        fun newInstance(item: MediaItem.Image): MediaImageFragment {
            val fragment = MediaImageFragment()
            val args = Bundle()
            args.putParcelable(ARG_MEDIA_ITEM, item)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMediaImageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val item = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            arguments?.getParcelable(ARG_MEDIA_ITEM, MediaItem.Image::class.java)
        } else {
            @Suppress("DEPRECATION")
            arguments?.getParcelable(ARG_MEDIA_ITEM)
        } ?: return

        // Load thumbnail into ZoomImageView
        binding.imageView.resetZoom()
        binding.imageView.load(item.uri) {
            crossfade(true)
        }
        
        binding.imageView.setOnClickListener {
            (activity as? MediaViewerActivity)?.toggleControls()
        }
    }

    override fun onResume() {
        super.onResume()
        // Reset zoom state when coming back
        if (_binding != null) {
            binding.imageView.resetZoom()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
