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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.TextButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.AssistChip
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.clickable

/** Every algorithm with what it is; AES-256 first as the sensible default. */
@Composable
private fun AlgorithmPicker(alg: Aead, onChange: (Aead) -> Unit) {
    Column {
        Aead.entries.forEach { a ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable { onChange(a) }.padding(vertical = 4.dp),
            ) {
                RadioButton(selected = a == alg, onClick = { onChange(a) })
                Column {
                    Text(a.label, style = MaterialTheme.typography.titleSmall)
                    Text(a.detail.substringAfter(": "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** Password (stretched with Argon2id) or a random AES-256 key the user keeps elsewhere and never the fingerprint. */
@Composable
private fun SecretInput(
    useKey: Boolean,
    onUseKey: (Boolean) -> Unit,
    password: String,
    onPassword: (String) -> Unit,
    keyHex: String,
    onKeyHex: (String) -> Unit,
    allowGenerate: Boolean,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        SegmentedButton(selected = !useKey, onClick = { onUseKey(false) }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("Contraseña") }
        SegmentedButton(selected = useKey, onClick = { onUseKey(true) }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("Clave AES-256") }
    }
    if (!useKey) {
        PasswordField(password, onPassword)
        Text(
            "Se convierte en clave con Argon2id. Usa una larga: una frase de 6 palabras del Generador es ideal.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        OutlinedTextField(
            value = keyHex,
            onValueChange = onKeyHex,
            label = { Text("Clave (64 caracteres hexadecimales)") },
            minLines = 2,
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            isError = keyHex.isNotBlank() && parseRawKey(keyHex) == null,
            supportingText = { if (keyHex.isNotBlank() && parseRawKey(keyHex) == null) Text("No es una clave de 256 bits válida") },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (allowGenerate) FilledTonalButton(onClick = { onKeyHex(newRawKeyHex()) }) { Text("🎲 Generar clave") }
            if (parseRawKey(keyHex) != null) OutlinedButton(onClick = { copySensitive(context, scope, keyHex) }) { Text("Copiar clave") }
        }
        Text(
            "Una clave aleatoria de 256 bits: imposible de adivinar. Apúntala en papel y guárdala SEPARADA del texto cifrado: " +
                "sin ella nadie, ni tú, puede descifrarlo.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun secretOf(useKey: Boolean, password: String, keyHex: String): Secret? =
    if (useKey) parseRawKey(keyHex)?.let { Secret.RawKey(it) } else password.takeIf { it.isNotEmpty() }?.let { Secret.Password(it.toCharArray()) }

private fun Secret.wipe() = when (this) {
    is Secret.Password -> chars.fill('\u0000')
    is Secret.RawKey -> bytes.fill(0)
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
    val bip39 = remember { Bip39(context.assets.open("bip39_english.txt").bufferedReader().readLines().filter { it.isNotBlank() }) }
    var encrypt by remember { mutableStateOf(true) }
    var seedMode by remember { mutableStateOf(true) }
    var alg by remember { mutableStateOf(Aead.AES_256_GCM) }
    var input by remember { mutableStateOf("") }
    var useKey by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var keyHex by remember { mutableStateOf("") }
    var output by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    fun reset() {
        output = null
        error = null
    }

    ToolColumn("Cifrar texto", onBack) {
        ModePicker(encrypt) {
            encrypt = it
            input = ""
            reset()
        }
        if (encrypt) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = seedMode, onClick = { seedMode = true; reset() }, label = { Text("🌱 Frase semilla") })
                FilterChip(selected = !seedMode, onClick = { seedMode = false; reset() }, label = { Text("Texto libre") })
            }
            OutlinedTextField(
                value = input,
                onValueChange = { input = if (seedMode) it.lowercase() else it; reset() },
                label = { Text(if (seedMode) "Las 12, 18 o 24 palabras de tu wallet" else "Texto a cifrar") },
                minLines = 4,
                keyboardOptions = if (seedMode) KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Password) else KeyboardOptions.Default,
                modifier = Modifier.fillMaxWidth(),
            )
            if (seedMode) SeedHelper(bip39, input) { input = it }
        } else {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it; reset() },
                label = { Text("Texto cifrado (empieza por $TEXT_PREFIX)") },
                minLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )
            inspectText(input)?.let { (a, raw) ->
                LaunchedEffect(a, raw) { useKey = raw }
                Text("Cifrado con ${a.label} y ${if (raw) "una clave AES-256" else "una contraseña"}.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        SecretInput(useKey, { useKey = it; reset() }, password, { password = it; reset() }, keyHex, { keyHex = it; reset() }, allowGenerate = encrypt)
        if (encrypt) {
            Text("Algoritmo", style = MaterialTheme.typography.titleSmall)
            AlgorithmPicker(alg) { alg = it }
        }
        Button(
            enabled = !busy && input.isNotBlank() && secretOf(useKey, password, keyHex) != null,
            onClick = {
                val secret = secretOf(useKey, password, keyHex) ?: return@Button
                busy = true
                reset()
                val plain = if (seedMode) bip39.normalize(input).joinToString(" ") else input
                scope.launch {
                    try {
                        output = withContext(Dispatchers.Default) { if (encrypt) encryptText(plain, secret, alg) else decryptText(input, secret) }
                    } catch (e: CryptoFailure) {
                        error = if (useKey && e.message == CryptoFailure().message) "Clave incorrecta o texto dañado" else e.message
                    } finally {
                        secret.wipe()
                        busy = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (encrypt) "Cifrar" else "Descifrar") }
        if (busy) Busy(if (useKey) "Procesando…" else "Derivando la clave con Argon2id…")
        error?.let { Text(it, color = NegativeColor) }
        output?.let { out ->
            val seed = !encrypt && bip39.check(out).let { it.validCount && it.unknown.isEmpty() }
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        when {
                            encrypt -> "Texto cifrado"
                            seed -> "Frase semilla (${bip39.normalize(out).size} palabras)"
                            else -> "Texto original"
                        },
                        style = MaterialTheme.typography.labelMedium,
                    )
                    if (seed) SeedGrid(bip39.normalize(out)) else SelectionContainer { Text(out, fontFamily = if (encrypt) FontFamily.Monospace else FontFamily.Default) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { copySensitive(context, scope, out) }) { Text("Copiar") }
                        if (encrypt) {
                            OutlinedButton(onClick = {
                                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, out), "Compartir"))
                            }) { Text("Compartir") }
                        }
                    }
                    if (encrypt) {
                        Text(
                            "Guarda este texto donde quieras (nube, correo, papel). Sin tu ${if (useKey) "clave" else "contraseña"} es inútil. " +
                                "Antes de borrar tu copia original, prueba a descifrarlo.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** Word count, BIP-39 checksum, misspelled words with a one-tap fix, and completions for the word being typed. */
@Composable
private fun SeedHelper(bip39: Bip39, input: String, onChange: (String) -> Unit) {
    if (input.isBlank()) return
    val check = bip39.check(input)
    val words = bip39.normalize(input)
    val typing = if (input.endsWith(" ")) "" else words.lastOrNull().orEmpty()
    val completions = if (typing.isNotEmpty() && typing !in bip39.words) bip39.complete(typing) else emptyList()
    if (completions.isNotEmpty()) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            completions.forEach { w ->
                AssistChip(onClick = { onChange((words.dropLast(1) + w).joinToString(" ") + " ") }, label = { Text(w) })
            }
        }
    }
    check.unknown.filter { it.word != typing }.forEach { u ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Palabra ${u.position}: «${u.word}» no es BIP-39.", color = NegativeColor, modifier = Modifier.weight(1f))
            u.suggestion?.let { s ->
                TextButton(onClick = { onChange(words.mapIndexed { i, w -> if (i == u.position - 1) s else w }.joinToString(" ")) }) { Text("¿$s?") }
            }
        }
    }
    val (text, color) = when {
        check.checksumOk == true -> "✓ ${check.count} palabras · checksum BIP-39 correcto" to PositiveColor
        check.checksumOk == false -> "⚠ ${check.count} palabras pero el checksum no cuadra: revisa el orden o alguna palabra" to NegativeColor
        !check.validCount -> "${check.count} palabras (lo habitual son 12, 15, 18, 21 o 24)" to MaterialTheme.colorScheme.onSurfaceVariant
        else -> "${check.count} palabras" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(text, color = color, style = MaterialTheme.typography.bodySmall)
}

/** The recovered words numbered, as wallets show them. */
@Composable
private fun SeedGrid(words: List<String>) {
    val columns = 3
    val rows = (words.size + columns - 1) / columns
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (r in 0 until rows) {
            Row {
                for (c in 0 until columns) {
                    val i = c * rows + r
                    Text(
                        words.getOrNull(i)?.let { "${(i + 1).toString().padStart(2)}. $it" }.orEmpty(),
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.weight(1f),
                    )
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
    var useKey by remember { mutableStateOf(false) }
    var keyHex by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var busy by remember { mutableStateOf(false) }
    // Decrypted data waits here (app-private cache) until the user picks where to save it; deleted right after.
    var pending by remember { mutableStateOf<File?>(null) }

    val saveEncrypted = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { dest ->
        val src = source ?: return@rememberLauncherForActivityResult
        if (dest == null) return@rememberLauncherForActivityResult
        val secret = secretOf(useKey, password, keyHex) ?: return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            message = try {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(src)!!.use { input ->
                        context.contentResolver.openOutputStream(dest, "wt")!!.use { out -> encryptStream(input.buffered(), out.buffered(), sourceName, secret, alg) }
                    }
                }
                true to "Archivo cifrado y guardado."
            } catch (e: Exception) {
                false to "No se pudo cifrar el archivo."
            } finally {
                secret.wipe()
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
        SecretInput(useKey, { useKey = it; message = null }, password, { password = it; message = null }, keyHex, { keyHex = it; message = null }, allowGenerate = encrypt)
        if (encrypt) {
            Text("Algoritmo", style = MaterialTheme.typography.titleSmall)
            AlgorithmPicker(alg) { alg = it }
        }
        Button(
            enabled = !busy && source != null && secretOf(useKey, password, keyHex) != null,
            onClick = {
                if (encrypt) {
                    saveEncrypted.launch(sourceName + ENCRYPTED_EXTENSION)
                } else {
                    val src = source ?: return@Button
                    val secret = secretOf(useKey, password, keyHex) ?: return@Button
                    busy = true
                    scope.launch {
                        val tmp = File.createTempFile("dec", null, context.cacheDir)
                        try {
                            val name = withContext(Dispatchers.IO) {
                                context.contentResolver.openInputStream(src)!!.use { input ->
                                    tmp.outputStream().buffered().use { out -> decryptStream(input.buffered(), out, secret) }
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
                            secret.wipe()
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
