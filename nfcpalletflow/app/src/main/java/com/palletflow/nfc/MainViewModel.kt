package com.palletflow.nfc

import android.app.Application
import android.content.Context
import android.nfc.NdefMessage
import android.nfc.Tag
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.palletflow.nfc.data.HistoryEntry
import com.palletflow.nfc.data.Profile
import com.palletflow.nfc.data.Store
import com.palletflow.nfc.nfc.NdefCodec
import com.palletflow.nfc.nfc.NfcError
import com.palletflow.nfc.nfc.RecordItem
import com.palletflow.nfc.nfc.TagInfo
import com.palletflow.nfc.nfc.TagOps
import com.palletflow.nfc.tasks.TaskItem
import com.palletflow.nfc.tasks.TaskResult
import com.palletflow.nfc.tasks.TaskRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Tab(val label: String) { READ("Leer"), WRITE("Escribir"), TASKS("Tareas"), TOOLS("Otros"), HISTORY("Historial") }

/** Operación pendiente que se realizará con la próxima etiqueta que se acerque. */
sealed class NfcOp(val title: String) {
    class Write(val message: NdefMessage, label: String) : NfcOp(label)
    data object CopyRead : NfcOp("Leer etiqueta de origen")
    class CopyWrite(val message: NdefMessage) : NfcOp("Escribir copia")
    data object Erase : NfcOp("Borrar etiqueta")
    data object Format : NfcOp("Formatear etiqueta")
    data object Lock : NfcOp("Bloquear etiqueta")
    data object Memory : NfcOp("Leer memoria")
    class Raw(val hex: String) : NfcOp("Enviar comando")
}

sealed class ScanState {
    data object Idle : ScanState()
    data class Waiting(val op: NfcOp) : ScanState()
    data class Busy(val op: NfcOp) : ScanState()
    data class Success(val title: String, val detail: String) : ScanState()
    data class Failure(val title: String, val detail: String, val retry: NfcOp?) : ScanState()
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val store = Store(app)

    var tab by mutableStateOf(Tab.READ)
    var scan by mutableStateOf<ScanState>(ScanState.Idle)
        private set
    var lastTag by mutableStateOf<TagInfo?>(null)
        private set
    var copyBuffer by mutableStateOf<NdefMessage?>(null)
        private set
    var memoryDump by mutableStateOf<List<String>>(emptyList())
        private set
    val rawLog = mutableStateListOf<String>()

    val records = mutableStateListOf<RecordItem>()
    val tasks = mutableStateListOf<TaskItem>().apply { addAll(store.draftTasks()) }
    val history = mutableStateListOf<HistoryEntry>().apply { addAll(store.history()) }
    val profiles = mutableStateListOf<Profile>().apply { addAll(store.profiles()) }

    var running by mutableStateOf(false)
        private set
    val runResults = mutableStateListOf<TaskResult>()

    fun request(op: NfcOp) {
        scan = ScanState.Waiting(op)
    }

    fun dismissScan() {
        scan = ScanState.Idle
    }

    /** Llega desde el hilo del lector NFC. */
    fun onTag(tag: Tag) {
        viewModelScope.launch {
            val current = scan
            val op = (current as? ScanState.Waiting)?.op
            if (current is ScanState.Busy) return@launch
            if (op == null) {
                if (current !is ScanState.Idle) return@launch
                readTag(tag)
                return@launch
            }
            scan = ScanState.Busy(op)
            scan = try {
                withContext(Dispatchers.IO) { perform(op, tag) }
            } catch (e: NfcError) {
                ScanState.Failure("No se pudo completar", e.message ?: "", op)
            } catch (e: Exception) {
                ScanState.Failure("Error inesperado", e.message ?: e.javaClass.simpleName, op)
            }
        }
    }

    private suspend fun readTag(tag: Tag) {
        val info = try {
            withContext(Dispatchers.IO) { TagOps.read(tag) }
        } catch (e: Exception) {
            return
        }
        lastTag = info
        tab = Tab.READ
        log("Lectura", info.type, info.uid, info.summary)
    }

