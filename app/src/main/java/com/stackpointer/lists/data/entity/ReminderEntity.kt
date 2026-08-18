package com.stackpointer.lists.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "reminders",
    foreignKeys = [
        ForeignKey(
            entity = ReminderListEntity::class,
            parentColumns = ["id"],
            childColumns = ["listId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("listId"), Index("deletedAt"), Index("isCompleted"), Index("placeId")]
)
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val listId: Long,
    val title: String,
    val note: String? = null,
    val isAllDay: Boolean = false,
    val dueAt: Long? = null,
    val isCompleted: Boolean = false,
    val completedAt: Long? = null,
    val isImportant: Boolean = false,
    val deletedAt: Long? = null,
    val createdAt: Long,
    // RFC 5545 rule parameters, e.g. "FREQ=WEEKLY;INTERVAL=2;BYDAY=TU,FR".
    // Null means the reminder doesn't repeat. See recurrence/RRule.kt.
    val repeatRule: String? = null,
    // The first occurrence of a repeating series. [dueAt] moves forward each
    // time the reminder is completed, so it can't double as the series anchor:
    // a COUNT=10 rule would restart its count on every completion and never end.
    val seriesStartAt: Long? = null,
    // ---- Place trigger (Phase 7) -------------------------------------------
    // No foreign key to `places`: adding one to an existing table means
    // rebuilding it in SQLite, and the only rule it would enforce -- clearing
    // placeId when a place is deleted -- is one line in PlaceRepository.
    val placeId: Long? = null,
    /** "ARRIVE" or "LEAVE"; see [com.stackpointer.lists.places.PlaceTrigger]. */
    val placeTrigger: String? = null,
    /**
     * Optional "only between" window, in minutes from local midnight, so a
     * geofence doesn't alert at 3 am. The platform can't time-limit a geofence,
     * so this is applied when the transition arrives, not when it's registered.
     * An end before the start means the window crosses midnight.
     */
    val placeWindowStartMinute: Int? = null,
    val placeWindowEndMinute: Int? = null,
    /** Comma-separated MO,TU,WE,TH,FR,SA,SU. Null means every day. */
    val placeWindowDays: String? = null
)
