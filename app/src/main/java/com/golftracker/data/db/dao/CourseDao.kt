package com.golftracker.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.golftracker.data.entity.Course
import com.golftracker.data.entity.Hole
import com.golftracker.data.entity.HoleTeeYardage
import com.golftracker.data.entity.TeeSet
import kotlinx.coroutines.flow.Flow

@Dao
interface CourseDao {
    @Query("SELECT * FROM courses ORDER BY name ASC")
    fun getAllCourses(): Flow<List<Course>>

    @Query("SELECT * FROM courses WHERE id = :courseId")
    suspend fun getCourse(courseId: Int): Course?

    @Upsert
    suspend fun insertCourse(course: Course): Long

    @Update
    suspend fun updateCourse(course: Course)

    @Delete
    suspend fun deleteCourse(course: Course)

    // Tee Sets
    @Query("SELECT * FROM tee_sets")
    fun getAllTeeSets(): Flow<List<TeeSet>>

    @Query("SELECT * FROM tee_sets WHERE course_id = :courseId")
    fun getTeeSets(courseId: Int): Flow<List<TeeSet>>

    @Query("SELECT * FROM tee_sets WHERE id = :teeSetId")
    suspend fun getTeeSet(teeSetId: Int): TeeSet?

    @Upsert
    suspend fun insertTeeSet(teeSet: TeeSet): Long

    @Delete
    suspend fun deleteTeeSet(teeSet: TeeSet)

    // Holes
    @Query("SELECT * FROM holes")
    fun getAllHoles(): Flow<List<Hole>>

    @Query("SELECT * FROM holes WHERE course_id = :courseId ORDER BY hole_number ASC")
    fun getHoles(courseId: Int): Flow<List<Hole>>
    
    @Upsert
    suspend fun insertHole(hole: Hole): Long
    
    @Update
    suspend fun updateHole(hole: Hole)
    
    @Upsert
    suspend fun insertHoles(holes: List<Hole>)
    
    // Yardages
    @Query("SELECT * FROM hole_tee_yardages")
    fun getAllYardages(): Flow<List<HoleTeeYardage>>

    @Query("SELECT * FROM hole_tee_yardages WHERE tee_set_id = :teeSetId")
    fun getYardagesForTeeSet(teeSetId: Int): Flow<List<HoleTeeYardage>>
    
    @Upsert
    suspend fun insertYardage(yardage: HoleTeeYardage): Long
}
