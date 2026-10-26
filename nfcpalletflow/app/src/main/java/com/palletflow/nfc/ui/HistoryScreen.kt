package com.palletflow.nfc.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.palletflow.nfc.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(vm: MainViewModel) {
    var confirm by remember { mutableStateOf(false) }
    val fmt = remember { SimpleDateFormat("dd MMM · HH:mm:ss", Locale("es")) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "head") {
            Row(Modifier.fillMaxWidth().animateItem(), verticalAlignment = Alignment.CenterVertically) {
                Text("${vm.history.size} evento(s)", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (vm.history.isNotEmpty()) TextButton(onClick = { confirm = true }) { Text("Vaciar") }
            }
        }
        if (vm.history.isEmpty()) {
            item(key = "empty") {
                EmptyState(Icons.Filled.History, "Sin actividad", "Aquí verás las lecturas, escrituras y tareas ejecutadas.", Modifier.animateItem())
            }
        }
        itemsIndexed(vm.history, key = { _, h -> "${h.time}_${h.uid}_${h.action}" }) { i, h ->
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                shape = MaterialTheme.shapes.medium,
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth().animateItem().stagger(i),
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(
                        when (h.action) {
                            "Escritura" -> Icons.Filled.Edit
                            "Copia" -> Icons.Filled.ContentCopy
                            "Borrado", "Formato" -> Icons.Filled.DeleteSweep
                            "Bloqueo" -> Icons.Filled.Lock
                            "Tareas ejecutadas" -> Icons.Filled.PlaylistPlay
                            else -> Icons.Filled.Nfc
                        }
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(h.action, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            Text(fmt.format(Date(h.time)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(
                            listOf(h.tagType, h.uid).filter { it.isNotBlank() }.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary,
                        )
                        if (h.summary.isNotBlank()) {
                            Text(h.summary, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            icon = { androidx.compose.material3.Icon(Icons.Filled.DeleteForever, null) },
            title = { Text("¿Vaciar el historial?") },
            confirmButton = { TextButton(onClick = { vm.clearHistory(); confirm = false }) { Text("Vaciar") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancelar") } },
        )
    }
}
