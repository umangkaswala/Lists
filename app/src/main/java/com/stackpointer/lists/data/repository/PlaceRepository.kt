package com.stackpointer.lists.data.repository

import com.stackpointer.lists.data.dao.PlaceDao
import com.stackpointer.lists.data.entity.PlaceEntity
import com.stackpointer.lists.data.entity.ReminderEntity
import kotlinx.coroutines.flow.Flow

class PlaceRepository(
    private val placeDao: PlaceDao,
    private val geofences: ReminderAlarms = ReminderAlarms.None
) {
    fun observePlaces(): Flow<List<PlaceEntity>> = placeDao.observeAll()

    fun observePlaceReminders(): Flow<List<ReminderEntity>> = placeDao.observeActivePlaceReminders()

    suspend fun getPlace(id: Long): PlaceEntity? = placeDao.getById(id)

    suspend fun savePlace(place: PlaceEntity): Long {
        val id = if (place.id == 0L) {
            placeDao.insert(place)
        } else {
            placeDao.update(place)
            place.id
        }
        // A moved place or a changed radius is a different circle on the
        // ground, so the fences have to be rebuilt even though no reminder
        // changed.
        geofences.requestSync()
        return id
    }

    /**
     * Deletes a place and detaches it from its reminders.
     *
     * The reminders themselves survive — losing a saved location shouldn't
     * silently delete the things you wanted to be reminded of there. They simply
     * stop having a trigger, which is visible on Detail rather than invisible.
     */
    suspend fun deletePlace(id: Long) {
        placeDao.clearPlaceFromReminders(id)
        placeDao.deleteById(id)
        geofences.requestSync()
    }
}
