package com.stackpointer.lists.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A photo attached to a reminder.
 *
 * Only the file *name* is stored, never a full path or a content:// URI. The
 * app's own files directory moves between installs and between devices, and a
 * URI handed out by the gallery is a temporary grant that stops resolving as
 * soon as the process that received it dies — so the picked image is copied
 * into app-private storage and this row points at that copy.
 */
@Entity(
    tableName = "attachments",
    foreignKeys = [
        ForeignKey(
            entity = ReminderEntity::class,
            parentColumns = ["id"],
            childColumns = ["reminderId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("reminderId")]
)
data class AttachmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val reminderId: Long,
    /** Resolved against the attachments directory — see AttachmentRepository. */
    val fileName: String,
    val createdAt: Long
)
