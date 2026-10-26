package com.palletflow.nfc.nfc

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.ui.graphics.vector.ImageVector
import com.palletflow.nfc.data.Configurable
import com.palletflow.nfc.data.Field
import com.palletflow.nfc.data.FieldKind

/** Registros NDEF que se pueden escribir en una etiqueta. */
enum class RecordType(
    override val label: String,
    val icon: ImageVector,
    override val fields: List<Field>,
) : Configurable {
    TEXT(
        "Texto", Icons.Filled.TextFields,
        listOf(Field("text", "Texto", FieldKind.MULTILINE))
    ),
    URL(
        "Enlace web", Icons.Filled.Link,
        listOf(Field("url", "Dirección", FieldKind.URL, hint = "https://…"))
    ),
    PHONE(
        "Teléfono", Icons.Filled.Phone,
        listOf(Field("number", "Número", FieldKind.PHONE))
    ),
    SMS(
        "SMS", Icons.Filled.Sms,
        listOf(
            Field("number", "Número", FieldKind.PHONE),
            Field("body", "Mensaje", FieldKind.MULTILINE, required = false),
        )
    ),
    EMAIL(
        "Correo", Icons.Filled.Email,
        listOf(
            Field("to", "Destinatario", FieldKind.EMAIL),
            Field("subject", "Asunto", required = false),
            Field("body", "Mensaje", FieldKind.MULTILINE, required = false),
        )
    ),
    CONTACT(
        "Contacto", Icons.Filled.Person,
        listOf(
            Field("name", "Nombre"),
            Field("org", "Empresa", required = false),
            Field("phone", "Teléfono", FieldKind.PHONE, required = false),
            Field("email", "Correo", FieldKind.EMAIL, required = false),
            Field("address", "Dirección", required = false),
            Field("web", "Sitio web", FieldKind.URL, required = false),
        )
    ),
    WIFI(
        "Red Wi‑Fi", Icons.Filled.Wifi,
        listOf(
            Field("ssid", "Nombre de la red (SSID)"),
            Field(
                "auth", "Seguridad", FieldKind.CHOICE,
                options = listOf("wpa2" to "WPA2 / WPA3", "wpa" to "WPA", "open" to "Abierta")
            ),
            Field("password", "Contraseña", FieldKind.PASSWORD, required = false),
        )
    ),
    LOCATION(
        "Ubicación", Icons.Filled.Place,
        listOf(
            Field("lat", "Latitud", FieldKind.DECIMAL, hint = "-12.0464"),
            Field("lng", "Longitud", FieldKind.DECIMAL, hint = "-77.0428"),
        )
    ),
    ADDRESS(
        "Dirección", Icons.Filled.Map,
        listOf(Field("address", "Dirección o lugar", FieldKind.MULTILINE))
    ),
    APP(
        "Abrir app", Icons.Filled.Apps,
        listOf(Field("package", "Aplicación", FieldKind.APP))
    ),
    BLUETOOTH(
        "Bluetooth", Icons.Filled.Bluetooth,
        listOf(Field("mac", "Dirección MAC", hint = "00:11:22:33:44:55"))
    ),
    CUSTOM_URI(
        "URI personalizada", Icons.Filled.Public,
        listOf(Field("uri", "URI", FieldKind.URL, hint = "esquema://…"))
    );
}

data class RecordItem(
    val type: RecordType,
    val values: Map<String, String>,
    val id: Long = com.palletflow.nfc.data.nextId(),
) {
    val summary: String
        get() = when (type) {
            RecordType.WIFI -> values["ssid"].orEmpty()
            RecordType.LOCATION -> "${values["lat"]}, ${values["lng"]}"
            RecordType.CONTACT -> listOfNotNull(values["name"], values["phone"]?.takeIf { it.isNotBlank() })
                .joinToString(" · ")
            else -> type.fields.firstOrNull()?.let { values[it.key] }.orEmpty()
        }
}
