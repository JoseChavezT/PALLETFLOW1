package com.palletflow.nfc.tasks

import org.json.JSONArray
import org.json.JSONObject

/** Formato compacto de las tareas guardadas en una etiqueta: [{"t":"FLASHLIGHT","p":{"state":"on"}}]. */
object TaskCodec {

    fun encode(tasks: List<TaskItem>): String = toJson(tasks).toString()

    fun decode(json: String): List<TaskItem> = fromJson(JSONArray(json))

    fun toJson(tasks: List<TaskItem>): JSONArray = JSONArray().apply {
        tasks.forEach { task ->
            put(JSONObject().apply {
                put("t", task.type.name)
                put("p", JSONObject().apply { task.values.forEach { (k, v) -> if (v.isNotEmpty()) put(k, v) } })
            })
        }
    }

    fun fromJson(array: JSONArray): List<TaskItem> = buildList {
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val type = runCatching { TaskType.valueOf(o.optString("t")) }.getOrNull() ?: continue
            val p = o.optJSONObject("p")
            val values = mutableMapOf<String, String>()
            if (p != null) {
                for (k in p.keys()) values[k] = p.optString(k)
            }
            add(TaskItem(type, values))
        }
    }
}
