package com.tarang.launcher.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.roundToInt

/**
 * Current weather for the overlays. Kept deliberately tiny — a temperature and a condition glyph — to
 * sit quietly beside the clock or in the top bar.
 */
data class WeatherData(val temp: Int, val glyph: String) {
    val tempText: String get() = "$temp°"
}

/** A geocoded city candidate for the city picker. */
data class GeoResult(val name: String, val country: String?, val lat: Double, val lon: Double) {
    val label: String get() = if (!country.isNullOrBlank()) "$name, $country" else name
}

/**
 * Fetches current weather while the launcher is visible and [enabled]. If [lat]/[lon] are set (a chosen
 * city) they're used; otherwise the location is resolved once by IP (no GPS on a TV). Weather then
 * refreshes every 30 min. Everything is best-effort — any failure just leaves the readout hidden.
 * Uses Open-Meteo (free, no API key), and ipapi.co for the coarse auto-location.
 */
@Composable
fun rememberWeather(enabled: Boolean, fahrenheit: Boolean, lat: Double?, lon: Double?): WeatherData? {
    var weather by remember { mutableStateOf<WeatherData?>(null) }
    RunWhileStarted(enabled, fahrenheit, lat, lon) {
        if (!enabled) return@RunWhileStarted
        val loc = if (lat != null && lon != null) {
            GeoLoc(lat, lon)
        } else {
            withContext(Dispatchers.IO) { fetchLocation() } ?: return@RunWhileStarted
        }
        while (true) {
            withContext(Dispatchers.IO) { fetchWeather(loc, fahrenheit) }?.let { weather = it }
            delay(30 * 60_000L)
        }
    }
    return if (enabled) weather else null
}

/** Looks up cities matching [query] (Open-Meteo geocoding). Empty on blank query or any failure. */
suspend fun geocodeCity(query: String): List<GeoResult> = withContext(Dispatchers.IO) {
    if (query.isBlank()) return@withContext emptyList()
    runCatching {
        val url = "https://geocoding-api.open-meteo.com/v1/search?name=" +
            URLEncoder.encode(query.trim(), "UTF-8") + "&count=6&language=en&format=json"
        val json = httpGet(url) ?: return@runCatching emptyList<GeoResult>()
        val arr = JSONObject(json).optJSONArray("results") ?: return@runCatching emptyList<GeoResult>()
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            GeoResult(
                name = o.getString("name"),
                country = o.optString("country").ifBlank { null },
                lat = o.getDouble("latitude"),
                lon = o.getDouble("longitude"),
            )
        }
    }.getOrDefault(emptyList())
}

private data class GeoLoc(val lat: Double, val lon: Double)

private fun fetchLocation(): GeoLoc? = runCatching {
    val json = httpGet("https://ipapi.co/json/") ?: return null
    val o = JSONObject(json)
    GeoLoc(o.getDouble("latitude"), o.getDouble("longitude"))
}.getOrNull()

private fun fetchWeather(loc: GeoLoc, fahrenheit: Boolean): WeatherData? = runCatching {
    val unit = if (fahrenheit) "fahrenheit" else "celsius"
    val url = "https://api.open-meteo.com/v1/forecast?latitude=${loc.lat}&longitude=${loc.lon}" +
        "&current=temperature_2m,weather_code&temperature_unit=$unit"
    val json = httpGet(url) ?: return null
    val cur = JSONObject(json).getJSONObject("current")
    WeatherData(
        temp = cur.getDouble("temperature_2m").roundToInt(),
        glyph = weatherGlyph(cur.getInt("weather_code")),
    )
}.getOrNull()

private fun httpGet(urlStr: String): String? = runCatching {
    val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
        connectTimeout = 8000
        readTimeout = 8000
        requestMethod = "GET"
        setRequestProperty("User-Agent", "TarangLauncher")
    }
    try {
        if (conn.responseCode != 200) return null
        conn.inputStream.bufferedReader().use { it.readText() }
    } finally {
        conn.disconnect()
    }
}.getOrNull()

/** WMO weather codes → a single condition glyph. */
private fun weatherGlyph(code: Int): String = when (code) {
    0 -> "☀️"
    1, 2 -> "⛅"
    3 -> "☁️"
    45, 48 -> "🌫️"
    in 51..57 -> "🌦️"
    in 61..67 -> "🌧️"
    in 71..77 -> "🌨️"
    in 80..82 -> "🌧️"
    in 85..86 -> "🌨️"
    in 95..99 -> "⛈️"
    else -> "☁️"
}
