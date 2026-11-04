package br.com.observatorioespacial

import android.net.Uri
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors

/**
 * Dados orbitais oficiais do JPL Horizons e imagens do NASA Image and Video Library.
 * A ordem dos corpos é: Mercúrio, Vênus, Terra, Marte, Júpiter, Saturno, Urano, Netuno.
 */
object AstronomyDataService {
    private val executor = Executors.newFixedThreadPool(3)
    private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneOffset.UTC)
    private val bodyIds = listOf("199", "299", "399", "499", "599", "699", "799", "899")
    private val bodyNames = listOf("Mercúrio", "Vênus", "Terra", "Marte", "Júpiter", "Saturno", "Urano", "Netuno")

    fun fetchPlanetPositions(callback: (FloatArray?, String) -> Unit) {
        executor.execute {
            try {
                val start = Instant.now().truncatedTo(ChronoUnit.HOURS)
                val stop = start.plus(24, ChronoUnit.HOURS)
                val output = FloatArray(8 * 25 * 3)
                bodyIds.forEachIndexed { bodyIndex, id ->
                    val url = Uri.parse("https://ssd.jpl.nasa.gov/api/horizons.api").buildUpon()
                        .appendQueryParameter("format", "json")
                        .appendQueryParameter("COMMAND", "'$id'")
                        .appendQueryParameter("OBJ_DATA", "NO")
                        .appendQueryParameter("MAKE_EPHEM", "YES")
                        .appendQueryParameter("EPHEM_TYPE", "VECTORS")
                        .appendQueryParameter("CENTER", "'500@10'")
                        .appendQueryParameter("START_TIME", "'${dateFormat.format(start)}'")
                        .appendQueryParameter("STOP_TIME", "'${dateFormat.format(stop)}'")
                        .appendQueryParameter("STEP_SIZE", "'1 h'")
                        .appendQueryParameter("VEC_TABLE", "1")
                        .appendQueryParameter("CSV_FORMAT", "YES")
                        .appendQueryParameter("OUT_UNITS", "AU-D")
                        .build().toString()
                    val json = JSONObject(getText(url))
                    if (json.has("error")) throw IllegalStateException(json.optString("error"))
                    val result = json.optString("result")
                    val rows = parseVectorRows(result)
                    if (rows.size < 25) throw IllegalStateException("JPL retornou poucas amostras para ${bodyNames[bodyIndex]}")
                    for (step in 0 until 25) {
                        val p = rows[step.coerceAtMost(rows.lastIndex)]
                        val offset = (bodyIndex * 25 + step) * 3
                        output[offset] = p[0]
                        output[offset + 1] = p[1]
                        output[offset + 2] = p[2]
                    }
                }
                callback(output, "Posições orbitais recebidas do NASA/JPL Horizons • janela de 24 horas")
            } catch (e: Exception) {
                callback(null, "Dados JPL indisponíveis: ${e.message ?: "erro de conexão"}")
            }
        }
    }

    fun fetchNasaThumbnail(query: String, callback: (String?) -> Unit) {
        executor.execute {
            try {
                val url = Uri.parse("https://images-api.nasa.gov/search").buildUpon()
                    .appendQueryParameter("q", query)
                    .appendQueryParameter("media_type", "image")
                    .appendQueryParameter("page_size", "5")
                    .build().toString()
                val json = JSONObject(getText(url))
                val items = json.optJSONObject("collection")?.optJSONArray("items")
                var imageUrl: String? = null
                if (items != null) {
                    for (i in 0 until items.length()) {
                        val item = items.optJSONObject(i) ?: continue
                        val links = item.optJSONArray("links") ?: continue
                        for (j in 0 until links.length()) {
                            val href = links.optJSONObject(j)?.optString("href") ?: continue
                            if (href.startsWith("https://") && (href.contains("~thumb") || href.contains("~medium") || href.endsWith(".jpg") || href.endsWith(".png"))) {
                                imageUrl = href
                                break
                            }
                        }
                        if (imageUrl != null) break
                    }
                }
                callback(imageUrl)
            } catch (_: Exception) {
                callback(null)
            }
        }
    }

    fun downloadImage(url: String, callback: (ByteArray?) -> Unit) {
        executor.execute {
            try { callback(getBytes(url)) } catch (_: Exception) { callback(null) }
        }
    }

    private fun parseVectorRows(result: String): List<FloatArray> {
        val start = result.indexOf("$$SOE")
        val end = result.indexOf("$$EOE")
        if (start < 0 || end <= start) throw IllegalStateException("Formato de efemérides JPL inesperado")
        val section = result.substring(start + 5, end)
        val rows = mutableListOf<FloatArray>()
        for (line in section.lineSequence()) {
            val fields = line.trim().split(',')
            if (fields.size < 5) continue
            val xyz = fields.takeLast(3).map { it.trim().replace("\"", "").toFloatOrNull() }
            if (xyz.all { it != null && it.isFinite() }) rows.add(floatArrayOf(xyz[0]!!, xyz[1]!!, xyz[2]!!))
        }
        return rows
    }

    private fun getText(address: String): String = String(getBytes(address) ?: throw IllegalStateException("Resposta vazia"), Charsets.UTF_8)

    private fun getBytes(address: String): ByteArray? {
        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 20000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "ObservatorioEspacial-Android/1.1.0")
        }
        return try {
            if (connection.responseCode !in 200..299) throw IllegalStateException("HTTP ${connection.responseCode}")
            connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }
}
