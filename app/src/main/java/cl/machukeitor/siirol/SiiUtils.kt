package cl.machukeitor.siirol

import org.json.JSONArray
import org.json.JSONObject

data class PredioResult(
    val rol: String, val direccion: String, val destino: String,
    val comuna: String, val manzana: Int, val predio: Int,
    val valorTotal: Long = 0L, val valorAfecto: Long = 0L, val valorExento: Long = 0L,
    val agnoSancion: Int = 0
)

data class AvaluoData(val total: Long, val afecto: Long, val exento: Long, val agnoSancion: Int) {
    companion object {
        val VACIO = AvaluoData(0L, 0L, 0L, 0)
    }
}

data class HistorialItem(val codigoSii: String, val calle: String, val numero: String)

/**
 * Lógica pura (sin dependencias de Android salvo org.json) extraída de las Activities
 * para poder cubrirla con tests unitarios en la JVM.
 */
object SiiUtils {

    const val MAX_HISTORIAL = 5

    // ── Formato ──────────────────────────────────────────────────────────────

    /** Formatea pesos chilenos con punto como separador de miles, independiente del Locale del dispositivo. */
    fun formatPesos(value: Long): String {
        val digits = kotlin.math.abs(value).toString()
        val grouped = digits.reversed().chunked(3).joinToString(".").reversed()
        return (if (value < 0) "-$" else "$") + grouped
    }

    // ── Rol ──────────────────────────────────────────────────────────────────

    /** Separa "123-45" en ("123", "45"). Si no hay guion, el sub-rol queda vacío. */
    fun separarRol(rol: String): Pair<String, String> {
        val partes = rol.trim().split("-")
        return (partes.getOrNull(0)?.trim() ?: "") to (partes.getOrNull(1)?.trim() ?: "")
    }

    /** Solo se ofrecen certificados para roles con formato "rol-subrol" válido. */
    fun tieneRolCompleto(rol: String): Boolean {
        val (r, s) = separarRol(rol)
        return rol.contains("-") && r.isNotEmpty() && s.isNotEmpty()
    }

    // ── Clima (códigos WMO de open-meteo) ────────────────────────────────────

    fun climaDesdeCode(code: Int): Pair<String, String> = when (code) {
        0                -> "☀️"  to "Despejado"
        1                -> "🌤" to "Mayormente despejado"
        2                -> "⛅" to "Parcialmente nublado"
        3                -> "☁️"  to "Nublado"
        45, 48           -> "🌫️"  to "Neblina"
        51, 53, 55       -> "🌦️"  to "Llovizna"
        56, 57           -> "🌦️"  to "Llovizna helada"
        61, 63, 65       -> "🌧️"  to "Lluvia"
        66, 67           -> "🌧️"  to "Lluvia helada"
        71, 73, 75       -> "🌨️"  to "Nieve"
        77               -> "🌨️"  to "Granizo"
        80, 81, 82       -> "🌧️"  to "Chubascos"
        85, 86           -> "❄️"  to "Nevada"
        95               -> "⛈️"  to "Tormenta eléctrica"
        96, 99           -> "⛈️"  to "Tormenta con granizo"
        else             -> "🌡️"  to "Variable"
    }

    // ── TGR ──────────────────────────────────────────────────────────────────

    private const val PDF_MARKER = "data:application/pdf;base64,"

    /** Extrae el PDF embebido en base64 del HTML de TGR. Devuelve null si no existe. */
    fun extraerBase64Pdf(html: String): String? {
        val start = html.indexOf(PDF_MARKER)
        if (start == -1) return null
        return html.substring(start + PDF_MARKER.length)
            .takeWhile { c -> c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c == '+' || c == '/' || c == '=' }
            .ifEmpty { null }
    }

    /** Body x-www-form-urlencoded para TGR, con cada valor codificado en UTF-8. */
    fun construirFormTgr(region: String, comuna: String, rol: String, subRol: String): String =
        listOf(
            "region" to region,
            "comuna" to comuna,
            "rol" to rol,
            "subRol" to subRol,
            "g-recaptcha-response" to ""
        ).joinToString("&") { (k, v) -> "$k=" + java.net.URLEncoder.encode(v, "UTF-8") }

    // ── Historial ────────────────────────────────────────────────────────────

    /** Inserta [nuevo] al inicio, elimina duplicados y limita a [max] elementos. */
    fun actualizarHistorial(
        actual: List<HistorialItem>,
        nuevo: HistorialItem,
        max: Int = MAX_HISTORIAL
    ): List<HistorialItem> =
        (listOf(nuevo) + actual.filter { it != nuevo }).take(max)

    fun historialDesdeJson(json: String?): List<HistorialItem> = try {
        val arr = JSONArray(json ?: "[]")
        (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let {
                HistorialItem(it.optString("codigoSii"), it.optString("calle"), it.optString("numero"))
            }
        }
    } catch (e: Exception) {
        emptyList()
    }

    fun historialAJson(items: List<HistorialItem>): String {
        val arr = JSONArray()
        items.forEach {
            arr.put(JSONObject().apply {
                put("codigoSii", it.codigoSii); put("calle", it.calle); put("numero", it.numero)
            })
        }
        return arr.toString()
    }

    // ── SII: requests ────────────────────────────────────────────────────────

    private const val NS = "cl.sii.sdi.lob.bbrr.mapas.data.api.interfaces.MapasFacadeService"

    private fun metaData(metodo: String, transactionId: String) = JSONObject().apply {
        put("namespace",      "$NS/$metodo")
        put("conversationId", "UNAUTHENTICATED-CALL")
        put("transactionId",  transactionId)
    }

    fun construirBodyPredios(comuna: Comuna, calle: String, numero: String, transactionId: String): String =
        JSONObject().apply {
            put("metaData", metaData("getPrediosDireccion", transactionId))
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

    fun construirBodyAvaluo(comuna: Comuna, manzana: Int, predio: Int, transactionId: String): String =
        JSONObject().apply {
            put("metaData", metaData("getPredioNacional", transactionId))
            put("data", JSONObject().apply {
                put("predio", JSONObject().apply {
                    put("comuna",  comuna.codigoSii)
                    put("manzana", manzana.toString())
                    put("predio",  predio.toString())
                })
                put("servicios", JSONArray())
            })
        }.toString()

    // ── SII: responses ───────────────────────────────────────────────────────

    fun parsearPredios(response: String): List<PredioResult> {
        val dataArr = JSONObject(response).optJSONArray("data") ?: return emptyList()
        return (0 until dataArr.length()).mapNotNull { i ->
            dataArr.optJSONObject(i)?.let { p ->
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

    fun parsearAvaluo(response: String): AvaluoData {
        val data = JSONObject(response).optJSONObject("data") ?: return AvaluoData.VACIO
        return AvaluoData(
            data.optLong("valorTotal",  0L),
            data.optLong("valorAfecto", 0L),
            data.optLong("valorExento", 0L),
            data.optInt("agnoSancion",  0)
        )
    }
}
