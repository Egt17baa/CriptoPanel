package app.criptopanel.security.android

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.fragment.app.FragmentActivity
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private const val ALIAS = "criptopanel_vault_biometric"
private const val PREFS = "vault_biometric"
private const val KEY_IV = "iv"
private const val KEY_BLOB = "wrapped"
private const val REVEAL_ALIAS = "criptopanel_vault_reveal"
private const val KEY_REVEAL_GATE = "reveal_gate"

/**
 * Wraps the vault's data key with an AES key that lives in the Android Keystore (secure hardware when available)
 * and can only be used right after a strong biometric check. Adding a new fingerprint invalidates it.
 * The wrapped key never leaves the phone and is not part of exported vaults.
 */
object BiometricVault {
    fun available(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

    fun enabled(context: Context): Boolean = prefs(context).contains(KEY_BLOB)

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun keyStore() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun createKey(alias: String = ALIAS): SecretKey {
        val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
            }
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply { init(spec) }.generateKey()
    }

    fun disable(context: Context) {
        // Only the unlock key: the setting and key for viewing secrets are independent of it.
        prefs(context).edit { remove(KEY_IV); remove(KEY_BLOB) }
        runCatching { keyStore().deleteEntry(ALIAS) }
    }

    private fun prompt(
        activity: FragmentActivity,
        title: String,
        cipher: Cipher,
        onOk: (Cipher) -> Unit,
        onError: (String) -> Unit,
        onCancel: () -> Unit = {},
        /** The "use the master password" button; by default the same as cancelling. */
        onNegative: () -> Unit = onCancel,
    ) {
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle("Bóveda de CriptoPanel")
            .setNegativeButtonText("Usar contraseña maestra")
            .setAllowedAuthenticators(BIOMETRIC_STRONG)
            .build()
        BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    result.cryptoObject?.cipher?.let(onOk) ?: onError("No se pudo usar la huella")
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                        onNegative()
                    } else if (errorCode == BiometricPrompt.ERROR_USER_CANCELED) {
                        onCancel()
                    } else {
                        onError(errString.toString())
                    }
                }
            },
        ).authenticate(info, BiometricPrompt.CryptoObject(cipher))
    }

    /** Asks for the fingerprint, then stores [dataKey] wrapped; [dataKey] is wiped either way. */
    fun enable(activity: FragmentActivity, dataKey: ByteArray, onDone: (String?) -> Unit) {
        val cipher = try {
            runCatching { keyStore().deleteEntry(ALIAS) }
            Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, createKey()) }
        } catch (e: Exception) {
            dataKey.fill(0)
            onDone("Este móvil no permite guardar la clave con huella")
            return
        }
        prompt(activity, "Activar desbloqueo con huella", cipher, onOk = { c ->
            try {
                val wrapped = c.doFinal(dataKey)
                prefs(activity).edit {
                    putString(KEY_IV, Base64.getEncoder().encodeToString(c.iv))
                    putString(KEY_BLOB, Base64.getEncoder().encodeToString(wrapped))
                }
                onDone(null)
            } catch (e: Exception) {
                onDone("No se pudo activar la huella")
            } finally {
                dataKey.fill(0)
            }
        }, onError = {
            dataKey.fill(0)
            onDone(it)
        }, onCancel = {
            dataKey.fill(0) // Cancelled: the copy of the data key must not linger.
        })
    }

    /** Asks for the fingerprint and hands back the data key; [onError] with a message otherwise. */
    fun unlock(activity: FragmentActivity, onKey: (ByteArray) -> Unit, onError: (String) -> Unit) {
        val p = prefs(activity)
        val iv = p.getString(KEY_IV, null)
        val blob = p.getString(KEY_BLOB, null)
        if (iv == null || blob == null) return onError("La huella no está activada")
        val cipher = try {
            val key = keyStore().getKey(ALIAS, null) as? SecretKey ?: throw KeyPermanentlyInvalidatedException()
            Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, Base64.getDecoder().decode(iv))) }
        } catch (e: KeyPermanentlyInvalidatedException) {
            disable(activity)
            return onError("Has cambiado las huellas del móvil: entra con la contraseña maestra y vuelve a activarla")
        } catch (e: Exception) {
            return onError("No se pudo usar la huella")
        }
        prompt(activity, "Desbloquear la bóveda", cipher, onOk = { c ->
            try {
                onKey(c.doFinal(Base64.getDecoder().decode(blob)))
            } catch (e: Exception) {
                onError("No se pudo desbloquear con la huella")
            }
        }, onError = onError)
    }

    // ---- Fingerprint for each secret --------------------------------------------------------------------------

    /** Whether secrets ask for the fingerprint (or the master password) before they are shown or copied. On by default. */
    fun revealEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_REVEAL_GATE, true)

    fun setRevealEnabled(context: Context, on: Boolean) {
        prefs(context).edit { putBoolean(KEY_REVEAL_GATE, on) }
    }

    /** Forgets the key used for viewing secrets; a new one is made at the next use (after the master password). */
    fun rearmReveal(context: Context) {
        runCatching { keyStore().deleteEntry(REVEAL_ALIAS) }
    }

    /**
     * One fingerprint check, valid for this single use: a Keystore key that needs a strong biometric every time is
     * used through the prompt. [onNeedsMaster] when the phone's fingerprints changed (the key is gone: the master
     * password is needed once to arm a new one) or when the user chose "use the master password".
     */
    fun authorizeReveal(
        activity: FragmentActivity,
        onOk: () -> Unit,
        onError: (String) -> Unit,
        onNeedsMaster: () -> Unit,
    ) {
        val cipher = try {
            val key = keyStore().getKey(REVEAL_ALIAS, null) as? SecretKey ?: createKey(REVEAL_ALIAS)
            Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key) }
        } catch (e: KeyPermanentlyInvalidatedException) {
            rearmReveal(activity)
            return onNeedsMaster()
        } catch (e: Exception) {
            return onError("No se pudo usar la huella")
        }
        prompt(activity, "Ver este secreto", cipher, onOk = { c ->
            try {
                c.doFinal(ByteArray(1)) // Only works if the Keystore accepted the fingerprint for this use.
                onOk()
            } catch (e: Exception) {
                onError("No se pudo comprobar la huella")
            }
        }, onError = onError, onCancel = {}, onNegative = onNeedsMaster)
    }
}
