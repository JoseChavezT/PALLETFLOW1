package com.palletflow.nfc.tasks

import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.AlarmClock
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.KeyEvent
import android.widget.Toast
import com.palletflow.nfc.NfcApp
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Calendar
import java.util.Locale
import kotlin.coroutines.resume

data class TaskResult(val task: TaskItem, val ok: Boolean, val message: String)

/**
 * Ejecuta tareas en orden. Las que Android no permite cambiar directamente
 * (Wi‑Fi en Android 10+, permisos especiales) abren el ajuste correspondiente.
 */
class TaskRunner(private val context: Context) {

    suspend fun run(tasks: List<TaskItem>, onProgress: (TaskResult) -> Unit = {}): List<TaskResult> {
        val results = mutableListOf<TaskResult>()
        for (task in tasks) {
            val result = try {
                execute(task)
            } catch (e: StopTasks) {
                TaskResult(task, true, e.message ?: "Condición no cumplida").also {
                    results += it; onProgress(it)
                }
                break
            } catch (e: Exception) {
                TaskResult(task, false, e.message ?: e.javaClass.simpleName)
            }
            results += result
            onProgress(result)
        }
        return results
    }

    private class StopTasks(message: String) : Exception(message)

    private fun ok(task: TaskItem, msg: String = "Hecho") = TaskResult(task, true, msg)

