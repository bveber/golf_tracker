package com.golftracker.data.api

import com.golftracker.data.api.model.NetworkCourseDetailsResponse
import com.golftracker.data.api.model.NetworkCourseSearchResponse
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface CourseApiService {
    
    @GET("v1/search")
    suspend fun searchCourses(
        @Query("search_query") query: String
    ): NetworkCourseSearchResponse

    @GET("v1/courses/{courseId}")
    suspend fun getCourseDetails(
        @Path("courseId") courseId: Int
    ): NetworkCourseDetailsResponse
}
