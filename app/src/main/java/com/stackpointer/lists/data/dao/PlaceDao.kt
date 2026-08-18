package com.stackpointer.lists.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.stackpointer.lists.data.entity.PlaceEntity
import com.stackpointer.lists.data.entity.ReminderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaceDao {
    @Query("SELECT * FROM places ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<PlaceEntity>>

    @Query("SELECT * FROM places WHERE id = :id")
    suspend fun getById(id: Long): PlaceEntity?

    @Query("SELECT * FROM places")
    suspend fun getAll(): List<PlaceEntity>

    @Insert
    suspend fun insert(place: PlaceEntity): Long

    @Update
    suspend fun update(place: PlaceEntity)

    @Query("DELETE FROM places WHERE id = :id")
    suspend fun deleteById(id: Long)

    /**
     * Every live reminder tied to a place. Soft-deleted and completed ones are
     * excluded because they are exactly the reminders whose geofence should
     * *stop* being registered, and the 100-fence budget is small enough that
     * spending it on finished reminders matters.
     *
     * Newest first, and the ORDER BY is load-bearing: the registrar truncates
     * this list at the platform's 100-geofence limit, so without a defined
     * order the survivors would be whatever SQLite happened to return and
     * could change from one sync to the next.
     */
    @Query(
        "SELECT * FROM reminders WHERE placeId IS NOT NULL AND placeTrigger IS NOT NULL " +
            "AND deletedAt IS NULL AND isCompleted = 0 ORDER BY id DESC"
    )
    suspend fun activePlaceReminders(): List<ReminderEntity>

    @Query(
        "SELECT * FROM reminders WHERE placeId IS NOT NULL AND placeTrigger IS NOT NULL " +
            "AND deletedAt IS NULL AND isCompleted = 0"
    )
    fun observeActivePlaceReminders(): Flow<List<ReminderEntity>>

    /**
     * Detaches a place from its reminders. Stands in for the `ON DELETE SET
     * NULL` foreign key that would otherwise force a table rebuild — see the
     * note on [ReminderEntity.placeId].
     */
    @Query(
        "UPDATE reminders SET placeId = NULL, placeTrigger = NULL, " +
            "placeWindowStartMinute = NULL, placeWindowEndMinute = NULL, " +
            "placeWindowDays = NULL WHERE placeId = :placeId"
    )
    suspend fun clearPlaceFromReminders(placeId: Long)
}
