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
                val stop = now.plusSeconds(24 * 3600L)
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
                val rows = result.substring(start + 5, end).lineSequence()
                    .filter { it.contains(",") }
                    .map { line -> line.split(',').map { it.trim().trim('"') } }
                    .filter { it.size >= 5 }
                    .toList()
                val fields = rows.firstOrNull() ?: throw IllegalStateException("Não há amostra de posição disponível.")
                val visibleRows = rows.filter { (it.getOrNull(4)?.toDoubleOrNull() ?: -90.0) > 0.0 }
                val firstVisible = visibleRows.firstOrNull()
                val lastVisible = visibleRows.lastOrNull()
                val blueLabel = if (firstVisible != null && lastVisible != null) {
                    "VISÍVEL ATÉ: ${lastVisible[0]} UTC"
                } else {
                    "VISIBILIDADE: não acima do horizonte nas próximas 24 horas"
                }
                val nextWindow = firstVisible?.get(0)?.plus(" UTC") ?: "não prevista nas próximas 24 horas"
                callback(
                    "$blueLabel\n" +
                    "PRÓXIMA VISIBILIDADE: $name • $nextWindow\n" +
                    "Fonte: NASA/JPL Horizons • previsão para as próximas 24 horas\n" +
                    "Data/hora UTC: ${fields[0]}\n" +
                    "Ascensão reta: ${fields.getOrElse(1) { "—" }}°\n" +
                    "Declinação: ${fields.getOrElse(2) { "—" }}°\n" +
                    "Azimute: ${fields.getOrElse(3) { "—" }}°\n" +
                    "Elevação acima do horizonte: ${fields.getOrElse(4) { "—" }}°\n\n" +
                    "Posição calculada para sua localização GPS. Amostras de 1 hora, horários UTC; previsão aproximada, não vídeo ao vivo."
                )
            } catch (e: Exception) {
                callback("Não foi possível localizar esse objeto no JPL Horizons. Confira o nome ou tente uma designação oficial.\n\nDetalhe: ${e.message ?: "erro de conexão"}")
            }
        }
    }

    @OptIn(kotlin.time.ExperimentalTime::class)
    fun searchArtificialSatellite(name: String, latitude: Double, longitude: Double, altitudeMeters: Double, callback: (String) -> Unit) {
        executor.execute {
            try {
                val address = Uri.parse("https://celestrak.org/NORAD/elements/gp.php").buildUpon()
                    .appendQueryParameter("NAME", name)
                    .appendQueryParameter("FORMAT", "JSON")
                    .build().toString()
                val list = org.json.JSONArray(getText(address))
                if (list.length() == 0) throw IllegalStateException("Satélite não encontrado no CelesTrak.")
                val item = list.getJSONObject(0)
                fun field(key: String): String = item.optString(key).takeIf { it.isNotBlank() && it != "null" }
                    ?: throw IllegalStateException("Campo orbital ausente: " + key)
                val epochText = field("EPOCH").let { if (it.endsWith("Z")) it else it + "Z" }
                val omm = com.parodison.orbit.core.satellite.model.OrbitMeanElementsMessage(
                    objectName = field("OBJECT_NAME"),
                    objectId = field("OBJECT_ID"),
                    epoch = kotlin.time.Instant.parse(epochText),
                    meanMotion = field("MEAN_MOTION").toDouble(),
                    eccentricity = field("ECCENTRICITY").toDouble(),
                    inclination = field("INCLINATION").toDouble(),
                    raOfAscNode = field("RA_OF_ASC_NODE").toDouble(),
                    argOfPericenter = field("ARG_OF_PERICENTER").toDouble(),
                    meanAnomaly = field("MEAN_ANOMALY").toDouble(),
                    ephemerisType = field("EPHEMERIS_TYPE").toLong(),
                    classificationType = field("CLASSIFICATION_TYPE"),
                    noradCatId = field("NORAD_CAT_ID").toLong(),
                    elementSetNo = field("ELEMENT_SET_NO").toLong(),
                    revAtEpoch = field("REV_AT_EPOCH").toLong(),
                    bstar = field("BSTAR").toDouble(),
                    meanMotionDot = field("MEAN_MOTION_DOT").toDouble(),
                    meanMotionDdot = field("MEAN_MOTION_DDOT").toDouble()
                )
                val sat = com.parodison.orbit.core.satellite.Satellite(omm)
                val observer = com.parodison.orbit.core.satellite.ObserverCoordinates(
                    latitudeDeg = latitude, longitudeDeg = longitude, altitudeKm = altitudeMeters / 1000.0
                )
                val now = kotlin.time.Instant.fromEpochMilliseconds(System.currentTimeMillis())
                val look = sat.lookAnglesFrom(observer, now)
                val ground = sat.subPointAt(now)
                val passes = sat.nextPassesFrom(observer, now, kotlin.time.Duration.parse("24h"), 10.0, 30)
                val pass = passes.firstOrNull()
                val visibleNow = sat.isVisuallyVisibleFrom(observer, now, 10.0, -6.0)
                val sunlit = sat.isSunlitAt(now)
                fun utc(value: kotlin.time.Instant): String =
                    java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                        .withZone(ZoneOffset.UTC).format(java.time.Instant.parse(value.toString()))
                val ageHours = java.time.Duration.between(java.time.Instant.parse(epochText),
                    java.time.Instant.ofEpochMilli(System.currentTimeMillis())).toHours().coerceAtLeast(0)
                val blue = if (pass != null) "VISÍVEL ATÉ: " + utc(pass.los) + " UTC"
                    else "VISIBILIDADE: nenhuma passagem acima de 10° nas próximas 24 horas"
                val gray = "SATÉLITE: " + omm.objectName + (if (pass != null) " • próxima passagem: " + utc(pass.aos) + " UTC" else " • sem passagem em 24 h")
                val freshness = if (ageHours > 72) "ATENÇÃO: elementos com " + (ageHours / 24) + " dias; atualize os dados."
                    else "Elementos orbitais recentes • idade: " + ageHours + " h"
                val fmt = java.util.Locale.US
                callback(
                    blue + "\n" + gray + "\nFonte: CelesTrak/NORAD • SGP4/SDP4 local\n" +
                    "NORAD: " + omm.noradCatId + " • época: " + epochText + "\n" + freshness + "\n" +
                    "Azimute: " + String.format(fmt, "%.2f", look.azimuthDeg) + "°\n" +
                    "Elevação: " + String.format(fmt, "%.2f", look.elevationDeg) + "°\n" +
                    "Distância: " + String.format(fmt, "%.1f", look.rangeKm) + " km\n" +
                    "Subponto: " + String.format(fmt, "%.4f", ground.latitudeDeg) + "°, " +
                    String.format(fmt, "%.4f", ground.longitudeDeg) + "°\n" +
                    "Altitude orbital: " + String.format(fmt, "%.1f", ground.altitudeKm) + " km • velocidade: " +
                    String.format(fmt, "%.2f", sat.speedAt(now)) + " km/s\n" +
                    "Iluminado pelo Sol: " + (if (sunlit) "sim" else "não") +
                    " • visível a olho nu agora: " + (if (visibleNow) "condições favoráveis" else "não") + "\n" +
                    (if (pass != null) "Passagem: início " + utc(pass.aos) + " UTC • máximo " +
                        String.format(fmt, "%.1f", pass.maxElevationDeg) + "° às " + utc(pass.tca) +
                        " UTC • fim " + utc(pass.los) + " UTC" else "Nenhuma passagem acima de 10° na janela consultada.") +
                    "\n\nPosição calculada localmente a partir dos elementos orbitais; não é detecção por câmera nem transmissão de rádio."
                )
            } catch (e: Exception) {
                callback("Falha na propagação SGP4/SDP4. Confira o nome ou número NORAD.\n\nDetalhe: " + (e.message ?: "erro de conexão"))
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
