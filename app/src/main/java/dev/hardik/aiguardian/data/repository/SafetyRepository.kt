package dev.hardik.aiguardian.data.repository

import dev.hardik.aiguardian.data.local.MedicineDao
import dev.hardik.aiguardian.data.local.BlocklistDao
import dev.hardik.aiguardian.data.local.ScamDao
import dev.hardik.aiguardian.data.model.BlockedNumber
import dev.hardik.aiguardian.data.model.Medicine
import dev.hardik.aiguardian.data.model.ScamEvent
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SafetyRepository @Inject constructor(
    private val medicineDao: MedicineDao,
    private val blocklistDao: BlocklistDao,
    private val scamDao: ScamDao
) {
    // Medicine
    val allMedicines: Flow<List<Medicine>> = medicineDao.getAllMedicines()

    suspend fun addMedicine(medicine: Medicine) = medicineDao.insertMedicine(medicine)

    suspend fun removeMedicine(medicine: Medicine) = medicineDao.deleteMedicine(medicine)

    // Blocklist
    val blocklist: Flow<List<BlockedNumber>> = blocklistDao.getBlocklist()

    suspend fun addToBlocklist(number: String, reason: String = "Scam Detection") {
        blocklistDao.addToBlocklist(BlockedNumber(number, reason))
    }

    suspend fun isBlocked(number: String): Boolean = blocklistDao.isBlocked(number)

    // Scams
    val allScams: Flow<List<ScamEvent>> = scamDao.getAllScams()
    
    suspend fun logScam(scam: ScamEvent) = scamDao.insertScam(scam)
}
