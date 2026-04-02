package cl.machukeitor.siirol

import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import kotlinx.coroutines.*
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class TgrActivity : AppCompatActivity() {

    private lateinit var progressBar: ProgressBar
    private lateinit var tvEstado: TextView
    private lateinit var btnAbrir: Button
    private lateinit var btnCompartir: Button
    private lateinit var btnReintentar: Button
    private lateinit var btnVolver: Button

    private var pdfFile: File? = null
    private var _pdfUri: Uri? = null

    private var region: String = ""
    private var comuna: String = ""
    private var rol: String = ""
    private var subRol: String = ""
    private var rolCompleto: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tgr)

        region      = intent.getStringExtra("region")      ?: ""
        comuna      = intent.getStringExtra("comuna")      ?: ""
        rol         = intent.getStringExtra("rol")         ?: ""
        subRol      = intent.getStringExtra("subRol")      ?: ""
        rolCompleto = intent.getStringExtra("rolCompleto") ?: "$rol-$subRol"
        val direccion = intent.getStringExtra("direccion") ?: ""

        progressBar  = findViewById(R.id.tgrProgressBar)
        tvEstado     = findViewById(R.id.tvEstado)
        btnAbrir     = findViewById(R.id.btnAbrirPdf)
        btnCompartir = findViewById(R.id.btnCompartir)
        btnReintentar= findViewById(R.id.btnReintentar)
        btnVolver    = findViewById(R.id.btnVolver)

        findViewById<TextView>(R.id.tvTgrInfo).text = "ROL $rolCompleto — $direccion"

        btnVolver.setOnClickListener     { finish() }
        btnAbrir.setOnClickListener      { abrirPdf() }
        btnCompartir.setOnClickListener  { compartirPdf() }
        btnReintentar.setOnClickListener { descargarCertificado() }

        descargarCertificado()
    }

    private fun descargarCertificado() {
        progressBar.visibility   = View.VISIBLE
        btnAbrir.visibility      = View.GONE
        btnCompartir.visibility  = View.GONE
        btnReintentar.visibility = View.GONE
        tvEstado.text            = "⏳ Conectando con TGR..."

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val cookies = obtenerSesion()
                withContext(Dispatchers.Main) { tvEstado.text = "⏳ Generando certificado PDF..." }

                val html = obtenerCertificadoHtml(cookies)
                val b64  = extraerBase64Pdf(html)
                    ?: throw Exception("El servidor no devolvió el PDF.\nVerifica que el ROL exista en TGR.")

                val bytes   = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
                val nombre  = "CertTGR_${rolCompleto.replace("-", "_")}.pdf"
                val archivo = guardarEnDescargas(bytes, nombre)
                pdfFile = archivo

                withContext(Dispatchers.Main) {
                    progressBar.visibility   = View.GONE
                    tvEstado.text = "✅ Certificado guardado en:\nDescargas/roles de propiedad/${archivo.name}"
                    btnAbrir.visibility      = View.VISIBLE
                    btnCompartir.visibility  = View.VISIBLE
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility   = View.GONE
                    tvEstado.text            = "❌ ${e.message}"
                    btnReintentar.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun obtenerSesion(): String {
        val body  = "region=$region&comuna=$comuna&rol=$rol&subRol=$subRol&g-recaptcha-response="
        val bytes = body.toByteArray(Charsets.UTF_8)
        val conn  = (URL(BEGIN_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type",   "application/x-www-form-urlencoded")
            setRequestProperty("Content-Length",  bytes.size.toString())
            setRequestProperty("User-Agent",      UA)
            setRequestProperty("Referer",         REFERER)
            instanceFollowRedirects = true
            connectTimeout = 15000; readTimeout = 15000; doOutput = true
        }
        conn.outputStream.use { it.write(bytes) }
        conn.responseCode
        val cookies = conn.headerFields["Set-Cookie"]
            ?.joinToString("; ") { it.substringBefore(";") } ?: ""
        conn.disconnect()
        return cookies
    }

    private fun obtenerCertificadoHtml(cookies: String): String {
        val body  = "region=$region&comuna=$comuna&rol=$rol&subRol=$subRol&g-recaptcha-response="
        val bytes = body.toByteArray(Charsets.UTF_8)
        val conn  = (URL(CERT_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type",   "application/x-www-form-urlencoded")
            setRequestProperty("Content-Length",  bytes.size.toString())
            setRequestProperty("User-Agent",      UA)
            setRequestProperty("Referer",         BEGIN_URL)
            setRequestProperty("Cookie",          cookies)
            instanceFollowRedirects = true
            connectTimeout = 15000; readTimeout = 30000; doOutput = true
        }
        conn.outputStream.use { it.write(bytes) }
        val code = conn.responseCode
        val html = conn.inputStream.bufferedReader(Charsets.ISO_8859_1).readText()
        conn.disconnect()
        if (code != 200) throw Exception("Error HTTP $code al obtener certificado")
        return html
    }

    private fun extraerBase64Pdf(html: String): String? {
        val marker = "data:application/pdf;base64,"
        val start  = html.indexOf(marker)
        if (start == -1) return null
        val raw = html.substring(start + marker.length)
        return raw.takeWhile { c -> c.isLetterOrDigit() || c == '+' || c == '/' || c == '=' }
            .ifEmpty { null }
    }

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

    private fun getPdfUri(): Uri? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && _pdfUri != null) {
            _pdfUri
        } else {
            val file = pdfFile ?: return null
            FileProvider.getUriForFile(this, "$packageName.provider", file)
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
            Toast.makeText(this, "Instala un lector de PDF para abrirlo", Toast.LENGTH_LONG).show()
        }
    }

    private fun compartirPdf() {
        val uri = getPdfUri() ?: return
        try {
            startActivity(Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type  = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "Certificado TGR — ROL $rolCompleto")
                    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                },
                "Compartir certificado TGR"
            ))
        } catch (e: Exception) {
            Toast.makeText(this, "No se pudo compartir el archivo", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private const val BEGIN_URL = "https://www.tesoreria.cl/CertDeudasRolCutAixWeb/begin.do"
        private const val CERT_URL  = "https://www.tesoreria.cl/CertDeudasRolCutAixWeb/TraerCertificadoDeudasAction.do"
        private const val REFERER   = "https://www.tgr.cl/tramites-tgr/certificado-de-deuda-de-contribuciones/"
        private const val UA        = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
    }
}
