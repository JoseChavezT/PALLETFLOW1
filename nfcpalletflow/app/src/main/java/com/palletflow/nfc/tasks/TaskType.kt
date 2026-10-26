package com.palletflow.nfc.tasks

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AirplanemodeActive
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.NetworkCell
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.ScreenLockRotation
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.ui.graphics.vector.ImageVector
import com.palletflow.nfc.data.Configurable
import com.palletflow.nfc.data.Field
import com.palletflow.nfc.data.FieldKind
import com.palletflow.nfc.data.display

enum class TaskCategory(val label: String) {
    SOUND("Sonido"),
    SCREEN("Pantalla"),
    CONNECTIVITY("Conectividad"),
    APPS("Apps y web"),
    COMMUNICATION("Comunicación"),
    TIME("Alarmas y tiempo"),
    VARIOUS("Varios"),
    CONDITIONS("Condiciones"),
}

private val onOffToggle = listOf("toggle" to "Alternar", "on" to "Encender", "off" to "Apagar")

/** Acciones que se ejecutan al acercar una etiqueta con tareas. */
enum class TaskType(
    override val label: String,
    val category: TaskCategory,
    val icon: ImageVector,
    override val fields: List<Field> = emptyList(),
) : Configurable {
    // Sonido
    VOLUME(
        "Volumen", TaskCategory.SOUND, Icons.Filled.VolumeUp,
        listOf(
            Field(
                "stream", "Canal", FieldKind.CHOICE,
                options = listOf("media" to "Multimedia", "ring" to "Tono de llamada", "alarm" to "Alarma", "notification" to "Notificaciones")
            ),
            Field("level", "Nivel", FieldKind.SLIDER, default = "50"),
        )
    ),
    SOUND_MODE(
        "Modo de sonido", TaskCategory.SOUND, Icons.Filled.Notifications,
        listOf(Field("mode", "Modo", FieldKind.CHOICE, options = listOf("normal" to "Sonido", "vibrate" to "Vibración", "silent" to "Silencio")))
    ),
    DND(
        "No molestar", TaskCategory.SOUND, Icons.Filled.DoNotDisturbOn,
        listOf(Field("mode", "Estado", FieldKind.CHOICE, options = listOf("priority" to "Solo prioritarias", "none" to "Silencio total", "off" to "Desactivar")))
    ),
    MEDIA(
        "Control multimedia", TaskCategory.SOUND, Icons.Filled.MusicNote,
        listOf(Field("action", "Acción", FieldKind.CHOICE, options = listOf("play_pause" to "Reproducir / pausar", "next" to "Siguiente", "previous" to "Anterior", "stop" to "Detener")))
    ),

    // Pantalla
    BRIGHTNESS(
        "Brillo", TaskCategory.SCREEN, Icons.Filled.Brightness6,
        listOf(Field("level", "Nivel", FieldKind.SLIDER, default = "60"))
    ),
    AUTO_BRIGHTNESS(
        "Brillo automático", TaskCategory.SCREEN, Icons.Filled.WbSunny,
        listOf(Field("state", "Estado", FieldKind.CHOICE, options = onOffToggle))
    ),
    AUTO_ROTATE(
        "Rotación automática", TaskCategory.SCREEN, Icons.Filled.ScreenLockRotation,
        listOf(Field("state", "Estado", FieldKind.CHOICE, options = onOffToggle))
    ),
    SCREEN_TIMEOUT(
        "Tiempo de pantalla", TaskCategory.SCREEN, Icons.Filled.Schedule,
        listOf(
            Field(
                "seconds", "Apagar tras", FieldKind.CHOICE,
                options = listOf("15" to "15 s", "30" to "30 s", "60" to "1 min", "120" to "2 min", "300" to "5 min", "600" to "10 min")
            )
        )
    ),
    FLASHLIGHT(
        "Linterna", TaskCategory.SCREEN, Icons.Filled.FlashlightOn,
        listOf(Field("state", "Estado", FieldKind.CHOICE, options = onOffToggle))
    ),

    // Conectividad
    WIFI(
        "Wi‑Fi", TaskCategory.CONNECTIVITY, Icons.Filled.Wifi,
        listOf(Field("state", "Estado", FieldKind.CHOICE, options = onOffToggle))
    ),
    BLUETOOTH("Ajustes de Bluetooth", TaskCategory.CONNECTIVITY, Icons.Filled.Bluetooth),
    MOBILE_DATA("Panel de datos móviles", TaskCategory.CONNECTIVITY, Icons.Filled.NetworkCell),
    AIRPLANE("Ajustes de modo avión", TaskCategory.CONNECTIVITY, Icons.Filled.AirplanemodeActive),

    // Apps y web
    OPEN_APP(
        "Abrir aplicación", TaskCategory.APPS, Icons.Filled.Apps,
        listOf(Field("package", "Aplicación", FieldKind.APP))
    ),
    OPEN_URL(
        "Abrir enlace", TaskCategory.APPS, Icons.Filled.Language,
        listOf(Field("url", "Dirección", FieldKind.URL, hint = "https://…"))
    ),
    NAVIGATE(
        "Navegar a", TaskCategory.APPS, Icons.Filled.Navigation,
        listOf(Field("address", "Destino", FieldKind.MULTILINE))
    ),

    // Comunicación
    CALL(
        "Llamar", TaskCategory.COMMUNICATION, Icons.Filled.Phone,
        listOf(Field("number", "Número", FieldKind.PHONE))
    ),
    SMS(
        "Escribir SMS", TaskCategory.COMMUNICATION, Icons.Filled.Sms,
        listOf(
            Field("number", "Número", FieldKind.PHONE),
            Field("body", "Mensaje", FieldKind.MULTILINE, required = false),
        )
    ),
    EMAIL(
        "Escribir correo", TaskCategory.COMMUNICATION, Icons.Filled.Email,
        listOf(
            Field("to", "Destinatario", FieldKind.EMAIL),
            Field("subject", "Asunto", required = false),
            Field("body", "Mensaje", FieldKind.MULTILINE, required = false),
        )
    ),

    // Alarmas y tiempo
    ALARM(
        "Crear alarma", TaskCategory.TIME, Icons.Filled.Alarm,
        listOf(
            Field("time", "Hora", FieldKind.TIME, default = "07:00", hint = "HH:MM"),
            Field("label", "Etiqueta", required = false),
        )
    ),
    TIMER(
        "Temporizador", TaskCategory.TIME, Icons.Filled.Timer,
        listOf(
            Field("minutes", "Minutos", FieldKind.NUMBER, default = "5"),
            Field("label", "Etiqueta", required = false),
        )
    ),
    WAIT(
        "Esperar", TaskCategory.TIME, Icons.Filled.HourglassEmpty,
        listOf(Field("seconds", "Segundos", FieldKind.NUMBER, default = "2"))
    ),

    // Varios
    SPEAK(
        "Leer en voz alta", TaskCategory.VARIOUS, Icons.Filled.RecordVoiceOver,
        listOf(Field("text", "Texto", FieldKind.MULTILINE))
    ),
    VIBRATE(
        "Vibrar", TaskCategory.VARIOUS, Icons.Filled.Vibration,
        listOf(Field("ms", "Duración (ms)", FieldKind.NUMBER, default = "400"))
    ),
    MESSAGE(
        "Mostrar mensaje", TaskCategory.VARIOUS, Icons.Filled.Notifications,
        listOf(Field("text", "Mensaje", FieldKind.MULTILINE))
    ),
    CLIPBOARD(
        "Copiar al portapapeles", TaskCategory.VARIOUS, Icons.Filled.ContentPaste,
        listOf(Field("text", "Texto", FieldKind.MULTILINE))
    ),

    // Condiciones: si no se cumplen, se detienen las tareas siguientes.
    IF_TIME(
        "Solo entre horas", TaskCategory.CONDITIONS, Icons.Filled.Schedule,
        listOf(
            Field("from", "Desde", FieldKind.TIME, default = "08:00", hint = "HH:MM"),
            Field("to", "Hasta", FieldKind.TIME, default = "18:00", hint = "HH:MM"),
        )
    ),
    IF_DAYS(
        "Solo ciertos días", TaskCategory.CONDITIONS, Icons.Filled.Event,
        listOf(Field("days", "Días", FieldKind.CHOICE, options = listOf("weekdays" to "Lunes a viernes", "weekend" to "Fin de semana", "saturday" to "Lunes a sábado")))
    );
}

data class TaskItem(
    val type: TaskType,
    val values: Map<String, String>,
    val id: Long = com.palletflow.nfc.data.nextId(),
) {
    val title: String
        get() {
            val detail = type.fields.joinToString(" · ") { f -> f.display(values[f.key]) }.trim(' ', '·')
            return if (detail.isBlank()) type.label else "${type.label}: $detail"
        }

    val detail: String
        get() = type.fields.mapNotNull { f -> f.display(values[f.key]).takeIf { it.isNotBlank() } }.joinToString(" · ")
}
