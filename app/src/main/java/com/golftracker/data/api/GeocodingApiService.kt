package com.golftracker.data.api

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Query

interface GeocodingApiService {
    @GET("search")
    suspend fun geocode(
        @Query("city") city: String,
        @Query("state") state: String,
        @Query("country") country: String = "US",
        @Query("format") format: String = "json",
        @Query("limit") limit: Int = 1
    ): List<NominatimResult>
}

data class NominatimResult(
    @SerializedName("lat") val lat: String,
    @SerializedName("lon") val lon: String
)
