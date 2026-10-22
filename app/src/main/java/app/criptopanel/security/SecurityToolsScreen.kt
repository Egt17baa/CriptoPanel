package app.criptopanel.security

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.criptopanel.core.ui.BackScaffold
import app.criptopanel.core.ui.GlassCard
import app.criptopanel.core.ui.NegativeColor
import app.criptopanel.core.ui.PositiveColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

@Composable
private fun AlgorithmPicker(alg: Aead, onChange: (Aead) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        Aead.entries.forEachIndexed { i, a ->
            SegmentedButton(selected = a == alg, onClick = { onChange(a) }, shape = SegmentedButtonDefaults.itemShape(i, Aead.entries.size)) {
                Text(a.label)
            }
        }
    }
}

@Composable
private fun ModePicker(encrypt: Boolean, onChange: (Boolean) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        SegmentedButton(selected = encrypt, onClick = { onChange(true) }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("Cifrar") }
        SegmentedButton(selected = !encrypt, onClick = { onChange(false) }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("Descifrar") }
    }
}

@Composable
private fun Busy(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ToolColumn(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    SecureWindow()
    BackScaffold(title, onBack) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) { content() }
    }
}

@Composable
fun TextCryptoScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var encrypt by remember { mutableStateOf(true) }
    var alg by remember { mutableStateOf(Aead.AES_256_GCM) }
    var input by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var output by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    ToolColumn("Cifrar texto", onBack) {
        ModePicker(encrypt) {
            encrypt = it
            output = null
            error = null
        }
        OutlinedTextField(
            value = input,
            onValueChange = { input = it; output = null; error = null },
            label = { Text(if (encrypt) "Texto a cifrar" else "Texto cifrado (empieza por $TEXT_PREFIX)") },
            minLines = 4,
            modifier = Modifier.fillMaxWidth(),
        )
        PasswordField(password, { password = it; error = null })
        if (encrypt) {
            Text("Algoritmo", style = MaterialTheme.typography.labelMedium)
            AlgorithmPicker(alg) { alg = it }
            Text(
                "Quien lo reciba necesita la misma contraseña y CriptoPanel. Compártela por otro canal, nunca junto al texto.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Button(
            enabled = !busy && input.isNotEmpty() && password.isNotEmpty(),
            onClick = {
                busy = true
                error = null
                val pw = password.toCharArray()
                scope.launch {
                    try {
                        output = withContext(Dispatchers.Default) { if (encrypt) encryptText(input, pw, alg) else decryptText(input, pw) }
                    } catch (e: CryptoFailure) {
                        error = e.message
                    } finally {
                        pw.fill('\u0000')
                        busy = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (encrypt) "Cifrar" else "Descifrar") }
        if (busy) Busy("Derivando la clave con Argon2id…")
        error?.let { Text(it, color = NegativeColor) }
        output?.let { out ->
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (encrypt) "Texto cifrado" else "Texto original", style = MaterialTheme.typography.labelMedium)
                    SelectionContainer { Text(out, fontFamily = if (encrypt) FontFamily.Monospace else FontFamily.Default) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { copySensitive(context, scope, out) }) { Text("Copiar") }
                        if (encrypt) {
                            OutlinedButton(onClick = {
                                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, out), "Compartir"))
                            }) { Text("Compartir") }
                        }
                    }
                }
            }
        }
    }
}

private fun displayName(context: Context, uri: Uri): String =
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    } ?: "archivo"

