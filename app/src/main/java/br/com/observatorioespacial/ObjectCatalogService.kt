package br.com.observatorioespacial

import android.net.Uri
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors

/**
 * Pesquisa de objetos adicionais.
 * Corpos naturais: efemérides topocêntricas do JPL Horizons.
 * Satélites artificiais: elementos orbitais TLE do CelesTrak.
 */
object ObjectCatalogService {
    private val executor = Executors.newSingleThreadExecutor()
    private val utcFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneOffset.UTC)

    fun searchNaturalObject(name: String, latitude: Double, longitude: Double, altitudeMeters: Double, callback: (String) -> Unit) {
        executor.execute {
            try {
                val now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MINUTES)
                val stop = now.plusSeconds(3600)
                val site = String.format(java.util.Locale.US, "%.6f,%.6f,%.3f", longitude, latitude, altitudeMeters / 1000.0)
                val url = Uri.parse("https://ssd.jpl.nasa.gov/api/horizons.api").buildUpon()
                    .appendQueryParameter("format", "json")
                    .appendQueryParameter("COMMAND", "'$name'")
                    .appendQueryParameter("OBJ_DATA", "YES")
                    .appendQueryParameter("MAKE_EPHEM", "YES")
                    .appendQueryParameter("EPHEM_TYPE", "OBSERVER")
                    .appendQueryParameter("CENTER", "'coord@399'")
                    .appendQueryParameter("COORD_TYPE", "'GEODETIC'")
                    .appendQueryParameter("SITE_COORD", "'$site'")
                    .appendQueryParameter("START_TIME", "'${utcFormat.format(now)}'")
                    .appendQueryParameter("STOP_TIME", "'${utcFormat.format(stop)}'")
                    .appendQueryParameter("STEP_SIZE", "'1 h'")
                    .appendQueryParameter("QUANTITIES", "'1,4,20'")
                    .appendQueryParameter("ANG_FORMAT", "DEG")
                    .appendQueryParameter("CSV_FORMAT", "YES")
                    .build().toString()
                val json = JSONObject(getText(url))
                if (json.has("error")) throw IllegalStateException(json.optString("error"))
                val result = json.optString("result")
                val start = result.indexOf("\$\$SOE")
                val end = result.indexOf("\$\$EOE")
                if (start < 0 || end <= start) throw IllegalStateException("O JPL não retornou uma posição para esse nome.")
                val row = result.substring(start + 5, end).lineSequence().firstOrNull { it.contains(",") }
                    ?: throw IllegalStateException("Não há amostra de posição disponível.")
                val fields = row.split(',').map { it.trim().trim('"') }
                if (fields.size < 6) throw IllegalStateException("Formato de posição inesperado.")
                callback(
                    "Objeto: $name\n" +
                    "Fonte: NASA/JPL Horizons\n" +
                    "Data/hora UTC: ${fields[0]}\n" +
                    "Ascensão reta: ${fields.getOrElse(2) { "—" }}°\n" +
                    "Declinação: ${fields.getOrElse(3) { "—" }}°\n" +
                    "Azimute: ${fields.getOrElse(4) { "—" }}°\n" +
                    "Elevação acima do horizonte: ${fields.getOrElse(5) { "—" }}°\n\n" +
                    "Posição calculada para sua localização informada pelo GPS. Efeméride calculada, não imagem ao vivo."
                )
            } catch (e: Exception) {
                callback("Não foi possível localizar esse objeto no JPL Horizons. Confira o nome ou tente uma designação oficial.\n\nDetalhe: ${e.message ?: "erro de conexão"}")
            }
        }
    }

    fun searchArtificialSatellite(name: String, callback: (String) -> Unit) {
        executor.execute {
            try {
                val url = Uri.parse("https://celestrak.org/NORAD/elements/gp.php").buildUpon()
                    .appendQueryParameter("NAME", name)
                    .appendQueryParameter("FORMAT", "TLE")
                    .build().toString()
                val tle = getText(url).trim()
                val lines = tle.lines().filter { it.isNotBlank() }
                if (lines.size < 3 || !lines.any { it.startsWith("1 ") } || !lines.any { it.startsWith("2 ") }) {
                    throw IllegalStateException("Satélite não encontrado no catálogo público CelesTrak.")
                }
                callback(
                    "Satélite: ${lines.first()}\n" +
                    "Fonte: CelesTrak / catálogo NORAD\n\n" +
                    "Elementos orbitais TLE:\n${lines.filter { it.startsWith("1 ") || it.startsWith("2 ") }.joinToString("\n")}\n\n" +
                    "O TLE foi localizado. A propagação SGP4 para calcular a posição instantânea no céu ainda não está integrada; por isso, esta tela não inventa azimute, elevação ou coordenadas atuais."
                )
            } catch (e: Exception) {
                callback("Não foi possível obter o TLE desse satélite. Tente o nome oficial ou o número NORAD.\n\nDetalhe: ${e.message ?: "erro de conexão"}")
            }
        }
    }

    private fun getText(address: String): String {
        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 20000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "ObservatorioEspacial-Android/1.1.0")
        }
        return try {
            if (connection.responseCode !in 200..299) throw IllegalStateException("HTTP ${connection.responseCode}")
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
