package br.com.observatorioespacial

import android.net.Uri
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.Normalizer
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Consulta sondas e telescópios espaciais no JPL Horizons.
 * Os IDs NAIF negativos identificam alvos espaciais/spacecraft no Horizons.
 * As posições são efemérides calculadas, não telemetria de bordo nem controle da missão.
 */
object SpacecraftCatalogService {
    private data class Mission(val name: String, val naifId: String, val aliases: List<String>)

    private val missions = listOf(
        Mission("Voyager 1", "-31", listOf("voyager 1", "voyager1", "vg1")),
        Mission("Voyager 2", "-32", listOf("voyager 2", "voyager2", "vg2")),
        Mission("New Horizons", "-98", listOf("new horizons", "newhorizons", "pluto probe")),
        Mission("Parker Solar Probe", "-96", listOf("parker", "parker solar probe", "psp")),
        Mission("Juno", "-61", listOf("juno", "jupiter probe")),
        Mission("Hubble Space Telescope", "-48", listOf("hubble", "hst", "telescopio hubble")),
        Mission("JUICE", "-28", listOf("juice", "jupiter icy moons explorer")),
        Mission("Hayabusa2", "-37", listOf("hayabusa2", "hayabusa 2")),
        Mission("SOHO", "-21", listOf("soho", "solar and heliospheric observatory")),
        Mission("Lucy", "-49", listOf("lucy", "lucy mission"))
    )

    private val executor = Executors.newSingleThreadExecutor()
    private val utcFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneOffset.UTC)
    private val outputFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC)

    fun search(query: String, latitude: Double, longitude: Double, altitudeMeters: Double, callback: (String) -> Unit) {
        executor.execute {
            try {
                val term = normalize(query)
                val mission = missions.firstOrNull { item ->
                    normalize(item.name) == term || item.aliases.any { normalize(it) == term } || item.naifId == term
                }
                // Qualquer alvo reconhecido pelo JPL Horizons pode ser consultado pelo nome ou ID.
                val targetId = mission?.naifId ?: query.trim()
                val targetName = mission?.name ?: query.trim()

                val start = Instant.now().truncatedTo(ChronoUnit.MINUTES)
                val stop = start.plus(24, ChronoUnit.HOURS)
                val site = String.format(Locale.US, "%.6f,%.6f,%.3f", longitude, latitude, altitudeMeters / 1000.0)
                val address = Uri.parse("https://ssd.jpl.nasa.gov/api/horizons.api").buildUpon()
                    .appendQueryParameter("format", "json")
                    .appendQueryParameter("COMMAND", "'${targetId}'")
                    .appendQueryParameter("OBJ_DATA", "YES")
                    .appendQueryParameter("MAKE_EPHEM", "YES")
                    .appendQueryParameter("EPHEM_TYPE", "OBSERVER")
                    .appendQueryParameter("CENTER", "'coord@399'")
                    .appendQueryParameter("COORD_TYPE", "'GEODETIC'")
                    .appendQueryParameter("SITE_COORD", "'$site'")
                    .appendQueryParameter("START_TIME", "'${utcFormat.format(start)}'")
                    .appendQueryParameter("STOP_TIME", "'${utcFormat.format(stop)}'")
                    .appendQueryParameter("STEP_SIZE", "'1 h'")
                    .appendQueryParameter("QUANTITIES", "'1,4,20'")
                    .appendQueryParameter("ANG_FORMAT", "DEG")
                    .appendQueryParameter("CSV_FORMAT", "YES")
                    .build().toString()

                val json = JSONObject(getText(address))
                if (json.has("error")) throw IllegalStateException(json.optString("error"))
                val result = json.optString("result")
                val begin = result.indexOf("\$\$SOE")
                val end = result.indexOf("\$\$EOE")
                if (begin < 0 || end <= begin) throw IllegalStateException("O JPL Horizons não retornou efemérides para esta missão/data.")
                val rows = result.substring(begin + 5, end).lineSequence()
                    .filter { it.contains(",") }
                    .map { it.split(',').map { field -> field.trim().trim('"') } }
                    .filter { it.size >= 5 }
                    .toList()
                val first = rows.firstOrNull() ?: throw IllegalStateException("Sem amostras de posição.")
                val above = rows.filter { (it.getOrNull(4)?.toDoubleOrNull() ?: -90.0) > 0.0 }
                val lastVisible = above.lastOrNull()
                val firstVisible = above.firstOrNull()
                val visibleLabel = if (lastVisible != null) "VISÍVEL ATÉ: ${lastVisible[0]} UTC"
                    else "VISIBILIDADE: não acima do horizonte nas próximas 24 horas"
                val nextLabel = if (firstVisible != null) "PRÓXIMA VISIBILIDADE: ${targetName} • ${firstVisible[0]} UTC"
                    else "PRÓXIMA VISIBILIDADE: ${targetName} • não prevista nas próximas 24 horas"

                callback(
                    "$visibleLabel\n$nextLabel\n" +
                    "MISSÃO: ${targetName} • ID NAIF/JPL: ${targetId}\n" +
                    "Fonte: NASA/JPL Horizons • efemérides topocêntricas calculadas\n" +
                    "Data/hora: ${first[0]} UTC\n" +
                    "Ascensão reta: ${first.getOrElse(1) { "—" }}°\n" +
                    "Declinação: ${first.getOrElse(2) { "—" }}°\n" +
                    "Azimute: ${first.getOrElse(3) { "—" }}°\n" +
                    "Elevação: ${first.getOrElse(4) { "—" }}°\n\n" +
                    "Localização usada no cálculo: ${String.format(Locale.US, "%.4f", latitude)}°, ${String.format(Locale.US, "%.4f", longitude)}°. " +
                    "A posição é uma previsão orbital do JPL, não sinal de rádio, telemetria instantânea, vídeo ao vivo ou comando da sonda."
                )
            } catch (e: Exception) {
                callback("Não foi possível consultar a missão no NASA/JPL Horizons.\n\nDetalhe: ${e.message ?: "erro de conexão"}")
            }
        }
    }

    private fun normalize(value: String): String =
        Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")
            .lowercase(Locale.ROOT)

    private fun getText(address: String): String {
        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 25000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "ObservatorioEspacial-Android/1.2.5")
        }
        return try {
            if (connection.responseCode !in 200..299) throw IllegalStateException("HTTP ${connection.responseCode}")
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
