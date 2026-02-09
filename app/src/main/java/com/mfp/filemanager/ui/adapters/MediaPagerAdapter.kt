package com.mfp.filemanager.ui.adapters

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.mfp.filemanager.data.MediaItem
import com.mfp.filemanager.ui.fragments.MediaImageFragment
import com.mfp.filemanager.ui.fragments.MediaVideoFragment

class MediaPagerAdapter(
    fragmentActivity: FragmentActivity,
    private val mediaItems: List<MediaItem>
) : FragmentStateAdapter(fragmentActivity) {

    override fun getItemCount(): Int = mediaItems.size

    override fun createFragment(position: Int): Fragment {
        return when (val item = mediaItems[position]) {
            is MediaItem.Image -> MediaImageFragment.newInstance(item)
            is MediaItem.Video -> MediaVideoFragment.newInstance(item)
        }
    }
}
