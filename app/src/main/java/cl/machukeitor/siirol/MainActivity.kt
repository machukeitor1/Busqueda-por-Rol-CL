package cl.machukeitor.siirol

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

class MainActivity : AppCompatActivity() {

    data class Comuna(
        val codigoSii: String,
        val nombre: String,
        val nombreApi: String,
        val tgrRegion: String,
        val tgrComuna: String
    )

    private val comunas = listOf(
        Comuna("8101", "Chillán",     "CHILLÁN",     "16", "168"),
        Comuna("8401", "Los Ángeles", "LOS ANGELES", "8",  "204"),
        Comuna("9201", "Temuco",      "TEMUCO",      "9",  "227")
    )

    private val API_URL = "https://www4.sii.cl/mapasui/services/data/mapasFacadeService/getPrediosDireccion"

    private lateinit var spinnerComuna: Spinner
    private lateinit var etCalle: EditText
    private lateinit var etNumero: EditText
    private lateinit var btnBuscar: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvResultado: TextView
    private lateinit var cardResultado: View
    private lateinit var containerResultados: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        spinnerComuna       = findViewById(R.id.spinnerComuna)
        etCalle             = findViewById(R.id.etCalle)
        etNumero            = findViewById(R.id.etNumero)
        btnBuscar           = findViewById(R.id.btnBuscar)
        progressBar         = findViewById(R.id.progressBar)
        tvResultado         = findViewById(R.id.tvResultado)
        cardResultado       = findViewById(R.id.cardResultado)
        containerResultados = findViewById(R.id.containerResultados)

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, comunas.map { it.nombre })
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerComuna.adapter = adapter

        btnBuscar.setOnClickListener { buscarRol() }
    }

    private fun abrirTgr(predio: PredioResult, comuna: Comuna) {
        val partes = predio.rol.split("-")
        startActivity(Intent(this, TgrActivity::class.java).apply {
            putExtra("region",      comuna.tgrRegion)
            putExtra("comuna",      comuna.tgrComuna)
            putExtra("rol",         partes.getOrNull(0) ?: "")
            putExtra("subRol",      partes.getOrNull(1) ?: "")
            putExtra("rolCompleto", predio.rol)
            putExtra("direccion",   predio.direccion)
        })
    }

    private fun buscarRol() {
        val calle  = etCalle.text.toString().trim()
        val numero = etNumero.text.toString().trim()

        if (calle.isEmpty())  { etCalle.error  = "Ingresa el nombre de la calle"; return }
        if (numero.isEmpty()) { etNumero.error = "Ingresa el número"; return }

        val comuna = comunas[spinnerComuna.selectedItemPosition]

        progressBar.visibility   = View.VISIBLE
        cardResultado.visibility = View.GONE
        btnBuscar.isEnabled      = false

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val predios = consultarAPI(comuna, calle, numero)
                withContext(Dispatchers.Main) { mostrarResultado(predios) }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { mostrarError(e.message ?: "Error desconocido") }
            } finally {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    btnBuscar.isEnabled    = true
                }
            }
        }
    }

    private fun consultarAPI(comuna: Comuna, calle: String, numero: String): List<PredioResult> {
        val body = JSONObject().apply {
            put("metaData", JSONObject().apply {
                put("namespace",      "cl.sii.sdi.lob.bbrr.mapas.data.api.interfaces.MapasFacadeService/getPrediosDireccion")
                put("conversationId", "UNAUTHENTICATED-CALL")
                put("transactionId",  UUID.randomUUID().toString())
            })
            put("data", JSONObject().apply {
                put("rolDireccion", JSONObject().apply {
                    put("comuna",         comuna.codigoSii)
                    put("nombreComuna",   comuna.nombreApi)
                    put("calle",          calle.lowercase().trim())
                    put("numeroCalleStr", numero.trim())
                    put("detalle",        0)
                })
                put("servicios", JSONArray())
            })
        }.toString()

        val bytes = body.toByteArray(Charsets.UTF_8)
        val conn  = (URL(API_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type",     "application/json")
            setRequestProperty("Content-Length",   bytes.size.toString())
            setRequestProperty("Accept",           "application/json, text/plain, */*")
            setRequestProperty("Origin",           "https://www4.sii.cl")
            setRequestProperty("Referer",          "https://www4.sii.cl/mapasui/internet/")
            setRequestProperty("X-Requested-With", "XMLHttpRequest")
            setRequestProperty("User-Agent",       UA)
            doOutput       = true
            connectTimeout = 15000
            readTimeout    = 15000
        }

        conn.outputStream.use { it.write(bytes) }

        val code = conn.responseCode
        if (code != HttpURLConnection.HTTP_OK) {
            val err = conn.errorStream?.bufferedReader()?.readText()?.take(300) ?: ""
            conn.disconnect()
            throw Exception("HTTP $code — $err")
        }

        val response = conn.inputStream.bufferedReader(Charsets.UTF_8).readText()
        conn.disconnect()

        val dataArr = JSONObject(response).optJSONArray("data") ?: return emptyList()

        return (0 until dataArr.length()).map { i ->
            dataArr.getJSONObject(i).let { p ->
                PredioResult(
                    rol       = p.optString("rol",                "-"),
                    direccion = p.optString("direccion",          "-").trim(),
                    destino   = p.optString("destinoDescripcion", "-"),
                    comuna    = p.optString("nombreComuna",       "-"),
                    manzana   = p.optInt("manzana", 0),
                    predio    = p.optInt("predio",  0)
                )
            }
        }
    }

    private fun mostrarResultado(predios: List<PredioResult>) {
        cardResultado.visibility = View.VISIBLE
        containerResultados.removeAllViews()
        tvResultado.visibility   = View.GONE

        val comuna = comunas[spinnerComuna.selectedItemPosition]

        if (predios.isEmpty()) {
            tvResultado.visibility = View.VISIBLE
            tvResultado.text       = "⚠️ Sin resultados.\n\nVerifica la calle (sin tildes) y el número."
            return
        }

        val inflater = LayoutInflater.from(this)
        predios.forEachIndexed { i, predio ->
            val item = inflater.inflate(R.layout.item_resultado, containerResultados, false)

            item.findViewById<TextView>(R.id.tvDireccion).text = "🏠 ${predio.direccion}"
            item.findViewById<TextView>(R.id.tvRol).text       = "📋 ROL: ${predio.rol}  |  Manzana: ${predio.manzana}  |  Predio: ${predio.predio}"
            item.findViewById<TextView>(R.id.tvDestino).text   = "🏗 ${predio.destino}  —  ${predio.comuna}"

            val btn = item.findViewById<Button>(R.id.btnItemCertificado)
            if (predio.rol.contains("-")) {
                btn.setOnClickListener { abrirTgr(predio, comuna) }
            } else {
                btn.visibility = View.GONE
            }

            // Ocultar separador en el último item
            if (i == predios.lastIndex) {
                item.findViewById<View>(R.id.separador)?.visibility = View.GONE
            }

            containerResultados.addView(item)
        }
    }

    private fun mostrarError(msg: String) {
        cardResultado.visibility = View.VISIBLE
        containerResultados.removeAllViews()
        tvResultado.visibility   = View.VISIBLE
        tvResultado.text         = "❌ $msg"
    }

    data class PredioResult(
        val rol: String, val direccion: String, val destino: String,
        val comuna: String, val manzana: Int, val predio: Int
    )

    companion object {
        private const val UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }
}
