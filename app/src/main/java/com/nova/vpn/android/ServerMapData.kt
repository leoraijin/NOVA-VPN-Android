package com.nova.vpn.android

import android.content.Context
import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale
import kotlin.math.*

internal data class MapPoint(val x: Double, val y: Double) {
    companion object {
        fun fromGeo(longitude: Double, latitude: Double): MapPoint {
            val lat = Math.toRadians(latitude.coerceIn(-85.0, 85.0))
            return MapPoint((longitude + 180) / 360, (1 - ln(tan(lat) + 1 / cos(lat)) / PI) / 2)
        }
    }
}

internal data class MapCountry(val code: String, val iso3: String, val name: String,
    val aliases: List<String>, val center: MapPoint, val rings: List<List<MapPoint>>)
internal data class MapCity(val name: String, val iso3: String, val aliases: List<String>, val point: MapPoint)
internal data class ServerLocation(val country: String, val city: String?, val point: MapPoint) {
    val label get() = city?.let { "$country · $it" } ?: "$country · по названию профиля"
}

/** Public geographic data only. Resolves profile labels locally; never queries IP geolocation. */
internal class ServerMapData(val countries: List<MapCountry>, private val cities: List<MapCity>) {
    fun resolve(name: String): ServerLocation? {
        val text = normalize(name)
        val flag = flagCountry(name)
        val code = Regex("(?<![\\p{L}\\p{N}])([A-Z]{2})(?![\\p{L}])").findAll(name)
            .map { if (it.groupValues[1] == "UK") "GB" else it.groupValues[1] }
            .firstOrNull { c -> countries.any { it.code == c } }
        var country = countries.firstOrNull { it.code == flag }
            ?: countries.firstOrNull { it.code == code }
            ?: countries.filter { c -> c.aliases.any { matches(text, it) } }
                .maxByOrNull { c -> c.aliases.filter { matches(text, it) }.maxOf { it.length } }
        val found = cities.filter { (country == null || it.iso3 == country!!.iso3) && it.aliases.any { a -> matches(text, a) } }
        val city = found.maxByOrNull { c -> c.aliases.filter { matches(text, it) }.maxOf { it.length } }
            ?.takeIf { country != null || found.map { it.iso3 }.distinct().size == 1 }
        if (country == null && city != null) country = countries.firstOrNull { it.iso3 == city.iso3 }
        return country?.let { ServerLocation(it.name, city?.name, city?.point ?: it.center) }
    }

    companion object {
        @Volatile private var cached: ServerMapData? = null
        @Synchronized fun load(context: Context): ServerMapData = cached ?: parse(
            context.assets.open("world-map.json").bufferedReader().use { it.readText() }
        ).also { cached = it }

        internal fun parse(raw: String): ServerMapData {
            val json = JSONObject(raw)
            val cs = json.getJSONArray("countries")
            val countries = (0 until cs.length()).map { i ->
                val c = cs.getJSONObject(i)
                val aliases = c.getJSONArray("aliases")
                val names = listOf(c.optString("name"), c.optString("english")) +
                    (0 until aliases.length()).map { aliases.optString(it) } + when(c.optString("code")) {
                        "US" -> listOf("USA", "США", "United States", "America")
                        "GB" -> listOf("UK", "Britain", "Великобритания", "Англия")
                        "NL" -> listOf("Голландия")
                        "RU" -> listOf("Россия", "Russia")
                        "KR" -> listOf("South Korea", "Южная Корея")
                        else -> emptyList()
                    }
                val rings = c.getJSONArray("rings")
                MapCountry(c.optString("code"), c.getString("iso3"), c.optString("name").ifBlank { c.getString("english") },
                    names.filter { it.isNotBlank() && it != "null" }.map(::normalize).distinct(),
                    MapPoint.fromGeo(c.getDouble("x"), c.getDouble("y")),
                    (0 until rings.length()).map { r ->
                        val ring = rings.getJSONArray(r)
                        (0 until ring.length()).map { p -> ring.getJSONArray(p).let { MapPoint.fromGeo(it.getDouble(0), it.getDouble(1)) } }
                    })
            }
            val ci = json.getJSONArray("cities")
            val cities = (0 until ci.length()).map { i ->
                val c = ci.getJSONObject(i)
                val names = listOf(c.optString("name"), c.optString("english"), c.optString("alias"))
                    .filter { it.isNotBlank() && it != "null" }
                MapCity(names.firstOrNull().orEmpty(), c.getString("iso3"), names.map(::normalize).distinct(),
                    MapPoint.fromGeo(c.getDouble("x"), c.getDouble("y")))
            }
            require(countries.isNotEmpty() && countries.any { it.rings.isNotEmpty() })
            return ServerMapData(countries, cities)
        }

        private fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT).replace('ё', 'е')
        private fun matches(text: String, alias: String): Boolean {
            if (alias.length < 3) return false
            var start = text.indexOf(alias)
            while (start >= 0) {
                val end = start + alias.length
                if ((start == 0 || !text[start - 1].isLetterOrDigit()) && (end == text.length || !text[end].isLetterOrDigit())) return true
                start = text.indexOf(alias, start + 1)
            }
            return false
        }
        internal fun flagCountry(name: String): String? {
            val chars = name.codePoints().toArray()
            for (i in 0 until chars.size - 1) {
                if (chars[i] in 0x1F1E6..0x1F1FF && chars[i + 1] in 0x1F1E6..0x1F1FF) {
                    return "${('A'.code + chars[i] - 0x1F1E6).toChar()}${('A'.code + chars[i + 1] - 0x1F1E6).toChar()}"
                }
            }
            return null
        }
    }
}

/** Normalized Mercator camera; zoom keeps the point under the gesture centroid stationary. */
internal data class ServerMapCamera(val x: Double = 0.54, val y: Double = 0.34, val zoom: Float = 3.5f) {
    fun transform(panX: Float, panY: Float, factor: Float, focusX: Float, focusY: Float, width: Float, height: Float): ServerMapCamera {
        if (width <= 0 || height <= 0) return this
        val nextZoom = (zoom * factor).coerceIn(1f, 24f)
        val before = width * zoom
        val after = width * nextZoom
        val worldX = x + (focusX - width / 2) / before
        val worldY = y + (focusY - height / 2) / before
        return ServerMapCamera((worldX - (focusX - width / 2 + panX) / after).coerceIn(0.0, 1.0),
            (worldY - (focusY - height / 2 + panY) / after).coerceIn(0.0, 1.0), nextZoom)
    }
}
