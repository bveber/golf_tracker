package com.golftracker.data.api.model

import com.google.gson.annotations.SerializedName

data class NetworkCourseSearchResponse(
    @SerializedName("courses") val courses: List<NetworkCourseSummary>
)

data class NetworkCourseSummary(
    @SerializedName("id") val id: Int,
    @SerializedName("course_name") val name: String,
    @SerializedName("location") val location: NetworkLocation?
) {
    val city: String? get() = location?.city
    val state: String? get() = location?.state
    val holes: Int? get() = 18 // Defaulting to 18 for now as it's common, or can be derived from tees
}

data class NetworkCourseDetailsResponse(
    @SerializedName("course") val course: NetworkCourseDetails
)

data class NetworkCourseDetails(
    @SerializedName("id") val id: Int,
    @SerializedName("course_name") val name: String,
    @SerializedName("location") val location: NetworkLocation?,
    @SerializedName("tees") val tees: NetworkTees?
)

data class NetworkLocation(
    @SerializedName("city") val city: String?,
    @SerializedName("state") val state: String?,
    @SerializedName("address") val address: String?
)

data class NetworkTees(
    @SerializedName("male") val male: List<NetworkScorecard>?,
    @SerializedName("female") val female: List<NetworkScorecard>?
) {
    val all: List<NetworkScorecard> get() = (male ?: emptyList()) + (female ?: emptyList())
}

data class NetworkScorecard(
    @SerializedName("tee_name") val teeName: String,
    @SerializedName("par_total") val par: Int, 
    @SerializedName("slope_rating") val slope: Int?,
    @SerializedName("course_rating") val rating: Double?,
    @SerializedName("holes") val holes: List<NetworkHole>
)

data class NetworkHole(
    @SerializedName("par") val par: Int,
    @SerializedName("yardage") val yardage: Int,
    @SerializedName("handicap") val handicapIndex: Int?
)
