package com.palletflow.nfc.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.palletflow.nfc.MainViewModel
import com.palletflow.nfc.NfcStatus
import com.palletflow.nfc.Tab

private fun Tab.icon(): ImageVector = when (this) {
    Tab.READ -> Icons.Filled.Nfc
    Tab.WRITE -> Icons.Filled.Edit
    Tab.TASKS -> Icons.Filled.PlaylistPlay
    Tab.TOOLS -> Icons.Filled.Build
    Tab.HISTORY -> Icons.Filled.History
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(vm: MainViewModel, nfc: NfcStatus) {
    val scroll = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("NFCPalletflow", style = MaterialTheme.typography.titleLarge)
                        AnimatedContent(vm.tab, transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) }, label = "subtitle") {
                            Text(it.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                },
                scrollBehavior = scroll,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = vm.tab == t,
                        onClick = { vm.tab = t },
                        icon = { Icon(t.icon(), null) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            NfcBanner(nfc)
            AnimatedContent(
                targetState = vm.tab,
                transitionSpec = {
                    val forward = targetState.ordinal > initialState.ordinal
                    val dir = if (forward) 1 else -1
                    (fadeIn(tween(260, delayMillis = 40, easing = FastOutSlowInEasing)) +
                        slideInHorizontally(tween(320, easing = FastOutSlowInEasing)) { w -> dir * w / 10 })
                        .togetherWith(
                            fadeOut(tween(140)) + slideOutHorizontally(tween(260, easing = FastOutSlowInEasing)) { w -> -dir * w / 14 }
                        )
                },
                label = "tabs",
                modifier = Modifier.fillMaxSize(),
            ) { tab ->
                Box(Modifier.fillMaxSize()) {
                    when (tab) {
                        Tab.READ -> ReadScreen(vm)
                        Tab.WRITE -> WriteScreen(vm)
                        Tab.TASKS -> TasksScreen(vm)
                        Tab.TOOLS -> ToolsScreen(vm)
                        Tab.HISTORY -> HistoryScreen(vm)
                    }
                }
            }
        }
    }
    ScanSheet(vm)
}

@Composable
private fun NfcBanner(nfc: NfcStatus) {
    val context = LocalContext.current
    AnimatedVisibility(
        visible = nfc == NfcStatus.OFF || nfc == NfcStatus.MISSING,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.tertiaryContainer,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.WarningAmber, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                Spacer(Modifier.width(10.dp))
                Text(
                    if (nfc == NfcStatus.MISSING) "Este dispositivo no tiene NFC" else "El NFC está desactivado",
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                if (nfc == NfcStatus.OFF) {
                    TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) }) { Text("Activar") }
                }
            }
        }
    }
}
