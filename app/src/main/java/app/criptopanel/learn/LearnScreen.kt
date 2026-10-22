package app.criptopanel.learn

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.criptopanel.core.ui.BackScaffold
import app.criptopanel.core.ui.GlassCard
import app.criptopanel.core.ui.NegativeColor
import app.criptopanel.core.ui.PositiveColor

private const val PREFS = "criptopanel"
private const val KEY_READ = "lessons_read"

/** Lesson ids read on this device. */
fun readLessons(context: Context): Set<String> =
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet(KEY_READ, emptySet()).orEmpty()

fun markRead(context: Context, id: String) {
    val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    val now = prefs.getStringSet(KEY_READ, emptySet()).orEmpty() + id
    prefs.edit { putStringSet(KEY_READ, now) }
}

fun resetProgress(context: Context) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { remove(KEY_READ) }
}

private const val EDU_NOTE = "Contenido educativo. No es asesoramiento financiero."

@Composable
fun LearnScreen(onLesson: (String) -> Unit, onGlossary: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    var read by remember { mutableStateOf(readLessons(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { read = readLessons(context) }
    val done = LESSONS.count { it.id in read }

    BackScaffold("Aprende", onBack) { padding ->
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Criptomonedas, desde cero", style = MaterialTheme.typography.titleMedium)
                        Text("$done de ${LESSONS.size} lecciones leídas", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        LinearProgressIndicator(progress = { done / LESSONS.size.toFloat() }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
            itemsIndexed(LESSONS, key = { _, l -> l.id }) { i, l ->
                GlassCard(Modifier.fillMaxWidth(), onClick = { onLesson(l.id) }) {
                    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(
                            if (l.id in read) "✓" else "${i + 1}",
                            style = MaterialTheme.typography.titleLarge,
                            color = if (l.id in read) PositiveColor else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.width(28.dp),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(l.title, style = MaterialTheme.typography.titleSmall)
                            Text(l.subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${l.level.label} · ${l.minutes} min", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item {
                GlassCard(Modifier.fillMaxWidth(), onClick = onGlossary) {
                    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("A–Z", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f)) {
                            Text("Glosario", style = MaterialTheme.typography.titleSmall)
                            Text("${GLOSSARY.size} términos, con buscador", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if (done > 0) {
                item {
                    TextButton(onClick = {
                        resetProgress(context)
                        read = emptySet()
                    }) { Text("Reiniciar progreso") }
                }
            }
            item { Text(EDU_NOTE, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
fun LessonScreen(lesson: Lesson, onNext: ((String) -> Unit)?, onBack: () -> Unit) {
    val context = LocalContext.current
    val scroll = rememberScrollState()
    // Reaching the end of the text counts as read.
    LaunchedEffect(lesson.id) {
        snapshotFlow { scroll.maxValue > 0 && scroll.value >= scroll.maxValue - 200 }.collect { atEnd ->
            if (atEnd) markRead(context, lesson.id)
        }
    }
    val next = LESSONS.getOrNull(LESSONS.indexOf(lesson) + 1)

    BackScaffold(lesson.title, onBack) { padding ->
        Column(Modifier.padding(padding).verticalScroll(scroll).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("${lesson.level.label} · ${lesson.minutes} min · ${lesson.subtitle}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            lesson.blocks.forEach { RenderBlock(it) }
            GlassCard(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("En resumen", style = MaterialTheme.typography.titleSmall)
                    lesson.summary.forEach { Text("• $it") }
                }
            }
            Text("Comprueba lo aprendido", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            Quiz(lesson) { markRead(context, lesson.id) }
            if (next != null && onNext != null) {
                Button(onClick = { onNext(next.id) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Siguiente: ${next.title}") }
            }
            Text(EDU_NOTE, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RenderBlock(b: Block) {
    when (b) {
        is Block.Heading -> Text(b.text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 6.dp))
        is Block.Paragraph -> Text(b.text, style = MaterialTheme.typography.bodyLarge)
        is Block.Bullets -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { b.items.forEach { Text("• $it", style = MaterialTheme.typography.bodyLarge) } }
        is Block.Callout -> OutlinedCard(
            border = BorderStroke(1.dp, if (b.warning) NegativeColor else MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text((if (b.warning) "⚠ " else "💡 ") + b.text, modifier = Modifier.padding(12.dp))
        }
        is Block.Table -> GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.horizontalScroll(rememberScrollState()).padding(12.dp)) {
                b.rows.forEachIndexed { r, row ->
                    Row {
                        row.forEachIndexed { c, cell ->
                            Text(
                                cell,
                                fontWeight = if (r == 0 || c == 0) FontWeight.Bold else FontWeight.Normal,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.width(if (c == 0) 130.dp else 150.dp).padding(end = 8.dp, top = 4.dp, bottom = 4.dp),
                            )
                        }
                    }
                    if (r == 0) HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun Quiz(lesson: Lesson, onFinished: () -> Unit) {
    val answers = remember(lesson.id) { mutableStateListOf<Int?>().apply { repeat(lesson.quiz.size) { add(null) } } }
    var reported by rememberSaveable(lesson.id) { mutableStateOf(false) }
    lesson.quiz.forEachIndexed { qi, q ->
        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${qi + 1}. ${q.text}", style = MaterialTheme.typography.titleSmall)
                val picked = answers[qi]
                q.options.forEachIndexed { oi, option ->
                    val color: Color = when {
                        picked == null -> MaterialTheme.colorScheme.onSurface
                        oi == q.answer -> PositiveColor
                        oi == picked -> NegativeColor
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Text(
                        (if (picked != null && oi == q.answer) "✓ " else if (picked == oi) "✗ " else "○ ") + option,
                        color = color,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = picked == null) {
                                answers[qi] = oi
                                if (answers.all { it != null } && !reported) {
                                    reported = true
                                    onFinished()
                                }
                            }
                            .padding(vertical = 6.dp),
                    )
                }
                if (picked != null) {
                    Text(
                        (if (picked == q.answer) "¡Correcto! " else "No exactamente. ") + q.why,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
    if (answers.all { it != null }) {
        val right = lesson.quiz.indices.count { answers[it] == lesson.quiz[it].answer }
        Text("Has acertado $right de ${lesson.quiz.size}", style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
fun GlossaryScreen(onBack: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val terms = remember(query) { searchGlossary(query) }
    BackScaffold("Glosario", onBack) { padding ->
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Buscar término") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (terms.isEmpty()) item { Text("Sin resultados", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp)) }
            items(terms, key = { it.name }) { t ->
                Column(Modifier.padding(vertical = 8.dp)) {
                    Text(t.name, style = MaterialTheme.typography.titleSmall)
                    Text(t.definition, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HorizontalDivider()
            }
        }
    }
}
