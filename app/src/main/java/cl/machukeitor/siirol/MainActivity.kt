package cl.machukeitor.siirol

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import android.widget.AdapterView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

class MainActivity : AppCompatActivity() {

    // ── State ─────────────────────────────────────────────────────────────────

    private var comunasFiltradas = listOf<Comuna>()
    private var climaJob: Job? = null

    private val API_URL    = "https://www4.sii.cl/mapasui/services/data/mapasFacadeService/getPrediosDireccion"
    private val AVALUO_URL = "https://www4.sii.cl/mapasui/services/data/mapasFacadeService/getPredioNacional"

    private lateinit var spinnerRegion: Spinner
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

        spinnerRegion       = findViewById(R.id.spinnerRegion)
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

        // Región spinner
        val regionAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, ComunasData.regiones.map { it.nombre })
        regionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerRegion.adapter = regionAdapter

        spinnerRegion.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
                filtrarComunas(pos)
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        // Comuna spinner — se carga al seleccionar región
        spinnerComuna.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
                if (pos < comunasFiltradas.size) cargarClima(comunasFiltradas[pos])
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        btnBuscar.setOnClickListener { buscarRol() }
        mostrarHistorial()
    }

    // ── Filtrado de comunas por región ────────────────────────────────────────

    private fun filtrarComunas(regionIdx: Int) {
        val region = ComunasData.regiones.getOrNull(regionIdx) ?: return
        comunasFiltradas = ComunasData.comunasDeRegion(region.tgrCode)
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, comunasFiltradas.map { it.nombre })
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerComuna.adapter = adapter
        if (comunasFiltradas.isNotEmpty()) cargarClima(comunasFiltradas[0])
    }

    private fun seleccionarComuna(comuna: Comuna) {
        val regionIdx = ComunasData.regiones.indexOfFirst { it.tgrCode == comuna.tgrRegion }
        if (regionIdx < 0) return
        spinnerRegion.setSelection(regionIdx)
        filtrarComunas(regionIdx)
        val comunaIdx = comunasFiltradas.indexOfFirst { it.codigoSii == comuna.codigoSii }
        if (comunaIdx >= 0) spinnerComuna.setSelection(comunaIdx)
    }

    // ── Certificados ──────────────────────────────────────────────────────────

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
        val (rol, subRol) = SiiUtils.separarRol(predio.rol)
        startActivity(Intent(this, TgrActivity::class.java).apply {
            putExtra("region",      comuna.tgrRegion)
            putExtra("comuna",      comuna.tgrComuna)
            putExtra("rol",         rol)
            putExtra("subRol",      subRol)
            putExtra("rolCompleto", predio.rol)
            putExtra("direccion",   predio.direccion)
        })
    }

    // ── Búsqueda ──────────────────────────────────────────────────────────────

    private fun buscarRol() {
        val calle  = etCalle.text.toString().trim()
        val numero = etNumero.text.toString().trim()

        if (calle.isEmpty())  { etCalle.error  = "Ingresa el nombre de la calle"; return }
        if (numero.isEmpty()) { etNumero.error = "Ingresa el número"; return }
        if (comunasFiltradas.isEmpty()) return

        val comuna = comunasFiltradas.getOrNull(spinnerComuna.selectedItemPosition) ?: return
        guardarHistorial(comuna.codigoSii, calle, numero)

        progressBar.visibility   = View.VISIBLE
        cardResultado.visibility = View.GONE
        btnBuscar.isEnabled      = false

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val predios = consultarAPI(comuna, calle, numero)
                val prediosConAvaluo = predios.map { p ->
                    async {
                        val av = try {
                            consultarAvaluo(comuna, p.manzana, p.predio)
                        } catch (e: Exception) { AvaluoData.VACIO }
                        p.copy(valorTotal = av.total, valorAfecto = av.afecto,
                               valorExento = av.exento, agnoSancion = av.agnoSancion)
                    }
                }.awaitAll()
                withContext(Dispatchers.Main) { mostrarResultado(prediosConAvaluo, comuna) }
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
        val body = SiiUtils.construirBodyPredios(comuna, calle, numero, UUID.randomUUID().toString())

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

        return SiiUtils.parsearPredios(response)
    }

    private fun consultarAvaluo(comuna: Comuna, manzana: Int, predio: Int): AvaluoData {
        val body = SiiUtils.construirBodyAvaluo(comuna, manzana, predio, UUID.randomUUID().toString())

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
        if (conn.responseCode != HttpURLConnection.HTTP_OK) { conn.disconnect(); return AvaluoData.VACIO }
        val response = conn.inputStream.bufferedReader(Charsets.UTF_8).readText()
        conn.disconnect()
        return SiiUtils.parsearAvaluo(response)
    }

    private fun mostrarResultado(predios: List<PredioResult>, comuna: Comuna) {
        cardResultado.visibility = View.VISIBLE
        containerResultados.removeAllViews()
        tvResultado.visibility   = View.GONE

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
                tvAvaluo.text = "💰 Avalúo fiscal: ${SiiUtils.formatPesos(predio.valorTotal)}"
                tvAvaluo.visibility = View.VISIBLE
            } else {
                tvAvaluo.visibility = View.GONE
            }

            val btn = item.findViewById<Button>(R.id.btnItemCertificado)
            val btnSii = item.findViewById<Button>(R.id.btnItemCertificadoSii)
            if (SiiUtils.tieneRolCompleto(predio.rol)) {
                btn.setOnClickListener { abrirTgr(predio, comuna) }
                btnSii.setOnClickListener { abrirSiiCertificado(predio, comuna) }
            } else {
                btn.visibility = View.GONE
                btnSii.visibility = View.GONE
            }

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

    // ── Historial ─────────────────────────────────────────────────────────────

    private fun guardarHistorial(codigoSii: String, calle: String, numero: String) {
        val prefs = getSharedPreferences("historial", Context.MODE_PRIVATE)
        val actual = SiiUtils.historialDesdeJson(prefs.getString("busquedas", "[]"))
        val nueva  = SiiUtils.actualizarHistorial(actual, HistorialItem(codigoSii, calle, numero))
        prefs.edit().putString("busquedas", SiiUtils.historialAJson(nueva)).apply()
        mostrarHistorial()
    }

    private fun mostrarHistorial() {
        val prefs = getSharedPreferences("historial", Context.MODE_PRIVATE)
        val items = SiiUtils.historialDesdeJson(prefs.getString("busquedas", "[]"))
        containerHistorial.removeAllViews()
        if (items.isEmpty()) { scrollHistorial.visibility = View.GONE; return }

        var hayChips = false
        for ((codigoSii, calle, numero) in items) {
            if (codigoSii.isEmpty() || calle.isEmpty()) continue
            val comuna = ComunasData.buscarPorCodigo(codigoSii) ?: continue
            hayChips = true
            val chip = TextView(this).apply {
                text = "🕐 ${comuna.nombre} · $calle $numero"
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
                    seleccionarComuna(comuna)
                    etCalle.setText(calle)
                    etNumero.setText(numero)
                    buscarRol()
                }
            }
            containerHistorial.addView(chip)
        }
        scrollHistorial.visibility = if (hayChips) View.VISIBLE else View.GONE
    }

    // ── Clima ─────────────────────────────────────────────────────────────────

    private fun cargarClima(comuna: Comuna) {
        tvClimaDesc.text    = "Cargando clima..."
        tvClimaTemp.text    = "--°C"
        tvClimaIcono.text   = "🌡"
        tvClimaDetalle.text = ""

        // Cancela la consulta anterior para que una respuesta lenta no pise la de la comuna actual
        climaJob?.cancel()
        climaJob = lifecycleScope.launch(Dispatchers.IO) {
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

                val (icono, desc) = SiiUtils.climaDesdeCode(code)

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

    companion object {
        private const val UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }
}
