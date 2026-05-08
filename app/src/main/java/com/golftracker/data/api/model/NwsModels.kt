package com.golftracker.data.api.model

import com.google.gson.annotations.SerializedName

data class NwsPointsResponse(
    val properties: NwsPointsProperties
)

data class NwsPointsProperties(
    @SerializedName("observationStations")
    val observationStationsUrl: String
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
