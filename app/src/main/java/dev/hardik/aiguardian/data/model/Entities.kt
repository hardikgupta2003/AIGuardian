package dev.hardik.aiguardian.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "medicines")
data class Medicine(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val dosage: String,
    val time: String, // HH:mm format
    val isActive: Boolean = true
)

@Entity(tableName = "blocklist")
data class BlockedNumber(
    @PrimaryKey val phoneNumber: String,
    val reason: String = "Scam Detection"
)

@Entity(tableName = "scam_events")
data class ScamEvent(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val phoneNumber: String,
    val transcription: String,
    val timestamp: Long = System.currentTimeMillis()
)
