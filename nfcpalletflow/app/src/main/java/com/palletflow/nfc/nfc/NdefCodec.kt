package com.palletflow.nfc.nfc

import android.net.Uri
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import com.palletflow.nfc.tasks.TaskCodec
import com.palletflow.nfc.tasks.TaskItem
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset

const val TASKS_MIME = "application/vnd.palletflow.tasks"
private const val WIFI_MIME = "application/vnd.wfa.wsc"
private const val BT_MIME = "application/vnd.bluetooth.ep.oob"

/** Registro tal como se muestra al leer una etiqueta. */
data class ParsedRecord(
    val kind: String,
    val value: String,
    val format: String,
    val size: Int,
    val tasks: List<TaskItem>? = null,
)

object NdefCodec {

    fun build(items: List<RecordItem>): NdefMessage = NdefMessage(items.map { toRecord(it) }.toTypedArray())

    fun tasksMessage(tasks: List<TaskItem>): NdefMessage =
        NdefMessage(arrayOf(NdefRecord.createMime(TASKS_MIME, TaskCodec.encode(tasks).toByteArray())))

    fun emptyMessage(): NdefMessage =
        NdefMessage(arrayOf(NdefRecord(NdefRecord.TNF_EMPTY, null, null, null)))

    fun toRecord(item: RecordItem): NdefRecord {
        val v = item.values
        fun s(key: String) = v[key].orEmpty().trim()
        return when (item.type) {
            RecordType.TEXT -> NdefRecord.createTextRecord("es", s("text"))
            RecordType.URL -> NdefRecord.createUri(normalizeUrl(s("url")))
            RecordType.PHONE -> NdefRecord.createUri("tel:" + s("number").replace(" ", ""))
            RecordType.SMS -> {
                val body = s("body")
                val uri = "sms:" + s("number").replace(" ", "") +
                    if (body.isNotEmpty()) "?body=" + Uri.encode(body) else ""
                NdefRecord.createUri(uri)
            }
            RecordType.EMAIL -> {
                val q = listOfNotNull(
                    s("subject").takeIf { it.isNotEmpty() }?.let { "subject=" + Uri.encode(it) },
                    s("body").takeIf { it.isNotEmpty() }?.let { "body=" + Uri.encode(it) },
                ).joinToString("&")
                NdefRecord.createUri("mailto:" + s("to") + if (q.isNotEmpty()) "?$q" else "")
            }
            RecordType.CONTACT -> NdefRecord.createMime("text/vcard", vcard(v).toByteArray())
            RecordType.WIFI -> NdefRecord.createMime(WIFI_MIME, wifi(s("ssid"), s("auth"), v["password"].orEmpty()))
            RecordType.LOCATION -> NdefRecord.createUri("geo:${s("lat")},${s("lng")}")
            RecordType.ADDRESS -> NdefRecord.createUri("geo:0,0?q=" + Uri.encode(s("address")))
            RecordType.APP -> NdefRecord.createApplicationRecord(s("package"))
            RecordType.BLUETOOTH -> NdefRecord.createMime(BT_MIME, bluetooth(s("mac")))
            RecordType.CUSTOM_URI -> NdefRecord.createUri(s("uri"))
        }
    }

    private fun normalizeUrl(url: String): String =
        if (url.contains("://") || url.startsWith("mailto:")) url else "https://$url"

    private fun vcard(v: Map<String, String>): String = buildString {
        fun line(key: String, prefix: String) {
            v[key]?.trim()?.takeIf { it.isNotEmpty() }?.let { append(prefix).append(it).append("\r\n") }
        }
        append("BEGIN:VCARD\r\nVERSION:3.0\r\n")
        line("name", "FN:")
        line("org", "ORG:")
        line("phone", "TEL:")
        line("email", "EMAIL:")
        line("address", "ADR:;;")
        line("web", "URL:")
        append("END:VCARD\r\n")
    }

    /** Credencial Wi‑Fi Simple Configuration (WSC), el formato estándar que Android reconoce. */
    private fun wifi(ssid: String, auth: String, password: String): ByteArray {
        val (authType, encType) = when (auth) {
            "open" -> 0x0001 to 0x0001
            "wpa" -> 0x0002 to 0x0004
            else -> 0x0020 to 0x0008
        }
        val cred = ByteArrayOutputStream()
        fun tlv(out: ByteArrayOutputStream, type: Int, value: ByteArray) {
            out.write(type shr 8); out.write(type and 0xFF)
            out.write(value.size shr 8); out.write(value.size and 0xFF)
            out.write(value)
        }
        fun u16(x: Int) = byteArrayOf((x shr 8).toByte(), (x and 0xFF).toByte())
        tlv(cred, 0x1026, byteArrayOf(1))
        tlv(cred, 0x1045, ssid.toByteArray())
        tlv(cred, 0x1003, u16(authType))
        tlv(cred, 0x100F, u16(encType))
        tlv(cred, 0x1027, if (auth == "open") ByteArray(0) else password.toByteArray())
        tlv(cred, 0x1020, ByteArray(6) { 0xFF.toByte() })
        val out = ByteArrayOutputStream()
        tlv(out, 0x100E, cred.toByteArray())
        return out.toByteArray()
    }

    private fun bluetooth(mac: String): ByteArray {
        val bytes = mac.split(":", "-").filter { it.isNotEmpty() }.map { it.toInt(16).toByte() }
        require(bytes.size == 6) { "Dirección MAC no válida" }
        return byteArrayOf(8, 0) + bytes.reversed().toByteArray()
    }

    // ---------- Lectura ----------

    fun parse(message: NdefMessage): List<ParsedRecord> = message.records.map { parse(it) }

