package com.golftracker.data.api

import com.golftracker.data.api.model.NwsObservationResponse
import com.golftracker.data.api.model.NwsPointsResponse
import com.golftracker.data.api.model.NwsStationsResponse
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Url

interface WeatherApiService {

    @GET("points/{lat},{lon}")
    suspend fun getPoints(
        @Path("lat") lat: Double,
        @Path("lon") lon: Double
    ): NwsPointsResponse

    @GET
    suspend fun getObservationStations(
        @Url url: String
    ): NwsStationsResponse

    @GET("stations/{stationId}/observations/latest")
    suspend fun getLatestObservation(
        @Path("stationId") stationId: String
    ): NwsObservationResponse
}
