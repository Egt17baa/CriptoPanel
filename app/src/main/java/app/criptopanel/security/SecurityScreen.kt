package app.criptopanel.security

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.criptopanel.core.ui.BackScaffold
import app.criptopanel.core.ui.GlassCard

data class SecurityTool(val route: String, val icon: String, val title: String, val subtitle: String)

val SECURITY_TOOLS = listOf(
    SecurityTool("security/vault", "🗄", "Bóveda de contraseñas", "Contraseñas, frases semilla y notas, con huella"),
    SecurityTool("security/totp", "⏱", "Autenticador 2FA", "Códigos de 6 dígitos como Google Authenticator"),
    SecurityTool("security/text", "🔤", "Cifrar texto", "AES-256-GCM o ChaCha20-Poly1305, para enviar por chat o correo"),
    SecurityTool("security/file", "📁", "Cifrar archivos", "Cualquier archivo del móvil, de cualquier tamaño"),
    SecurityTool("security/generator", "🎲", "Generador de contraseñas", "Contraseñas y frases con su fortaleza"),
    SecurityTool("security/breach", "🕵", "¿Mi contraseña está filtrada?", "Have I Been Pwned, sin enviar tu contraseña"),
    SecurityTool("security/hash", "#", "Huella de archivo (SHA-256)", "Comprueba que una descarga no se ha alterado"),
)

@Composable
fun SecurityScreen(onTool: (String) -> Unit, onBack: () -> Unit) {
    SecureWindow()
    BackScaffold("Seguridad", onBack) { padding ->
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Text(
                    "Todo se hace en tu móvil. Solo la bóveda guarda datos, cifrados y únicamente en este móvil.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            SECURITY_TOOLS.forEach { t ->
                item {
                    GlassCard(Modifier.fillMaxWidth(), onClick = { onTool(t.route) }) {
                        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text(t.icon, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                            Column(Modifier.weight(1f)) {
                                Text(t.title, style = MaterialTheme.typography.titleSmall)
                                Text(t.subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Cómo protegemos tus datos", style = MaterialTheme.typography.titleSmall)
                        listOf(
                            "Cifrado autenticado AES-256-GCM o ChaCha20-Poly1305: cualquier cambio en lo cifrado se detecta.",
                            "Tu contraseña se convierte en clave con Argon2id (64 MiB), muy costoso de atacar por fuerza bruta.",
                            "Sin capturas de pantalla en Seguridad; lo que copias se borra del portapapeles a los 45 s.",
                            "Si olvidas la contraseña de algo cifrado, no hay forma de recuperarlo.",
                            "Estas herramientas no han pasado una auditoría independiente. Para fondos importantes usa una wallet hardware.",
                        ).forEach { Text("• $it", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }
    }
}