@Composable
fun FileCryptoScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var encrypt by remember { mutableStateOf(true) }
    var alg by remember { mutableStateOf(Aead.AES_256_GCM) }
    var source by remember { mutableStateOf<Uri?>(null) }
    var sourceName by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var busy by remember { mutableStateOf(false) }
    // Decrypted data waits here (app-private cache) until the user picks where to save it; deleted right after.
    var pending by remember { mutableStateOf<File?>(null) }

    val saveEncrypted = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { dest ->
        val src = source ?: return@rememberLauncherForActivityResult
        if (dest == null) return@rememberLauncherForActivityResult
        busy = true
        val pw = password.toCharArray()
        scope.launch {
            message = try {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(src)!!.use { input ->
                        context.contentResolver.openOutputStream(dest, "wt")!!.use { out -> encryptStream(input.buffered(), out.buffered(), sourceName, pw, alg) }
                    }
                }
                true to "Archivo cifrado y guardado."
            } catch (e: Exception) {
                false to "No se pudo cifrar el archivo."
            } finally {
                pw.fill('\u0000')
                busy = false
            }
        }
    }
    val saveDecrypted = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { dest ->
        val tmp = pending ?: return@rememberLauncherForActivityResult
        scope.launch {
            message = try {
                if (dest != null) {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(dest, "wt")!!.use { out -> tmp.inputStream().use { it.copyTo(out) } }
                    }
                    true to "Archivo descifrado y guardado."
                } else {
                    false to "No se ha guardado nada."
                }
            } catch (e: Exception) {
                false to "No se pudo guardar el archivo."
            } finally {
                tmp.delete()
                pending = null
            }
        }
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            source = uri
            sourceName = displayName(context, uri)
            message = null
        }
    }

    ToolColumn("Cifrar archivos", onBack) {
        ModePicker(encrypt) {
            encrypt = it
            source = null
            message = null
        }
        OutlinedButton(onClick = { pick.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
            Text(if (source == null) (if (encrypt) "Elegir archivo" else "Elegir archivo $ENCRYPTED_EXTENSION") else sourceName)
        }
        PasswordField(password, { password = it; message = null })
        if (encrypt) {
            Text("Algoritmo", style = MaterialTheme.typography.labelMedium)
            AlgorithmPicker(alg) { alg = it }
        }
        Button(
            enabled = !busy && source != null && password.isNotEmpty(),
            onClick = {
                if (encrypt) {
                    saveEncrypted.launch(sourceName + ENCRYPTED_EXTENSION)
                } else {
                    val src = source ?: return@Button
                    busy = true
                    val pw = password.toCharArray()
                    scope.launch {
                        val tmp = File.createTempFile("dec", null, context.cacheDir)
                        try {
                            val name = withContext(Dispatchers.IO) {
                                context.contentResolver.openInputStream(src)!!.use { input ->
                                    tmp.outputStream().buffered().use { out -> decryptStream(input.buffered(), out, pw) }
                                }
                            }
                            pending = tmp
                            saveDecrypted.launch(safeFileName(name))
                        } catch (e: CryptoFailure) {
                            tmp.delete()
                            message = false to (e.message ?: "Contraseña incorrecta o archivo dañado")
                        } catch (e: Exception) {
                            tmp.delete()
                            message = false to "No se pudo leer el archivo."
                        } finally {
                            pw.fill('\u0000')
                            busy = false
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (encrypt) "Cifrar y guardar" else "Descifrar y guardar") }
        if (busy) Busy(if (encrypt) "Cifrando…" else "Descifrando y comprobando…")
        message?.let { (ok, text) -> Text(text, color = if (ok) PositiveColor else NegativeColor) }
        Text(
            "Se cifra por bloques de 64 KiB: sirve para archivos de cualquier tamaño. Al descifrar se comprueba todo el archivo " +
                "antes de guardarlo; si alguien lo ha modificado o la contraseña no es la correcta, no se guarda nada.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StrengthBar(bits: Double) {
    val s = strength(bits)
    val color = when (s) {
        Strength.WEAK -> NegativeColor
        Strength.FAIR -> Color(0xFFFCD34D)
        else -> PositiveColor
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        LinearProgressIndicator(progress = { (bits / 128).toFloat().coerceIn(0.05f, 1f) }, color = color, modifier = Modifier.fillMaxWidth())
        Text("${s.label} · ${bits.roundToInt()} bits de entropía", color = color, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun GeneratorScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val words = remember { context.assets.open("eff_large_wordlist.txt").bufferedReader().readLines().filter { it.isNotBlank() } }
    var phrase by remember { mutableStateOf(false) }
    var p by remember { mutableStateOf(PasswordOptions()) }
    var w by remember { mutableStateOf(PassphraseOptions()) }
    var seed by remember { mutableStateOf(0) }
    val result = remember(phrase, p, w, seed) { if (phrase) generatePassphrase(words, w) else generatePassword(p) }
    val bits = if (phrase) passphraseEntropy(words.size, w) else passwordEntropy(p)

    ToolColumn("Generador", onBack) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(selected = !phrase, onClick = { phrase = false }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("Contraseña") }
            SegmentedButton(selected = phrase, onClick = { phrase = true }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("Frase") }
        }
        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SelectionContainer { Text(result ?: "Elige al menos un tipo de carácter", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.titleMedium) }
                StrengthBar(bits)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { seed++ }) { Text("↻ Otra") }
                    OutlinedButton(enabled = result != null, onClick = { result?.let { copySensitive(context, scope, it) } }) { Text("Copiar") }
                }
            }
        }
        if (!phrase) {
            Text("Longitud: ${p.length}", style = MaterialTheme.typography.labelMedium)
            Slider(value = p.length.toFloat(), onValueChange = { p = p.copy(length = it.roundToInt()) }, valueRange = 8f..64f)
            Option("Minúsculas (a-z)", p.lower) { p = p.copy(lower = it) }
            Option("Mayúsculas (A-Z)", p.upper) { p = p.copy(upper = it) }
            Option("Números (0-9)", p.digits) { p = p.copy(digits = it) }
            Option("Símbolos (!@#…)", p.symbols) { p = p.copy(symbols = it) }
            Option("Evitar caracteres confusos (0 O 1 l I)", p.excludeAmbiguous) { p = p.copy(excludeAmbiguous = it) }
        } else {
            Text("Palabras: ${w.words}", style = MaterialTheme.typography.labelMedium)
            Slider(value = w.words.toFloat(), onValueChange = { w = w.copy(words = it.roundToInt()) }, valueRange = 4f..10f, steps = 5)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                listOf("-" to "guion", " " to "espacio", "." to "punto", "_" to "barra baja").forEach { (sep, label) ->
                    FilterChip(selected = w.separator == sep, onClick = { w = w.copy(separator = sep) }, label = { Text(label) })
                }
            }
            Option("Primera letra en mayúscula", w.capitalize) { w = w.copy(capitalize = it) }
            Option("Añadir un número", w.addNumber) { w = w.copy(addNumber = it) }
            Text(
                "Palabras de la lista de la EFF (7.776 palabras en inglés, CC BY 3.0). Una frase de 6 palabras es fácil de recordar y muy difícil de adivinar.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Option(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun BreachScreen(api: PwnedPasswordsApi, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var busy by remember { mutableStateOf(false) }
    ToolColumn("¿Contraseña filtrada?", onBack) {
        Text(
            "Comprobamos tu contraseña contra más de 900 millones de contraseñas filtradas (Have I Been Pwned). Tu contraseña nunca sale " +
                "del móvil: calculamos su huella SHA-1 aquí y solo enviamos sus 5 primeros caracteres; la comparación final se hace en tu móvil.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PasswordField(password, { password = it; result = null })
        Button(
            enabled = !busy && password.isNotEmpty(),
            onClick = {
                busy = true
                val pw = password.toCharArray()
                scope.launch {
                    result = try {
                        val n = pwnedTimes(api, pw)
                        if (n > 0) {
                            false to "⚠ Aparece $n ${if (n == 1) "vez" else "veces"} en filtraciones. No la uses: cámbiala donde la tengas."
                        } else {
                            true to "✓ No aparece en filtraciones conocidas. Aun así, usa una distinta para cada sitio."
                        }
                    } catch (e: Exception) {
                        false to "No se pudo comprobar. Revisa la conexión."
                    } finally {
                        pw.fill('\u0000')
                        busy = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Comprobar") }
        if (busy) Busy("Comprobando…")
        result?.let { (ok, text) -> Text(text, color = if (ok) PositiveColor else NegativeColor) }
    }
}

@Composable
fun FileHashScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf<String?>(null) }
    var hash by remember { mutableStateOf<String?>(null) }
    var expected by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        name = displayName(context, uri)
        hash = null
        busy = true
        scope.launch {
            hash = try {
                withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)!!.use { sha256Hex(it.buffered()) } }
            } catch (e: Exception) {
                null
            } finally {
                busy = false
            }
        }
    }
    ToolColumn("Huella SHA-256", onBack) {
        Text(
            "Calcula la huella SHA-256 de un archivo y compárala con la que publica la web oficial (por ejemplo, de una app o del firmware " +
                "de tu wallet). Si no coinciden, el archivo se ha alterado o está dañado.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = { pick.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) { Text(name ?: "Elegir archivo") }
        if (busy) Busy("Calculando…")
        hash?.let { h ->
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("SHA-256", style = MaterialTheme.typography.labelMedium)
                    SelectionContainer { Text(h, fontFamily = FontFamily.Monospace) }
                    FilledTonalButton(onClick = { copySensitive(context, scope, h) }) { Text("Copiar") }
                }
            }
            OutlinedTextField(
                value = expected,
                onValueChange = { expected = it },
                label = { Text("Pega la huella publicada") },
                modifier = Modifier.fillMaxWidth(),
            )
            if (expected.isNotBlank()) {
                if (sameHash(h, expected)) {
                    Text("✓ Coincide: el archivo es el original.", color = PositiveColor)
                } else {
                    Text("✗ No coincide: no uses este archivo.", color = NegativeColor)
                }
            }
        }
    }
}
