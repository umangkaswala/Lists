package com.stackpointer.lists.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reminder_lists")
data class ReminderListEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colorArgb: Int,
    val position: Int,
    val isDefault: Boolean = false,
    val createdAt: Long
)

/**
 * Where a deleted list's reminders go: the default list, or failing that
 * whichever list is left at the top. Null only when the list being deleted is
 * the last one.
 *
 * Lives beside the entity because three places need the same answer -- the
 * transaction that does the move, and the confirmation dialog that names the
 * list beforehand -- and a second copy of the rule is a second copy to drift.
 */
fun fallbackListFor(
    lists: List<ReminderListEntity>,
    deleting: ReminderListEntity
): ReminderListEntity? = lists
    .filter { it.id != deleting.id }
    .minWithOrNull(compareByDescending<ReminderListEntity> { it.isDefault }.thenBy { it.position })
