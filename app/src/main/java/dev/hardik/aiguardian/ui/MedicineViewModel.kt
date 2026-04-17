package dev.hardik.aiguardian.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.hardik.aiguardian.data.model.Medicine
import dev.hardik.aiguardian.data.repository.SafetyRepository
import dev.hardik.aiguardian.reminder.MedicineManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MedicineViewModel @Inject constructor(
    private val repository: SafetyRepository,
    private val medicineManager: MedicineManager
) : ViewModel() {

    val medicines: StateFlow<List<Medicine>> = repository.allMedicines
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addMedicine(name: String, dosage: String, time: String) {
        viewModelScope.launch {
            val medicine = Medicine(name = name, dosage = dosage, time = time)
            repository.addMedicine(medicine)
            // Ideally we'd get the ID back from Room to schedule correctly
            // For now, assume scheduling happens on list update or manually
        }
    }

    fun deleteMedicine(medicine: Medicine) {
        viewModelScope.launch {
            repository.removeMedicine(medicine)
            medicineManager.cancelReminder(medicine)
        }
    }
}
