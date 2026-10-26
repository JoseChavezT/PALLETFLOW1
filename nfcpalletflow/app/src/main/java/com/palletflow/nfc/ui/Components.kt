package com.palletflow.nfc.ui

import android.content.Intent
import android.graphics.drawable.Drawable
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import com.palletflow.nfc.data.Configurable
import com.palletflow.nfc.data.FieldKind
import com.palletflow.nfc.data.defaults
import com.palletflow.nfc.data.missingRequired
import kotlinx.coroutines.delay

/** Leve reducción de escala al presionar: da respuesta táctil sin distraer. */
fun Modifier.pressScale(interaction: MutableInteractionSource): Modifier = composed {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.97f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    graphicsLayer { scaleX = scale; scaleY = scale }
}

/** Entrada escalonada: cada elemento aparece desde abajo con un pequeño retraso según su posición. */
fun Modifier.stagger(index: Int, key: Any? = Unit): Modifier = composed {
    val progress = remember(key) { Animatable(0f) }
    LaunchedEffect(key) {
        delay((index.coerceAtMost(12) * 35).toLong())
        progress.animateTo(1f, tween(380, easing = FastOutSlowInEasing))
    }
    graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 24.dp.toPx()
    }
}

/** Ondas concéntricas que se expanden suavemente: indica que se espera una etiqueta. */
@Composable
fun NfcPulse(icon: ImageVector, modifier: Modifier = Modifier, size: Dp = 168.dp, color: Color = MaterialTheme.colorScheme.primary) {
    val t = rememberInfiniteTransition(label = "pulse")
    val phase by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "phase")
    val breathe by t.animateFloat(0.96f, 1.04f, infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breathe")
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSizeCompat(size)) {
            val maxR = this.size.minDimension / 2f
            for (i in 0 until 3) {
                val p = (phase + i / 3f) % 1f
                drawCircle(
                    color = color.copy(alpha = (1f - p) * 0.35f),
                    radius = maxR * (0.35f + 0.65f * p),
                    style = Stroke(width = 2.dp.toPx()),
                )
            }
        }
        Surface(
            modifier = Modifier.size(size * 0.38f).graphicsLayer { scaleX = breathe; scaleY = breathe },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(size * 0.17f))
            }
        }
    }
}

private fun Modifier.matchParentSizeCompat(size: Dp) = this.size(size)

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 4.dp, top = 8.dp, bottom = 6.dp),
    )
}

@Composable
fun IconBadge(icon: ImageVector, container: Color = MaterialTheme.colorScheme.secondaryContainer, content: Color = MaterialTheme.colorScheme.onSecondaryContainer, size: Dp = 40.dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(12.dp)).background(container),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = content, modifier = Modifier.size(size * 0.55f)) }
}

/** Fila de un registro o tarea dentro de una lista editable. */
@Composable
fun EditableRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    index: Int,
    count: Int,
    onClick: () -> Unit,
    onMove: (Int) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    Surface(
        modifier = modifier.fillMaxWidth().pressScale(interaction),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        tonalElevation = 1.dp,
    ) {
        Row(
            Modifier.clickable(interactionSource = interaction, indication = androidx.compose.material3.ripple(), onClick = onClick)
                .padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge(icon)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle.isNotBlank()) {
                    Text(
                        subtitle, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (count > 1) {
                IconButton(onClick = { onMove(index - 1) }, enabled = index > 0) { Icon(Icons.Filled.ArrowUpward, "Subir", Modifier.size(18.dp)) }
                IconButton(onClick = { onMove(index + 1) }, enabled = index < count - 1) { Icon(Icons.Filled.ArrowDownward, "Bajar", Modifier.size(18.dp)) }
            }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Close, "Quitar", Modifier.size(20.dp)) }
        }
    }
}

