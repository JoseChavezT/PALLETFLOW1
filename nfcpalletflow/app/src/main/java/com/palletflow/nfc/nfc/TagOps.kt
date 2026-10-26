package com.palletflow.nfc.nfc

import android.nfc.FormatException
import android.nfc.NdefMessage
import android.nfc.Tag
import android.nfc.TagLostException
import android.nfc.tech.IsoDep
import android.nfc.tech.MifareClassic
import android.nfc.tech.MifareUltralight
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.nfc.tech.NfcA
import android.nfc.tech.NfcB
import android.nfc.tech.NfcF
import android.nfc.tech.NfcV
import android.nfc.tech.TagTechnology
import java.io.IOException

data class TagInfo(
    val time: Long,
    val uid: String,
    val type: String,
    val techs: List<String>,
    val details: List<Pair<String, String>>,
    val records: List<ParsedRecord>,
    val message: NdefMessage?,
    val usedBytes: Int,
    val maxBytes: Int,
) {
    val summary: String
        get() = records.firstOrNull { it.kind != "Vacío" }?.let { "${it.kind}: ${it.value.lineSequence().first()}" }
            ?: if (message == null) "Sin datos NDEF" else "Etiqueta vacía"
}

class NfcError(message: String) : Exception(message)

/** Operaciones sobre una etiqueta ya detectada. Todas son bloqueantes: llamarlas fuera del hilo principal. */
object TagOps {

    fun read(tag: Tag): TagInfo {
        val techs = tag.techList.map { it.substringAfterLast('.') }
        val details = mutableListOf<Pair<String, String>>()
        var type = "Etiqueta NFC"

        NfcA.get(tag)?.let { a ->
            details += "ATQA" to NdefCodec.hex(a.atqa)
            details += "SAK" to "%02X".format(a.sak)
            type = "ISO 14443‑3A"
            ntagVersion(a)?.let { (name, info) -> type = name; details += "Versión" to info }
        }
        NfcB.get(tag)?.let { type = "ISO 14443‑3B"; details += "Datos de aplicación" to NdefCodec.hex(it.applicationData) }
        NfcF.get(tag)?.let {
            type = "FeliCa (JIS 6319‑4)"
            details += "Fabricante" to NdefCodec.hex(it.manufacturer)
            details += "Código de sistema" to NdefCodec.hex(it.systemCode)
        }
        NfcV.get(tag)?.let {
            type = "ISO 15693 (NFC‑V)"
            details += "DSFID" to "%02X".format(it.dsfId)
        }
        IsoDep.get(tag)?.let {
            if (!type.startsWith("FeliCa")) type = if (NfcB.get(tag) != null) "ISO 14443‑4 (B)" else "ISO 14443‑4 (A)"
            it.historicalBytes?.let { h -> details += "Bytes históricos" to NdefCodec.hex(h) }
            it.hiLayerResponse?.let { h -> details += "Respuesta capa alta" to NdefCodec.hex(h) }
        }
        MifareClassic.get(tag)?.let {
            type = "MIFARE Classic " + when (it.size) {
                MifareClassic.SIZE_MINI -> "Mini"
                MifareClassic.SIZE_1K -> "1K"
                MifareClassic.SIZE_2K -> "2K"
                MifareClassic.SIZE_4K -> "4K"
                else -> ""
            }
            details += "Sectores" to "${it.sectorCount} · ${it.blockCount} bloques"
        }
        if (MifareUltralight.get(tag) != null && !type.startsWith("NTAG") && !type.startsWith("MIFARE Ultralight")) {
            type = when (MifareUltralight.get(tag)?.type) {
                MifareUltralight.TYPE_ULTRALIGHT_C -> "MIFARE Ultralight C"
                else -> "MIFARE Ultralight"
            }
        }

        val ndef = Ndef.get(tag)
        val message = ndef?.cachedNdefMessage
        var used = 0
        var max = 0
        if (ndef != null) {
            max = ndef.maxSize
            used = message?.byteArrayLength ?: 0
            details += "Formato" to ndefType(ndef.type)
            details += "Escribible" to if (ndef.isWritable) "Sí" else "No (solo lectura)"
            details += "Se puede bloquear" to if (ndef.canMakeReadOnly()) "Sí" else "No"
        } else if (NdefFormatable.get(tag) != null) {
            details += "Formato" to "Sin formato NDEF (se puede formatear)"
        } else {
            details += "Formato" to "No compatible con NDEF"
        }
        details += "Tecnologías" to techs.joinToString(", ")

        return TagInfo(
            time = System.currentTimeMillis(),
            uid = NdefCodec.hex(tag.id, ":"),
            type = type,
            techs = techs,
            details = details,
            records = message?.let { NdefCodec.parse(it) } ?: emptyList(),
            message = message,
            usedBytes = used,
            maxBytes = max,
        )
    }

    /** Pregunta la versión con GET_VERSION (0x60) para distinguir NTAG213/215/216. */
    private fun ntagVersion(a: NfcA): Pair<String, String>? = try {
        a.connect()
        val r = a.transceive(byteArrayOf(0x60))
        if (r.size < 8) null else {
            val product = r[2].toInt() and 0xFF
            val storage = r[6].toInt() and 0xFF
            val name = when {
                product == 0x04 && storage == 0x0F -> "NTAG213"
                product == 0x04 && storage == 0x11 -> "NTAG215"
                product == 0x04 && storage == 0x13 -> "NTAG216"
                product == 0x04 -> "NTAG"
                product == 0x03 && storage == 0x0B -> "MIFARE Ultralight EV1 (48 B)"
                product == 0x03 && storage == 0x0E -> "MIFARE Ultralight EV1 (128 B)"
                product == 0x03 -> "MIFARE Ultralight"
                else -> null
            }
            name?.let { it to NdefCodec.hex(r) }
        }
    } catch (e: IOException) {
        null
    } finally {
        runCatching { a.close() }
    }

