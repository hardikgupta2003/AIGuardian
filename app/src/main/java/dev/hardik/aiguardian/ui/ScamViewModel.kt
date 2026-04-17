package dev.hardik.aiguardian.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.hardik.aiguardian.data.model.ScamEvent
import dev.hardik.aiguardian.data.repository.SafetyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ScamViewModel @Inject constructor(
    private val repository: SafetyRepository
) : ViewModel() {

    val scams: StateFlow<List<ScamEvent>> = repository.allScams
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
