package com.golftracker.data.repository

import com.golftracker.data.db.dao.CourseDao
import com.golftracker.data.entity.Course
import com.golftracker.data.entity.Hole
import com.golftracker.data.entity.HoleTeeYardage
import com.golftracker.data.entity.TeeSet
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CourseRepository @Inject constructor(
    private val courseDao: CourseDao
) {
    val allCourses: Flow<List<Course>> = courseDao.getAllCourses()



    suspend fun getCourse(courseId: Int): Course? = courseDao.getCourse(courseId)

    suspend fun insertCourse(course: Course): Long = courseDao.insertCourse(course)

    suspend fun updateCourse(course: Course) = courseDao.updateCourse(course)

    suspend fun deleteCourse(course: Course) = courseDao.deleteCourse(course)

    // Tee Sets
    val allTeeSets: Flow<List<TeeSet>> = courseDao.getAllTeeSets()

    fun getTeeSets(courseId: Int): Flow<List<TeeSet>> = courseDao.getTeeSets(courseId)

    suspend fun getTeeSet(teeSetId: Int): TeeSet? = courseDao.getTeeSet(teeSetId)

    suspend fun insertTeeSet(teeSet: TeeSet): Long = courseDao.insertTeeSet(teeSet)

    suspend fun deleteTeeSet(teeSet: TeeSet) = courseDao.deleteTeeSet(teeSet)

    // Holes
    val allHoles: Flow<List<Hole>> = courseDao.getAllHoles()

    fun getHoles(courseId: Int): Flow<List<Hole>> = courseDao.getHoles(courseId)

    suspend fun insertHole(hole: Hole) = courseDao.insertHole(hole)

    suspend fun updateHole(hole: Hole) = courseDao.updateHole(hole)

    suspend fun insertHoles(holes: List<Hole>) = courseDao.insertHoles(holes)
    
    // Yardages
    val allYardages: Flow<List<HoleTeeYardage>> = courseDao.getAllYardages()

    fun getYardagesForTeeSet(teeSetId: Int): Flow<List<HoleTeeYardage>> = courseDao.getYardagesForTeeSet(teeSetId)
    
    suspend fun insertYardage(yardage: HoleTeeYardage) = courseDao.insertYardage(yardage)
}
