package com.palletflow.nfc.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.palletflow.nfc.MainViewModel
import com.palletflow.nfc.nfc.RecordItem
import com.palletflow.nfc.nfc.RecordType

@Composable
fun WriteScreen(vm: MainViewModel) {
    var picking by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Pair<RecordType, Int?>?>(null) }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "summary") { SizeSummary("${vm.records.size} registro(s)", vm.messageSize()) }
            if (vm.records.isEmpty()) {
                item(key = "empty") {
                    EmptyState(Icons.Filled.Edit, "Nada que escribir todavía", "Agrega texto, enlaces, contactos, Wi‑Fi y más.", Modifier.animateItem())
                }
            }
            itemsIndexed(vm.records, key = { _, r -> r.id }) { i, r ->
                EditableRow(
                    icon = r.type.icon,
                    title = r.type.label,
                    subtitle = r.summary,
                    index = i,
                    count = vm.records.size,
                    onClick = { editing = r.type to i },
                    onMove = { to -> vm.move(vm.records, i, to) },
                    onDelete = { vm.records.removeAt(i) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
        BottomActions(
            secondary = "Agregar registro", onSecondary = { picking = true },
            primary = "Escribir", primaryEnabled = vm.records.isNotEmpty(), onPrimary = { vm.writeRecords() },
        )
    }

    if (picking) {
        TypePickerSheet(
            groups = listOf("Tipo de registro" to RecordType.entries.toList()),
            label = { it.label }, icon = { it.icon },
            onDismiss = { picking = false },
            onPick = { picking = false; editing = it to null },
        )
    }
    editing?.let { (type, index) ->
        FieldsDialog(
            item = type,
            initial = index?.let { vm.records[it].values },
            onDismiss = { editing = null },
            onConfirm = { values ->
                if (index == null) vm.records.add(RecordItem(type, values))
                else vm.records[index] = vm.records[index].copy(values = values)
                editing = null
            },
        )
    }
}

@Composable
fun SizeSummary(title: String, bytes: Int, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Filled.Nfc, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                AnimatedContent(title, transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) }, label = "t") {
                    Text(it, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                AnimatedContent(bytes, transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) }, label = "b") {
                    Text(
                        "$it bytes · NTAG213 ≈ 137 · NTAG215 ≈ 496 · NTAG216 ≈ 868",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    )
                }
            }
        }
    }
}

@Composable
fun BottomActions(
    secondary: String,
    onSecondary: () -> Unit,
    primary: String,
    primaryEnabled: Boolean,
    onPrimary: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val a = remember { MutableInteractionSource() }
            val b = remember { MutableInteractionSource() }
            OutlinedButton(onClick = onSecondary, interactionSource = a, modifier = Modifier.weight(1f).height(52.dp).pressScale(a)) {
                Icon(Icons.Filled.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                Text(secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Button(onClick = onPrimary, enabled = primaryEnabled, interactionSource = b, modifier = Modifier.weight(1f).height(52.dp).pressScale(b)) {
                Icon(Icons.Filled.Nfc, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                Text(primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Hoja con una cuadrícula de opciones agrupadas que aparecen escalonadas. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> TypePickerSheet(
    groups: List<Pair<String, List<T>>>,
    label: (T) -> String,
    icon: (T) -> ImageVector,
    onDismiss: () -> Unit,
    onPick: (T) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        // Posición global de cada opción, para escalonar su aparición.
        val offsets = groups.runningFold(0) { acc, g -> acc + g.second.size }
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.navigationBarsPadding(),
        ) {
            groups.forEachIndexed { g, (title, list) ->
                item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle(title) }
                gridItemsIndexed(list) { j, t ->
                    val i = offsets[g] + j
                    val interaction = remember { MutableInteractionSource() }
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        shape = MaterialTheme.shapes.medium,
                        tonalElevation = 1.dp,
                        modifier = Modifier.stagger(i).pressScale(interaction),
                    ) {
                        Column(
                            Modifier.clickable(interactionSource = interaction, indication = androidx.compose.material3.ripple()) { onPick(t) }
                                .padding(vertical = 14.dp, horizontal = 6.dp)
                                .fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            IconBadge(icon(t))
                            Spacer(Modifier.height(8.dp))
                            Text(
                                label(t), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center,
                                maxLines = 2, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(12.dp)) }
        }
    }
}
