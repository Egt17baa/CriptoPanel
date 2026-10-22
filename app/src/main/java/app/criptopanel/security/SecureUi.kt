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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus

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

/** Label of everything this app copies, so a leftover copy can be recognised later. */
private const val CLIP_LABEL = "CriptoPanel"

private fun ClipboardManager.wipe() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) clearPrimaryClip() else setPrimaryClip(ClipData.newPlainText("", ""))
}

/** Copies [text] marked as sensitive (hidden in the keyboard's clipboard preview) and clears it after 45 s if unchanged. */
fun copySensitive(context: Context, text: String) {
    val cm = context.getSystemService(ClipboardManager::class.java) ?: return
    val clip = ClipData.newPlainText(CLIP_LABEL, text)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
    }
    cm.setPrimaryClip(clip)
    clipboardScope.launch {
        delay(CLIPBOARD_CLEAR_MS)
        // Someone else's copy since then (an image, another app's text): leave it alone.
        val label = runCatching { cm.primaryClipDescription?.label }.getOrNull()
        if (label != null && label != CLIP_LABEL) return@launch
        // Ours, or hidden from us (Android 10+ hides the clipboard from apps in the background): clear it, never
        // leave a secret there. If it is readable and no longer our text, it was replaced: leave it.
        val current = runCatching { cm.primaryClip?.getItemAt(0)?.text?.toString() }.getOrNull()
        if (current == null || current == text) cm.wipe()
    }
}

/**
 * Clears a copy of ours older than 45 s, left behind because the app was closed before its timer ran
 * (Android 8–12 never clear the clipboard themselves). Call when the app has focus (needed to read it).
 */
fun clearStaleCopy(context: Context) {
    val cm = context.getSystemService(ClipboardManager::class.java) ?: return
    val desc = runCatching { cm.primaryClipDescription }.getOrNull() ?: return
    if (desc.label == CLIP_LABEL && System.currentTimeMillis() - desc.timestamp >= CLIPBOARD_CLEAR_MS) cm.wipe()
}

/** The system text menu without Copy and Cut: those would skip the sensitive flag and the 45-second clearing. */
private class NoCopyToolbar(private val base: TextToolbar) : TextToolbar {
    override val status: TextToolbarStatus get() = base.status

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?,
    ) = base.showMenu(rect, null, onPasteRequested, null, onSelectAllRequested)

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?,
        onAutofillRequested: (() -> Unit)?,
    ) = base.showMenu(rect, null, onPasteRequested, null, onSelectAllRequested, onAutofillRequested)

    override fun hide() = base.hide()
}

/** Secrets typed or shown in [content] can only leave through the app's own "Copiar" (pasting still works). */
@Composable
fun NoCopy(content: @Composable () -> Unit) {
    val base = LocalTextToolbar.current
    CompositionLocalProvider(LocalTextToolbar provides remember(base) { NoCopyToolbar(base) }, content = content)
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
