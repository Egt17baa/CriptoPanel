package app.criptopanel.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.criptopanel.BuildConfig
import app.criptopanel.core.ui.GlassCard
import app.criptopanel.legal.DISCLAIMER
import app.criptopanel.learn.LESSONS
import app.criptopanel.legal.LegalDoc
import androidx.compose.foundation.clickable

private data class Entry(val icon: String, val title: String, val subtitle: String, val onClick: () -> Unit)

/** Fifth tab: tools, legal information and plain-language transparency. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(
    activeAlerts: Int,
    lessonsRead: Int,
    onLearn: () -> Unit,
    onSecurity: () -> Unit,
    onCompare: () -> Unit,
    onCalculator: () -> Unit,
    onWhatIf: () -> Unit,
    onAlerts: () -> Unit,
    onLegal: (LegalDoc) -> Unit,
    currencyCode: String,
    currencyNote: String?,
    onCurrency: (String) -> Unit,
) {
    val tools = listOf(
        Entry("⇄", "Comparador", "Hasta 4 monedas lado a lado", onCompare),
        Entry("＝", "Calculadora", "Cripto ⇄ dinero y entre criptos", onCalculator),
        Entry("↗", "¿Y si hubiera invertido?", "De una vez o comprando cada mes", onWhatIf),
        Entry("🔔", "Alertas", if (activeAlerts == 0) "Ninguna activa" else "$activeAlerts activas · en directo", onAlerts),
    )
    val info = LegalDoc.entries.map { doc ->
        val subtitle = when (doc) {
            LegalDoc.TERMS -> "Lo que aceptaste al empezar"
            LegalDoc.PRIVACY -> "Sin cuentas, sin publicidad, sin rastreo"
            LegalDoc.SOURCES -> "De dónde sale cada dato"
            LegalDoc.METHOD -> "TOTAL2/3, ganancias, APY, alertas"
        }
        Entry("§", doc.title, subtitle) { onLegal(doc) }
    }
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { TopAppBar(title = { Text("Más") }) },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "Herramientas, información y transparencia",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            item {
                Section(
                    "Aprende",
                    listOf(Entry("📚", "Aprende", "Orígenes, criptografía, wallets… · $lessonsRead de ${LESSONS.size} leídas", onLearn)),
                )
            }
            item {
                Section(
                    "Seguridad",
                    listOf(Entry("🔐", "Seguridad", "Cifrado AES-256 de texto y archivos, generador, filtraciones…", onSecurity)),
                )
            }
            item { Section("Herramientas", tools) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Preferencias", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 4.dp))
                    GlassCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Divisa", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(selected = currencyCode == "USD", onClick = { onCurrency("USD") }, label = { Text("Dólar · USD") })
                                FilterChip(selected = currencyCode == "EUR", onClick = { onCurrency("EUR") }, label = { Text("Euro · EUR") })
                            }
                            Text(
                                currencyNote ?: "En euros, todo se convierte con el cambio del BCE. Tu cartera usa el cambio del día de cada " +
                                    "operación; los gráficos de TradingView y los filtros de TVL siguen en dólares.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            item { Section("Información", info) }
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Lo importante, en claro", style = MaterialTheme.typography.titleSmall)
                        listOf(
                            "Es información, no asesoramiento financiero: no te decimos qué comprar ni vender.",
                            "No guardamos tu dinero ni operamos por ti. Sin custodia, sin trading, sin publicidad.",
                            "Tu cartera, favoritos y alertas se quedan en este móvil. No hay cuentas.",
                            "Usamos fuentes públicas gratuitas: pueden fallar o llegar con retraso; si pasa, verás el último dato con un aviso.",
                        ).forEach { Text("• $it", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
            item {
                Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("CriptoPanel", style = MaterialTheme.typography.titleMedium)
                    Text("Versión ${BuildConfig.VERSION_NAME}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(DISCLAIMER, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                    Text("Sin custodia · Sin trading · Sin publicidad", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, entries: List<Entry>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 4.dp))
        GlassCard(Modifier.fillMaxWidth()) {
            entries.forEachIndexed { i, e ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxWidth().clickable(onClick = e.onClick).padding(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    Text(e.icon, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 2.dp))
                    Column(Modifier.weight(1f)) {
                        Text(e.title, style = MaterialTheme.typography.titleSmall)
                        Text(e.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (i < entries.lastIndex) HorizontalDivider(Modifier.padding(start = 56.dp))
            }
        }
    }
}
