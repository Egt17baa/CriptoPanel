package app.criptopanel.legal

import android.content.Context
import androidx.core.content.edit
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.criptopanel.core.ui.GlassCard

private const val PREFS = "criptopanel"
private const val KEY_TERMS = "terms_version"
private const val KEY_TERMS_AT = "terms_accepted_at"

fun termsAccepted(context: Context): Boolean =
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_TERMS, null) == TERMS_VERSION

fun acceptTerms(context: Context) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
        putString(KEY_TERMS, TERMS_VERSION)
        putLong(KEY_TERMS_AT, System.currentTimeMillis())
    }
}

/** Shown before anything else until the current terms are accepted. */
@Composable
fun TermsGateScreen(onAccept: () -> Unit) {
    var checked by rememberSaveable { mutableStateOf(false) }
    var full by rememberSaveable { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Bienvenido a CriptoPanel", style = MaterialTheme.typography.headlineMedium)
        Text("Antes de empezar, lo importante:", color = MaterialTheme.colorScheme.onSurfaceVariant)
        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TERMS_SUMMARY.forEach { Text("• $it") }
            }
        }
        TextButton(onClick = { full = !full }) { Text(if (full) "Ocultar los términos completos" else "Leer los términos completos") }
        if (full) {
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { LegalSections(TERMS) }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { checked = !checked }) {
            Checkbox(checked = checked, onCheckedChange = { checked = it })
            Text("He leído y acepto los términos y entiendo que CriptoPanel no da asesoramiento financiero.")
        }
        Button(onClick = onAccept, enabled = checked, modifier = Modifier.fillMaxWidth()) { Text("Empezar") }
    }
}
