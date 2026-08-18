package com.stackpointer.lists.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One row for every time a reminder was ticked off.
 *
 * A completion can't be recorded on [ReminderEntity] alone. Completing a
 * *repeating* reminder never sets `isCompleted` — it rolls `dueAt` forward to
 * the next occurrence — so the reminder row keeps no trace of the occurrence
 * that was just finished. Without this table the Completed screen would never
 * show a repeating reminder at all, the "on time / 3 min late" line would be
 * measured against the *next* due date rather than the one that was met, and
 * Detail's streak card would have nothing to count.
 */
@Entity(
    tableName = "completions",
    foreignKeys = [
        ForeignKey(
            entity = ReminderEntity::class,
            parentColumns = ["id"],
            childColumns = ["reminderId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("reminderId"), Index("completedAt")]
)
data class CompletionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val reminderId: Long,
    /** When the user actually ticked it off. */
    val completedAt: Long,
    /** The due instant of the occurrence that was met — null if it had no due date. */
    val dueAt: Long? = null,
    /** An all-day occurrence is never "late", so the comparison has to know. */
    val wasAllDay: Boolean = false,
    /**
     * What the reminder's dueAt became once this completion was recorded — the
     * next occurrence for a repeating reminder, unchanged otherwise.
     *
     * Undo compares it against the reminder's *current* dueAt and only puts the
     * old date back when the two still match. Without that check, editing a due
     * date after completing something and then tapping Undo would silently
     * throw the edit away.
     */
    val nextDueAt: Long? = null
)
