package cl.machukeitor.siirol

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.content.ContentValues
import android.os.Build
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
    private lateinit var btnVolver: Button

    private var pdfFile: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tgr)

        val region      = intent.getStringExtra("region")      ?: ""
        val comuna      = intent.getStringExtra("comuna")      ?: ""
        val rol         = intent.getStringExtra("rol")         ?: ""
        val subRol      = intent.getStringExtra("subRol")      ?: ""
        val rolCompleto = intent.getStringExtra("rolCompleto") ?: "$rol-$subRol"
        val direccion   = intent.getStringExtra("direccion")   ?: ""

        progressBar = findViewById(R.id.tgrProgressBar)
        tvEstado    = findViewById(R.id.tvEstado)
        btnAbrir    = findViewById(R.id.btnAbrirPdf)
        btnVolver   = findViewById(R.id.btnVolver)

        findViewById<TextView>(R.id.tvTgrInfo).text = "ROL $rolCompleto — $direccion"

        btnVolver.setOnClickListener { finish() }
        btnAbrir.setOnClickListener  { abrirPdf() }

        descargarCertificado(region, comuna, rol, subRol, rolCompleto)
    }

    private fun descargarCertificado(
        region: String, comuna: String,
        rol: String, subRol: String,
        rolCompleto: String
    ) {
        progressBar.visibility = View.VISIBLE
        btnAbrir.visibility    = View.GONE
        tvEstado.text          = "⏳ Conectando con TGR..."

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 1. Sesión en begin.do
                val cookies = obtenerSesion(region, comuna, rol, subRol)

                withContext(Dispatchers.Main) { tvEstado.text = "⏳ Generando certificado PDF..." }

                // 2. HTML con PDF embebido en base64
                val html = obtenerCertificadoHtml(region, comuna, rol, subRol, cookies)

                // 3. Extraer base64
                val b64 = extraerBase64Pdf(html)
                    ?: throw Exception("El servidor no devolvió el PDF.\nVerifica que el ROL exista en TGR.")

                // 4. Decodificar y guardar en Descargas/roles de propiedad/
                val bytes    = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
                val nombre   = "CertTGR_${rolCompleto.replace("-","_")}.pdf"
                val archivo  = guardarEnDescargas(bytes, nombre)
                pdfFile = archivo

                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    tvEstado.text          = "✅ Certificado guardado en:\nDescargas/roles de propiedad/${archivo.name}"
                    btnAbrir.visibility    = View.VISIBLE
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    tvEstado.text          = "❌ ${e.message}"
                }
            }
        }
    }

    private fun obtenerSesion(region: String, comuna: String, rol: String, subRol: String): String {
        val body  = "region=$region&comuna=$comuna&rol=$rol&subRol=$subRol&g-recaptcha-response="
        val bytes = body.toByteArray(Charsets.UTF_8)
        val conn  = (URL(BEGIN_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type",  "application/x-www-form-urlencoded")
            setRequestProperty("Content-Length", bytes.size.toString())
            setRequestProperty("User-Agent",     UA)
            setRequestProperty("Referer",        REFERER)
            instanceFollowRedirects = true
            connectTimeout = 15000
            readTimeout    = 15000
            doOutput       = true
        }
        conn.outputStream.use { it.write(bytes) }
        conn.responseCode
        val cookies = conn.headerFields["Set-Cookie"]
            ?.joinToString("; ") { it.substringBefore(";") } ?: ""
        conn.disconnect()
        return cookies
    }

    private fun obtenerCertificadoHtml(
        region: String, comuna: String,
        rol: String, subRol: String,
        cookies: String
    ): String {
        val body  = "region=$region&comuna=$comuna&rol=$rol&subRol=$subRol&g-recaptcha-response="
        val bytes = body.toByteArray(Charsets.UTF_8)
        val conn  = (URL(CERT_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type",  "application/x-www-form-urlencoded")
            setRequestProperty("Content-Length", bytes.size.toString())
            setRequestProperty("User-Agent",     UA)
            setRequestProperty("Referer",        BEGIN_URL)
            setRequestProperty("Cookie",         cookies)
            instanceFollowRedirects = true
            connectTimeout = 15000
            readTimeout    = 30000
            doOutput       = true
        }
        conn.outputStream.use { it.write(bytes) }
        val code = conn.responseCode
        val html = conn.inputStream.bufferedReader(Charsets.ISO_8859_1).readText()
        conn.disconnect()
        if (code != 200) throw Exception("Error HTTP $code al obtener certificado")
        return html
    }

    /**
     * Guarda el PDF en Descargas/roles de propiedad/
     * - Android 10+ (API 29+): usa MediaStore (no requiere permisos)
     * - Android 9 e inferior: escribe directamente en el sistema de archivos
     */
    private fun guardarEnDescargas(bytes: ByteArray, nombre: String): File {
        val subcarpeta = "roles de propiedad"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+: MediaStore sin permiso de almacenamiento
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME,   nombre)
                put(MediaStore.Downloads.MIME_TYPE,      "application/pdf")
                put(MediaStore.Downloads.RELATIVE_PATH,  "Download/$subcarpeta")
                put(MediaStore.Downloads.IS_PENDING,     1)
            }
            val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw Exception("No se pudo crear el archivo en Descargas")

            contentResolver.openOutputStream(uri)?.use { it.write(bytes) }

            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            contentResolver.update(uri, values, null, null)

            // Retornar referencia al archivo para poder abrirlo después
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                subcarpeta
            )
            return File(dir, nombre).also { _pdfUri = uri }
        } else {
            // Android 9 e inferior
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                subcarpeta
            )
            dir.mkdirs()
            val archivo = File(dir, nombre)
            archivo.writeBytes(bytes)
            return archivo
        }
    }

    // URI de MediaStore para abrir el PDF en Android 10+
    private var _pdfUri: android.net.Uri? = null

    private fun extraerBase64Pdf(html: String): String? {
        val marker = "data:application/pdf;base64,"
        val start  = html.indexOf(marker)
        if (start == -1) return null
        val desde  = start + marker.length
        val raw    = html.substring(desde)
        return raw.takeWhile { c -> c.isLetterOrDigit() || c == '+' || c == '/' || c == '=' }
            .ifEmpty { null }
    }

    private fun abrirPdf() {
        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && _pdfUri != null) {
            _pdfUri!!   // Android 10+: URI de MediaStore
        } else {
            val file = pdfFile ?: return
            FileProvider.getUriForFile(this, "$packageName.provider", file)
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "PDF guardado en Descargas/roles de propiedad/\nInstala un lector de PDF para abrirlo",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    companion object {
        private const val BEGIN_URL = "https://www.tesoreria.cl/CertDeudasRolCutAixWeb/begin.do"
        private const val CERT_URL  = "https://www.tesoreria.cl/CertDeudasRolCutAixWeb/TraerCertificadoDeudasAction.do"
        private const val REFERER   = "https://www.tgr.cl/tramites-tgr/certificado-de-deuda-de-contribuciones/"
        private const val UA        = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
    }
}
