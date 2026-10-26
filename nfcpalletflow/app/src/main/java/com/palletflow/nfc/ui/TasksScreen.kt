package com.palletflow.nfc.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.palletflow.nfc.MainViewModel
import com.palletflow.nfc.tasks.TaskCategory
import com.palletflow.nfc.tasks.TaskItem
import com.palletflow.nfc.tasks.TaskType

@Composable
fun TasksScreen(vm: MainViewModel) {
    val context = LocalContext.current
    var picking by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Pair<TaskType, Int?>?>(null) }
    var savingProfile by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "summary") {
                SizeSummary("${vm.tasks.size} tarea(s) en orden de ejecución", if (vm.tasks.isEmpty()) 0 else vm.tasksSize())
            }
            if (vm.tasks.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        Icons.Filled.PlaylistPlay, "Sin tareas",
                        "Crea una secuencia: linterna, volumen, Wi‑Fi, abrir apps… y grábala en una etiqueta.",
                        Modifier.animateItem(),
                    )
                }
            } else {
                item(key = "actions") {
                    Row(Modifier.animateItem(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilledTonalButton(onClick = { vm.runTasks(context, vm.tasks.toList()) }, enabled = !vm.running) {
                            Icon(Icons.Filled.PlayArrow, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Probar ahora")
                        }
                        FilledTonalButton(onClick = { savingProfile = true }) {
                            Icon(Icons.Filled.Save, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Guardar perfil")
                        }
                    }
                }
            }
            item(key = "progress") {
                AnimatedVisibility(
                    visible = vm.running || vm.runResults.isNotEmpty(),
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) { RunResults(vm) }
            }
            itemsIndexed(vm.tasks, key = { _, t -> t.id }) { i, t ->
                EditableRow(
                    icon = t.type.icon,
                    title = "${i + 1}. ${t.type.label}",
                    subtitle = t.detail,
                    index = i,
                    count = vm.tasks.size,
                    onClick = { editing = t.type to i },
                    onMove = { to -> vm.move(vm.tasks, i, to) },
                    onDelete = { vm.tasks.removeAt(i); vm.saveDraft() },
                    modifier = Modifier.animateItem(),
                )
            }
            if (vm.profiles.isNotEmpty()) {
                item(key = "profiles-title") { SectionTitle("Perfiles guardados", Modifier.animateItem()) }
                items(vm.profiles, key = { "p_" + it.name }) { p ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        shape = MaterialTheme.shapes.medium,
                        tonalElevation = 1.dp,
                        modifier = Modifier.fillMaxWidth().animateItem(),
                    ) {
                        Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(p.name, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    p.tasks.joinToString(" · ") { it.type.label },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                                )
                            }
                            IconButton(onClick = { vm.runTasks(context, p.tasks) }, enabled = !vm.running) { Icon(Icons.Filled.PlayArrow, "Ejecutar") }
                            IconButton(onClick = { vm.loadTasks(p.tasks.map { it.copy(id = com.palletflow.nfc.data.nextId()) }) }) { Icon(Icons.Filled.FolderOpen, "Cargar") }
                            IconButton(onClick = { vm.deleteProfile(p) }) { Icon(Icons.Filled.Delete, "Eliminar") }
                        }
                    }
                }
            }
        }
        BottomActions(
            secondary = "Agregar tarea", onSecondary = { picking = true },
            primary = "Grabar", primaryEnabled = vm.tasks.isNotEmpty(), onPrimary = { vm.writeTasks() },
        )
    }

    if (picking) {
        TypePickerSheet(
            groups = TaskCategory.entries.map { c -> c.label to TaskType.entries.filter { it.category == c } },
            label = { it.label }, icon = { it.icon },
            onDismiss = { picking = false },
            onPick = { picking = false; editing = it to null },
        )
    }
    editing?.let { (type, index) ->
        FieldsDialog(
            item = type,
            initial = index?.let { vm.tasks[it].values },
            onDismiss = { editing = null },
            onConfirm = { values ->
                if (index == null) vm.tasks.add(TaskItem(type, values))
                else vm.tasks[index] = vm.tasks[index].copy(values = values)
                vm.saveDraft()
                editing = null
            },
        )
    }
    if (savingProfile) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { savingProfile = false },
            title = { Text("Guardar perfil") },
            text = {
                OutlinedTextField(name, { name = it }, label = { Text("Nombre") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            },
            confirmButton = { TextButton(onClick = { vm.saveProfile(name); savingProfile = false }) { Text("Guardar") } },
            dismissButton = { TextButton(onClick = { savingProfile = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun RunResults(vm: MainViewModel) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (vm.running) "Ejecutando…" else "Resultado de la ejecución",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                if (!vm.running) TextButton(onClick = { vm.runResults.clear() }) { Text("Ocultar") }
            }
            if (vm.running) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 6.dp))
            vm.runResults.forEach { r ->
                Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (r.ok) Icons.Filled.CheckCircle else Icons.Filled.Error, null,
                        tint = if (r.ok) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("${r.task.type.label} — ${r.message}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
