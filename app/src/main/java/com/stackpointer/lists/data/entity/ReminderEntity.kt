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
    indices = [Index("listId"), Index("deletedAt"), Index("isCompleted")]
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
    // Populated starting Phase 7 (places/geofencing).
    val placeId: Long? = null
)
