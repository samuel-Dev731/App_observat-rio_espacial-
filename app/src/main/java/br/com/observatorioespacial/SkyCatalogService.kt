package br.com.observatorioespacial

import android.location.Location
import java.text.Normalizer
import java.util.Locale
import kotlin.math.*

/**
 * Pequeno catálogo local original, inspirado em recursos comuns de planetários.
 * Coordenadas equatoriais J2000 aproximadas; não substitui um catálogo astrométrico completo.
 */
object SkyCatalogService {
    private data class SkyObject(
        val name: String,
        val catalogId: String,
        val kind: String,
        val constellation: String,
        val raDeg: Double,
        val decDeg: Double,
        val magnitude: Double?,
        val aliases: List<String> = emptyList()
    )

    private val objects = listOf(
        SkyObject("Sírius", "α Canis Majoris", "Estrela", "Cão Maior", 101.287, -16.716, -1.46, listOf("sirius", "alpha canis majoris")),
        SkyObject("Canopus", "α Carinae", "Estrela", "Carina", 95.988, -52.696, -0.74, listOf("canopus")),
        SkyObject("Arcturus", "α Bootis", "Estrela", "Boieiro", 213.915, 19.182, -0.05, listOf("arcturus")),
        SkyObject("Vega", "α Lyrae", "Estrela", "Lira", 279.234, 38.784, 0.03, listOf("vega")),
        SkyObject("Capella", "α Aurigae", "Estrela", "Cocheiro", 79.172, 45.998, 0.08, listOf("capella")),
        SkyObject("Rigel", "β Orionis", "Estrela", "Órion", 78.634, -8.202, 0.13, listOf("rigel")),
        SkyObject("Prócion", "α Canis Minoris", "Estrela", "Cão Menor", 114.825, 5.225, 0.34, listOf("procyon", "procion")),
        SkyObject("Betelgeuse", "α Orionis", "Estrela", "Órion", 88.793, 7.407, 0.42, listOf("betelgeuse")),
        SkyObject("Achernar", "α Eridani", "Estrela", "Erídano", 24.429, -57.237, 0.46, listOf("achernar")),
        SkyObject("Hadar", "β Centauri", "Estrela", "Centauro", 210.956, -60.373, 0.61, listOf("hadar", "beta centauri")),
        SkyObject("Altair", "α Aquilae", "Estrela", "Águia", 297.696, 8.868, 0.77, listOf("altair")),
        SkyObject("Acrux", "α Crucis", "Estrela", "Cruzeiro do Sul", 186.650, -63.099, 0.76, listOf("acrux")),
        SkyObject("Aldebaran", "α Tauri", "Estrela", "Touro", 68.980, 16.509, 0.85, listOf("aldebaran")),
        SkyObject("Espiga", "α Virginis", "Estrela", "Virgem", 201.298, -11.161, 0.98, listOf("spica", "espiga")),
        SkyObject("Antares", "α Scorpii", "Estrela", "Escorpião", 247.352, -26.432, 1.06, listOf("antares")),
        SkyObject("Pollux", "β Geminorum", "Estrela", "Gêmeos", 116.329, 28.026, 1.14, listOf("pollux")),
        SkyObject("Fomalhaut", "α Piscis Austrini", "Estrela", "Peixe Austral", 344.413, -29.622, 1.16, listOf("fomalhaut")),
        SkyObject("Deneb", "α Cygni", "Estrela", "Cisne", 310.358, 45.280, 1.25, listOf("deneb")),
        SkyObject("Regulus", "α Leonis", "Estrela", "Leão", 152.093, 11.967, 1.35, listOf("regulus")),
        SkyObject("Polaris", "α Ursae Minoris", "Estrela", "Ursa Menor", 37.954, 89.264, 1.98, listOf("estrela polar", "polaris")),
        SkyObject("Galáxia de Andrômeda", "M31", "Galáxia", "Andrômeda", 10.6847, 41.269, 3.4, listOf("andromeda", "m31")),
        SkyObject("Nebulosa de Órion", "M42", "Nebulosa", "Órion", 83.822, -5.391, 4.0, listOf("orion", "m42")),
        SkyObject("Plêiades", "M45", "Aglomerado aberto", "Touro", 56.750, 24.116, 1.6, listOf("pleiades", "sete irmas", "m45")),
        SkyObject("Aglomerado de Hércules", "M13", "Aglomerado globular", "Hércules", 250.423, 36.461, 5.8, listOf("m13", "hercules")),
        SkyObject("Nebulosa da Lagoa", "M8", "Nebulosa", "Sagitário", 270.925, -24.380, 6.0, listOf("lagoon", "m8")),
        SkyObject("Nebulosa Trífida", "M20", "Nebulosa", "Sagitário", 270.595, -23.030, 6.3, listOf("trifid", "m20")),
        SkyObject("Nebulosa do Anel", "M57", "Nebulosa planetária", "Lira", 283.396, 33.030, 8.8, listOf("ring", "m57")),
        SkyObject("Galáxia do Redemoinho", "M51", "Galáxia", "Cães de Caça", 202.470, 47.195, 8.4, listOf("whirlpool", "m51")),
        SkyObject("Ômega Centauri", "NGC 5139", "Aglomerado globular", "Centauro", 201.697, -47.479, 3.7, listOf("omega centauri", "ngc5139"))
    )