    private suspend fun perform(op: NfcOp, tag: Tag): ScanState {
        val uid = NdefCodec.hex(tag.id, ":")
        return when (op) {
            is NfcOp.Write -> {
                TagOps.write(tag, op.message)
                withContext(Dispatchers.Main) { log("Escritura", op.title, uid, "${op.message.records.size} registro(s)") }
                ScanState.Success("Etiqueta escrita", "${op.message.byteArrayLength} bytes · ${op.message.records.size} registro(s)")
            }
            NfcOp.CopyRead -> {
                val info = TagOps.read(tag)
                val msg = info.message ?: throw NfcError("La etiqueta de origen no tiene datos NDEF")
                withContext(Dispatchers.Main) { copyBuffer = msg }
                ScanState.Success("Contenido copiado", "${msg.records.size} registro(s) listos. Ahora pulsa «Escribir copia».")
            }
            is NfcOp.CopyWrite -> {
                TagOps.write(tag, op.message)
                withContext(Dispatchers.Main) { log("Copia", "Copia de etiqueta", uid, "${op.message.records.size} registro(s)") }
                ScanState.Success("Copia escrita", "${op.message.byteArrayLength} bytes")
            }
            NfcOp.Erase -> {
                TagOps.erase(tag)
                withContext(Dispatchers.Main) { log("Borrado", "Etiqueta", uid, "Contenido eliminado") }
                ScanState.Success("Etiqueta borrada", "Ahora contiene un registro vacío")
            }
            NfcOp.Format -> {
                val msg = TagOps.format(tag)
                withContext(Dispatchers.Main) { log("Formato", "Etiqueta", uid, msg) }
                ScanState.Success("Listo", msg)
            }
            NfcOp.Lock -> {
                TagOps.lock(tag)
                withContext(Dispatchers.Main) { log("Bloqueo", "Etiqueta", uid, "Solo lectura permanente") }
                ScanState.Success("Etiqueta bloqueada", "Ya no se podrá modificar")
            }
            NfcOp.Memory -> {
                val dump = TagOps.memory(tag)
                withContext(Dispatchers.Main) { memoryDump = dump }
                ScanState.Success("Memoria leída", "${dump.size} páginas")
            }
            is NfcOp.Raw -> {
                val res = TagOps.transceive(tag, op.hex)
                withContext(Dispatchers.Main) { rawLog.add(0, "→ ${op.hex.uppercase()}\n$res") }
                ScanState.Success("Respuesta recibida", res)
            }
        }
    }

    private fun log(action: String, type: String, uid: String, summary: String) {
        val list = store.addHistory(HistoryEntry(System.currentTimeMillis(), action, type, uid, summary))
        history.clear()
        history.addAll(list)
    }

    fun refreshHistory() {
        history.clear()
        history.addAll(store.history())
    }

    fun clearHistory() {
        store.clearHistory()
        history.clear()
    }

    fun clearCopy() {
        copyBuffer = null
    }

    // ---------- Registros ----------

    fun writeRecords() {
        if (records.isEmpty()) return
        val msg = try {
            NdefCodec.build(records.toList())
        } catch (e: Exception) {
            scan = ScanState.Failure("Registro no válido", e.message ?: "", null)
            return
        }
        request(NfcOp.Write(msg, "Escribir ${records.size} registro(s)"))
    }

    fun messageSize(): Int = runCatching { NdefCodec.build(records.toList()).byteArrayLength }.getOrDefault(0)

    fun move(list: MutableList<*>, from: Int, to: Int) {
        if (to !in list.indices) return
        @Suppress("UNCHECKED_CAST")
        val l = list as MutableList<Any?>
        l.add(to, l.removeAt(from))
        if (list === tasks) saveDraft()
    }

    // ---------- Tareas ----------

    fun saveDraft() = store.saveDraftTasks(tasks.toList())

    fun writeTasks() {
        if (tasks.isEmpty()) return
        request(NfcOp.Write(NdefCodec.tasksMessage(tasks.toList()), "Escribir ${tasks.size} tarea(s)"))
    }

    fun tasksSize(): Int = NdefCodec.tasksMessage(tasks.toList()).byteArrayLength

    fun runTasks(context: Context, list: List<TaskItem>) {
        if (running || list.isEmpty()) return
        running = true
        runResults.clear()
        viewModelScope.launch {
            try {
                val results = TaskRunner(context).run(list) { runResults.add(it) }
                val failed = results.count { !it.ok }
                android.widget.Toast.makeText(
                    context,
                    if (failed == 0) "${results.size} tarea(s) ejecutada(s)" else results.first { !it.ok }.message,
                    android.widget.Toast.LENGTH_SHORT,
                ).show()
            } finally {
                running = false
            }
        }
    }

    fun loadTasks(list: List<TaskItem>) {
        tasks.clear()
        tasks.addAll(list)
        saveDraft()
        tab = Tab.TASKS
    }

    fun saveProfile(name: String) {
        val clean = name.trim().ifEmpty { "Perfil ${profiles.size + 1}" }
        val i = profiles.indexOfFirst { it.name == clean }
        val p = Profile(clean, tasks.toList())
        if (i >= 0) profiles[i] = p else profiles.add(p)
        store.saveProfiles(profiles.toList())
    }

    fun deleteProfile(p: Profile) {
        profiles.remove(p)
        store.saveProfiles(profiles.toList())
    }
}
