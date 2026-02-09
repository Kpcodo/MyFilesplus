package com.mfp.filemanager.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.mfp.filemanager.data.SettingsRepository
import com.mfp.filemanager.databinding.FragmentMainPagerBinding
import com.mfp.filemanager.ui.viewmodels.MainViewModel
import kotlinx.coroutines.launch

class MainPagerFragment : Fragment() {

    private var _binding: FragmentMainPagerBinding? = null
    private val binding get() = _binding!!

    // Shared ViewModel with MainActivity
    private val mainViewModel: MainViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMainPagerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = MainPagerAdapter(this)
        binding.viewPager.adapter = adapter
        binding.viewPager.offscreenPageLimit = 3 // Keep all 4 tabs in memory
        
        // Remove 'bouncy' overscroll effect for cleaner look
        binding.viewPager.getChildAt(0).overScrollMode = View.OVER_SCROLL_NEVER

        // Sync ViewPager -> ViewModel (when User swipes)
        binding.viewPager.registerOnPageChangeCallback(object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                mainViewModel.setCurrentTab(position)
            }
        })

        // Sync ViewModel -> ViewPager (when BottomNav clicked)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                mainViewModel.targetTab.collect { index ->
                    binding.viewPager.setCurrentItem(index, true)
                }
            }
        }
        
        // Settings: Enable/Disable Swipe
        // If disabled, user input is blocked, so only BottomNav clicks (programmatic) work.
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                 SettingsRepository(requireContext().applicationContext).swipeNavigationEnabled.collect { enabled ->
                     binding.viewPager.isUserInputEnabled = enabled
                 }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private inner class MainPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
        override fun getItemCount(): Int = 4

        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> HomeFragment()
                1 -> MusicFragment()
                2 -> TrashFragment()
                3 -> SettingsFragment()
                else -> HomeFragment()
            }
        }
    }
}