    private fun ndefType(t: String): String = when (t) {
        Ndef.NFC_FORUM_TYPE_1 -> "NFC Forum Tipo 1"
        Ndef.NFC_FORUM_TYPE_2 -> "NFC Forum Tipo 2"
        Ndef.NFC_FORUM_TYPE_3 -> "NFC Forum Tipo 3"
        Ndef.NFC_FORUM_TYPE_4 -> "NFC Forum Tipo 4"
        Ndef.MIFARE_CLASSIC -> "MIFARE Classic"
        else -> t.substringAfterLast('.')
    }

    fun write(tag: Tag, message: NdefMessage) {
        val ndef = Ndef.get(tag)
        if (ndef != null) {
            guarded(ndef) {
                if (!ndef.isWritable) throw NfcError("La etiqueta es de solo lectura")
                val size = message.byteArrayLength
                if (size > ndef.maxSize) throw NfcError("No cabe: ocupa $size bytes y la etiqueta admite ${ndef.maxSize}")
                ndef.writeNdefMessage(message)
            }
            return
        }
        val formatable = NdefFormatable.get(tag) ?: throw NfcError("Esta etiqueta no es compatible con NDEF")
        guarded(formatable) { formatable.format(message) }
    }

    fun erase(tag: Tag) = write(tag, NdefCodec.emptyMessage())

    fun format(tag: Tag): String {
        val formatable = NdefFormatable.get(tag)
        if (formatable != null) {
            guarded(formatable) { formatable.format(NdefCodec.emptyMessage()) }
            return "Etiqueta formateada en NDEF"
        }
        if (Ndef.get(tag) != null) {
            erase(tag)
            return "Ya tenía formato NDEF: se dejó vacía"
        }
        throw NfcError("Esta etiqueta no se puede formatear en NDEF")
    }

    fun lock(tag: Tag) {
        val ndef = Ndef.get(tag) ?: throw NfcError("La etiqueta no tiene formato NDEF")
        guarded(ndef) {
            if (!ndef.canMakeReadOnly()) throw NfcError("Esta etiqueta no se puede bloquear")
            if (!ndef.makeReadOnly()) throw NfcError("No se pudo bloquear la etiqueta")
        }
    }

    /** Lee la memoria de etiquetas tipo 2 (NTAG / Ultralight), página por página. */
    fun memory(tag: Tag): List<String> {
        val ul = MifareUltralight.get(tag) ?: throw NfcError("Solo disponible para NTAG y MIFARE Ultralight")
        val lines = mutableListOf<String>()
        guarded(ul) {
            var page = 0
            var first: ByteArray? = null
            while (page < 256) {
                val block = try {
                    ul.readPages(page)
                } catch (e: IOException) {
                    break
                }
                // readPages devuelve 4 páginas; al final de la memoria vuelve a empezar desde la 0.
                if (first == null) first = block else if (block.contentEquals(first)) break
                for (p in 0 until 4) {
                    val bytes = block.copyOfRange(p * 4, p * 4 + 4)
                    val ascii = bytes.map { b -> val c = b.toInt() and 0xFF; if (c in 32..126) c.toChar() else '.' }.joinToString("")
                    lines += "%03d  %s  %s".format(page + p, NdefCodec.hex(bytes), ascii)
                }
                page += 4
            }
        }
        if (lines.isEmpty()) throw NfcError("No se pudo leer la memoria")
        return lines
    }

    /** Envía un comando en hexadecimal con la tecnología más adecuada. */
    fun transceive(tag: Tag, hex: String): String {
        val cmd = parseHex(hex)
        val tech: TagTechnology = IsoDep.get(tag) ?: NfcA.get(tag) ?: NfcV.get(tag) ?: NfcF.get(tag) ?: NfcB.get(tag)
            ?: throw NfcError("Tecnología no compatible")
        var response = ByteArray(0)
        guarded(tech) {
            response = when (tech) {
                is IsoDep -> tech.transceive(cmd)
                is NfcA -> tech.transceive(cmd)
                is NfcV -> tech.transceive(cmd)
                is NfcF -> tech.transceive(cmd)
                is NfcB -> tech.transceive(cmd)
                else -> throw NfcError("Tecnología no compatible")
            }
        }
        return "${tech.javaClass.simpleName} ← ${NdefCodec.hex(response)}"
    }

    fun parseHex(hex: String): ByteArray {
        val clean = hex.replace(Regex("[^0-9A-Fa-f]"), "")
        if (clean.isEmpty() || clean.length % 2 != 0) throw NfcError("Comando hexadecimal no válido")
        return ByteArray(clean.length / 2) { clean.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }

    private inline fun guarded(tech: TagTechnology, block: () -> Unit) {
        try {
            tech.connect()
            block()
        } catch (e: NfcError) {
            throw e
        } catch (e: TagLostException) {
            throw NfcError("Se perdió la conexión. Mantén la etiqueta quieta e inténtalo otra vez")
        } catch (e: FormatException) {
            throw NfcError("Los datos no tienen un formato NDEF válido")
        } catch (e: IOException) {
            throw NfcError("Error de comunicación con la etiqueta (${e.message ?: "E/S"})")
        } catch (e: SecurityException) {
            throw NfcError("La etiqueta se retiró antes de terminar")
        } finally {
            runCatching { tech.close() }
        }
    }
}
