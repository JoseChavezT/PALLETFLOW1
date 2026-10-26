package com.palletflow.nfc.data

import android.content.Context
import com.palletflow.nfc.tasks.TaskCodec
import com.palletflow.nfc.tasks.TaskItem
import org.json.JSONArray
import org.json.JSONObject

data class HistoryEntry(
    val time: Long,
    val action: String,
    val tagType: String,
    val uid: String,
    val summary: String,
)

data class Profile(val name: String, val tasks: List<TaskItem>)

/** Historial y perfiles de tareas, guardados localmente en el teléfono. */
class Store(context: Context) {
    private val prefs = context.getSharedPreferences("nfcpalletflow", Context.MODE_PRIVATE)

    fun history(): List<HistoryEntry> = runCatching {
        val a = JSONArray(prefs.getString("history", "[]"))
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            HistoryEntry(
                o.optLong("time"), o.optString("action"), o.optString("type"),
                o.optString("uid"), o.optString("summary"),
            )
        }
    }.getOrDefault(emptyList())

    fun addHistory(entry: HistoryEntry): List<HistoryEntry> {
        val list = (listOf(entry) + history()).take(MAX_HISTORY)
        val a = JSONArray()
        list.forEach {
            a.put(JSONObject().apply {
                put("time", it.time); put("action", it.action); put("type", it.tagType)
                put("uid", it.uid); put("summary", it.summary)
            })
        }
        prefs.edit().putString("history", a.toString()).apply()
        return list
    }

    fun clearHistory() = prefs.edit().remove("history").apply()

    fun profiles(): List<Profile> = runCatching {
        val a = JSONArray(prefs.getString("profiles", "[]"))
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            Profile(o.optString("name"), TaskCodec.fromJson(o.optJSONArray("tasks") ?: JSONArray()))
        }
    }.getOrDefault(emptyList())

    fun saveProfiles(list: List<Profile>) {
        val a = JSONArray()
        list.forEach { a.put(JSONObject().apply { put("name", it.name); put("tasks", TaskCodec.toJson(it.tasks)) }) }
        prefs.edit().putString("profiles", a.toString()).apply()
    }

    fun draftTasks(): List<TaskItem> =
        runCatching { TaskCodec.decode(prefs.getString("draft_tasks", "[]")!!) }.getOrDefault(emptyList())

    fun saveDraftTasks(tasks: List<TaskItem>) =
        prefs.edit().putString("draft_tasks", TaskCodec.encode(tasks)).apply()

    companion object {
        private const val MAX_HISTORY = 300
    }
}