    fun parse(r: NdefRecord): ParsedRecord {
        val size = r.toByteArray().size
        val type = String(r.type ?: ByteArray(0), Charsets.US_ASCII)
        val payload = r.payload ?: ByteArray(0)
        return when (r.tnf) {
            NdefRecord.TNF_EMPTY -> ParsedRecord("Vacío", "Sin contenido", "Vacío", size)
            NdefRecord.TNF_WELL_KNOWN -> when {
                r.type.contentEquals(NdefRecord.RTD_TEXT) ->
                    ParsedRecord("Texto", decodeText(payload), "NFC Forum · T", size)
                r.type.contentEquals(NdefRecord.RTD_URI) -> {
                    val uri = r.toUri()?.toString().orEmpty()
                    ParsedRecord(uriKind(uri), uri, "NFC Forum · U", size)
                }
                r.type.contentEquals(NdefRecord.RTD_SMART_POSTER) -> {
                    val inner = runCatching { NdefMessage(payload).records.map { parse(it) } }.getOrNull()
                    ParsedRecord(
                        "Smart Poster",
                        inner?.joinToString("\n") { "${it.kind}: ${it.value}" } ?: hex(payload),
                        "NFC Forum · Sp", size
                    )
                }
                else -> ParsedRecord("NFC Forum", hex(payload), "NFC Forum · $type", size)
            }
            NdefRecord.TNF_ABSOLUTE_URI -> ParsedRecord("URI", type, "URI absoluta", size)
            NdefRecord.TNF_MIME_MEDIA -> parseMime(type.lowercase(), payload, size)
            NdefRecord.TNF_EXTERNAL_TYPE ->
                if (type == "android.com:pkg") {
                    ParsedRecord("Abrir app", String(payload, Charsets.US_ASCII), "Android Application Record", size)
                } else {
                    ParsedRecord("Externo", printableOrHex(payload), "Externo · $type", size)
                }
            else -> ParsedRecord("Desconocido", hex(payload), "TNF ${r.tnf}", size)
        }
    }

    private fun parseMime(mime: String, payload: ByteArray, size: Int): ParsedRecord = when (mime) {
        TASKS_MIME -> {
            val tasks = runCatching { TaskCodec.decode(String(payload)) }.getOrDefault(emptyList())
            ParsedRecord(
                "Tareas", tasks.joinToString("\n") { "• " + it.title }.ifEmpty { "Sin tareas" },
                "MIME · $mime", size, tasks
            )
        }
        WIFI_MIME -> ParsedRecord("Red Wi‑Fi", parseWifi(payload), "MIME · $mime", size)
        BT_MIME -> ParsedRecord(
            "Bluetooth",
            if (payload.size >= 8) payload.copyOfRange(2, 8).reversed().joinToString(":") { "%02X".format(it) } else hex(payload),
            "MIME · $mime", size
        )
        "text/vcard", "text/x-vcard" -> ParsedRecord("Contacto", vcardSummary(String(payload)), "MIME · $mime", size)
        else -> ParsedRecord("MIME", printableOrHex(payload), "MIME · $mime", size)
    }

    private fun decodeText(p: ByteArray): String {
        if (p.isEmpty()) return ""
        val status = p[0].toInt()
        val langLen = status and 0x3F
        val charset = if (status and 0x80 != 0) Charsets.UTF_16 else Charsets.UTF_8
        if (1 + langLen > p.size) return ""
        return String(p, 1 + langLen, p.size - 1 - langLen, charset)
    }

    private fun uriKind(uri: String): String = when {
        uri.startsWith("http") -> "Enlace web"
        uri.startsWith("tel:") -> "Teléfono"
        uri.startsWith("sms:") || uri.startsWith("smsto:") -> "SMS"
        uri.startsWith("mailto:") -> "Correo"
        uri.startsWith("geo:") -> "Ubicación"
        else -> "URI"
    }

    private fun parseWifi(p: ByteArray): String {
        var ssid = ""
        var auth = ""
        fun walk(data: ByteArray) {
            var i = 0
            while (i + 4 <= data.size) {
                val t = ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
                val l = ((data[i + 2].toInt() and 0xFF) shl 8) or (data[i + 3].toInt() and 0xFF)
                val start = i + 4
                if (start + l > data.size) return
                val value = data.copyOfRange(start, start + l)
                when (t) {
                    0x100E -> walk(value)
                    0x1045 -> ssid = String(value)
                    0x1003 -> auth = when (((value[0].toInt() and 0xFF) shl 8) or (value[1].toInt() and 0xFF)) {
                        0x0001 -> "Abierta"
                        0x0002, 0x0004 -> "WPA"
                        else -> "WPA2 / WPA3"
                    }
                }
                i = start + l
            }
        }
        runCatching { walk(p) }
        return if (ssid.isEmpty()) hex(p) else "$ssid · $auth"
    }

    private fun vcardSummary(v: String): String =
        v.lines().mapNotNull { line ->
            val idx = line.indexOf(':')
            if (idx <= 0) return@mapNotNull null
            val key = line.substring(0, idx).substringBefore(';').uppercase()
            val value = line.substring(idx + 1).replace(";", " ").trim()
            when (key) {
                "FN" -> value
                "ORG", "TEL", "EMAIL", "URL", "ADR" -> value
                else -> null
            }
        }.filter { it.isNotEmpty() }.joinToString("\n")

    fun printableOrHex(p: ByteArray): String {
        val s = runCatching { String(p, Charset.forName("UTF-8")) }.getOrNull()
        return if (s != null && s.all { it == '\n' || it == '\r' || it == '\t' || !it.isISOControl() }) s else hex(p)
    }

    fun hex(b: ByteArray, sep: String = " "): String = b.joinToString(sep) { "%02X".format(it) }
}
