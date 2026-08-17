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
    // Populated starting Phase 3 (custom repeat) — schema-ready now to avoid a
    // later migration.
    val repeatRule: String? = null,
    // Populated starting Phase 7 (places/geofencing).
    val placeId: Long? = null
)
