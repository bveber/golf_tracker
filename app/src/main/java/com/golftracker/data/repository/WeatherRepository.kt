package com.golftracker.data.repository

import com.golftracker.data.api.GeocodingApiService
import com.golftracker.data.api.WeatherApiService
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

data class WeatherData(
    val condition: String?,
    val temperatureFahrenheit: Int?,
    val windSpeedMph: Int?,
    val windDirection: String?,
    val humidityPercent: Int?,
    val pressureInHg: Double?
)

@Singleton
class WeatherRepository @Inject constructor(
    private val weatherApiService: WeatherApiService,
    private val geocodingApiService: GeocodingApiService
) {
    suspend fun fetchWeatherForCity(city: String, state: String): WeatherData {
        val results = geocodingApiService.geocode(city = city, state = state)
        val result = results.firstOrNull() ?: error("Could not geocode $city, $state")
        return fetchWeather(result.lat.toDouble(), result.lon.toDouble())
    }

    suspend fun fetchWeather(lat: Double, lon: Double): WeatherData {
        val points = weatherApiService.getPoints(lat, lon)

        // Prefer hourly forecast — it's interpolated to the exact coordinates rather than
        // read from a distant airport observation station, giving more accurate local wind.
        val forecastData = runCatching {
            val props = points.properties
            if (props.gridId != null && props.gridX != null && props.gridY != null) {
                weatherApiService.getHourlyForecast(props.gridId, props.gridX, props.gridY)
                    .properties.periods.firstOrNull()
            } else null
        }.getOrNull()

        if (forecastData != null) {
            val tempF = forecastData.temperature?.let {
                if (forecastData.temperatureUnit == "C") celsiusToFahrenheit(it.toDouble()) else it
            }
            val windMph = forecastData.windSpeed?.let { parseWindSpeedMph(it) }
            val humidity = forecastData.relativeHumidity?.value?.roundToInt()

            return WeatherData(
                condition = forecastData.shortForecast,
                temperatureFahrenheit = tempF,
                windSpeedMph = windMph,
                windDirection = forecastData.windDirection,
                humidityPercent = humidity,
                pressureInHg = null
            )
        }

        // Fallback: observation stations (may be from a distant airport)
        val stations = weatherApiService.getObservationStations(points.properties.observationStationsUrl)
        if (stations.features.isEmpty()) error("No observation stations found for location")

        // Try up to 5 stations — nearby stations sometimes return null for temperature
        var bestObs = weatherApiService.getLatestObservation(
            stations.features[0].properties.stationIdentifier
        ).properties
        for (i in 1 until minOf(5, stations.features.size)) {
            if (bestObs.temperature?.value != null) break
            bestObs = weatherApiService.getLatestObservation(
                stations.features[i].properties.stationIdentifier
            ).properties
        }

        val tempF = bestObs.temperature?.value?.let { celsiusToFahrenheit(it) }
        val windMph = bestObs.windSpeed?.value?.let { metersPerSecondToMph(it) }
        val windDir = bestObs.windDirection?.value?.let { degreesToCardinal(it) }
        val pressureInHg = bestObs.barometricPressure?.value?.let { pascalsToInHg(it) }
        val humidity = bestObs.relativeHumidity?.value?.roundToInt()

        return WeatherData(
            condition = bestObs.textDescription,
            temperatureFahrenheit = tempF,
            windSpeedMph = windMph,
            windDirection = windDir,
            humidityPercent = humidity,
            pressureInHg = pressureInHg
        )
    }

    /**
     * Parses NWS wind speed strings like "15 mph", "10 to 20 mph", or "Calm".
     * For ranges, returns the average of the two values.
     */
    private fun parseWindSpeedMph(windSpeed: String): Int? {
        if (windSpeed.equals("Calm", ignoreCase = true)) return 0
        val rangeMatch = Regex("""(\d+)\s+to\s+(\d+)""").find(windSpeed)
        if (rangeMatch != null) {
            val lo = rangeMatch.groupValues[1].toIntOrNull() ?: return null
            val hi = rangeMatch.groupValues[2].toIntOrNull() ?: return null
            return (lo + hi) / 2
        }
        return Regex("""(\d+)""").find(windSpeed)?.groupValues?.get(1)?.toIntOrNull()
    }

    private fun celsiusToFahrenheit(celsius: Double): Int =
        ((celsius * 9.0 / 5.0) + 32).roundToInt()

    private fun metersPerSecondToMph(mps: Double): Int =
        (mps * 2.23694).roundToInt()

    private fun pascalsToInHg(pascals: Double): Double =
        (pascals / 3386.389).let { Math.round(it * 100).toDouble() / 100 }

    private fun degreesToCardinal(degrees: Double): String {
        val directions = listOf("N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
            "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW")
        val index = ((degrees + 11.25) / 22.5).toInt() % 16
        return directions[index]
    }
}
