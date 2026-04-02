package cl.machukeitor.siirol

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebStorage
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class SiiCertificadoActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvEstado: TextView
    private lateinit var tvRolInfo: TextView
    private lateinit var tvInstruccion: TextView
    private lateinit var btnVolver: Button
    private lateinit var btnCompartir: Button
    private lateinit var btnReintentar: Button

    private var comunaCnp: Int = 0
    private var manzana: Int = 0
    private var predio: Int = 0
    private var ultimoEacAplicado: Int = 0
    private var rolCompleto: String = ""
    private var direccion: String = ""

    private lateinit var loadingOverlay: android.widget.LinearLayout
    private val sessionHandler = Handler(Looper.getMainLooper())
    private val sessionTimeout = Runnable { cerrarSesionPorTimeout() }
    private var descargaIniciada = false
    private var rutGuardado: String = ""
    private var cookiesGuardadas: String = ""
    private var pdfFile: File? = null
    private var _pdfUri: Uri? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sii_certificado)

        comunaCnp         = intent.getIntExtra("comunaCnp", 0)
        manzana           = intent.getIntExtra("manzana", 0)
        predio            = intent.getIntExtra("predio", 0)
        ultimoEacAplicado = intent.getIntExtra("ultimoEacAplicado", 0)
        rolCompleto       = intent.getStringExtra("rolCompleto") ?: ""
        direccion         = intent.getStringExtra("direccion")   ?: ""

        webView       = findViewById(R.id.siiWebView)
        progressBar   = findViewById(R.id.siiProgressBar)
        tvEstado      = findViewById(R.id.tvSiiEstado)
        tvRolInfo     = findViewById(R.id.tvSiiRolInfo)
        tvInstruccion = findViewById(R.id.tvSiiInstruccion)
        loadingOverlay = findViewById(R.id.siiLoadingOverlay)
        btnVolver     = findViewById(R.id.btnVolverSii)
        btnCompartir  = findViewById(R.id.btnCompartirSii)
        btnReintentar = findViewById(R.id.btnReintentarSii)

        tvRolInfo.text     = "ROL $rolCompleto — $direccion"
        tvInstruccion.text = "Inicia sesión con tu Clave Tributaria"

        btnVolver.setOnClickListener    { finish() }
        btnCompartir.setOnClickListener { compartirPdf() }
        btnReintentar.setOnClickListener {
            sessionHandler.removeCallbacks(sessionTimeout)
            descargaIniciada         = false
            rutGuardado              = ""
            cookiesGuardadas         = ""
            btnReintentar.visibility = View.GONE
            btnCompartir.visibility  = View.GONE
            tvEstado.visibility      = View.GONE
            findViewById<android.widget.ScrollView>(R.id.scrollDescarga).visibility = View.GONE
            webView.visibility       = View.VISIBLE
            // Limpiar sesión y recargar login desde cero
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
            WebStorage.getInstance().deleteAllData()
            webView.clearCache(true)
            webView.clearHistory()
            webView.loadUrl(LOGIN_URL)
        }

        setupWebView()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        // Limpiar sesión anterior siempre: login fresco en cada apertura
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        WebStorage.getInstance().deleteAllData()
        webView.clearCache(true)
        webView.clearHistory()

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        with(webView.settings) {
            javaScriptEnabled   = true
            domStorageEnabled   = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            userAgentString     = UA
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                if (webView.visibility == View.VISIBLE) {
                    runOnUiThread { loadingOverlay.visibility = View.VISIBLE }
                }
            }
            override fun onPageFinished(view: WebView, url: String) {
                runOnUiThread { loadingOverlay.visibility = View.GONE }

                // Solo actuar cuando el usuario llega a la página VICA real (post-login exitoso)
                if (!url.contains("/vica/Menu/ConsultarAntecedentesSC")) return
                if (descargaIniciada) return

                // Verificar que la sesión sea válida (userId en localStorage)
                view.evaluateJavascript(
                    "(function(){ var u=localStorage.getItem('userId'); return (u&&u!='null'&&u!='undefined') ? u : ''; })()"
                ) { result ->
                    val rut = result?.trim()?.removeSurrounding("\"") ?: ""
                    if (rut.isBlank()) return@evaluateJavascript

                    descargaIniciada = true
                    val cookies = CookieManager.getInstance().getCookie("https://www2.sii.cl") ?: ""
                    rutGuardado      = rut
                    cookiesGuardadas = cookies

                    runOnUiThread {
                        webView.visibility    = View.GONE
                        loadingOverlay.visibility = View.GONE
                        tvInstruccion.text    = "✅ Sesión SII activa (expira en 5 min)"
                        findViewById<android.widget.ScrollView>(R.id.scrollDescarga).visibility = View.VISIBLE
                        // Iniciar timer de 5 minutos
                        sessionHandler.removeCallbacks(sessionTimeout)
                        sessionHandler.postDelayed(sessionTimeout, 5 * 60 * 1000L)
                    }
                    lanzarDescarga(rut, cookies)
                }
            }
        }

        webView.loadUrl(LOGIN_URL)
    }

    // ── Descarga ─────────────────────────────────────────────────────────────

    private fun lanzarDescarga(rut: String, cookies: String) {
        runOnUiThread {
            progressBar.visibility   = View.VISIBLE
            tvEstado.visibility      = View.VISIBLE
            tvEstado.text            = "⏳ Consultando propiedad..."
            btnCompartir.visibility  = View.GONE
            btnReintentar.visibility = View.GONE
        }
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val eac = obtenerUltimoEac(rut, cookies)
                withContext(Dispatchers.Main) { tvEstado.text = "⏳ Generando certificado..." }
                val b64     = obtenerCertificadoBase64(rut, cookies, eac)
                val bytes   = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
                val nombre  = "CertSII_${rolCompleto.replace("-", "_")}.pdf"
                val archivo = guardarEnDescargas(bytes, nombre)
                pdfFile = archivo
                withContext(Dispatchers.Main) {
                    progressBar.visibility  = View.GONE
                    tvEstado.text = "✅ Guardado en:\nDescargas/roles de propiedad/${archivo.name}"
                    btnCompartir.visibility = View.VISIBLE
                    Toast.makeText(
                        this@SiiCertificadoActivity,
                        "✅ PDF guardado en Descargas/roles de propiedad/",
                        Toast.LENGTH_LONG
                    ).show()
                    abrirPdf()
                }
            } catch (e: Exception) {
                val sesionExpirada = e.message?.contains("401") == true
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    tvEstado.text            = if (sesionExpirada) "❌ Sesión expirada. Presiona Reintentar para volver a ingresar." else "❌ ${e.message}"
                    btnReintentar.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun getPdfUri(): Uri? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && _pdfUri != null) _pdfUri
        else pdfFile?.let { FileProvider.getUriForFile(this, "$packageName.provider", it) }

    private fun compartirPdf() {
        val uri = getPdfUri() ?: return
        try {
            startActivity(Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type  = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "Certificado SII — ROL $rolCompleto")
                    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                },
                "Compartir certificado SII"
            ))
        } catch (e: Exception) {
            Toast.makeText(this, "No se pudo compartir el archivo", Toast.LENGTH_SHORT).show()
        }
    }

    // ── API ──────────────────────────────────────────────────────────────────

    /** Llama a mis-bbrr para obtener el ultimoEacAplicado real desde VICA */
    private fun obtenerUltimoEac(rut: String, cookies: String): Int {
        val body  = JSONObject().apply {
            put("comunaCnp",   comunaCnp)
            put("manzanaCnp",  manzana)
            put("predioCnp",   predio)
        }.toString()
        val bytes = body.toByteArray(Charsets.UTF_8)
        val conn  = (URL("https://www2.sii.cl/app/vica/$rut/v1/mis-bbrr/obtener/by-rol-sc")
            .openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type",  "application/json")
            setRequestProperty("Accept",        "application/json, text/plain, */*")
            setRequestProperty("Origin",        "https://www2.sii.cl")
            setRequestProperty("Referer",       "https://www2.sii.cl/vica/Menu/ConsultarAntecedentesSC")
            setRequestProperty("User-Agent",    UA)
            setRequestProperty("Cookie",        cookies)
            doOutput = true; connectTimeout = 20000; readTimeout = 20000
        }
        conn.outputStream.use { it.write(bytes) }
        val code = conn.responseCode
        if (code != HttpURLConnection.HTTP_OK) { conn.disconnect(); return ultimoEacAplicado }
        val resp = conn.inputStream.bufferedReader().readText()
        conn.disconnect()
        // Respuesta es un array JSON; tomar el primer elemento
        return try {
            val arr = org.json.JSONArray(resp)
            if (arr.length() > 0) arr.getJSONObject(0).optInt("ultimoEacAplicado", ultimoEacAplicado)
            else ultimoEacAplicado
        } catch (e: Exception) { ultimoEacAplicado }
    }

    private fun obtenerCertificadoBase64(rut: String, cookies: String, eac: Int): String {
        val body = JSONObject().apply {
            put("tipoDocumento",      "7")
            put("tipoSolicitante",    "1")
            put("motivo",             "0")
            put("institucionReceptor","0")
            put("tipoSolicitud",      1)
            put("bienesRaices", JSONArray().apply {
                put(JSONObject().apply {
                    put("comunaCnp",        comunaCnp)
                    put("manzanaCnp",       manzana)
                    put("predioCnp",        predio)
                    put("ultimoEacAplicado",eac)
                })
            })
        }.toString()

        val bytes = body.toByteArray(Charsets.UTF_8)
        val certUrl = "https://www2.sii.cl/app/vica/$rut/v1/cert-antecedentes/post/terceros-sc"

        val conn = (URL(certUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type",  "application/json")
            setRequestProperty("Accept",        "application/json, text/plain, */*")
            setRequestProperty("Origin",        "https://www2.sii.cl")
            setRequestProperty("Referer",       "https://www2.sii.cl/vica/Menu/ConsultarAntecedentesSC")
            setRequestProperty("User-Agent",    UA)
            setRequestProperty("Cookie",        cookies)
            doOutput       = true
            connectTimeout = 30000
            readTimeout    = 60000
        }
        conn.outputStream.use { it.write(bytes) }

        val code = conn.responseCode
        if (code != HttpURLConnection.HTTP_OK) {
            val err = conn.errorStream?.bufferedReader()?.readText()?.take(200) ?: ""
            conn.disconnect()
            throw Exception("HTTP $code — $err")
        }
        val response = conn.inputStream.bufferedReader().readText().trim()
        conn.disconnect()
        return response
    }

    // ── Guardar / Abrir PDF ──────────────────────────────────────────────────

    private fun guardarEnDescargas(bytes: ByteArray, nombre: String): File {
        val sub = "roles de propiedad"
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME,  nombre)
                put(MediaStore.Downloads.MIME_TYPE,     "application/pdf")
                put(MediaStore.Downloads.RELATIVE_PATH, "Download/$sub")
                put(MediaStore.Downloads.IS_PENDING,    1)
            }
            val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw Exception("No se pudo crear el archivo en Descargas")
            contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
            values.clear(); values.put(MediaStore.Downloads.IS_PENDING, 0)
            contentResolver.update(uri, values, null, null)
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), sub
            )
            File(dir, nombre).also { _pdfUri = uri }
        } else {
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), sub
            )
            dir.mkdirs()
            File(dir, nombre).also { it.writeBytes(bytes) }
        }
    }

    private fun abrirPdf() {
        val uri = getPdfUri() ?: return
        try {
            startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
            })
        } catch (e: Exception) {
            Toast.makeText(this, "PDF guardado en Descargas/roles de propiedad/", Toast.LENGTH_LONG).show()
        }
    }

    private fun cerrarSesionPorTimeout() {
        descargaIniciada = false
        rutGuardado      = ""
        cookiesGuardadas = ""
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        WebStorage.getInstance().deleteAllData()
        webView.clearCache(true)
        webView.clearHistory()
        // Ocultar panel de descarga y volver al login
        findViewById<android.widget.ScrollView>(R.id.scrollDescarga).visibility = View.GONE
        tvEstado.visibility      = View.GONE
        btnCompartir.visibility  = View.GONE
        btnReintentar.visibility = View.GONE
        tvInstruccion.text       = "Sesión expirada. Inicia sesión nuevamente."
        webView.visibility       = View.VISIBLE
        webView.loadUrl(LOGIN_URL)
    }

    override fun onDestroy() {
        super.onDestroy()
        sessionHandler.removeCallbacks(sessionTimeout)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView.visibility == View.VISIBLE && webView.canGoBack()) webView.goBack()
        else super.onBackPressed()
    }

    companion object {
        private const val LOGIN_URL =
            "https://www2.sii.cl/bifurcacion/?originalUrl=https://www2.sii.cl/vica/Menu/ConsultarAntecedentesSC&type=CT"
        private const val UA =
            "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    }
}
