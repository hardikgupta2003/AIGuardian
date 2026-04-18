package dev.hardik.aiguardian.utils

import android.content.Context
import kotlin.random.Random

object DeviceProfile {
    private const val PREFS_NAME = "AIGuardianDevicePrefs"
    private const val KEY_DEVICE_PIN = "DEVICE_PIN"
    private const val KEY_DEVICE_ROLE = "DEVICE_ROLE"

    enum class Role {
        ELDER, CARETAKER, NONE
    }

    fun getRole(context: Context): Role {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val roleStr = prefs.getString(KEY_DEVICE_ROLE, "NONE") ?: "NONE"
        return try {
            Role.valueOf(roleStr)
        } catch (e: Exception) {
            Role.NONE
        }
    }

    fun setRole(context: Context, role: Role) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_DEVICE_ROLE, role.name).apply()
    }

    fun getOrGeneratePin(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var pin = prefs.getString(KEY_DEVICE_PIN, null)
        if (pin == null) {
            pin = String.format("%06d", Random.nextInt(100000, 999999))
            prefs.edit().putString(KEY_DEVICE_PIN, pin).apply()
        }
        return pin
    }
}
