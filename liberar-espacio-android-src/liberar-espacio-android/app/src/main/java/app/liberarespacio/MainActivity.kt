package app.liberarespacio

import android.Manifest
import android.content.ContentUris
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.webkit.WebViewAssetLoader
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Muestra assets/index.html en un WebView y expone AndroidStorage al JavaScript.
 *
 * Flujo:
 *  - JS llama AndroidStorage.listMedia(callId) o AndroidStorage.trash(callId, idsJson).
 *  - Native responde con window.__storageResolve(callId, ok, payloadTexto).
 *  - Las fotos se sirven por https://appassets.androidplatform.net/media/<id>,
 *    así el HTML puede leerlas con fetch() sin rutas de archivo.
 */
class MainActivity : ComponentActivity() {

    companion object {
        private const val DOMAIN = "appassets.androidplatform.net"
        private const val VISUAL_USER_SELECTED = "android.permission.READ_MEDIA_VISUAL_USER_SELECTED"
    }

    private lateinit var webView: WebView
    private lateinit var assetLoader: WebViewAssetLoader

    /** Medios del último listado: id de JS ("i123" para imagen, "v45" para video) -> Uri de MediaStore. */
    private val knownUris = ConcurrentHashMap<String, Uri>()

    private var pendingListCallId: String? = null
    private var pendingTrash: Pair<String, List<String>>? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        val callId = pendingListCallId ?: return@registerForActivityResult
        pendingListCallId = null
        if (hasMediaAccess()) {
            Thread { queryMedia(callId) }.start()
        } else {
            respond(callId, false, "Permiso denegado para leer fotos y videos")
        }
    }

    private val trashLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val (callId, ids) = pendingTrash ?: return@registerForActivityResult
        pendingTrash = null
        val trashed = if (result.resultCode == RESULT_OK) ids else emptyList()
        respond(callId, true, JSONObject().put("trashed", JSONArray(trashed)).toString())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawable(ColorDrawable(Color.BLACK))

        assetLoader = WebViewAssetLoader.Builder()
            .setDomain(DOMAIN)
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .addPathHandler("/media/", MediaPathHandler())
            .build()

        webView = WebView(this).apply {
            setBackgroundColor(Color.BLACK)
            settings.javaScriptEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest
                ): WebResourceResponse? = assetLoader.shouldInterceptRequest(request.url)
            }
            addJavascriptInterface(StorageInterface(), "AndroidStorage")
        }
        setContentView(webView)
        webView.loadUrl("https://$DOMAIN/assets/index.html")
    }

    /* ---------- Interfaz expuesta al JavaScript (hilo del WebView) ---------- */

    inner class StorageInterface {

        @JavascriptInterface
        fun listMedia(callId: String) {
            runOnUiThread {
                if (hasMediaAccess()) {
                    Thread { queryMedia(callId) }.start()
                } else if (pendingListCallId != null) {
                    respond(callId, false, "Ya hay una solicitud de permiso en curso")
                } else {
                    pendingListCallId = callId
                    permissionLauncher.launch(mediaPermissions())
                }
            }
        }

        @JavascriptInterface
        fun trash(callId: String, idsJson: String) {
            val ids = try {
                JSONArray(idsJson).let { arr -> List(arr.length()) { arr.getString(it) } }
            } catch (e: Exception) {
                respond(callId, false, "Lista de ids no válida")
                return
            }
            runOnUiThread { requestTrash(callId, ids) }
        }
    }

    /* ---------- Permisos ---------- */

    private fun mediaPermissions(): Array<String> = when {
        Build.VERSION.SDK_INT >= 34 -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            VISUAL_USER_SELECTED
        )
        Build.VERSION.SDK_INT >= 33 -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO
        )
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    /** Con acceso parcial (Android 14+) basta con que alguno esté concedido; la consulta solo verá lo elegido. */
    private fun hasMediaAccess(): Boolean = mediaPermissions().any {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    /* ---------- Lectura de MediaStore ---------- */

    private fun queryMedia(callId: String) {
        try {
            knownUris.clear()
            val out = JSONArray()
            readCollection(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "i", false, out)
            readCollection(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, "v", true, out)
            respond(callId, true, out.toString())
        } catch (e: Exception) {
            respond(callId, false, "No se pudo leer la galería: ${e.message}")
        }
    }

    private fun readCollection(collection: Uri, prefix: String, isVideo: Boolean, out: JSONArray) {
        val projection = arrayListOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT
        )
        if (isVideo) projection.add(MediaStore.Video.VideoColumns.DURATION)

        contentResolver.query(
            collection,
            projection.toTypedArray(),
            null,
            null,
            "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val sizeCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val mimeCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
            val dateCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
            val wCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH)
            val hCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT)
            val durCol = if (isVideo) c.getColumnIndexOrThrow(MediaStore.Video.VideoColumns.DURATION) else -1

            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val key = prefix + id
                knownUris[key] = ContentUris.withAppendedId(collection, id)
                out.put(JSONObject().apply {
                    put("id", key)
                    put("name", c.getString(nameCol) ?: "Sin nombre")
                    put("size", c.getLong(sizeCol))
                    put("mime", c.getString(mimeCol) ?: "")
                    put("dateModified", c.getLong(dateCol) * 1000L) // MediaStore usa segundos
                    put("width", c.getInt(wCol))
                    put("height", c.getInt(hCol))
                    if (isVideo) put("duration", c.getLong(durCol) / 1000.0) // MediaStore usa milisegundos
                    put("url", "https://$DOMAIN/media/$key")
                })
            }
        }
    }

    /** Sirve el contenido de un medio autorizado en el último listado. */
    private inner class MediaPathHandler : WebViewAssetLoader.PathHandler {
        override fun handle(path: String): WebResourceResponse? {
            val uri = knownUris[path] ?: return null
            return try {
                val stream = contentResolver.openInputStream(uri) ?: return null
                WebResourceResponse(
                    contentResolver.getType(uri) ?: "application/octet-stream",
                    null,
                    stream
                )
            } catch (e: Exception) {
                null
            }
        }
    }

    /* ---------- Papelera del sistema ---------- */

    /** Muestra la confirmación del sistema; el resultado llega por trashLauncher. */
    private fun requestTrash(callId: String, ids: List<String>) {
        val pairs = ids.mapNotNull { id -> knownUris[id]?.let { id to it } }
        if (pairs.isEmpty()) {
            respond(callId, true, JSONObject().put("trashed", JSONArray()).toString())
            return
        }
        if (pendingTrash != null) {
            respond(callId, false, "Ya hay una confirmación de papelera en curso")
            return
        }
        try {
            val uris: Collection<Uri> = pairs.map { it.second }
            val intentSender = MediaStore.createTrashRequest(contentResolver, uris, true).intentSender
            pendingTrash = callId to pairs.map { it.first }
            trashLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
        } catch (e: Exception) {
            pendingTrash = null
            respond(callId, false, "No se pudo mover a la papelera: ${e.message}")
        }
    }

    /* ---------- Respuesta a JavaScript ---------- */

    private fun respond(callId: String, ok: Boolean, payload: String) {
        runOnUiThread {
            webView.evaluateJavascript(
                "window.__storageResolve(${JSONObject.quote(callId)}, $ok, ${JSONObject.quote(payload)})",
                null
            )
        }
    }
}
