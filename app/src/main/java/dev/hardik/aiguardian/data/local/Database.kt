package dev.hardik.aiguardian.data.local

import androidx.room.*
import dev.hardik.aiguardian.data.model.BlockedNumber
import dev.hardik.aiguardian.data.model.Medicine
import dev.hardik.aiguardian.data.model.ScamEvent
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicineDao {
    @Query("SELECT * FROM medicines")
    fun getAllMedicines(): Flow<List<Medicine>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedicine(medicine: Medicine)

    @Delete
    suspend fun deleteMedicine(medicine: Medicine)
}

@Dao
interface BlocklistDao {
    @Query("SELECT * FROM blocklist")
    fun getBlocklist(): Flow<List<BlockedNumber>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addToBlocklist(number: BlockedNumber)

    @Query("SELECT EXISTS(SELECT * FROM blocklist WHERE phoneNumber = :number)")
    suspend fun isBlocked(number: String): Boolean
}

@Dao
interface ScamDao {
    @Query("SELECT * FROM scam_events ORDER BY timestamp DESC")
    fun getAllScams(): Flow<List<ScamEvent>>

    @Insert
    suspend fun insertScam(scam: ScamEvent)
}

@Database(entities = [Medicine::class, BlockedNumber::class, ScamEvent::class], version = 2)
abstract class AppDatabase : RoomDatabase() {
    abstract fun medicineDao(): MedicineDao
    abstract fun blocklistDao(): BlocklistDao
    abstract fun scamDao(): ScamDao
}
