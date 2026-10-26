package com.palletflow.nfc

import android.app.Activity
import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import com.palletflow.nfc.data.HistoryEntry
import com.palletflow.nfc.data.Store
import com.palletflow.nfc.nfc.NdefCodec
import com.palletflow.nfc.tasks.TaskRunner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Se abre sin interfaz al acercar una etiqueta con tareas, las ejecuta y se cierra. */
class TaskRunActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handle(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent) {
        val messages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES, NdefMessage::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)?.filterIsInstance<NdefMessage>()?.toTypedArray()
        }
        val tag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
        }
        val tasks = messages.orEmpty()
            .flatMap { NdefCodec.parse(it) }
            .firstOrNull { it.tasks != null }
            ?.tasks.orEmpty()

        if (tasks.isEmpty()) {
            finish()
            return
        }
        scope.launch {
            val results = TaskRunner(this@TaskRunActivity).run(tasks)
            val failed = results.filterNot { it.ok }
            Toast.makeText(
                this@TaskRunActivity,
                if (failed.isEmpty()) "NFCPalletflow · ${results.size} tareas ejecutadas"
                else "NFCPalletflow · ${failed.first().message}",
                Toast.LENGTH_SHORT
            ).show()
            Store(this@TaskRunActivity).addHistory(
                HistoryEntry(
                    System.currentTimeMillis(), "Tareas ejecutadas", "Etiqueta de tareas",
                    tag?.id?.let { NdefCodec.hex(it, ":") }.orEmpty(),
                    tasks.joinToString(", ") { it.type.label },
                )
            )
            finish()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
