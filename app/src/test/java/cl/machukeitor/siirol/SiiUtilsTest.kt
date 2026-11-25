package cl.machukeitor.siirol

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class SiiUtilsTest {

    private val talca = Comuna("7101", "Talca", "TALCA", "7", "101", -35.4f, -71.6f)

    // ── formatPesos ──────────────────────────────────────────────────────────

    @Test fun formatPesos_ceroYPequenos() {
        assertEquals("$0", SiiUtils.formatPesos(0))
        assertEquals("$7", SiiUtils.formatPesos(7))
        assertEquals("$999", SiiUtils.formatPesos(999))
    }

    @Test fun formatPesos_separadorDeMilesConPunto() {
        assertEquals("$1.000", SiiUtils.formatPesos(1_000))
        assertEquals("$12.345.678", SiiUtils.formatPesos(12_345_678))
        assertEquals("$100.000.000", SiiUtils.formatPesos(100_000_000))
    }

    @Test fun formatPesos_negativo() {
        assertEquals("-$1.500", SiiUtils.formatPesos(-1_500))
    }

    @Test fun formatPesos_noDependeDelLocale() {
        val original = Locale.getDefault()
        try {
            for (loc in listOf(Locale.GERMANY, Locale.FRANCE, Locale("es", "CL"), Locale.US)) {
                Locale.setDefault(loc)
                assertEquals("Locale $loc", "$1.234.567", SiiUtils.formatPesos(1_234_567))
            }
        } finally {
            Locale.setDefault(original)
        }
    }

    // ── Rol ──────────────────────────────────────────────────────────────────

    @Test fun separarRol_normal() {
        assertEquals("123" to "45", SiiUtils.separarRol("123-45"))
        assertEquals("123" to "45", SiiUtils.separarRol(" 123 - 45 "))
    }

    @Test fun separarRol_sinGuion() {
        assertEquals("123" to "", SiiUtils.separarRol("123"))
        assertEquals("" to "", SiiUtils.separarRol(""))
    }

    @Test fun tieneRolCompleto() {
        assertTrue(SiiUtils.tieneRolCompleto("123-45"))
        assertFalse(SiiUtils.tieneRolCompleto("123"))
        assertFalse(SiiUtils.tieneRolCompleto("-"))       // valor por defecto de la API
        assertFalse(SiiUtils.tieneRolCompleto("123-"))
        assertFalse(SiiUtils.tieneRolCompleto("-45"))
    }

    // ── Clima ────────────────────────────────────────────────────────────────

    @Test fun climaDesdeCode_codigosConocidos() {
        val esperado = mapOf(
            0 to "Despejado", 1 to "Mayormente despejado", 2 to "Parcialmente nublado", 3 to "Nublado",
            45 to "Neblina", 48 to "Neblina",
            51 to "Llovizna", 53 to "Llovizna", 55 to "Llovizna",
            56 to "Llovizna helada", 57 to "Llovizna helada",
            61 to "Lluvia", 63 to "Lluvia", 65 to "Lluvia",
            66 to "Lluvia helada", 67 to "Lluvia helada",
            71 to "Nieve", 73 to "Nieve", 75 to "Nieve", 77 to "Granizo",
            80 to "Chubascos", 81 to "Chubascos", 82 to "Chubascos",
            85 to "Nevada", 86 to "Nevada",
            95 to "Tormenta eléctrica", 96 to "Tormenta con granizo", 99 to "Tormenta con granizo"
        )
        esperado.forEach { (code, desc) ->
            assertEquals("code $code", desc, SiiUtils.climaDesdeCode(code).second)
        }
    }

    @Test fun climaDesdeCode_todosLosCodigosWmoTienenDescripcion() {
        // Lista oficial de weather_code de open-meteo
        val wmo = listOf(0, 1, 2, 3, 45, 48, 51, 53, 55, 56, 57, 61, 63, 65, 66, 67,
                         71, 73, 75, 77, 80, 81, 82, 85, 86, 95, 96, 99)
        wmo.forEach { assertTrue("code $it cae en 'Variable'", SiiUtils.climaDesdeCode(it).second != "Variable") }
    }

    @Test fun climaDesdeCode_desconocido() {
        assertEquals("Variable", SiiUtils.climaDesdeCode(-1).second)
        assertEquals("Variable", SiiUtils.climaDesdeCode(42).second)
        assertTrue(SiiUtils.climaDesdeCode(42).first.isNotEmpty())
    }

    // ── extraerBase64Pdf ─────────────────────────────────────────────────────

    @Test fun extraerBase64Pdf_encuentraDatos() {
        val html = """<iframe src="data:application/pdf;base64,JVBERi0xLjQK+/AB==" width="100%">"""
        assertEquals("JVBERi0xLjQK+/AB==", SiiUtils.extraerBase64Pdf(html))
    }

    @Test fun extraerBase64Pdf_sinMarcador() {
        assertNull(SiiUtils.extraerBase64Pdf("<html>Error: rol no existe</html>"))
        assertNull(SiiUtils.extraerBase64Pdf(""))
    }

    @Test fun extraerBase64Pdf_marcadorVacio() {
        assertNull(SiiUtils.extraerBase64Pdf("""<embed src="data:application/pdf;base64,">"""))
    }

    @Test fun extraerBase64Pdf_noIncluyeLetrasNoAscii() {
        // HTML leído como ISO-8859-1: una letra acentuada no debe colarse en el base64
        assertEquals("QUJD", SiiUtils.extraerBase64Pdf("data:application/pdf;base64,QUJDñá rest"))
    }

    // ── Formulario TGR ───────────────────────────────────────────────────────

    @Test fun formTgr_valoresNumericosQuedanIgual() {
        // Mismo resultado que el string que se armaba a mano antes
        assertEquals(
            "region=7&comuna=101&rol=123&subRol=45&g-recaptcha-response=",
            SiiUtils.construirFormTgr("7", "101", "123", "45")
        )
    }

    @Test fun formTgr_codificaCaracteresEspeciales() {
        val body = SiiUtils.construirFormTgr("7", "101", "12 3&x=1", "ñ/+")
        assertEquals(
            "region=7&comuna=101&rol=12+3%26x%3D1&subRol=%C3%B1%2F%2B&g-recaptcha-response=",
            body
        )
        // El & dentro del valor no debe crear un parámetro extra
        assertEquals(5, body.split("&").size)
    }

    @Test fun formTgr_valoresVacios() {
        assertEquals(
            "region=&comuna=&rol=&subRol=&g-recaptcha-response=",
            SiiUtils.construirFormTgr("", "", "", "")
        )
    }

    // ── Historial ────────────────────────────────────────────────────────────

    private fun h(n: Int) = HistorialItem("7101", "calle$n", "$n")

    @Test fun historial_insertaAlInicio() {
        val r = SiiUtils.actualizarHistorial(listOf(h(1), h(2)), h(3))
        assertEquals(listOf(h(3), h(1), h(2)), r)
    }

    @Test fun historial_eliminaDuplicadoYLoMueveArriba() {
        val r = SiiUtils.actualizarHistorial(listOf(h(1), h(2), h(3)), h(2))
        assertEquals(listOf(h(2), h(1), h(3)), r)
    }

    @Test fun historial_maximoCinco() {
        val actual = (1..5).map { h(it) }
        val r = SiiUtils.actualizarHistorial(actual, h(6))
        assertEquals(5, r.size)
        assertEquals(h(6), r.first())
        assertFalse(r.contains(h(5)))
    }

    @Test fun historial_mismaCalleOtroNumeroNoEsDuplicado() {
        val r = SiiUtils.actualizarHistorial(listOf(HistorialItem("7101", "a", "1")), HistorialItem("7101", "a", "2"))
        assertEquals(2, r.size)
    }

    @Test fun historial_roundTripJson() {
        val items = listOf(h(1), HistorialItem("13101", "Av. Libertador O'Higgins", "1449"))
        assertEquals(items, SiiUtils.historialDesdeJson(SiiUtils.historialAJson(items)))
    }

    @Test fun historial_jsonInvalidoONulo() {
        assertTrue(SiiUtils.historialDesdeJson(null).isEmpty())
        assertTrue(SiiUtils.historialDesdeJson("no es json").isEmpty())
        assertTrue(SiiUtils.historialDesdeJson("[]").isEmpty())
    }

    @Test fun historial_compatibleConFormatoAnterior() {
        // Formato que guardaba la v1.4 en SharedPreferences
        val legacy = """[{"codigoSii":"7101","calle":"1 sur","numero":"100"}]"""
        assertEquals(listOf(HistorialItem("7101", "1 sur", "100")), SiiUtils.historialDesdeJson(legacy))
    }

    // ── Requests SII ─────────────────────────────────────────────────────────

    @Test fun bodyPredios_estructura() {
        val json = JSONObject(SiiUtils.construirBodyPredios(talca, "  1 Sur ", " 1450 ", "tx-1"))
        val meta = json.getJSONObject("metaData")
        assertEquals("cl.sii.sdi.lob.bbrr.mapas.data.api.interfaces.MapasFacadeService/getPrediosDireccion",
            meta.getString("namespace"))
        assertEquals("UNAUTHENTICATED-CALL", meta.getString("conversationId"))
        assertEquals("tx-1", meta.getString("transactionId"))

        val rd = json.getJSONObject("data").getJSONObject("rolDireccion")
        assertEquals("7101", rd.getString("comuna"))
        assertEquals("TALCA", rd.getString("nombreComuna"))
        assertEquals("1 sur", rd.getString("calle"))
        assertEquals("1450", rd.getString("numeroCalleStr"))
        assertEquals(0, rd.getInt("detalle"))
        assertEquals(0, json.getJSONObject("data").getJSONArray("servicios").length())
    }

    @Test fun bodyAvaluo_estructura() {
        val json = JSONObject(SiiUtils.construirBodyAvaluo(talca, 123, 45, "tx-2"))
        assertTrue(json.getJSONObject("metaData").getString("namespace").endsWith("/getPredioNacional"))
        val p = json.getJSONObject("data").getJSONObject("predio")
        assertEquals("7101", p.getString("comuna"))
        assertEquals("123", p.getString("manzana"))
        assertEquals("45", p.getString("predio"))
    }

    // ── Responses SII ────────────────────────────────────────────────────────

    @Test fun parsearPredios_ok() {
        val resp = """
            {"data":[
              {"rol":"123-45","direccion":"  1 SUR 1450 ","destinoDescripcion":"HABITACIONAL",
               "nombreComuna":"TALCA","manzana":123,"predio":45},
              {"rol":"123-46"}
            ]}""".trimIndent()
        val r = SiiUtils.parsearPredios(resp)
        assertEquals(2, r.size)
        assertEquals(PredioResult("123-45", "1 SUR 1450", "HABITACIONAL", "TALCA", 123, 45), r[0])
        assertEquals(PredioResult("123-46", "-", "-", "-", 0, 0), r[1])
    }

    @Test fun parsearPredios_sinData() {
        assertTrue(SiiUtils.parsearPredios("{}").isEmpty())
        assertTrue(SiiUtils.parsearPredios("""{"data":null}""").isEmpty())
        assertTrue(SiiUtils.parsearPredios("""{"data":[]}""").isEmpty())
    }

    @Test fun parsearPredios_ignoraElementosNoObjeto() {
        val r = SiiUtils.parsearPredios("""{"data":[null, 5, {"rol":"1-1"}]}""")
        assertEquals(1, r.size)
        assertEquals("1-1", r[0].rol)
    }

    @Test fun parsearAvaluo_ok() {
        val resp = """{"data":{"valorTotal":98765432,"valorAfecto":50000000,"valorExento":48765432,"agnoSancion":2025}}"""
        assertEquals(AvaluoData(98_765_432L, 50_000_000L, 48_765_432L, 2025), SiiUtils.parsearAvaluo(resp))
    }

    @Test fun parsearAvaluo_valoresGrandesNoDesbordan() {
        val resp = """{"data":{"valorTotal":5000000000}}"""
        assertEquals(5_000_000_000L, SiiUtils.parsearAvaluo(resp).total)
    }

    @Test fun parsearAvaluo_sinData() {
        assertEquals(AvaluoData.VACIO, SiiUtils.parsearAvaluo("{}"))
    }
}
