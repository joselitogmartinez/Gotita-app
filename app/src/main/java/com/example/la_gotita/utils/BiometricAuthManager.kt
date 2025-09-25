package com.example.la_gotita.utils

import android.os.Build
import android.util.Log // Importante para Logging
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat

private const val TAG_BIOMETRIC_MANAGER = "BiometricAuthManager" // Tag para logs

interface BiometricAuthListener {
    fun onBiometricAuthSuccess()
    fun onBiometricAuthError(errorCode: Int, errString: CharSequence)
    fun onBiometricAuthFailed()
}

class BiometricAuthManager(
    private val activity: AppCompatActivity,
    private val listener: BiometricAuthListener
) {

    private var biometricPrompt: BiometricPrompt
    private var promptInfo: BiometricPrompt.PromptInfo

    init {
        Log.d(TAG_BIOMETRIC_MANAGER, "Initializing BiometricAuthManager")
        val executor = ContextCompat.getMainExecutor(activity)

        biometricPrompt = BiometricPrompt(activity, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    Log.e(TAG_BIOMETRIC_MANAGER, "onAuthenticationError: Code: ${'$'}errorCode, Message: ${'$'}errString")
                    listener.onBiometricAuthError(errorCode, errString)
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    Log.d(TAG_BIOMETRIC_MANAGER, "onAuthenticationSucceeded")
                    listener.onBiometricAuthSuccess()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    Log.w(TAG_BIOMETRIC_MANAGER, "onAuthenticationFailed")
                    listener.onBiometricAuthFailed()
                }
            })

        val promptBuilder = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Autenticación Biométrica")
            .setSubtitle("Inicia sesión usando tu credencial biométrica o del dispositivo")
            // .setDescription("Coloca tu dedo en el sensor o usa la credencial de tu dispositivo.") // Opcional

        // Configurar qué tipos de autenticadores permitir
        // BIOMETRIC_STRONG (Huella, Rostro 3D seguro) Y DEVICE_CREDENTIAL (PIN, Patrón, Contraseña del dispositivo)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) { // Para setAllowedAuthenticators
             Log.d(TAG_BIOMETRIC_MANAGER, "SDK >= R, using setAllowedAuthenticators with BIOMETRIC_STRONG or DEVICE_CREDENTIAL")
            promptBuilder.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
        } else {
            // Para versiones anteriores a Android R (API 30), setDeviceCredentialAllowed(true) es la forma de incluir PIN/Patrón/Contraseña.
            // BiometricPrompt por defecto ya intenta BIOMETRIC_WEAK o BIOMETRIC_STRONG si están disponibles.
             Log.d(TAG_BIOMETRIC_MANAGER, "SDK < R, using setDeviceCredentialAllowed(true)")
            @Suppress("DEPRECATION")
            promptBuilder.setDeviceCredentialAllowed(true) // Deprecado pero necesario para < API 30
        }
        // No se puede usar setNegativeButtonText si se usa setDeviceCredentialAllowed(true) o si DEVICE_CREDENTIAL está en setAllowedAuthenticators
        // En su lugar, el sistema proporciona un botón de cancelación.
        // Si solo se usara BIOMETRIC_STRONG/WEAK sin DEVICE_CREDENTIAL, se podría usar:
        // .setNegativeButtonText("Cancelar")

        promptInfo = promptBuilder.build()
        Log.d(TAG_BIOMETRIC_MANAGER, "PromptInfo configured: ${'$'}promptInfo")
    }

    fun canAuthenticate(): Boolean {
        val biometricManager = BiometricManager.from(activity)
        val authenticators = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        } else {
            // Para API < 30, la comprobación es un poco diferente.
            // BiometricManager.canAuthenticate() (sin args) chequea BIOMETRIC_WEAK o mejor.
            // Y también necesitamos considerar DEVICE_CREDENTIAL por separado si queremos.
            // Esta es una simplificación; la lógica real de 'setDeviceCredentialAllowed(true)' está más integrada en el prompt.
            // Pero para el chequeo, si BIOMETRIC_WEAK está OK, eso cubre la parte biométrica.
            BiometricManager.Authenticators.BIOMETRIC_WEAK // Esto es lo que canAuthenticate() sin argumentos revisa.
        }

        val canAuthResultCode = biometricManager.canAuthenticate(authenticators)
        val canAuthResultBoolean = canAuthResultCode == BiometricManager.BIOMETRIC_SUCCESS
        
        Log.d(TAG_BIOMETRIC_MANAGER, "canAuthenticate() check for types: ${'$'}authenticators. Result code: ${'$'}canAuthResultCode (${'$'}{resultCodeToString(canAuthResultCode)}) -> ${'$'}canAuthResultBoolean")
        return canAuthResultBoolean
    }

    private fun resultCodeToString(code: Int): String {
        return when (code) {
            BiometricManager.BIOMETRIC_SUCCESS -> "BIOMETRIC_SUCCESS"
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> "BIOMETRIC_ERROR_NO_HARDWARE"
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> "BIOMETRIC_ERROR_HW_UNAVAILABLE"
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> "BIOMETRIC_ERROR_NONE_ENROLLED"
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> "BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED"
            BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED -> "BIOMETRIC_ERROR_UNSUPPORTED"
            BiometricManager.BIOMETRIC_STATUS_UNKNOWN -> "BIOMETRIC_STATUS_UNKNOWN"
            else -> "UNKNOWN_RESULT_CODE"
        }
    }

    fun showBiometricPrompt() {
        Log.d(TAG_BIOMETRIC_MANAGER, "showBiometricPrompt() called")
        if (canAuthenticate()) { // Doble chequeo por si acaso el estado cambió
            Log.d(TAG_BIOMETRIC_MANAGER, "Proceeding to show prompt.")
            biometricPrompt.authenticate(promptInfo)
        } else {
            Log.e(TAG_BIOMETRIC_MANAGER, "Cannot show prompt because canAuthenticate() is false.")
            // Opcionalmente, notificar al listener de un error aquí si se esperaba que funcionara
            // listener.onBiometricAuthError(-1, "No se puede autenticar en este dispositivo ahora.")
        }
    }
}
