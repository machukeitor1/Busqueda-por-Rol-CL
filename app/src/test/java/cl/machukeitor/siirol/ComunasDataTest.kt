package cl.machukeitor.siirol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ComunasDataTest {

    private val comunas = ComunasData.todasLasComunas
    private val regiones = ComunasData.regiones

    @Test fun hay16Regiones() {
        assertEquals(16, regiones.size)
        assertEquals("códigos TGR de región duplicados", regiones.size, regiones.map { it.tgrCode }.toSet().size)
    }

    @Test fun cantidadDeComunas() {
        // Chile tiene 346 comunas; se tolera un rango por si se agregan/omiten casos especiales
        assertTrue("hay ${comunas.size} comunas", comunas.size in 340..346)
    }

    @Test fun codigoSiiUnico() {
        val dup = comunas.groupBy { it.codigoSii }.filterValues { it.size > 1 }.keys
        assertTrue("codigoSii duplicados: $dup", dup.isEmpty())
    }

    @Test fun codigoTgrUnicoPorRegion() {
        val dup = comunas.groupBy { it.tgrRegion to it.tgrComuna }.filterValues { it.size > 1 }
            .mapValues { (_, v) -> v.map { it.nombre } }
        assertTrue("tgrComuna duplicados dentro de una región: $dup", dup.isEmpty())
    }

    @Test fun codigoSiiNumerico() {
        // abrirSiiCertificado hace codigoSii.toInt(): no debe lanzar NumberFormatException
        comunas.forEach {
            assertNotNull("codigoSii no numérico: ${it.codigoSii} (${it.nombre})", it.codigoSii.toIntOrNull())
            assertTrue("largo inesperado: ${it.codigoSii}", it.codigoSii.length in 4..5)
        }
    }

    @Test fun todaComunaPerteneceAUnaRegion() {
        val codigos = regiones.map { it.tgrCode }.toSet()
        val huerfanas = comunas.filter { it.tgrRegion !in codigos }.map { it.nombre }
        assertTrue("comunas sin región: $huerfanas", huerfanas.isEmpty())
    }

    @Test fun todaRegionTieneComunas() {
        regiones.forEach {
            assertTrue("región sin comunas: ${it.nombre}", ComunasData.comunasDeRegion(it.tgrCode).isNotEmpty())
        }
    }

    @Test fun camposNoVacios() {
        comunas.forEach {
            assertTrue("nombre vacío: $it", it.nombre.isNotBlank())
            assertTrue("nombreApi vacío: $it", it.nombreApi.isNotBlank())
            assertTrue("tgrComuna vacío: $it", it.tgrComuna.isNotBlank())
        }
    }

    @Test fun nombreApiEnMayusculasSinTildes() {
        // El resto del listado usa MAYÚSCULAS sin tildes (formato del SII)
        val malas = comunas.filter { c ->
            c.nombreApi != c.nombreApi.uppercase() || c.nombreApi.any { it in "ÁÉÍÓÚáéíóú" }
        }.map { "${it.codigoSii}=${it.nombreApi}" }
        assertTrue("nombreApi con tildes/minúsculas: $malas", malas.isEmpty())
    }

    @Test fun coordenadasDentroDeChile() {
        comunas.forEach {
            // Continental + insular (Rapa Nui ~ -109°, Juan Fernández ~ -79°) + Antártica
            assertTrue("lat fuera de rango: $it", it.lat in -90f..-17f)
            assertTrue("lon fuera de rango: $it", it.lon in -110f..-59f)
        }
    }

    @Test fun comunasDeRegion_ordenadasAlfabeticamente() {
        regiones.forEach { r ->
            val lista = ComunasData.comunasDeRegion(r.tgrCode).map { it.nombre }
            assertEquals("región ${r.nombre}", lista.sorted(), lista)
        }
    }

    @Test fun comunasDeRegion_codigoInexistente() {
        assertTrue(ComunasData.comunasDeRegion("99").isEmpty())
    }

    @Test fun buscarPorCodigo() {
        assertEquals("Santiago", ComunasData.buscarPorCodigo("13101")?.nombre)
        assertNull(ComunasData.buscarPorCodigo("00000"))
    }
}
