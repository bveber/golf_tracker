package com.golftracker.data.api.model

import com.google.gson.annotations.SerializedName

data class NwsPointsResponse(
    val properties: NwsPointsProperties
)

data class NwsPointsProperties(
    @SerializedName("observationStations")
    val observationStationsUrl: String,
    @SerializedName("gridId")
    val gridId: String? = null,
    @SerializedName("gridX")
    val gridX: Int? = null,
    @SerializedName("gridY")
    val gridY: Int? = null
)

data class NwsForecastResponse(
    val properties: NwsForecastProperties
)

data class NwsForecastProperties(
    val periods: List<NwsForecastPeriod>
)

data class NwsForecastPeriod(
    @SerializedName("startTime")
    val startTime: String?,
    @SerializedName("temperature")
    val temperature: Int?,
    @SerializedName("temperatureUnit")
    val temperatureUnit: String?,
    @SerializedName("windSpeed")
    val windSpeed: String?,
    @SerializedName("windDirection")
    val windDirection: String?,
    @SerializedName("shortForecast")
    val shortForecast: String?,
    @SerializedName("relativeHumidity")
    val relativeHumidity: NwsQuantitativeValue?
)

data class NwsStationsResponse(
    val features: List<NwsStationFeature>
)

data class NwsStationFeature(
    val properties: NwsStationProperties
)

data class NwsStationProperties(
    @SerializedName("stationIdentifier")
    val stationIdentifier: String
)

data class NwsObservationResponse(
    val properties: NwsObservationProperties
)

data class NwsObservationProperties(
    @SerializedName("textDescription")
    val textDescription: String?,
    val temperature: NwsQuantitativeValue?,
    @SerializedName("windSpeed")
    val windSpeed: NwsQuantitativeValue?,
    @SerializedName("windDirection")
    val windDirection: NwsQuantitativeValue?,
    @SerializedName("barometricPressure")
    val barometricPressure: NwsQuantitativeValue?,
    @SerializedName("relativeHumidity")
    val relativeHumidity: NwsQuantitativeValue?
)

data class NwsQuantitativeValue(
    val value: Double?,
    @SerializedName("unitCode")
    val unitCode: String?
)
