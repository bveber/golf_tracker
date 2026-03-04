package com.golftracker.util

import android.content.Context
import com.golftracker.data.model.RoundWithDetails
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject

class JsonExporter @Inject constructor() {

    private val gson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
        .create()

    /**
     * Exports a single round's detailed hole-by-hole statistics to a JSON file in the cache directory.
     *
     * @param context Application context for accessing the cache directory.
     * @param roundDetails The round and its associated statistics to export.
     * @return The generated File object, or null if an error occurred.
     */
    fun exportRoundToCache(context: Context, roundDetails: RoundWithDetails): File? {
        val round = roundDetails.round
        val course = roundDetails.course
        val dateStr = SimpleDateFormat("yyyyMMdd", Locale.US).format(round.date)
        val fileName = "golf_round_${course.name.replace(" ", "_")}_$dateStr.json"
        
        val cacheDir = context.cacheDir
        val file = File(cacheDir, fileName)
        
        return try {
            FileWriter(file).use { writer ->
                gson.toJson(roundDetails, writer)
            }
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Exports all comprehensive round data, including strokes gained metrics, to a single JSON file.
     *
     * @param context Application context for accessing the cache directory.
     * @param allRounds A list of all finalized rounds to include in the export.
     * @return The generated File object, or null if an error occurred.
     */
    fun exportAllDataToCache(context: Context, allRounds: List<RoundWithDetails>): File? {
        val fileName = "golf_tracker_all_data.json"
        val cacheDir = context.cacheDir
        val file = File(cacheDir, fileName)
        
        return try {
            FileWriter(file).use { writer ->
                gson.toJson(allRounds, writer)
            }
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
