package br.com.observatorioespacial

import android.net.Uri
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.parodison.orbit.core.satellite.ObserverCoordinates
import com.parodison.orbit.core.satellite.Satellite
import com.parodison.orbit.core.satellite.model.OrbitMeanElementsMessage

/**
 * Monitor orbital contínuo usando o catálogo público CelesTrak GP/OMM.
 * O catálogo ACTIVE pode conter milhares de objetos. Para preservar a memória
 * de aparelhos Android de entrada, cada ciclo calcula até 1.200 objetos e
 * informa o total recebido separadamente.
 */
object LiveSpaceMonitorService {
    private const val MAX_OBJECTS_PER_CYCLE = 1200
    private val utc = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'").withZone(ZoneOffset.UTC)

    data class Snapshot(
        val totalCatalogued: Int,
        val evaluated: Int,
        val visible: List<String>,
        val timestamp: String,
        val warning: String
    )

    fun fetchActiveSatelliteSnapshot(
        latitude: Double,
        longitude: Double,
        altitudeMeters: Double,
        callback: (Snapshot?, String?) -> Unit
    ) {
        Thread {
            try {
                val url = Uri.parse("https://celestrak.org/NORAD/elements/gp.php")
                    .buildUpon()
                    .appendQueryParameter("GROUP", "active")
                    .appendQueryParameter("FORMAT", "JSON")
                    .build().toString()
                val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 20000
                    readTimeout = 45000
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "ObservatorioEspacial-Android/1.2.5")
                }
                val json = try {
                    if (connection.responseCode !in 200..299) throw IllegalStateException("CelesTrak HTTP ${connection.responseCode}")
                    connection.inputStream.bufferedReader().use { JSONArray(it.readText()) }
                } finally {
                    connection.disconnect()
                }
                val total = json.length()
                val count = minOf(total, MAX_OBJECTS_PER_CYCLE)
                val observer = ObserverCoordinates(
                    latitudeDeg = latitude,
                    longitudeDeg = longitude,
                    altitudeKm = altitudeMeters / 1000.0
                )
                val now = kotlin.time.Instant.fromEpochMilliseconds(System.currentTimeMillis())
                val visible = ArrayList<Pair<Double, String>>()
                var evaluated = 0
                for (i in 0 until count) {
                    val item = json.optJSONObject(i) ?: continue
                    try {
                        fun field(key: String): String = item.optString(key).takeIf { it.isNotBlank() && it != "null" }
                            ?: throw IllegalArgumentException("Campo OMM ausente: $key")
                        val epoch = field("EPOCH").let { if (it.endsWith("Z")) it else it + "Z" }
                        val omm = OrbitMeanElementsMessage(
                            objectName = field("OBJECT_NAME"),
                            objectId = field("OBJECT_ID"),
                            epoch = kotlin.time.Instant.parse(epoch),
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
                        val satellite = Satellite(omm)
                        val look = satellite.lookAnglesFrom(observer, now)
                        evaluated++
                        if (look.elevationDeg > 0.0) {
                            visible.add(
                                look.elevationDeg to String.format(
                                    Locale.US, "%s • NORAD %d • elevação %.1f° • az %.1f°",
                                    omm.objectName, omm.noradCatId, look.elevationDeg, look.azimuthDeg
                                )
                            )
                        }
                    } catch (_: Exception) {
                        // Dados incompletos/formatos OMM não suportados são ignorados individualmente.
                    }
                }
                val warning = if (total > count) "Limite de desempenho: analisados $count de $total objetos ACTIVE neste aparelho."
                    else "Todos os $total objetos recebidos foram analisados neste ciclo."
                callback(
                    Snapshot(
                        totalCatalogued = total,
                        evaluated = evaluated,
                        visible = visible.sortedByDescending { it.first }.take(12).map { it.second },
                        timestamp = utc.format(Instant.now()),
                        warning = warning
                    ),
                    null
                )
            } catch (e: Exception) {
                callback(null, e.message ?: "Erro consultando CelesTrak")
            }
        }.start()
    }
}
