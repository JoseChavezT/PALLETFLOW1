package com.palletflow.nfc.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.palletflow.nfc.MainViewModel
import com.palletflow.nfc.nfc.ParsedRecord
import com.palletflow.nfc.nfc.TagInfo
import com.palletflow.nfc.ui.theme.MonoStyle

@Composable
fun ReadScreen(vm: MainViewModel) {
    AnimatedContent(
        targetState = vm.lastTag,
        contentKey = { it?.time ?: 0L },
        transitionSpec = {
            (fadeIn(tween(300)) + slideInVertically(tween(360, easing = FastOutSlowInEasing)) { it / 16 })
                .togetherWith(fadeOut(tween(150)))
        },
        label = "read",
    ) { info ->
        if (info == null) ReadIdle() else TagDetails(vm, info)
    }
}

@Composable
private fun ReadIdle() {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        NfcPulse(Icons.Filled.Nfc, size = 220.dp)
        Spacer(Modifier.height(28.dp))
        Text("Listo para leer", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "Acerca una etiqueta NFC a la parte trasera del teléfono para ver su información y contenido.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun TagDetails(vm: MainViewModel, info: TagInfo) {
    val context = LocalContext.current
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "head") { TagHeader(info, Modifier.stagger(0, info.time)) }

        val tasks = info.records.firstOrNull { it.tasks != null }?.tasks
        if (!tasks.isNullOrEmpty()) {
            item(key = "tasks") {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth().stagger(1, info.time),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.PlaylistPlay, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "Esta etiqueta tiene ${tasks.size} tarea(s)",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(onClick = { vm.runTasks(context, tasks) }, enabled = !vm.running) {
                                Icon(Icons.Filled.PlayArrow, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Ejecutar")
                            }
                            FilledTonalButton(onClick = { vm.loadTasks(tasks) }) {
                                Icon(Icons.Filled.PlaylistAdd, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Editar")
                            }
                        }
                    }
                }
            }
        }

        item(key = "info-title") { SectionTitle("Información", Modifier.stagger(2, info.time)) }
        item(key = "info") {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                shape = MaterialTheme.shapes.large,
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth().stagger(3, info.time),
            ) {
                Column(Modifier.padding(vertical = 6.dp)) {
                    info.details.forEach { (k, v) -> InfoRow(k, v) }
                }
            }
        }

        item(key = "rec-title") {
            SectionTitle(
                if (info.message == null) "Sin registros NDEF" else "Registros (${info.records.size})",
                Modifier.stagger(4, info.time),
            )
        }
        itemsIndexed(info.records, key = { i, _ -> "rec$i" }) { i, r ->
            RecordCard(r, context, Modifier.stagger(5 + i, info.time))
        }
    }
}

@Composable
private fun TagHeader(info: TagInfo, modifier: Modifier) {
    val context = LocalContext.current
    val usage = if (info.maxBytes > 0) info.usedBytes.toFloat() / info.maxBytes else 0f
    val animatedUsage by animateFloatAsState(usage, tween(900, delayMillis = 200, easing = FastOutSlowInEasing), label = "usage")
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Filled.CreditCard, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary, 44.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(info.type, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(info.uid, style = MonoStyle, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                }
                IconButton(onClick = { copy(context, info.uid) }) {
                    Icon(Icons.Filled.ContentCopy, "Copiar UID", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            if (info.maxBytes > 0) {
                Spacer(Modifier.height(16.dp))
                LinearProgressIndicator(
                    progress = { animatedUsage },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "${info.usedBytes} de ${info.maxBytes} bytes en uso",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                )
            }
        }
    }
}

@Composable
private fun InfoRow(k: String, v: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(k, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.42f))
        Text(v, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.58f))
    }
}

@Composable
private fun RecordCard(r: ParsedRecord, context: Context, modifier: Modifier) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, tween(260), label = "chev")
    val link = remember(r) { r.value.takeIf { r.format.endsWith("· U") || r.kind == "URI" } }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = MaterialTheme.shapes.large,
        tonalElevation = 1.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.clickable { expanded = !expanded }
                .animateContentSize(tween(280, easing = FastOutSlowInEasing))
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(r.kind, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(2.dp))
                    Text(r.value, style = MaterialTheme.typography.bodyLarge, maxLines = if (expanded) Int.MAX_VALUE else 3)
                }
                Icon(Icons.Filled.ExpandMore, null, Modifier.rotate(rotation), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (expanded) {
                Spacer(Modifier.height(10.dp))
                Text("${r.format} · ${r.size} bytes", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row {
                    TextButton(onClick = { copy(context, r.value) }) {
                        Icon(Icons.Filled.ContentCopy, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Copiar")
                    }
                    if (link != null) {
                        TextButton(onClick = {
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link))) }
                        }) {
                            Icon(Icons.Filled.OpenInNew, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Abrir")
                        }
                    }
                }
            }
        }
    }
}

fun copy(context: Context, text: String) {
    val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cb.setPrimaryClip(ClipData.newPlainText("NFCPalletflow", text))
    Toast.makeText(context, "Copiado", Toast.LENGTH_SHORT).show()
}
