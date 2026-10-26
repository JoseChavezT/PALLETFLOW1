package com.palletflow.nfc.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.palletflow.nfc.MainViewModel
import com.palletflow.nfc.ScanState

/** Hoja inferior que acompaña cada operación: espera, progreso y resultado. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanSheet(vm: MainViewModel) {
    val state = vm.scan
    if (state is ScanState.Idle) return
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = { vm.dismissScan() },
        sheetState = sheet,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        AnimatedContent(
            targetState = state,
            contentKey = { if (it is ScanState.Waiting || it is ScanState.Busy) "wait" else it.javaClass.name },
            transitionSpec = {
                (fadeIn(tween(260, delayMillis = 60)) + scaleIn(tween(260, delayMillis = 60), initialScale = 0.94f))
                    .togetherWith(fadeOut(tween(140)))
            },
            label = "scan",
        ) { s ->
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp).navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                when (s) {
                    is ScanState.Waiting, is ScanState.Busy -> {
                        val busy = s is ScanState.Busy
                        val title = (s as? ScanState.Waiting)?.op?.title ?: (s as ScanState.Busy).op.title
                        Text(title, style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(20.dp))
                        NfcPulse(Icons.Filled.Nfc)
                        Spacer(Modifier.height(20.dp))
                        Text(
                            if (busy) "Procesando… no retires la etiqueta" else "Acerca la etiqueta a la parte trasera del teléfono",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(16.dp))
                        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth()) else Spacer(Modifier.height(4.dp))
                        Spacer(Modifier.height(16.dp))
                        OutlinedButton(onClick = { vm.dismissScan() }, modifier = Modifier.fillMaxWidth()) { Text("Cancelar") }
                    }
                    is ScanState.Success -> Result(true, s.title, s.detail) {
                        Button(onClick = { vm.dismissScan() }, modifier = Modifier.fillMaxWidth()) { Text("Listo") }
                    }
                    is ScanState.Failure -> Result(false, s.title, s.detail) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(onClick = { vm.dismissScan() }, modifier = Modifier.weight(1f)) { Text("Cerrar") }
                            s.retry?.let { op ->
                                Button(onClick = { vm.request(op) }, modifier = Modifier.weight(1f)) { Text("Reintentar") }
                            }
                        }
                    }
                    ScanState.Idle -> Unit
                }
            }
        }
    }
}

@Composable
private fun Result(ok: Boolean, title: String, detail: String, actions: @Composable () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val scale = remember { Animatable(0.4f) }
    val shake = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        haptic.performHapticFeedback(if (ok) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove)
        scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
        if (!ok) {
            for (x in listOf(10f, -8f, 6f, -4f, 0f)) shake.animateTo(x, tween(55))
        }
    }
    val container = if (ok) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer
    val content = if (ok) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.error
    Spacer(Modifier.height(8.dp))
    Surface(
        shape = CircleShape,
        color = container,
        modifier = Modifier.size(96.dp).graphicsLayer {
            scaleX = scale.value; scaleY = scale.value
            translationX = shake.value.dp.toPx()
        },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(if (ok) Icons.Filled.Check else Icons.Filled.ErrorOutline, null, tint = content, modifier = Modifier.size(48.dp))
        }
    }
    Spacer(Modifier.height(20.dp))
    Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
    if (detail.isNotBlank()) {
        Spacer(Modifier.height(6.dp))
        Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
    Spacer(Modifier.height(24.dp))
    actions()
}