/** Formulario genérico para registros y tareas. */
@Composable
fun FieldsDialog(
    item: Configurable,
    initial: Map<String, String>?,
    onDismiss: () -> Unit,
    onConfirm: (Map<String, String>) -> Unit,
) {
    val values = remember(item) { mutableStateMapOf<String, String>().apply { putAll(initial ?: item.defaults()) } }
    var error by remember { mutableStateOf<String?>(null) }
    var pickingApp by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.label) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (item.fields.isEmpty()) {
                    Text("Esta acción no necesita configuración.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                item.fields.forEach { f ->
                    when (f.kind) {
                        FieldKind.CHOICE -> Column {
                            Text(f.label, style = MaterialTheme.typography.labelLarge)
                            f.options.forEach { (key, label) ->
                                Row(
                                    Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small)
                                        .selectable(selected = values[f.key] == key, onClick = { values[f.key] = key })
                                        .padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    RadioButton(selected = values[f.key] == key, onClick = { values[f.key] = key })
                                    Text(label)
                                }
                            }
                        }
                        FieldKind.SLIDER -> Column {
                            val v = values[f.key]?.toFloatOrNull() ?: 50f
                            Text("${f.label}: ${v.toInt()} %", style = MaterialTheme.typography.labelLarge)
                            Slider(value = v, onValueChange = { values[f.key] = it.toInt().toString() }, valueRange = 0f..100f)
                        }
                        FieldKind.APP -> OutlinedButton(onClick = { pickingApp = f.key }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.Apps, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(values[f.key]?.takeIf { it.isNotBlank() } ?: "Elegir aplicación", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        else -> {
                            var visible by remember { mutableStateOf(false) }
                            OutlinedTextField(
                                value = values[f.key].orEmpty(),
                                onValueChange = { values[f.key] = it; error = null },
                                label = { Text(if (f.required) f.label else "${f.label} (opcional)") },
                                placeholder = if (f.hint.isNotEmpty()) ({ Text(f.hint) }) else null,
                                singleLine = f.kind != FieldKind.MULTILINE,
                                minLines = if (f.kind == FieldKind.MULTILINE) 2 else 1,
                                visualTransformation = if (f.kind == FieldKind.PASSWORD && !visible) PasswordVisualTransformation() else VisualTransformation.None,
                                trailingIcon = if (f.kind == FieldKind.PASSWORD) ({
                                    IconButton(onClick = { visible = !visible }) {
                                        Icon(if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, "Mostrar")
                                    }
                                }) else null,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = when (f.kind) {
                                        FieldKind.NUMBER -> KeyboardType.Number
                                        FieldKind.DECIMAL -> KeyboardType.Decimal
                                        FieldKind.PHONE -> KeyboardType.Phone
                                        FieldKind.EMAIL -> KeyboardType.Email
                                        FieldKind.URL -> KeyboardType.Uri
                                        FieldKind.PASSWORD -> KeyboardType.Password
                                        FieldKind.TIME -> KeyboardType.Number
                                        else -> KeyboardType.Text
                                    }
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val missing = item.missingRequired(values)
                if (missing != null) error = "Completa «${missing.label}»" else onConfirm(values.toMap())
            }) { Text(if (initial == null) "Agregar" else "Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )

    pickingApp?.let { key ->
        AppPickerDialog(onDismiss = { pickingApp = null }) { pkg ->
            values[key] = pkg
            pickingApp = null
        }
    }
}

private data class AppEntry(val label: String, val pkg: String, val icon: Drawable?)

@Composable
fun AppPickerDialog(onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val context = LocalContext.current
    val apps = remember {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        pm.queryIntentActivities(intent, 0)
            .map { AppEntry(it.loadLabel(pm).toString(), it.activityInfo.packageName, runCatching { it.loadIcon(pm) }.getOrNull()) }
            .distinctBy { it.pkg }
            .sortedBy { it.label.lowercase() }
    }
    var query by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Elegir aplicación") },
        text = {
            Column {
                OutlinedTextField(query, { query = it }, label = { Text("Buscar") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.size(8.dp))
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    items(apps.filter { it.label.contains(query, true) || it.pkg.contains(query, true) }, key = { it.pkg }) { app ->
                        Row(
                            Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable { onPick(app.pkg) }.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val bmp = remember(app.pkg) { app.icon?.let { runCatching { it.toBitmap(96, 96).asImageBitmap() }.getOrNull() } }
                            if (bmp != null) Image(bmp, null, Modifier.size(32.dp)) else IconBadge(Icons.Filled.Apps, size = 32.dp)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(app.label, style = MaterialTheme.typography.bodyLarge)
                                Text(app.pkg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
}

@Composable
fun EmptyState(icon: ImageVector, title: String, text: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        IconBadge(icon, MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.onSurfaceVariant, 56.dp)
        Spacer(Modifier.size(14.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.size(4.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