    private suspend fun execute(task: TaskItem): TaskResult {
        val v = task.values
        fun s(key: String) = v[key].orEmpty().trim()
        fun i(key: String, def: Int) = s(key).toIntOrNull() ?: def

        return when (task.type) {
            TaskType.VOLUME -> {
                val am = audio()
                val stream = when (s("stream")) {
                    "ring" -> AudioManager.STREAM_RING
                    "alarm" -> AudioManager.STREAM_ALARM
                    "notification" -> AudioManager.STREAM_NOTIFICATION
                    else -> AudioManager.STREAM_MUSIC
                }
                val max = am.getStreamMaxVolume(stream)
                am.setStreamVolume(stream, (i("level", 50).coerceIn(0, 100) * max + 50) / 100, AudioManager.FLAG_SHOW_UI)
                ok(task)
            }
            TaskType.SOUND_MODE -> {
                val mode = s("mode")
                if (mode == "silent" && !policyAccess()) return openPolicySettings(task)
                audio().ringerMode = when (mode) {
                    "vibrate" -> AudioManager.RINGER_MODE_VIBRATE
                    "silent" -> AudioManager.RINGER_MODE_SILENT
                    else -> AudioManager.RINGER_MODE_NORMAL
                }
                ok(task)
            }
            TaskType.DND -> {
                if (!policyAccess()) return openPolicySettings(task)
                notifications().setInterruptionFilter(
                    when (s("mode")) {
                        "none" -> NotificationManager.INTERRUPTION_FILTER_NONE
                        "off" -> NotificationManager.INTERRUPTION_FILTER_ALL
                        else -> NotificationManager.INTERRUPTION_FILTER_PRIORITY
                    }
                )
                ok(task)
            }
            TaskType.MEDIA -> {
                val code = when (s("action")) {
                    "next" -> KeyEvent.KEYCODE_MEDIA_NEXT
                    "previous" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
                    "stop" -> KeyEvent.KEYCODE_MEDIA_STOP
                    else -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                }
                val am = audio()
                val now = SystemClock.uptimeMillis()
                am.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0))
                am.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0))
                ok(task)
            }
            TaskType.BRIGHTNESS -> withWriteSettings(task) {
                Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, (i("level", 60).coerceIn(1, 100) * 255) / 100)
            }
            TaskType.AUTO_BRIGHTNESS -> withWriteSettings(task) {
                val key = Settings.System.SCREEN_BRIGHTNESS_MODE
                val current = Settings.System.getInt(context.contentResolver, key, 0) == 1
                Settings.System.putInt(context.contentResolver, key, if (toggleTarget(s("state"), current)) 1 else 0)
            }
            TaskType.AUTO_ROTATE -> withWriteSettings(task) {
                val key = Settings.System.ACCELEROMETER_ROTATION
                val current = Settings.System.getInt(context.contentResolver, key, 0) == 1
                Settings.System.putInt(context.contentResolver, key, if (toggleTarget(s("state"), current)) 1 else 0)
            }
            TaskType.SCREEN_TIMEOUT -> withWriteSettings(task) {
                Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, i("seconds", 30) * 1000)
            }
            TaskType.FLASHLIGHT -> {
                val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                val id = cm.cameraIdList.firstOrNull {
                    cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                } ?: return TaskResult(task, false, "Este teléfono no tiene flash")
                val target = toggleTarget(s("state"), NfcApp.torchState[id] == true)
                cm.setTorchMode(id, target)
                NfcApp.torchState[id] = target
                ok(task, if (target) "Linterna encendida" else "Linterna apagada")
            }
            TaskType.WIFI -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    start(Intent(Settings.Panel.ACTION_WIFI))
                    ok(task, "Android solo permite cambiar el Wi‑Fi desde su panel")
                } else {
                    @Suppress("DEPRECATION")
                    val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                    @Suppress("DEPRECATION")
                    wm.isWifiEnabled = toggleTarget(s("state"), wm.isWifiEnabled)
                    ok(task)
                }
            }
            TaskType.BLUETOOTH -> start(task, Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            TaskType.MOBILE_DATA -> start(
                task,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
                else Intent(Settings.ACTION_DATA_ROAMING_SETTINGS)
            )
            TaskType.AIRPLANE -> start(task, Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS))
            TaskType.OPEN_APP -> {
                val intent = context.packageManager.getLaunchIntentForPackage(s("package"))
                    ?: return TaskResult(task, false, "La app no está instalada")
                start(task, intent)
            }
            TaskType.OPEN_URL -> {
                val url = s("url").let { if (it.contains("://")) it else "https://$it" }
                start(task, Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
            TaskType.NAVIGATE -> {
                val q = Uri.encode(s("address"))
                val nav = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$q"))
                if (nav.resolveActivity(context.packageManager) != null) start(task, nav)
                else start(task, Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$q")))
            }
            TaskType.CALL -> start(task, Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(s("number")))))
            TaskType.SMS -> start(
                task,
                Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(s("number")))).putExtra("sms_body", s("body"))
            )
            TaskType.EMAIL -> start(
                task,
                Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + s("to")))
                    .putExtra(Intent.EXTRA_SUBJECT, s("subject"))
                    .putExtra(Intent.EXTRA_TEXT, s("body"))
            )
            TaskType.ALARM -> {
                val (h, m) = parseTime(s("time")) ?: return TaskResult(task, false, "Hora no válida")
                start(
                    task,
                    Intent(AlarmClock.ACTION_SET_ALARM)
                        .putExtra(AlarmClock.EXTRA_HOUR, h)
                        .putExtra(AlarmClock.EXTRA_MINUTES, m)
                        .putExtra(AlarmClock.EXTRA_MESSAGE, s("label"))
                        .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                )
            }
            TaskType.TIMER -> start(
                task,
                Intent(AlarmClock.ACTION_SET_TIMER)
                    .putExtra(AlarmClock.EXTRA_LENGTH, i("minutes", 5).coerceAtLeast(1) * 60)
                    .putExtra(AlarmClock.EXTRA_MESSAGE, s("label"))
                    .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            )
            TaskType.WAIT -> {
                delay(i("seconds", 2).coerceIn(0, 600) * 1000L)
                ok(task)
            }
            TaskType.SPEAK -> {
                speak(s("text"))
                ok(task)
            }
            TaskType.VIBRATE -> {
                val ms = i("ms", 400).coerceIn(10, 10_000).toLong()
                val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                }
                vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
                ok(task)
            }
            TaskType.MESSAGE -> {
                Toast.makeText(context, s("text"), Toast.LENGTH_LONG).show()
                ok(task)
            }
            TaskType.CLIPBOARD -> {
                val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cb.setPrimaryClip(ClipData.newPlainText("NFCPalletflow", s("text")))
                ok(task)
            }
            TaskType.IF_TIME -> {
                val from = parseTime(s("from")) ?: return TaskResult(task, false, "Hora no válida")
                val to = parseTime(s("to")) ?: return TaskResult(task, false, "Hora no válida")
                val c = Calendar.getInstance()
                val now = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
                val a = from.first * 60 + from.second
                val b = to.first * 60 + to.second
                val inside = if (a <= b) now in a..b else now >= a || now <= b
                if (!inside) throw StopTasks("Fuera del horario: se detienen las tareas siguientes")
                ok(task, "Dentro del horario")
            }
            TaskType.IF_DAYS -> {
                val d = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
                val match = when (s("days")) {
                    "weekend" -> d == Calendar.SATURDAY || d == Calendar.SUNDAY
                    "saturday" -> d != Calendar.SUNDAY
                    else -> d != Calendar.SATURDAY && d != Calendar.SUNDAY
                }
                if (!match) throw StopTasks("Hoy no corresponde: se detienen las tareas siguientes")
                ok(task, "Día válido")
            }
        }
    }

    private fun toggleTarget(state: String, current: Boolean) = when (state) {
        "on" -> true
        "off" -> false
        else -> !current
    }

    private fun parseTime(t: String): Pair<Int, Int>? {
        val parts = t.split(":")
        if (parts.size != 2) return null
        val h = parts[0].trim().toIntOrNull() ?: return null
        val m = parts[1].trim().toIntOrNull() ?: return null
        return if (h in 0..23 && m in 0..59) h to m else null
    }

    private fun audio() = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private fun notifications() = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private fun policyAccess() = notifications().isNotificationPolicyAccessGranted

    private fun openPolicySettings(task: TaskItem): TaskResult {
        start(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        return TaskResult(task, false, "Concede el acceso a «No molestar» y vuelve a intentarlo")
    }

    private inline fun withWriteSettings(task: TaskItem, block: () -> Unit): TaskResult {
        if (!Settings.System.canWrite(context)) {
            start(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + context.packageName)))
            return TaskResult(task, false, "Permite «Modificar ajustes del sistema» y vuelve a intentarlo")
        }
        block()
        return ok(task)
    }

    private fun start(intent: Intent) {
        if (context !is android.app.Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    private fun start(task: TaskItem, intent: Intent): TaskResult = try {
        start(intent)
        ok(task)
    } catch (e: ActivityNotFoundException) {
        TaskResult(task, false, "No hay ninguna app que pueda hacerlo")
    }

    private suspend fun speak(text: String) {
        if (text.isBlank()) return
        var tts: TextToSpeech? = null
        try {
            val ready = withTimeoutOrNull(5_000) {
                suspendCancellableCoroutine<Boolean> { cont ->
                    tts = TextToSpeech(context) { status -> if (cont.isActive) cont.resume(status == TextToSpeech.SUCCESS) }
                }
            } ?: false
            val engine = tts ?: return
            if (!ready) return
            engine.language = Locale("es")
            withTimeoutOrNull(60_000) {
                suspendCancellableCoroutine<Unit> { cont ->
                    engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {}
                        override fun onDone(utteranceId: String?) { if (cont.isActive) cont.resume(Unit) }
                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) { if (cont.isActive) cont.resume(Unit) }
                    })
                    engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "nfcp")
                }
            }
        } finally {
            tts?.shutdown()
        }
    }
}
