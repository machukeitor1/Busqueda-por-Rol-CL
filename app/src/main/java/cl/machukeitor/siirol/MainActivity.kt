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

    data class Region(val nombre: String, val tgrCode: String)

    data class Comuna(
        val codigoSii: String,
        val nombre: String,
        val nombreApi: String,
        val tgrRegion: String,
        val tgrComuna: String,
        val lat: Float,
        val lon: Float
    )

    // ── Regiones (norte → sur) ────────────────────────────────────────────────

    private val regiones = listOf(
        Region("XV - Arica y Parinacota",  "15"),
        Region("I - Tarapacá",             "1"),
        Region("II - Antofagasta",         "2"),
        Region("III - Atacama",            "3"),
        Region("IV - Coquimbo",            "4"),
        Region("V - Valparaíso",           "5"),
        Region("RM - Metropolitana",       "13"),
        Region("VI - O'Higgins",           "6"),
        Region("VII - Maule",              "7"),
        Region("XVI - Ñuble",              "16"),
        Region("VIII - Biobío",            "8"),
        Region("IX - La Araucanía",        "9"),
        Region("XIV - Los Ríos",           "14"),
        Region("X - Los Lagos",            "10"),
        Region("XI - Aysén",               "11"),
        Region("XII - Magallanes",         "12")
    )

    // ── Todas las comunas (~344) ──────────────────────────────────────────────
    // codigoSii = código INE/SII sin cero inicial (4 o 5 dígitos)
    // Regiones XVI y antiguas prov. Ñuble usan codigos 81xx (antiguo sistema SII)
    // Región VIII actual usa 82xx (Arauco), 83xx (Concepción), 84xx (Biobío)
    // Región IX: Prov. Malleco 91xx, Prov. Cautín 92xx

    private val todasLasComunas = listOf(
        // ── XV Arica y Parinacota ─────────────────────────────────────────────
        Comuna("15101", "Arica",         "ARICA",         "15", "1",   -18.477f,  -70.322f),
        Comuna("15102", "Camarones",     "CAMARONES",     "15", "295", -19.009f,  -69.907f),
        Comuna("15201", "Putre",         "PUTRE",         "15", "294", -18.180f,  -69.554f),
        Comuna("15202", "Gral. Lagos",   "GRAL LAGOS",    "15", "293", -17.832f,  -69.609f),

        // ── I Tarapacá ────────────────────────────────────────────────────────
        Comuna("1101",  "Iquique",       "IQUIQUE",       "1",  "2",   -20.223f,  -70.146f),
        Comuna("1107",  "Alto Hospicio", "ALTO HOSPICIO", "1",  "347", -20.268f,  -70.101f),
        Comuna("1401",  "Pozo Almonte",  "POZO ALMONTE",  "1",  "5",   -20.253f,  -69.785f),
        Comuna("1402",  "Camiña",        "CAMINA",        "1",  "296", -19.312f,  -69.426f),
        Comuna("1403",  "Colchane",      "COLCHANE",      "1",  "297", -19.279f,  -68.634f),
        Comuna("1404",  "Huara",         "HUARA",         "1",  "3",   -19.996f,  -69.772f),
        Comuna("1405",  "Pica",          "PICA",          "1",  "4",   -20.489f,  -69.329f),

        // ── II Antofagasta ────────────────────────────────────────────────────
        Comuna("2101",  "Antofagasta",           "ANTOFAGASTA",           "2", "7",   -23.650f, -70.407f),
        Comuna("2102",  "Mejillones",            "MEJILLONES",            "2", "8",   -23.096f, -70.450f),
        Comuna("2103",  "Sierra Gorda",          "SIERRA GORDA",          "2", "299", -22.892f, -69.320f),
        Comuna("2104",  "Taltal",                "TALTAL",                "2", "9",   -25.405f, -70.483f),
        Comuna("2201",  "Calama",                "CALAMA",                "2", "10",  -22.454f, -68.934f),
        Comuna("2202",  "Ollagüe",               "OLLAGUE",               "2", "300", -21.224f, -68.253f),
        Comuna("2203",  "San Pedro de Atacama",  "SAN PEDRO DE ATACAMA",  "2", "301", -22.916f, -68.200f),
        Comuna("2301",  "Tocopilla",             "TOCOPILLA",             "2", "6",   -22.086f, -70.193f),
        Comuna("2302",  "María Elena",           "MARIA ELENA",           "2", "298", -22.164f, -69.417f),

        // ── III Atacama ───────────────────────────────────────────────────────
        Comuna("3101",  "Copiapó",         "COPIAPO",         "3", "13",  -27.365f, -70.331f),
        Comuna("3102",  "Caldera",         "CALDERA",         "3", "14",  -27.067f, -70.817f),
        Comuna("3103",  "Tierra Amarilla", "TIERRA AMARILLA", "3", "15",  -27.488f, -70.270f),
        Comuna("3201",  "Chañaral",        "CHANARAL",        "3", "11",  -26.343f, -70.611f),
        Comuna("3202",  "Diego de Almagro","DIEGO DE ALMAGRO","3", "12",  -26.377f, -70.049f),
        Comuna("3301",  "Vallenar",        "VALLENAR",        "3", "16",  -28.578f, -70.757f),
        Comuna("3302",  "Alto del Carmen", "ALTO DEL CARMEN", "3", "302", -28.751f, -70.488f),
        Comuna("3303",  "Freirina",        "FREIRINA",        "3", "17",  -28.500f, -71.076f),
        Comuna("3304",  "Huasco",          "HUASCO",          "3", "18",  -28.452f, -71.224f),

        // ── IV Coquimbo ───────────────────────────────────────────────────────
        Comuna("4101",  "La Serena",    "LA SERENA",    "4", "19",  -29.897f, -71.242f),
        Comuna("4102",  "Coquimbo",     "COQUIMBO",     "4", "21",  -29.968f, -71.337f),
        Comuna("4103",  "Andacollo",    "ANDACOLLO",    "4", "22",  -30.236f, -71.083f),
        Comuna("4104",  "La Higuera",   "LA HIGUERA",   "4", "20",  -29.497f, -71.266f),
        Comuna("4105",  "Paiguano",     "PAIHUANO",     "4", "24",  -30.250f, -70.383f),
        Comuna("4106",  "Vicuña",       "VICUNA",       "4", "23",  -30.029f, -70.711f),
        Comuna("4201",  "Illapel",      "ILLAPEL",      "4", "30",  -31.624f, -71.163f),
        Comuna("4202",  "Canela",       "CANELA",       "4", "31",  -31.394f, -71.458f),
        Comuna("4203",  "Los Vilos",    "LOS VILOS",    "4", "33",  -31.916f, -71.511f),
        Comuna("4204",  "Salamanca",    "SALAMANCA",    "4", "32",  -31.774f, -70.972f),
        Comuna("4301",  "Ovalle",       "OVALLE",       "4", "25",  -30.594f, -71.198f),
        Comuna("4302",  "Combarbalá",   "COMBARBALA",   "4", "29",  -31.176f, -70.998f),
        Comuna("4303",  "Monte Patria", "MONTE PATRIA", "4", "26",  -30.829f, -70.698f),
        Comuna("4304",  "Punitaqui",    "PUNITAQUI",    "4", "27",  -30.826f, -71.259f),
        Comuna("4305",  "Río Hurtado",  "RIO HURTADO",  "4", "28",  -30.260f, -70.667f),

        // ── V Valparaíso ──────────────────────────────────────────────────────
        Comuna("5101",  "Valparaíso",    "VALPARAISO",    "5", "34",  -33.044f, -71.623f),
        Comuna("5102",  "Casablanca",    "CASABLANCA",    "5", "40",  -33.326f, -71.398f),
        Comuna("5103",  "Concón",        "CONCON",        "5", "340", -32.931f, -71.519f),
        Comuna("5104",  "Juan Fernández","JUAN FERNANDEZ","5", "321", -33.617f, -78.867f),
        Comuna("5105",  "Puchuncaví",    "PUCHUNCAVI",    "5", "36",  -32.750f, -71.396f),
        Comuna("5107",  "Quintero",      "QUINTERO",      "5", "35",  -32.787f, -71.527f),
        Comuna("5109",  "Viña del Mar",  "VINA DEL MAR",  "5", "37",  -33.045f, -71.522f),
        Comuna("5201",  "Isla de Pascua","ISLA DE PASCUA","5", "41",  -27.150f, -109.423f),
        Comuna("5301",  "Los Andes",     "LOS ANDES",     "5", "66",  -32.835f, -70.597f),
        Comuna("5302",  "Calle Larga",   "CALLE LARGA",   "5", "67",  -32.951f, -70.552f),
        Comuna("5303",  "Rinconada",     "RINCONADA",     "5", "68",  -32.877f, -70.709f),
        Comuna("5304",  "San Esteban",   "SAN ESTEBAN",   "5", "69",  -32.693f, -70.370f),
        Comuna("5401",  "La Ligua",      "LA LIGUA",      "5", "59",  -32.449f, -71.231f),
        Comuna("5402",  "Cabildo",       "CABILDO",       "5", "56",  -32.410f, -71.080f),
        Comuna("5403",  "Papudo",        "PAPUDO",        "5", "57",  -32.470f, -71.384f),
        Comuna("5404",  "Petorca",       "PETORCA",       "5", "55",  -32.197f, -70.832f),
        Comuna("5405",  "Zapallar",      "ZAPALLAR",      "5", "58",  -32.593f, -71.369f),
        Comuna("5501",  "Quillota",      "QUILLOTA",      "5", "48",  -32.879f, -71.251f),
        Comuna("5502",  "La Calera",     "LA CALERA",     "5", "50",  -32.784f, -71.159f),
        Comuna("5503",  "Hijuelas",      "HIJUELAS",      "5", "51",  -32.867f, -71.093f),
        Comuna("5504",  "La Cruz",       "LA CRUZ",       "5", "49",  -32.826f, -71.259f),
        Comuna("5506",  "Nogales",       "NOGALES",       "5", "52",  -32.692f, -71.189f),
        Comuna("5601",  "San Antonio",   "SAN ANTONIO",   "5", "42",  -33.581f, -71.613f),
        Comuna("5602",  "Algarrobo",     "ALGARROBO",     "5", "44",  -33.333f, -71.602f),
        Comuna("5603",  "Cartagena",     "CARTAGENA",     "5", "46",  -33.534f, -71.463f),
        Comuna("5604",  "El Quisco",     "EL QUISCO",     "5", "45",  -33.416f, -71.656f),
        Comuna("5605",  "El Tabo",       "EL TABO",       "5", "47",  -33.485f, -71.586f),
        Comuna("5606",  "Santo Domingo", "SANTO DOMINGO", "5", "43",  -33.708f, -71.630f),
        Comuna("5701",  "San Felipe",    "SAN FELIPE",    "5", "60",  -32.746f, -70.749f),
        Comuna("5702",  "Catemu",        "CATEMU",        "5", "63",  -32.698f, -70.956f),
        Comuna("5703",  "Llaillay",      "LLAY LLAY",     "5", "65",  -32.890f, -70.894f),
        Comuna("5704",  "Panquehue",     "PANQUEHUE",     "5", "62",  -32.808f, -70.843f),
        Comuna("5705",  "Putaendo",      "PUTAENDO",      "5", "61",  -32.628f, -70.717f),
        Comuna("5706",  "Santa María",   "SANTA MARIA",   "5", "64",  -32.745f, -70.654f),
        Comuna("5801",  "Quilpué",       "QUILPUE",       "5", "38",  -33.049f, -71.444f),
        Comuna("5802",  "Limache",       "LIMACHE",       "5", "53",  -33.004f, -71.261f),
        Comuna("5803",  "Olmué",         "OLMUE",         "5", "54",  -33.013f, -71.153f),
        Comuna("5804",  "Villa Alemana", "VILLA ALEMANA", "5", "39",  -33.043f, -71.372f),

        // ── RM Metropolitana ──────────────────────────────────────────────────
        Comuna("13101", "Santiago",          "SANTIAGO",          "13", "70",  -33.442f, -70.654f),
        Comuna("13102", "Cerrillos",         "CERRILLOS",         "13", "333", -33.497f, -70.711f),
        Comuna("13103", "Cerro Navia",       "CERRO NAVIA",       "13", "324", -33.427f, -70.743f),
        Comuna("13104", "Conchalí",          "CONCHALI",          "13", "75",  -33.386f, -70.673f),
        Comuna("13105", "El Bosque",         "EL BOSQUE",         "13", "338", -33.564f, -70.671f),
        Comuna("13106", "Estación Central",  "ESTACION CENTRAL",  "13", "328", -33.450f, -70.675f),
        Comuna("13107", "Huechuraba",        "HUECHURABA",        "13", "334", -33.367f, -70.632f),
        Comuna("13108", "Independencia",     "INDEPENDENCIA",     "13", "330", -33.420f, -70.663f),
        Comuna("13109", "La Cisterna",       "LA CISTERNA",       "13", "96",  -33.538f, -70.661f),
        Comuna("13110", "La Florida",        "LA FLORIDA",        "13", "93",  -33.523f, -70.595f),
        Comuna("13111", "La Granja",         "LA GRANJA",         "13", "97",  -33.537f, -70.619f),
        Comuna("13112", "La Pintana",        "LA PINTANA",        "13", "327", -33.590f, -70.632f),
        Comuna("13113", "La Reina",          "LA REINA",          "13", "92",  -33.457f, -70.535f),
        Comuna("13114", "Las Condes",        "LAS CONDES",        "13", "71",  -33.415f, -70.584f),
        Comuna("13115", "Lo Barnechea",      "LO BARNECHEA",      "13", "332", -33.299f, -70.375f),
        Comuna("13116", "Lo Espejo",         "LO ESPEJO",         "13", "337", -33.525f, -70.692f),
        Comuna("13117", "Lo Prado",          "LO PRADO",          "13", "325", -33.449f, -70.721f),
        Comuna("13118", "Macul",             "MACUL",             "13", "323", -33.492f, -70.597f),
        Comuna("13119", "Maipú",             "MAIPU",             "13", "94",  -33.521f, -70.757f),
        Comuna("13120", "Ñuñoa",             "NUNOA",             "13", "91",  -33.461f, -70.593f),
        Comuna("13121", "Pedro Aguirre Cerda","PEDRO AGUIRRE CERDA","13","336", -33.489f, -70.673f),
        Comuna("13122", "Peñalolén",         "PENALOLEN",         "13", "322", -33.490f, -70.511f),
        Comuna("13123", "Providencia",       "PROVIDENCIA",       "13", "72",  -33.421f, -70.603f),
        Comuna("13124", "Pudahuel",          "PUDAHUEL",          "13", "82",  -33.418f, -70.832f),
        Comuna("13125", "Quilicura",         "QUILICURA",         "13", "79",  -33.355f, -70.728f),
        Comuna("13126", "Quinta Normal",     "QUINTA NORMAL",     "13", "81",  -33.428f, -70.696f),
        Comuna("13127", "Recoleta",          "RECOLETA",          "13", "329", -33.417f, -70.630f),
        Comuna("13128", "Renca",             "RENCA",             "13", "77",  -33.414f, -70.713f),
        Comuna("13129", "San Joaquín",       "SAN JOAQUIN",       "13", "335", -33.496f, -70.625f),
        Comuna("13130", "San Miguel",        "SAN MIGUEL",        "13", "95",  -33.502f, -70.649f),
        Comuna("13131", "San Ramón",         "SAN RAMON",         "13", "326", -33.535f, -70.639f),
        Comuna("13132", "Vitacura",          "VITACURA",          "13", "331", -33.386f, -70.570f),
        Comuna("13201", "Puente Alto",       "PUENTE ALTO",       "13", "100", -33.608f, -70.578f),
        Comuna("13202", "Pirque",            "PIRQUE",            "13", "101", -33.738f, -70.491f),
        Comuna("13203", "San José de Maipo", "SAN JOSE DE MAIPO", "13", "102", -33.692f, -70.133f),
        Comuna("13301", "Colina",            "COLINA",            "13", "76",  -33.200f, -70.670f),
        Comuna("13302", "Lampa",             "LAMPA",             "13", "78",  -33.286f, -70.879f),
        Comuna("13303", "Tiltil",            "TIL-TIL",           "13", "80",  -33.066f, -70.847f),
        Comuna("13401", "San Bernardo",      "SAN BERNARDO",      "13", "98",  -33.591f, -70.702f),
        Comuna("13402", "Buin",              "BUIN",              "13", "103", -33.754f, -70.716f),
        Comuna("13403", "Calera de Tango",   "CALERA DE TANGO",   "13", "99",  -33.633f, -70.782f),
        Comuna("13404", "Paine",             "PAINE",             "13", "104", -33.867f, -70.730f),
        Comuna("13501", "Melipilla",         "MELIPILLA",         "13", "88",  -33.687f, -71.214f),
        Comuna("13502", "Alhué",             "ALHUE",             "13", "109", -34.036f, -71.028f),
        Comuna("13503", "Curacaví",          "CURACAVI",          "13", "83",  -33.406f, -71.133f),
        Comuna("13504", "María Pinto",       "MARIA PINTO",       "13", "90",  -33.515f, -71.119f),
        Comuna("13505", "San Pedro",         "SAN PEDRO",         "13", "108", -33.878f, -71.461f),
        Comuna("13601", "Talagante",         "TALAGANTE",         "13", "86",  -33.664f, -70.930f),
        Comuna("13602", "El Monte",          "EL MONTE",          "13", "89",  -33.666f, -71.029f),
        Comuna("13603", "Isla de Maipo",     "ISLA DE MAIPO",     "13", "87",  -33.754f, -70.886f),
        Comuna("13604", "Padre Hurtado",     "PADRE HURTADO",     "13", "339", -33.576f, -70.800f),
        Comuna("13605", "Peñaflor",          "PENAFLOR",          "13", "85",  -33.614f, -70.888f),

        // ── VI O'Higgins ──────────────────────────────────────────────────────
        Comuna("6101",  "Rancagua",          "RANCAGUA",          "6", "105", -34.162f, -70.741f),
        Comuna("6102",  "Codegua",           "CODEGUA",           "6", "110", -34.044f, -70.513f),
        Comuna("6103",  "Coinco",            "COINCO",            "6", "114", -34.292f, -70.971f),
        Comuna("6104",  "Coltauco",          "COLTAUCO",          "6", "113", -34.250f, -71.079f),
        Comuna("6105",  "Donihue",           "DONIHUE",           "6", "112", -34.202f, -70.933f),
        Comuna("6106",  "Graneros",          "GRANEROS",          "6", "107", -34.071f, -70.750f),
        Comuna("6107",  "Las Cabras",        "LAS CABRAS",        "6", "116", -34.295f, -71.307f),
        Comuna("6108",  "Machalí",           "MACHALI",           "6", "106", -34.294f, -70.337f),
        Comuna("6109",  "Malloa",            "MALLOA",            "6", "122", -34.446f, -70.945f),
        Comuna("6110",  "Mostazal",          "SAN FCO MOSTAZAL",  "6", "111", -33.977f, -70.709f),
        Comuna("6111",  "Olivar",            "OLIVAR",            "6", "120", -34.219f, -70.836f),
        Comuna("6112",  "Peumo",             "PEUMO",             "6", "115", -34.380f, -71.169f),
        Comuna("6113",  "Pichidegua",        "PICHIDEGUA",        "6", "118", -34.376f, -71.347f),
        Comuna("6114",  "Quinta de Tilcoco", "QUINTA DE TILCOCO", "6", "123", -34.367f, -71.010f),
        Comuna("6115",  "Rengo",             "RENGO",             "6", "121", -34.402f, -70.856f),
        Comuna("6116",  "Requínoa",          "REQUINOA",          "6", "119", -34.353f, -70.680f),
        Comuna("6117",  "San Vicente",       "SAN VICENTE",       "6", "117", -34.438f, -71.079f),
        Comuna("6201",  "Pichilemu",         "PICHILEMU",         "6", "137", -34.387f, -72.003f),
        Comuna("6202",  "La Estrella",       "LA ESTRELLA",       "6", "139", -34.204f, -71.607f),
        Comuna("6203",  "Litueche",          "LITUECHE",          "6", "136", -34.107f, -71.720f),
        Comuna("6204",  "Marchihue",         "MARCHIGUE",         "6", "134", -34.398f, -71.614f),
        Comuna("6205",  "Navidad",           "NAVIDAD",           "6", "138", -34.007f, -71.810f),
        Comuna("6206",  "Paredones",         "PAREDONES",         "6", "133", -34.697f, -71.898f),
        Comuna("6301",  "San Fernando",      "SAN FERNANDO",      "6", "124", -34.584f, -70.987f),
        Comuna("6302",  "Chépica",           "CHEPICA",           "6", "132", -34.730f, -71.269f),
        Comuna("6303",  "Chimbarongo",       "CHIMBARONGO",       "6", "125", -34.755f, -70.975f),
        Comuna("6304",  "Lolol",             "LOLOL",             "6", "129", -34.769f, -71.645f),
        Comuna("6305",  "Nancagua",          "NANCAGUA",          "6", "126", -34.662f, -71.175f),
        Comuna("6306",  "Palmilla",          "PALMILLA",          "6", "130", -34.604f, -71.358f),
        Comuna("6307",  "Peralillo",         "PERALILLO",         "6", "131", -34.459f, -71.500f),
        Comuna("6308",  "Placilla",          "PLACILLA",          "6", "127", -34.614f, -71.095f),
        Comuna("6309",  "Pumanque",          "PUMANQUE",          "6", "135", -34.605f, -71.644f),
        Comuna("6310",  "Santa Cruz",        "SANTA CRUZ",        "6", "128", -34.638f, -71.367f),

        // ── VII Maule ─────────────────────────────────────────────────────────
        // Provincia Curicó (71xx)
        Comuna("7101",  "Curicó",         "CURICO",         "7", "140", -34.976f, -71.224f),
        Comuna("7102",  "Hualañé",        "HUALANE",        "7", "144", -34.976f, -71.804f),
        Comuna("7103",  "Licantén",       "LICANTEN",       "7", "145", -34.959f, -72.027f),
        Comuna("7104",  "Molina",         "MOLINA",         "7", "147", -35.090f, -71.279f),
        Comuna("7105",  "Rauco",          "RAUCO",          "7", "143", -34.930f, -71.311f),
        Comuna("7106",  "Romeral",        "ROMERAL",        "7", "141", -34.963f, -71.121f),
        Comuna("7107",  "Sagrada Familia","SAGRADA FAMILIA","7", "148", -34.995f, -71.380f),
        Comuna("7108",  "Teno",           "TENO",           "7", "142", -34.870f, -71.090f),
        Comuna("7109",  "Vichuquén",      "VICHUQUEN",      "7", "146", -34.859f, -72.007f),
        // Provincia Talca (72xx)
        Comuna("7201",  "Talca",          "TALCA",          "7", "150", -35.429f, -71.661f),
        Comuna("7202",  "Constitución",   "CONSTITUCION",   "7", "157", -35.331f, -72.414f),
        Comuna("7203",  "Curepto",        "CUREPTO",        "7", "155", -35.091f, -72.022f),
        Comuna("7204",  "Empedrado",      "EMPEDRADO",      "7", "158", -35.621f, -72.247f),
        Comuna("7205",  "Maule",          "MAULE",          "7", "154", -35.506f, -71.707f),
        Comuna("7206",  "Pelarco",        "PELARCO",        "7", "152", -35.372f, -71.328f),
        Comuna("7207",  "Pencahue",       "PENCAHUE",       "7", "153", -35.305f, -71.828f),
        Comuna("7208",  "Río Claro",      "RIO CLARO",      "7", "149", -35.283f, -71.267f),
        Comuna("7209",  "San Clemente",   "SAN CLEMENTE",   "7", "151", -35.534f, -71.487f),
        Comuna("7210",  "San Rafael",     "SAN RAFAEL",     "7", "341", -35.294f, -71.525f),
        // Provincia Linares (73xx)
        Comuna("7301",  "Linares",        "LINARES",        "7", "159", -35.850f, -71.585f),
        Comuna("7302",  "Colbún",         "COLBUN",         "7", "161", -35.693f, -71.407f),
        Comuna("7303",  "Longaví",        "LONGAVI",        "7", "162", -35.966f, -71.682f),
        Comuna("7304",  "Parral",         "PARRAL",         "7", "164", -36.140f, -71.824f),
        Comuna("7305",  "Retiro",         "RETIRO",         "7", "165", -36.046f, -71.759f),
        Comuna("7306",  "San Javier",     "SAN JAVIER",     "7", "156", -35.604f, -71.736f),
        Comuna("7307",  "Villa Alegre",   "VILLA ALEGRE",   "7", "163", -35.687f, -71.670f),
        Comuna("7308",  "Yerbas Buenas",  "YERBAS BUENAS",  "7", "160", -35.688f, -71.564f),
        // Provincia Cauquenes (74xx)
        Comuna("7401",  "Cauquenes",      "CAUQUENES",      "7", "166", -35.974f, -72.314f),
        Comuna("7402",  "Chanco",         "CHANCO",         "7", "167", -35.734f, -72.533f),
        Comuna("7403",  "Pelluhue",       "PELLUHUE",       "7", "320", -35.815f, -72.574f),

        // ── XVI Ñuble (SII usa códigos 81xx del antiguo sistema) ──────────────
        Comuna("8101",  "Chillán",        "CHILLÁN",        "16", "168", -36.601f, -72.109f),
        Comuna("8121",  "Chillán Viejo",  "CHILLAN VIEJO",  "16", "342", -36.633f, -72.140f),
        Comuna("8102",  "Bulnes",         "BULNES",         "16", "180", -36.742f, -72.302f),
        Comuna("8106",  "El Carmen",      "EL CARMEN",      "16", "185", -36.896f, -72.022f),
        Comuna("8109",  "Pemuco",         "PEMUCO",         "16", "184", -36.987f, -72.019f),
        Comuna("8110",  "Pinto",          "PINTO",          "16", "169", -36.698f, -71.893f),
        Comuna("8112",  "Quillón",        "QUILLON",        "16", "182", -36.738f, -72.469f),
        Comuna("8117",  "San Ignacio",    "SAN IGNACIO",    "16", "181", -36.819f, -71.988f),
        Comuna("8120",  "Yungay",         "YUNGAY",         "16", "183", -37.122f, -72.019f),
        Comuna("8103",  "Cobquecura",     "COBQUECURA",     "16", "175", -36.132f, -72.791f),
        Comuna("8104",  "Coelemu",        "COELEMU",        "16", "186", -36.488f, -72.702f),
        Comuna("8107",  "Ninhue",         "NINHUE",         "16", "174", -36.401f, -72.397f),
        Comuna("8111",  "Portezuelo",     "PORTEZUELO",     "16", "171", -36.529f, -72.433f),
        Comuna("8113",  "Quirihue",       "QUIRIHUE",       "16", "172", -36.284f, -72.541f),
        Comuna("8114",  "Ranquil",        "RANQUIL",        "16", "187", -36.649f, -72.606f),
        Comuna("8119",  "Trehuaco",       "TREHUACO",       "16", "173", -36.410f, -72.660f),
        Comuna("8105",  "Coihueco",       "COIHUECO",       "16", "170", -36.617f, -71.834f),
        Comuna("8108",  "Ñiquén",         "NIQUEN",         "16", "177", -36.285f, -71.899f),
        Comuna("8115",  "San Carlos",     "SAN CARLOS",     "16", "176", -36.422f, -71.959f),
        Comuna("8116",  "San Fabián",     "SAN FABIAN",     "16", "178", -36.554f, -71.549f),
        Comuna("8118",  "San Nicolás",    "SAN NICOLAS",    "16", "179", -36.500f, -72.213f),

        // ── VIII Biobío (actual, sin Ñuble) ───────────────────────────────────
        // Provincia Arauco (82xx)
        Comuna("8201",  "Lebu",              "LEBU",              "8", "199", -37.608f, -73.651f),
        Comuna("8202",  "Arauco",            "ARAUCO",            "8", "198", -37.257f, -73.284f),
        Comuna("8203",  "Cañete",            "CANETE",            "8", "201", -37.804f, -73.402f),
        Comuna("8204",  "Contulmo",          "CONTULMO",          "8", "202", -38.026f, -73.258f),
        Comuna("8205",  "Curanilahue",       "CURANILAHUE",       "8", "197", -37.476f, -73.353f),
        Comuna("8206",  "Los Álamos",        "LOS ALAMOS",        "8", "200", -37.675f, -73.390f),
        Comuna("8207",  "Tirúa",             "TIRUA",             "8", "203", -38.332f, -73.379f),
        // Provincia Concepción (83xx)
        Comuna("8301",  "Concepción",        "CONCEPCION",        "8", "188", -36.815f, -73.029f),
        Comuna("8302",  "Coronel",           "CORONEL",           "8", "194", -37.027f, -73.150f),
        Comuna("8303",  "Chiguayante",       "CHIGUAYANTE",       "8", "344", -36.905f, -73.016f),
        Comuna("8304",  "Florida",           "FLORIDA",           "8", "193", -36.821f, -72.662f),
        Comuna("8305",  "Hualqui",           "HUALQUI",           "8", "192", -37.015f, -72.866f),
        Comuna("8306",  "Lota",              "LOTA",              "8", "195", -37.091f, -73.155f),
        Comuna("8307",  "Penco",             "PENCO",             "8", "191", -36.742f, -72.998f),
        Comuna("8308",  "San Pedro de la Paz","SAN PEDRO DE LA PAZ","8","343", -36.864f, -73.109f),
        Comuna("8309",  "Santa Juana",       "SANTA JUANA",       "8", "196", -37.173f, -72.935f),
        Comuna("8310",  "Talcahuano",        "TALCAHUANO",        "8", "189", -36.736f, -73.105f),
        Comuna("8311",  "Tomé",              "TOME",              "8", "190", -36.618f, -72.958f),
        Comuna("8312",  "Hualpén",           "HUALPEN",           "8", "346", -36.783f, -73.145f),
        // Provincia Biobío (84xx)
        Comuna("8401",  "Los Ángeles",       "LOS ANGELES",       "8", "204", -37.473f, -72.351f),
        Comuna("8402",  "Antuco",            "ANTUCO",            "8", "303", -37.327f, -71.678f),
        Comuna("8403",  "Cabrero",           "CABRERO",           "8", "208", -37.037f, -72.406f),
        Comuna("8404",  "Laja",              "LAJA",              "8", "210", -37.277f, -72.717f),
        Comuna("8405",  "Mulchén",           "MULCHEN",           "8", "214", -37.715f, -72.239f),
        Comuna("8406",  "Nacimiento",        "NACIMIENTO",        "8", "212", -37.501f, -72.676f),
        Comuna("8407",  "Negrete",           "NEGRETE",           "8", "213", -37.597f, -72.565f),
        Comuna("8408",  "Quilaco",           "QUILACO",           "8", "215", -37.680f, -72.007f),
        Comuna("8409",  "Quilleco",          "QUILLECO",          "8", "206", -37.434f, -71.876f),
        Comuna("8410",  "San Rosendo",       "SAN ROSENDO",       "8", "211", -37.202f, -72.748f),
        Comuna("8411",  "Santa Bárbara",     "SANTA BARBARA",     "8", "205", -37.663f, -72.018f),
        Comuna("8412",  "Tucapel",           "TUCAPEL",           "8", "209", -37.290f, -71.949f),
        Comuna("8413",  "Yumbel",            "YUMBEL",            "8", "207", -37.096f, -72.556f),
        Comuna("8414",  "Alto Biobío",       "ALTO BIO BIO",      "8", "349", -37.871f, -71.611f),

        // ── IX La Araucanía ───────────────────────────────────────────────────
        // Provincia Malleco (91xx)
        Comuna("9101",  "Angol",          "ANGOL",          "9", "216", -37.803f, -72.702f),
        Comuna("9102",  "Collipulli",     "COLLIPULLI",     "9", "220", -37.953f, -72.432f),
        Comuna("9103",  "Curacautín",     "CURACAUTIN",     "9", "225", -38.432f, -71.890f),
        Comuna("9104",  "Ercilla",        "ERCILLA",        "9", "221", -38.059f, -72.358f),
        Comuna("9105",  "Lonquimay",      "LONQUIMAY",      "9", "226", -38.450f, -71.374f),
        Comuna("9106",  "Los Sauces",     "LOS SAUCES",     "9", "218", -37.975f, -72.829f),
        Comuna("9107",  "Lumaco",         "LUMACO",         "9", "223", -38.164f, -72.892f),
        Comuna("9108",  "Purén",          "PUREN",          "9", "217", -38.033f, -73.073f),
        Comuna("9109",  "Renaico",        "RENAICO",        "9", "219", -37.665f, -72.569f),
        Comuna("9110",  "Traiguén",       "TRAIGUEN",       "9", "222", -38.251f, -72.665f),
        Comuna("9111",  "Victoria",       "VICTORIA",       "9", "224", -38.234f, -72.333f),
        // Provincia Cautín (92xx)
        Comuna("9201",  "Temuco",         "TEMUCO",         "9", "227", -38.736f, -72.599f),
        Comuna("9202",  "Carahue",        "CARAHUE",        "9", "235", -38.712f, -73.165f),
        Comuna("9203",  "Cunco",          "CUNCO",          "9", "230", -38.931f, -72.026f),
        Comuna("9204",  "Curarrehue",     "CURARREHUE",     "9", "305", -39.359f, -71.588f),
        Comuna("9205",  "Freire",         "FREIRE",         "9", "229", -38.954f, -72.622f),
        Comuna("9206",  "Galvarino",      "GALVARINO",      "9", "232", -38.409f, -72.780f),
        Comuna("9207",  "Gorbea",         "GORBEA",         "9", "238", -39.095f, -72.672f),
        Comuna("9208",  "Lautaro",        "LAUTARO",        "9", "231", -38.529f, -72.427f),
        Comuna("9209",  "Loncoche",       "LONCOCHE",       "9", "240", -39.368f, -72.632f),
        Comuna("9210",  "Melipeuco",      "MELIPEUCO",      "9", "304", -38.843f, -71.687f),
        Comuna("9211",  "Nueva Imperial", "NUEVA IMPERIAL", "9", "234", -38.745f, -72.948f),
        Comuna("9212",  "Padre las Casas","PADRE LAS CASAS","9", "345", -38.766f, -72.593f),
        Comuna("9213",  "Perquenco",      "PERQUENCO",      "9", "233", -38.415f, -72.373f),
        Comuna("9214",  "Pitrufquén",     "PITRUFQUEN",     "9", "237", -38.983f, -72.643f),
        Comuna("9215",  "Pucón",          "PUCON",          "9", "242", -39.282f, -71.955f),
        Comuna("9216",  "Saavedra",       "PUERTO SAAVEDRA","9", "236", -38.780f, -73.390f),
        Comuna("9217",  "Teodoro Schmidt","TEODORO SCHMIDT","9", "306", -38.999f, -73.093f),
        Comuna("9218",  "Toltén",         "TOLTEN",         "9", "239", -39.202f, -73.200f),
        Comuna("9219",  "Vilcún",         "VILCUN",         "9", "228", -38.670f, -72.270f),
        Comuna("9220",  "Villarrica",     "VILLARRICA",     "9", "241", -39.280f, -72.218f),
        Comuna("9221",  "Cholchol",       "CHOLCHOL",       "9", "348", -38.596f, -72.845f),

        // ── XIV Los Ríos ──────────────────────────────────────────────────────
        Comuna("14101", "Valdivia",    "VALDIVIA",    "14", "243", -39.820f, -73.246f),
        Comuna("14102", "Corral",      "CORRAL",      "14", "244", -39.889f, -73.433f),
        Comuna("14103", "Lanco",       "LANCO",       "14", "249", -39.452f, -72.775f),
        Comuna("14104", "Los Lagos",   "LOS LAGOS",   "14", "247", -39.864f, -72.812f),
        Comuna("14105", "Máfil",       "MAFIL",       "14", "246", -39.665f, -72.958f),
        Comuna("14106", "Mariquina",   "MARIQUINA",   "14", "245", -39.540f, -72.962f),
        Comuna("14107", "Paillaco",    "PAILLACO",    "14", "252", -40.071f, -72.871f),
        Comuna("14108", "Panguipulli", "PANGUIPULLI", "14", "250", -39.644f, -72.337f),
        Comuna("14201", "La Unión",    "LA UNION",    "14", "251", -40.295f, -73.083f),
        Comuna("14202", "Futrono",     "FUTRONO",     "14", "248", -40.124f, -72.393f),
        Comuna("14203", "Lago Ranco",  "LAGO RANCO",  "14", "254", -40.312f, -72.500f),
        Comuna("14204", "Río Bueno",   "RIO BUENO",   "14", "253", -40.333f, -72.951f),

        // ── X Los Lagos ───────────────────────────────────────────────────────
        Comuna("10101", "Puerto Montt",       "PUERTO MONTT",       "10", "261", -41.463f, -72.931f),
        Comuna("10102", "Calbuco",            "CALBUCO",            "10", "265", -41.778f, -73.142f),
        Comuna("10103", "Cochamó",            "COCHAMO",            "10", "262", -41.488f, -72.304f),
        Comuna("10104", "Fresia",             "FRESIA",             "10", "268", -41.154f, -73.422f),
        Comuna("10105", "Frutillar",          "FRUTILLAR",          "10", "269", -41.117f, -73.050f),
        Comuna("10106", "Los Muermos",        "LOS MUERMOS",        "10", "264", -41.400f, -73.465f),
        Comuna("10107", "Llanquihue",         "LLANQUIHUE",         "10", "267", -41.257f, -73.005f),
        Comuna("10108", "Maullín",            "MAULLIN",            "10", "263", -41.617f, -73.596f),
        Comuna("10109", "Puerto Varas",       "PUERTO VARAS",       "10", "266", -41.316f, -72.984f),
        Comuna("10201", "Castro",             "CASTRO",             "10", "270", -42.480f, -73.763f),
        Comuna("10202", "Ancud",              "ANCUD",              "10", "277", -41.866f, -73.834f),
        Comuna("10203", "Chonchi",            "CHONCHI",            "10", "271", -42.623f, -73.774f),
        Comuna("10204", "Curaco de Vélez",    "CURACO DE VELEZ",    "10", "276", -42.440f, -73.604f),
        Comuna("10205", "Dalcahue",           "DALCAHUE",           "10", "279", -42.378f, -73.652f),
        Comuna("10206", "Puqueldón",          "PUQUELDON",          "10", "274", -42.602f, -73.671f),
        Comuna("10207", "Queilén",            "QUEILEN",            "10", "272", -42.900f, -73.483f),
        Comuna("10208", "Quellón",            "QUELLON",            "10", "273", -43.116f, -73.617f),
        Comuna("10209", "Quemchi",            "QUEMCHI",            "10", "278", -42.143f, -73.475f),
        Comuna("10210", "Quinchao",           "QUINCHAO",           "10", "275", -42.472f, -73.490f),
        Comuna("10301", "Osorno",             "OSORNO",             "10", "255", -40.575f, -73.132f),
        Comuna("10302", "Puerto Octay",       "PUERTO OCTAY",       "10", "258", -40.976f, -72.883f),
        Comuna("10303", "Purranque",          "PURRANQUE",          "10", "260", -40.909f, -73.165f),
        Comuna("10304", "Puyehue",            "PUYEHUE",            "10", "256", -40.681f, -72.599f),
        Comuna("10305", "Río Negro",          "RIO NEGRO",          "10", "259", -40.783f, -73.232f),
        Comuna("10306", "San Juan de la Costa","SAN JUAN DE LA COSTA","10","307", -40.516f, -73.400f),
        Comuna("10307", "San Pablo",          "SAN PABLO",          "10", "257", -40.412f, -73.010f),
        Comuna("10401", "Chaitén",            "CHAITEN",            "10", "280", -42.917f, -72.716f),
        Comuna("10402", "Futaleufú",          "FUTALEUFU",          "10", "281", -43.186f, -71.867f),
        Comuna("10403", "Hualaihué",          "HUALAIHUE",          "10", "308", -42.097f, -72.404f),
        Comuna("10404", "Palena",             "PALENA",             "10", "282", -43.616f, -71.818f),

        // ── XI Aysén ──────────────────────────────────────────────────────────
        Comuna("11101", "Coyhaique",   "COYHAIQUE",   "11", "284", -45.576f, -72.062f),
        Comuna("11102", "Lago Verde",  "LAGO VERDE",  "11", "312", -44.224f, -71.840f),
        Comuna("11201", "Aysén",       "AYSEN",       "11", "285", -45.398f, -72.699f),
        Comuna("11202", "Cisnes",      "CISNES",      "11", "286", -44.728f, -72.683f),
        Comuna("11203", "Guaitecas",   "GUAITECAS",   "11", "309", -43.878f, -73.749f),
        Comuna("11301", "Cochrane",    "COCHRANE",    "11", "289", -47.249f, -72.578f),
        Comuna("11302", "O'Higgins",   "O'HIGGINS",   "11", "310", -48.464f, -72.561f),
        Comuna("11303", "Tortel",      "TORTEL",      "11", "311", -47.824f, -73.565f),
        Comuna("11401", "Chile Chico", "CHILE CHICO", "11", "287", -46.540f, -71.722f),
        Comuna("11402", "Río Ibáñez",  "RIO IBANEZ",  "11", "288", -46.294f, -71.936f),

        // ── XII Magallanes ────────────────────────────────────────────────────
        Comuna("12101", "Punta Arenas",    "PUNTA ARENAS",    "12", "290", -53.164f, -70.931f),
        Comuna("12102", "Laguna Blanca",   "LAGUNA BLANCA",   "12", "316", -52.300f, -71.161f),
        Comuna("12103", "Río Verde",       "RIO VERDE",       "12", "314", -52.581f, -71.513f),
        Comuna("12104", "San Gregorio",    "SAN GREGORIO",    "12", "315", -52.314f, -69.684f),
        Comuna("12201", "Cabo de Hornos",  "CABO DE HORNOS",  "12", "319", -54.935f, -67.604f),
        Comuna("12202", "Antártica",       "ANTARTICA",       "12", "888", -64.359f, -60.820f),
        Comuna("12301", "Porvenir",        "PORVENIR",        "12", "292", -53.290f, -70.363f),
        Comuna("12302", "Primavera",       "PRIMAVERA",       "12", "317", -52.712f, -69.250f),
        Comuna("12303", "Timaukel",        "TIMAUKEL",        "12", "318", -54.288f, -69.164f),
        Comuna("12401", "Puerto Natales",  "PUERTO NATALES",  "12", "291", -51.722f, -72.521f),
        Comuna("12402", "Torres del Paine","TORRES DEL PAINE","12", "313", -50.990f, -73.089f)
    )

    // ── State ─────────────────────────────────────────────────────────────────

    private var comunasFiltradas = listOf<Comuna>()

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
        val regionAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, regiones.map { it.nombre })
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
        val region = regiones[regionIdx]
        comunasFiltradas = todasLasComunas
            .filter { it.tgrRegion == region.tgrCode }
            .sortedBy { it.nombre }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, comunasFiltradas.map { it.nombre })
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerComuna.adapter = adapter
        if (comunasFiltradas.isNotEmpty()) cargarClima(comunasFiltradas[0])
    }

    private fun seleccionarComuna(comuna: Comuna) {
        val regionIdx = regiones.indexOfFirst { it.tgrCode == comuna.tgrRegion }
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

    // ── Búsqueda ──────────────────────────────────────────────────────────────

    private fun buscarRol() {
        val calle  = etCalle.text.toString().trim()
        val numero = etNumero.text.toString().trim()

        if (calle.isEmpty())  { etCalle.error  = "Ingresa el nombre de la calle"; return }
        if (numero.isEmpty()) { etNumero.error = "Ingresa el número"; return }
        if (comunasFiltradas.isEmpty()) return

        val comuna = comunasFiltradas[spinnerComuna.selectedItemPosition]
        guardarHistorial(comuna.codigoSii, calle, numero)

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

        val comuna = comunasFiltradas[spinnerComuna.selectedItemPosition]

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
        val json  = JSONArray(prefs.getString("busquedas", "[]"))
        val nuevo = JSONObject().apply {
            put("codigoSii", codigoSii); put("calle", calle); put("numero", numero)
        }
        val lista = (0 until json.length()).map { json.getJSONObject(it) }
            .filter { !(it.optString("codigoSii") == codigoSii &&
                        it.optString("calle")     == calle &&
                        it.optString("numero")    == numero) }
        val nueva = JSONArray()
        nueva.put(nuevo)
        lista.take(4).forEach { nueva.put(it) }
        prefs.edit().putString("busquedas", nueva.toString()).apply()
        mostrarHistorial()
    }

    private fun mostrarHistorial() {
        val prefs = getSharedPreferences("historial", Context.MODE_PRIVATE)
        val json  = JSONArray(prefs.getString("busquedas", "[]"))
        containerHistorial.removeAllViews()
        if (json.length() == 0) { scrollHistorial.visibility = View.GONE; return }

        var hayChips = false
        for (i in 0 until json.length()) {
            val item      = json.getJSONObject(i)
            val codigoSii = item.optString("codigoSii")
            val calle     = item.optString("calle")
            val numero    = item.optString("numero")
            if (codigoSii.isEmpty() || calle.isEmpty()) continue
            val comuna = todasLasComunas.find { it.codigoSii == codigoSii } ?: continue
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

    // ── Modelos ───────────────────────────────────────────────────────────────

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
