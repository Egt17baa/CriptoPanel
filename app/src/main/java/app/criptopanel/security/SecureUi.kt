package app.criptopanel.security

import android.app.Activity
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.os.PersistableBundle
import android.view.WindowManager
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

const val CLIPBOARD_CLEAR_MS = 45_000L

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

/**
 * No screenshots, screen recording or previews in Recents while a security screen is shown, and no taps while
 * another app draws over it (tapjacking; Android 12+ also blocks that itself).
 */
@Composable
fun SecureWindow() {
    val activity = LocalContext.current.activity()
    DisposableEffect(activity) {
        val window = activity?.window
        if (window != null) {
            // Counted: going from one secure screen to the next, the old one leaves after the new one arrived.
            if (secureScreens++ == 0) {
                window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                window.decorView.filterTouchesWhenObscured = true
            }
        }
        onDispose {
            if (window != null && --secureScreens == 0) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                window.decorView.filterTouchesWhenObscured = false
            }
        }
    }
}

/** Secure screens currently shown (main thread only; the app has a single activity). */
private var secureScreens = 0

/** Outlives any screen, so leaving the screen right after copying still clears the clipboard. */
private val clipboardScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

/** Copies [text] marked as sensitive (hidden in the keyboard's clipboard preview) and clears it after 45 s if unchanged. */
fun copySensitive(context: Context, text: String) {
    val cm = context.getSystemService(ClipboardManager::class.java) ?: return
    val clip = ClipData.newPlainText("CriptoPanel", text)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
    }
    cm.setPrimaryClip(clip)
    clipboardScope.launch {
        delay(CLIPBOARD_CLEAR_MS)
        val still = runCatching { cm.primaryClip?.getItemAt(0)?.text?.toString() == text }.getOrDefault(false)
        if (still) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) cm.clearPrimaryClip() else cm.setPrimaryClip(ClipData.newPlainText("", ""))
        }
    }
}

/** Password field with a show/hide switch; never put in saved state. */
@Composable
fun PasswordField(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, label: String = "Contraseña") {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
        trailingIcon = { TextButton(onClick = { visible = !visible }) { Text(if (visible) "Ocultar" else "Ver", style = MaterialTheme.typography.labelMedium) } },
        modifier = modifier.fillMaxWidth(),
    )
}