    fun search(query: String, observer: Location?): String {
        val term = normalize(query.trim())
        if (term.isEmpty()) return "Nenhum termo de pesquisa informado."
        val matches = objects.filter { item ->
            normalize(listOf(item.name, item.catalogId, item.kind, item.constellation).plus(item.aliases).joinToString(" "))
                .contains(term)
        }.take(8)
        if (matches.isEmpty()) {
            return "VISIBILIDADE: objeto não encontrado no catálogo local\nOBJETO: $query\n\nTente nomes como Sírius, Vega, M31, M42, M45, M13 ou M57. Este catálogo inicial é selecionado e não contém todos os objetos do céu."
        }
        return matches.joinToString("\n\n────────────────\n\n") { item ->
            val raHours = item.raDeg / 15.0
            val h = floor(raHours).toInt()
            val mFloat = (raHours - h) * 60.0
            val m = floor(mFloat).toInt()
            val s = (mFloat - m) * 60.0
            val decSign = if (item.decDeg < 0) "−" else "+"
            val decAbs = abs(item.decDeg)
            val decD = floor(decAbs).toInt()
            val decMFloat = (decAbs - decD) * 60.0
            val decM = floor(decMFloat).toInt()
            val decS = (decMFloat - decM) * 60.0
            val mag = item.magnitude?.let { String.format(Locale.US, "%.2f", it) } ?: "—"
            val position = observer?.let { horizontalCoordinates(item.raDeg, item.decDeg, it) }
            val visibility = when {
                position == null -> "VISIBILIDADE: localização indisponível; posição horizontal não calculada"
                position.second >= 0.0 -> "VISIBILIDADE: acima do horizonte geométrico agora"
                else -> "VISIBILIDADE: abaixo do horizonte geométrico agora"
            }
            buildString {
                append(visibility).append('\n')
                append("OBJETO: ").append(item.name).append(" • ").append(item.catalogId).append('\n')
                append("Tipo: ").append(item.kind).append(" • constelação: ").append(item.constellation).append('\n')
                append("Ascensão reta (J2000): ").append(String.format(Locale.US, "%02dh %02dm %04.1fs", h, m, s)).append(" • ")
                    .append(String.format(Locale.US, "%.4f", item.raDeg)).append("°\n")
                append("Declinação (J2000): ").append(decSign)
                    .append(String.format(Locale.US, "%02d° %02d′ %04.1f″", decD, decM, decS)).append('\n')
                append("Magnitude visual aproximada: ").append(mag)
                if (position != null) {
                    append("\nAzimute: ").append(String.format(Locale.US, "%.1f", position.first)).append("°")
                    append("\nElevação: ").append(String.format(Locale.US, "%.1f", position.second)).append("°")
                }
                append("\nCoordenadas equatoriais J2000; posição horizontal calculada para a hora e localização atuais.")
                append("\nA visibilidade considera apenas o horizonte geométrico; não inclui relevo, edifícios, nuvens, poluição luminosa ou refração atmosférica.")
            }
        }
    }

    private fun horizontalCoordinates(raDeg: Double, decDeg: Double, observer: Location): Pair<Double, Double> {
        val jd = System.currentTimeMillis() / 86400000.0 + 2440587.5
        val days = jd - 2451545.0
        val centuries = days / 36525.0
        val gmst = 280.46061837 + 360.98564736629 * days +
            0.000387933 * centuries * centuries -
            centuries * centuries * centuries / 38710000.0
        val lst = normalizeDegrees(gmst + observer.longitude)
        val hourAngle = Math.toRadians(normalizeSigned(lst - raDeg))
        val latitude = Math.toRadians(observer.latitude)
        val declination = Math.toRadians(decDeg)
        val sinAltitude = sin(latitude) * sin(declination) +
            cos(latitude) * cos(declination) * cos(hourAngle)
        val altitude = asin(sinAltitude.coerceIn(-1.0, 1.0))
        val azimuth = atan2(
            sin(hourAngle),
            cos(hourAngle) * sin(latitude) - tan(declination) * cos(latitude)
        ) + Math.PI
        return Pair(normalizeDegrees(Math.toDegrees(azimuth)), Math.toDegrees(altitude))
    }

    private fun normalize(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")
            .lowercase(Locale.ROOT)

    private fun normalizeDegrees(value: Double): Double = ((value % 360.0) + 360.0) % 360.0

    private fun normalizeSigned(value: Double): Double {
        val normalized = normalizeDegrees(value)
        return if (normalized > 180.0) normalized - 360.0 else normalized
    }
}
