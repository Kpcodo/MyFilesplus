package com.mfp.filemanager.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel : ViewModel() {

    // Current Tab Index (0=Home, 1=Music, 2=Trash, 3=Settings)
    private val _currentTab = MutableStateFlow(0)
    val currentTab: StateFlow<Int> = _currentTab.asStateFlow()

    // Events to request Tab Change (from Bottom Nav click)
    private val _targetTab = MutableSharedFlow<Int>()
    val targetTab: SharedFlow<Int> = _targetTab.asSharedFlow()

    fun setCurrentTab(index: Int) {
        _currentTab.value = index
    }

    fun requestTabChange(index: Int) {
        viewModelScope.launch {
            _targetTab.emit(index)
        }
    }
}
