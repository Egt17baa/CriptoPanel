package app.criptopanel.legal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import app.criptopanel.core.ui.BackScaffold
import app.criptopanel.core.ui.GlassCard
import app.criptopanel.core.ui.openSafely

@Composable
fun LegalSections(sections: List<LegalSection>) {
    sections.forEach { s ->
        Text(s.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
        Text(s.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun LegalScreen(doc: LegalDoc, onBack: () -> Unit) {
    val uri = LocalUriHandler.current
    BackScaffold(doc.title, onBack) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            when (doc) {
                LegalDoc.TERMS -> {
                    Text("Versión del $TERMS_VERSION", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LegalSections(TERMS)
                }
                LegalDoc.PRIVACY -> LegalSections(PRIVACY)
                LegalDoc.METHOD -> LegalSections(METHOD)
                LegalDoc.SOURCES -> {
                    Text("Todas son públicas y gratuitas. Toca una para abrir su web.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    SOURCES.forEach { s ->
                        GlassCard(Modifier.fillMaxWidth().padding(top = 6.dp), onClick = { uri.openSafely(s.url) }) {
                            Column(Modifier.padding(14.dp)) {
                                Text("${s.name} ↗", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                                Text(s.gives, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            Text(DISCLAIMER, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 16.dp))
        }
    }
}
