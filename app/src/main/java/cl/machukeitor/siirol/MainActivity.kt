package cl.machukeitor.siirol

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import android.widget.AdapterView
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
        val tgrComuna: String,
        val lat: Double,
        val lon: Double
    )

    private val comunas = listOf(
        Comuna("8101", "Chillán",     "CHILLÁN",     "16", "168", -36.607, -72.103),
        Comuna("8401", "Los Ángeles", "LOS ANGELES", "8",  "204", -37.470, -72.351),
        Comuna("9201", "Temuco",      "TEMUCO",      "9",  "227", -38.739, -72.590)
    )

    private val API_URL    = "https://www4.sii.cl/mapasui/services/data/mapasFacadeService/getPrediosDireccion"
    private val AVALUO_URL = "https://www4.sii.cl/mapasui/services/data/mapasFacadeService/getPredioNacional"

    private lateinit var spinnerComuna: Spinner
    private lateinit var etCalle: EditText
    private lateinit var etNumero: EditText
    private lateinit var btnBuscar: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvResultado: TextView
    private lateinit var cardResultado: View
    private lateinit var containerResultados: LinearLayout
    private lateinit var tvClimaIcono: TextView
    private lateinit var tvClimaTemp: TextView
    private lateinit var tvClimaDesc: TextView
    private lateinit var tvClimaDetalle: TextView
    private lateinit var containerHistorial: LinearLayout
    private lateinit var scrollHistorial: android.widget.HorizontalScrollView

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
        tvClimaIcono        = findViewById(R.id.tvClimaIcono)
        tvClimaTemp         = findViewById(R.id.tvClimaTemp)
        tvClimaDesc         = findViewById(R.id.tvClimaDesc)
        tvClimaDetalle      = findViewById(R.id.tvClimaDetalle)
        containerHistorial  = findViewById(R.id.containerHistorial)
        scrollHistorial     = findViewById(R.id.scrollHistorial)

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, comunas.map { it.nombre })
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerComuna.adapter = adapter

        spinnerComuna.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
                cargarClima(comunas[pos])
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        btnBuscar.setOnClickListener { buscarRol() }
        mostrarHistorial()
    }

    private fun abrirSiiCertificado(predio: PredioResult, comuna: Comuna) {
        startActivity(Intent(this, SiiCertificadoActivity::class.java).apply {
            putExtra("comunaCnp",         comuna.codigoSii.toInt())
            putExtra("manzana",           predio.manzana)
            putExtra("predio",            predio.predio)
            putExtra("ultimoEacAplicado", predio.agnoSancion)
            putExtra("rolCompleto",       predio.rol)
            putExtra("direccion",         predio.direccion)
        })
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
        guardarHistorial(spinnerComuna.selectedItemPosition, calle, numero)

        progressBar.visibility   = View.VISIBLE
        cardResultado.visibility = View.GONE
        btnBuscar.isEnabled      = false

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val predios = consultarAPI(comuna, calle, numero)
                val prediosConAvaluo = predios.map { p ->
                    async {
                        val av = try {
                            consultarAvaluo(comuna, p.manzana, p.predio)
                        } catch (e: Exception) { AvaluoData(0L, 0L, 0L, 0) }
                        p.copy(valorTotal = av.total, valorAfecto = av.afecto,
                               valorExento = av.exento, agnoSancion = av.agnoSancion)
                    }
                }.awaitAll()
                withContext(Dispatchers.Main) { mostrarResultado(prediosConAvaluo) }
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

    data class AvaluoData(val total: Long, val afecto: Long, val exento: Long, val agnoSancion: Int)

    private fun consultarAvaluo(comuna: Comuna, manzana: Int, predio: Int): AvaluoData {
        val body = JSONObject().apply {
            put("metaData", JSONObject().apply {
                put("namespace",      "cl.sii.sdi.lob.bbrr.mapas.data.api.interfaces.MapasFacadeService/getPredioNacional")
                put("conversationId", "UNAUTHENTICATED-CALL")
                put("transactionId",  UUID.randomUUID().toString())
            })
            put("data", JSONObject().apply {
                put("predio", JSONObject().apply {
                    put("comuna",  comuna.codigoSii)
                    put("manzana", manzana.toString())
                    put("predio",  predio.toString())
                })
                put("servicios", JSONArray())
            })
        }.toString()

        val bytes = body.toByteArray(Charsets.UTF_8)
        val conn  = (URL(AVALUO_URL).openConnection() as HttpURLConnection).apply {
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
        if (conn.responseCode != HttpURLConnection.HTTP_OK) { conn.disconnect(); return AvaluoData(0L, 0L, 0L, 0) }
        val response = conn.inputStream.bufferedReader(Charsets.UTF_8).readText()
        conn.disconnect()
        val data = JSONObject(response).optJSONObject("data") ?: return AvaluoData(0L, 0L, 0L, 0)
        return AvaluoData(
            data.optLong("valorTotal",  0L),
            data.optLong("valorAfecto", 0L),
            data.optLong("valorExento", 0L),
            data.optInt("agnoSancion",  0)
        )
    }

    private fun formatPesos(value: Long): String =
        "$" + String.format("%,d", value).replace(",", ".")

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

            val tvAvaluo = item.findViewById<TextView>(R.id.tvAvaluo)
            if (predio.valorTotal > 0L) {
                tvAvaluo.text = "💰 Avalúo fiscal: ${formatPesos(predio.valorTotal)}"
                tvAvaluo.visibility = View.VISIBLE
            } else {
                tvAvaluo.visibility = View.GONE
            }

            val btn = item.findViewById<Button>(R.id.btnItemCertificado)
            val btnSii = item.findViewById<Button>(R.id.btnItemCertificadoSii)
            if (predio.rol.contains("-")) {
                btn.setOnClickListener { abrirTgr(predio, comuna) }
                btnSii.setOnClickListener { abrirSiiCertificado(predio, comuna) }
            } else {
                btn.visibility = View.GONE
                btnSii.visibility = View.GONE
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

    // ── Historial ────────────────────────────────────────────────────────────

    private fun guardarHistorial(comunaIdx: Int, calle: String, numero: String) {
        val prefs  = getSharedPreferences("historial", Context.MODE_PRIVATE)
        val json   = JSONArray(prefs.getString("busquedas", "[]"))
        val nuevo  = JSONObject().apply {
            put("comunaIdx", comunaIdx); put("calle", calle); put("numero", numero)
        }
        // Evitar duplicado exacto al inicio
        val lista = (0 until json.length()).map { json.getJSONObject(it) }
            .filter { !(it.optInt("comunaIdx") == comunaIdx &&
                        it.optString("calle") == calle &&
                        it.optString("numero") == numero) }
        val nueva = JSONArray()
        nueva.put(nuevo)
        lista.take(4).forEach { nueva.put(it) }   // máximo 5
        prefs.edit().putString("busquedas", nueva.toString()).apply()
        mostrarHistorial()
    }

    private fun mostrarHistorial() {
        val prefs = getSharedPreferences("historial", Context.MODE_PRIVATE)
        val json  = JSONArray(prefs.getString("busquedas", "[]"))
        containerHistorial.removeAllViews()
        if (json.length() == 0) { scrollHistorial.visibility = View.GONE; return }
        scrollHistorial.visibility = View.VISIBLE
        for (i in 0 until json.length()) {
            val item      = json.getJSONObject(i)
            val comunaIdx = item.optInt("comunaIdx", 0)
            val calle     = item.optString("calle")
            val numero    = item.optString("numero")
            val chip = TextView(this).apply {
                text = "🕐 ${comunas[comunaIdx].nombre} · $calle $numero"
                textSize = 12f
                setTextColor(0xFF1A3A5C.toInt())
                setBackgroundResource(R.drawable.bg_chip_historial)
                setPadding(24, 12, 24, 12)
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.setMargins(0, 0, 16, 0)
                layoutParams = lp
                setOnClickListener {
                    spinnerComuna.setSelection(comunaIdx)
                    etCalle.setText(calle)
                    etNumero.setText(numero)
                    buscarRol()
                }
            }
            containerHistorial.addView(chip)
        }
    }

    // ── Clima ────────────────────────────────────────────────────────────────

    private fun cargarClima(comuna: Comuna) {
        tvClimaDesc.text    = "Cargando clima..."
        tvClimaTemp.text    = "--°C"
        tvClimaIcono.text   = "🌡"
        tvClimaDetalle.text = ""

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = "https://api.open-meteo.com/v1/forecast" +
                    "?latitude=${comuna.lat}&longitude=${comuna.lon}" +
                    "&current=temperature_2m,apparent_temperature,relative_humidity_2m," +
                    "wind_speed_10m,weather_code&wind_speed_unit=kmh&timezone=America%2FSantiago"

                val conn = (URL(url).openConnection() as java.net.HttpURLConnection).apply {
                    connectTimeout = 10000; readTimeout = 10000
                }
                val json    = JSONObject(conn.inputStream.bufferedReader().readText())
                conn.disconnect()
                val current = json.getJSONObject("current")

                val tempC    = current.getDouble("temperature_2m")
                val sensTemp = current.getDouble("apparent_temperature")
                val humedad  = current.getInt("relative_humidity_2m")
                val viento   = current.getDouble("wind_speed_10m")
                val code     = current.getInt("weather_code")

                val (icono, desc) = climaDesdeCode(code)

                withContext(Dispatchers.Main) {
                    tvClimaIcono.text   = icono
                    tvClimaTemp.text    = "${tempC.toInt()}°C"
                    tvClimaDesc.text    = desc
                    tvClimaDetalle.text = "Sens. ${sensTemp.toInt()}°C · 💧${humedad}% · 💨${viento.toInt()} km/h"
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    tvClimaDesc.text = "Sin datos de clima"
                }
            }
        }
    }

    private fun climaDesdeCode(code: Int): Pair<String, String> = when (code) {
        0            -> "☀️"  to "Despejado"
        1            -> "🌤" to "Mayormente despejado"
        2            -> "⛅" to "Parcialmente nublado"
        3            -> "☁️"  to "Nublado"
        45, 48       -> "🌫️"  to "Neblina"
        51, 53, 55   -> "🌦️"  to "Llovizna"
        61, 63, 65   -> "🌧️"  to "Lluvia"
        71, 73, 75   -> "🌨️"  to "Nieve"
        77           -> "🌨️"  to "Granizo"
        80, 81, 82   -> "🌧️"  to "Chubascos"
        85, 86       -> "❄️"  to "Nevada"
        95           -> "⛈️"  to "Tormenta eléctrica"
        96, 99       -> "⛈️"  to "Tormenta con granizo"
        else         -> "🌡️"  to "Variable"
    }

    // ── Modelos ──────────────────────────────────────────────────────────────

    data class PredioResult(
        val rol: String, val direccion: String, val destino: String,
        val comuna: String, val manzana: Int, val predio: Int,
        val valorTotal: Long = 0L, val valorAfecto: Long = 0L, val valorExento: Long = 0L,
        val agnoSancion: Int = 0
    )

    companion object {
        private const val UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }
}
