package app.criptopanel.security

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import app.criptopanel.security.android.BiometricVault
import kotlinx.coroutines.launch

/** Seconds a secret stays visible after it was unlocked with the fingerprint. */
const val REVEAL_SECONDS = 30

/**
 * The check done before a secret is shown or copied. When [enabled], [request] asks for the fingerprint (every time,
 * hardware-enforced: see [BiometricVault.authorizeReveal]) and runs the action only if it passes; without a usable
 * fingerprint, or by choice, it asks for the master password. When off it just runs the action.
 */
class RevealGate(val enabled: Boolean, val request: (onGranted: () -> Unit) -> Unit)

/** Builds the [RevealGate] of the vault screens, with the master-password dialog it may need. */
@Composable
fun rememberRevealGate(store: VaultStore): RevealGate {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    var wrong by remember { mutableStateOf(false) }
    val enabled = BiometricVault.revealEnabled(context)

    pending?.let { granted ->
        PasswordDialog(
            title = "Contraseña maestra",
            confirmLabel = "Continuar",
            warning = if (wrong) "Contraseña incorrecta" else null,
            onDismiss = {
                pending = null
                wrong = false
            },
        ) { pw ->
            scope.launch {
                val ok = try {
                    store.checkMaster(pw)
                } finally {
                    pw.fill('\u0000')
                }
                if (ok) {
                    BiometricVault.rearmReveal(context) // A new fingerprint key, so a changed fingerprint set is trusted again.
                    pending = null
                    wrong = false
                    granted()
                } else {
                    wrong = true
                }
            }
        }
    }

    return RevealGate(enabled) { onGranted ->
        store.touch()
        val activity = context.fragmentActivity()
        when {
            !enabled -> onGranted()
            activity != null && BiometricVault.available(context) -> BiometricVault.authorizeReveal(
                activity,
                onOk = onGranted,
                onError = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() },
                onNeedsMaster = { pending = onGranted },
            )
            else -> pending = onGranted
        }
    }
}
