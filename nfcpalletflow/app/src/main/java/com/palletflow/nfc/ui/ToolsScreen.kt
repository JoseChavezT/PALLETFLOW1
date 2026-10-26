package com.palletflow.nfc.ui

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.LayersClear
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.palletflow.nfc.MainViewModel
import com.palletflow.nfc.NfcOp
import com.palletflow.nfc.ui.theme.MonoStyle

@Composable
fun ToolsScreen(vm: MainViewModel) {
    val context = LocalContext.current
    var confirmLock by remember { mutableStateOf(false) }
    var confirmErase by remember { mutableStateOf(false) }
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    var hex by rememberSaveable { mutableStateOf("30 00") }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { SectionTitle("Etiquetas", Modifier.stagger(0)) }
        item {
            ToolCard(Icons.Filled.ContentCopy, "Copiar etiqueta", "Lee una etiqueta y escribe su contenido en otra", Modifier.stagger(1)) {
                expanded = if (expanded == "copy") null else "copy"
            }
        }
        item {
            AnimatedVisibility(expanded == "copy", enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Panel {
                    val buffer = vm.copyBuffer
                    Text(
                        if (buffer == null) "Paso 1: lee la etiqueta de origen."
                        else "Copia en memoria: ${buffer.records.size} registro(s), ${buffer.byteArrayLength} bytes. Paso 2: escríbela en otra etiqueta.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilledTonalButton(onClick = { vm.request(NfcOp.CopyRead) }) { Text("Leer origen") }
                        Button(onClick = { buffer?.let { vm.request(NfcOp.CopyWrite(it)) } }, enabled = buffer != null) { Text("Escribir copia") }
                    }
                    if (buffer != null) TextButton(onClick = { vm.clearCopy() }) { Text("Descartar copia") }
                }
            }
        }
        item { ToolCard(Icons.Filled.DeleteForever, "Borrar etiqueta", "Elimina todo el contenido NDEF", Modifier.stagger(2)) { confirmErase = true } }
        item { ToolCard(Icons.Filled.LayersClear, "Formatear", "Prepara una etiqueta nueva o sin formato para NDEF", Modifier.stagger(3)) { vm.request(NfcOp.Format) } }
        item { ToolCard(Icons.Filled.Lock, "Bloquear", "Deja la etiqueta en solo lectura de forma permanente", Modifier.stagger(4)) { confirmLock = true } }

        item { SectionTitle("Avanzado", Modifier.stagger(5)) }
        item {
            ToolCard(Icons.Filled.Memory, "Ver memoria", "Volcado por páginas de NTAG y MIFARE Ultralight", Modifier.stagger(6)) {
                expanded = "memory"
                vm.request(NfcOp.Memory)
            }
        }
        item {
            AnimatedVisibility(
                expanded == "memory" && vm.memoryDump.isNotEmpty(),
                enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut(),
            ) {
                Panel {
                    Column(Modifier.horizontalScroll(rememberScrollState())) {
                        Text("Pág  Datos        ASCII", style = MonoStyle, color = MaterialTheme.colorScheme.primary)
                        vm.memoryDump.forEach { Text(it, style = MonoStyle) }
                    }
                }
            }
        }
        item {
            ToolCard(Icons.Filled.Code, "Comandos avanzados", "Envía bytes en hexadecimal (transceive)", Modifier.stagger(7)) {
                expanded = if (expanded == "raw") null else "raw"
            }
        }
        item {
            AnimatedVisibility(expanded == "raw", enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Panel {
                    OutlinedTextField(
                        hex, { hex = it },
                        label = { Text("Comando (hex)") },
                        supportingText = { Text("Ej.: 30 04 lee la página 4 de una NTAG · 60 pide la versión") },
                        textStyle = MonoStyle,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { vm.request(NfcOp.Raw(hex)) }, enabled = hex.isNotBlank()) {
                        androidx.compose.material3.Icon(Icons.Filled.Send, null)
                        Spacer(Modifier.width(6.dp)); Text("Enviar al acercar")
                    }
                    vm.rawLog.take(10).forEach {
                        Spacer(Modifier.height(8.dp))
                        Text(it, style = MonoStyle)
                    }
                }
            }
        }

        item { SectionTitle("Permisos para tareas", Modifier.stagger(8)) }
        item {
            ToolCard(Icons.Filled.DoNotDisturbOn, "Acceso a «No molestar»", permissionText(hasPolicyAccess(context)), Modifier.stagger(9)) {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            }
        }
        item {
            ToolCard(Icons.Filled.Settings, "Modificar ajustes del sistema", permissionText(Settings.System.canWrite(context)), Modifier.stagger(10)) {
                context.startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + context.packageName)))
            }
        }
        item {
            ToolCard(Icons.Filled.Nfc, "Ajustes de NFC", "Abre la configuración NFC del teléfono", Modifier.stagger(11)) {
                context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
            }
        }
    }

    if (confirmErase) {
        ConfirmDialog("¿Borrar la etiqueta?", "Se eliminará todo su contenido.", "Borrar", { confirmErase = false }) {
            confirmErase = false; vm.request(NfcOp.Erase)
        }
    }
    if (confirmLock) {
        ConfirmDialog(
            "¿Bloquear la etiqueta?",
            "Es permanente: la etiqueta quedará en solo lectura y no se podrá volver a escribir ni borrar.",
            "Bloquear", { confirmLock = false },
        ) { confirmLock = false; vm.request(NfcOp.Lock) }
    }
}

private fun hasPolicyAccess(context: Context) =
    (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).isNotificationPolicyAccessGranted

private fun permissionText(granted: Boolean) =
    if (granted) "Concedido" else "Necesario para algunas tareas · toca para conceder"

@Composable
private fun ToolCard(icon: ImageVector, title: String, subtitle: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 1.dp,
        modifier = modifier.fillMaxWidth().pressScale(interaction),
    ) {
        Row(
            Modifier.clickable(interactionSource = interaction, indication = androidx.compose.material3.ripple(), onClick = onClick).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge(icon)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Panel(content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.animateContentSize(tween(260, easing = FastOutSlowInEasing)).padding(14.dp)) { content() }
    }
}

@Composable
private fun ConfirmDialog(title: String, text: String, confirm: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm, color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
