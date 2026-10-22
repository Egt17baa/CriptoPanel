package app.criptopanel.security

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import app.criptopanel.portfolio.readAtMost
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.criptopanel.core.ui.GlassCard
import app.criptopanel.core.ui.MenuColor
import app.criptopanel.core.ui.NegativeColor
import app.criptopanel.security.android.BiometricVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private tailrec fun Context.fragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.fragmentActivity()
    else -> null
}

/** Any touch on the vault counts as use for the 5-minute idle lock. */
private fun Modifier.touchesVault(store: VaultStore) = pointerInput(store) {
    awaitPointerEventScope {
        while (true) {
            awaitPointerEvent(PointerEventPass.Initial)
            store.touch()
        }
    }
}

private const val VAULT_IMPORT_MAX_BYTES = 20 * 1024 * 1024

@Composable
fun VaultIdleLock(store: VaultStore) {
    LaunchedEffect(store) {
        while (true) {
            delay(15_000)
            store.lockIfIdle()
        }
    }
}

/** The vault (or only its 2FA codes when [totpOnly]). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultScreen(store: VaultStore, totpOnly: Boolean, onOpen: (String) -> Unit, onNew: (EntryType) -> Unit, onBack: () -> Unit) {
    SecureWindow()
    VaultIdleLock(store)
    val state by store.state.collectAsStateWithLifecycle()
    LaunchedEffect(store) { store.load() }
    val snackbar = remember { SnackbarHostState() }
    var menu by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val text = store.exportText() ?: return@launch
            val ok = runCatching {
                withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray()) } }
            }.isSuccess
            snackbar.showSnackbar(if (ok) "Copia cifrada guardada. Necesitarás tu contraseña maestra para abrirla." else "No se pudo guardar la copia")
        }
    }
    var importText by remember { mutableStateOf<String?>(null) }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            importText = runCatching {
                withContext(Dispatchers.IO) {
                    // A real export is a few hundred KB; a huge file must not exhaust memory.
                    context.contentResolver.openInputStream(uri)!!.use { it.readAtMost(VAULT_IMPORT_MAX_BYTES + 1) }
                        .also { require(it.size <= VAULT_IMPORT_MAX_BYTES) }.decodeToString()
                }
            }.getOrNull()
            if (importText == null) snackbar.showSnackbar("No se pudo leer el archivo") else dialog = "import"
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(if (totpOnly) "Autenticador 2FA" else "Bóveda") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás") } },
                actions = {
                    if (state is VaultState.Unlocked) {
                        TextButton(onClick = { store.lock() }) { Text("Bloquear") }
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Más opciones") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = MenuColor) {
                            val bio = BiometricVault.enabled(context)
                            if (BiometricVault.available(context)) {
                                DropdownMenuItem(text = { Text(if (bio) "Desactivar huella" else "Activar huella") }, onClick = {
                                    menu = false
                                    if (bio) {
                                        BiometricVault.disable(context)
                                        scope.launch { snackbar.showSnackbar("Huella desactivada") }
                                    } else {
                                        val activity = context.fragmentActivity() ?: return@DropdownMenuItem
                                        val key = store.dataKeyCopy() ?: return@DropdownMenuItem
                                        BiometricVault.enable(activity, key) { err -> scope.launch { snackbar.showSnackbar(err ?: "Huella activada") } }
                                    }
                                })
                            }
                            DropdownMenuItem(text = { Text("Exportar copia cifrada") }, onClick = {
                                menu = false
                                VaultLock.keepOpenOnce = true
                                exporter.launch("criptopanel-boveda.json")
                            })
                            DropdownMenuItem(text = { Text("Importar copia") }, onClick = {
                                menu = false
                                VaultLock.keepOpenOnce = true
                                importer.launch(arrayOf("application/json", "application/octet-stream", "text/*"))
                            })
                            DropdownMenuItem(text = { Text("Cambiar contraseña maestra") }, onClick = {
                                menu = false
                                dialog = "master"
                            })
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (state is VaultState.Unlocked) {
                ExtendedFloatingActionButton(
                    onClick = { onNew(if (totpOnly) EntryType.TOTP else EntryType.LOGIN) },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text(if (totpOnly) "Código" else "Añadir") },
                )
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize().touchesVault(store)) {
            when (val s = state) {
                VaultState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                VaultState.NoVault -> CreateVault(store)
                VaultState.Locked -> UnlockVault(store)
                is VaultState.Unlocked -> EntryList(s.entries, totpOnly, onOpen)
            }
        }
    }

    when (dialog) {
        "master" -> PasswordDialog(
            title = "Nueva contraseña maestra",
            confirmLabel = "Cambiar",
            askTwice = true,
            onDismiss = { dialog = null },
        ) { pw ->
            scope.launch {
                val changed = store.changeMaster(pw)
                pw.fill('\u0000')
                if (changed) {
                    BiometricVault.disable(context) // It wrapped the old data key.
                    snackbar.showSnackbar("Contraseña maestra cambiada. Vuelve a activar la huella si la usabas.")
                } else {
                    snackbar.showSnackbar("La bóveda se bloqueó: no se ha cambiado nada")
                }
            }
            dialog = null
        }
        "import" -> PasswordDialog(
            title = "Contraseña maestra de la copia",
            confirmLabel = "Importar",
            warning = "Se reemplazará la bóveda actual de este móvil por la de la copia.",
            onDismiss = { dialog = null },
        ) { pw ->
            val text = importText ?: return@PasswordDialog
            scope.launch {
                val msg = try {
                    store.importReplace(text, pw)
                    BiometricVault.disable(context)
                    "Copia importada"
                } catch (e: CryptoFailure) {
                    e.message ?: "Contraseña incorrecta"
                } finally {
                    pw.fill('\u0000')
                }
                snackbar.showSnackbar(msg)
            }
            dialog = null
        }
    }
}

@Composable
private fun PasswordDialog(
    title: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    askTwice: Boolean = false,
    warning: String? = null,
    onConfirm: (CharArray) -> Unit,
) {
    var a by remember { mutableStateOf("") }
    var b by remember { mutableStateOf("") }
    val ok = a.length >= MIN_MASTER && (!askTwice || a == b)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MenuColor,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                warning?.let { Text(it, color = NegativeColor) }
                PasswordField(a, { a = it })
                if (askTwice) PasswordField(b, { b = it }, label = "Repítela")
                if (askTwice && b.isNotEmpty() && a != b) Text("No coinciden", color = NegativeColor)
            }
        },
        confirmButton = { TextButton(enabled = ok, onClick = { onConfirm(a.toCharArray()) }) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

const val MIN_MASTER = 10

@Composable
private fun CreateVault(store: VaultStore) {
    val scope = rememberCoroutineScope()
    var a by remember { mutableStateOf("") }
    var b by remember { mutableStateOf("") }
    var understood by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Crea tu bóveda", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Guarda contraseñas, frases semilla de tus wallets, notas y códigos 2FA, cifrados con AES-256-GCM y una clave derivada de tu " +
                "contraseña maestra con Argon2id. Solo se guarda en este móvil (no va a la copia de Google); puedes exportar una copia cifrada.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PasswordField(a, { a = it }, label = "Contraseña maestra (mínimo $MIN_MASTER caracteres)")
        PasswordField(b, { b = it }, label = "Repítela")
        if (b.isNotEmpty() && a != b) Text("No coinciden", color = NegativeColor)
        Text(
            "Consejo: usa una frase de 5 o 6 palabras que solo tú conozcas (el Generador te puede dar una).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { understood = !understood }) {
            Checkbox(checked = understood, onCheckedChange = { understood = it })
            Text("Entiendo que si olvido la contraseña maestra nadie puede recuperar lo que guarde.")
        }
        Button(
            enabled = !busy && a.length >= MIN_MASTER && a == b && understood,
            onClick = {
                busy = true
                val pw = a.toCharArray()
                scope.launch {
                    store.create(pw)
                    pw.fill('\u0000')
                    busy = false
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Crear bóveda") }
        if (busy) CircularProgressIndicator(Modifier.size(24.dp))
    }
}

@Composable
private fun UnlockVault(store: VaultStore) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pw by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var confirmDestroy by remember { mutableStateOf(false) }
    val bio = BiometricVault.enabled(context) && store.biometricAllowed
    fun withFingerprint() {
        val activity = context.fragmentActivity() ?: return
        BiometricVault.unlock(activity, onKey = { key ->
            scope.launch {
                try {
                    store.unlockWithKey(key)
                } catch (e: Exception) {
                    error = e.message
                }
            }
        }, onError = { error = it })
    }
    LaunchedEffect(Unit) { if (bio) withFingerprint() }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("🔒 Bóveda bloqueada", style = MaterialTheme.typography.headlineSmall)
        PasswordField(pw, { pw = it; error = null }, label = "Contraseña maestra")
        Button(
            enabled = !busy && pw.isNotEmpty(),
            onClick = {
                busy = true
                val chars = pw.toCharArray()
                scope.launch {
                    try {
                        store.unlock(chars)
                        pw = ""
                    } catch (e: CryptoFailure) {
                        error = "Contraseña incorrecta"
                    } finally {
                        chars.fill('\u0000')
                        busy = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Desbloquear") }
        if (busy) CircularProgressIndicator(Modifier.size(24.dp))
        if (bio) OutlinedButton(onClick = ::withFingerprint, modifier = Modifier.fillMaxWidth()) { Text("Usar huella") }
        if (BiometricVault.enabled(context) && !store.biometricAllowed) {
            Text("Por seguridad, cada 7 días hay que escribir la contraseña maestra.", style = MaterialTheme.typography.bodySmall)
        }
        error?.let { Text(it, color = NegativeColor) }
        TextButton(onClick = { confirmDestroy = true }) { Text("He olvidado la contraseña maestra") }
    }
    if (confirmDestroy) {
        AlertDialog(
            onDismissRequest = { confirmDestroy = false },
            containerColor = MenuColor,
            title = { Text("Sin la contraseña no hay recuperación") },
            text = { Text("Lo guardado no se puede descifrar. Puedes borrar la bóveda y crear una nueva vacía. Esto no se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDestroy = false
                    BiometricVault.disable(context)
                    scope.launch { store.destroy() }
                }) { Text("Borrar bóveda", color = NegativeColor) }
            },
            dismissButton = { TextButton(onClick = { confirmDestroy = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun EntryList(entries: List<VaultEntry>, totpOnly: Boolean, onOpen: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    var type by remember { mutableStateOf<EntryType?>(if (totpOnly) EntryType.TOTP else null) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val shown = entries.filter { e ->
        (type == null || e.type == type) &&
            (query.isBlank() || listOf(e.title, e.username, e.url, e.totp?.issuer.orEmpty()).any { it.contains(query, ignoreCase = true) })
    }
    LazyColumn(contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item {
            OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("Buscar") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        if (!totpOnly) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    FilterChip(selected = type == null, onClick = { type = null }, label = { Text("Todo (${entries.size})") })
                    EntryType.entries.forEach { t ->
                        FilterChip(selected = type == t, onClick = { type = t }, label = { Text("${t.label} (${entries.count { it.type == t }})") })
                    }
                }
            }
        }
        if (shown.isEmpty()) {
            item {
                Text(
                    if (entries.isEmpty()) "Tu bóveda está vacía. Pulsa «Añadir»." else "Sin resultados",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
            }
        }
        items(shown, key = { it.id }) { e ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().clickable { onOpen(e.id) }.padding(vertical = 10.dp),
            ) {
                Text(typeIcon(e.type), style = MaterialTheme.typography.titleLarge)
                Column(Modifier.weight(1f)) {
                    Text(e.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val sub = when (e.type) {
                        EntryType.LOGIN -> e.username.ifBlank { e.url }
                        EntryType.SEED -> "${seedWordCount(e.seedWords).first} palabras"
                        EntryType.NOTE -> "Nota"
                        EntryType.TOTP -> listOf(e.totp?.issuer, e.totp?.account).filter { !it.isNullOrBlank() }.joinToString(" · ")
                    }
                    if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                e.totp?.let { t ->
                    totpCode(t, now)?.let { (code, left) ->
                        Column(horizontalAlignment = Alignment.End) {
                            Text(code.chunked(3).joinToString(" "), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.titleMedium)
                            Text("${left}s", style = MaterialTheme.typography.labelSmall, color = if (left <= 5) NegativeColor else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            HorizontalDivider()
        }
    }
}

fun typeIcon(t: EntryType) = when (t) {
    EntryType.LOGIN -> "🔑"
    EntryType.SEED -> "🌱"
    EntryType.NOTE -> "📝"
    EntryType.TOTP -> "⏱"
}
