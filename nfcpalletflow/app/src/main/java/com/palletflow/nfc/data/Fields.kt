package com.palletflow.nfc.data

private val idCounter = java.util.concurrent.atomic.AtomicLong()

/** Identificador único en memoria para las listas animadas. */
fun nextId(): Long = idCounter.incrementAndGet()

/** Tipo de entrada de un campo editable (lo usan tanto registros como tareas). */
enum class FieldKind { TEXT, MULTILINE, NUMBER, DECIMAL, PHONE, EMAIL, URL, SLIDER, CHOICE, APP, TIME, PASSWORD }

data class Field(
    val key: String,
    val label: String,
    val kind: FieldKind = FieldKind.TEXT,
    val options: List<Pair<String, String>> = emptyList(),
    val default: String = "",
    val required: Boolean = true,
    val hint: String = "",
)

/** Cualquier elemento configurable mediante un formulario de campos. */
interface Configurable {
    val label: String
    val fields: List<Field>
}

fun Configurable.defaults(): Map<String, String> =
    fields.associate { f -> f.key to (f.default.ifEmpty { f.options.firstOrNull()?.first ?: "" }) }

fun Configurable.missingRequired(values: Map<String, String>): Field? =
    fields.firstOrNull { it.required && values[it.key].isNullOrBlank() }

fun Field.display(value: String?): String = when (kind) {
    FieldKind.CHOICE -> options.firstOrNull { it.first == value }?.second ?: value.orEmpty()
    FieldKind.SLIDER -> "${value ?: "0"} %"
    FieldKind.PASSWORD -> if (value.isNullOrEmpty()) "" else "••••••"
    else -> value.orEmpty()
}
