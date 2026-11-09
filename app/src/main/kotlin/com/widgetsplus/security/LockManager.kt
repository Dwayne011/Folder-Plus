package com.widgetsplus.security

import android.content.Context
import android.content.SharedPreferences
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators
import com.widgetsplus.core.model.LockMode
import com.widgetsplus.core.security.PinHasher

/**
 * One app-wide PIN protects every folder that has a PIN lock. Biometric
 * availability is read from the system. Nothing here keeps folders unlocked
 * between openings: each open re-authenticates.
 */
class LockManager(context: Context) {
    private val appContext = context.applicationContext
    private val prefs: SharedPreferences = appContext.getSharedPreferences("widgetsplus_lock", Context.MODE_PRIVATE)

    val hasPin: Boolean get() = prefs.contains(KEY_PIN_HASH)

    fun setPin(pin: String) {
        prefs.edit().putString(KEY_PIN_HASH, PinHasher.hash(pin)).apply()
    }

    fun clearPin() {
        prefs.edit().remove(KEY_PIN_HASH).apply()
    }

    fun verifyPin(pin: String): Boolean {
        val stored = prefs.getString(KEY_PIN_HASH, null) ?: return false
        return PinHasher.verify(pin, stored)
    }

    fun biometricsAvailable(): Boolean =
        BiometricManager.from(appContext).canAuthenticate(Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS

    /** True when the system prompt can authenticate with biometrics or the device PIN/pattern. */
    fun biometricOrCredentialAvailable(): Boolean =
        BiometricManager.from(appContext)
            .canAuthenticate(Authenticators.BIOMETRIC_WEAK or Authenticators.DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS

    /**
     * Whether opening a folder with [mode] must show the lock gate. A PIN-only
     * lock with no PIN configured is treated as unlocked rather than locking
     * the user out; the editor prevents this state in the first place.
     */
    fun requiresUnlock(mode: LockMode): Boolean = when (mode) {
        LockMode.NONE -> false
        LockMode.PIN -> hasPin
        LockMode.BIOMETRIC -> biometricOrCredentialAvailable()
        LockMode.PIN_OR_BIOMETRIC -> hasPin || biometricOrCredentialAvailable()
    }

    companion object {
        private const val KEY_PIN_HASH = "pin_hash"
    }
}
