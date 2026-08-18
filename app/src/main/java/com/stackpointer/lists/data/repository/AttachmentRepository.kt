package com.stackpointer.lists.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.stackpointer.lists.data.dao.AttachmentDao
import com.stackpointer.lists.data.entity.AttachmentEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.util.UUID

/**
 * Photos attached to reminders.
 *
 * Images are **copied** into the app's own files directory rather than
 * referenced where they sit. A `content://` URI from the photo picker is a
 * temporary read grant that stops resolving once the process that received it
 * is gone, and a gallery image can be deleted or moved by its owning app at any
 * time — either way the attachment would silently become a broken thumbnail.
 * A copy costs disk space and is the only version that still works next week.
 */
class AttachmentRepository(
    private val context: Context,
    private val attachmentDao: AttachmentDao
) {
    /**
     * `by lazy`, not a getter: [fileFor] is called from composition, once per
     * thumbnail, and a getter that called `mkdirs()` every time meant a
     * filesystem syscall on the main thread per photo per recomposition.
     */
    private val directory: File by lazy {
        File(context.filesDir, DIRECTORY_NAME).apply { mkdirs() }
    }

    fun observeForReminder(reminderId: Long): Flow<List<AttachmentEntity>> =
        attachmentDao.observeForReminder(reminderId)

    /** Absolute path for display. Attachments are private to the app. */
    fun fileFor(attachment: AttachmentEntity): File = File(directory, attachment.fileName)

    fun fileFor(fileName: String): File = File(directory, fileName)

    /**
     * Copies [source] into app storage and returns the stored file name, or
     * null if the copy failed — a revoked URI or a file the picker can no
     * longer open, both of which happen in normal use and neither of which
     * should crash the sheet.
     */
    suspend fun importImage(source: Uri): String? = withContext(Dispatchers.IO) {
        val fileName = "${UUID.randomUUID()}.jpg"
        val target = File(directory, fileName)
        runCatching {
            context.contentResolver.openInputStream(source)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: error("Could not open $source")
            fileName
        }.getOrElse {
            Log.w(TAG, "Could not import attachment", it)
            target.delete()
            null
        }
    }

    /** A file the camera can write straight into, avoiding a second copy. */
    suspend fun newCameraFile(): File = withContext(Dispatchers.IO) {
        File(directory, "${UUID.randomUUID()}.jpg")
    }

    suspend fun attach(reminderId: Long, fileNames: List<String>) {
        val now = Instant.now().toEpochMilli()
        fileNames.forEach { name ->
            attachmentDao.insert(
                AttachmentEntity(reminderId = reminderId, fileName = name, createdAt = now)
            )
        }
    }

    suspend fun delete(attachmentId: Long) {
        val attachment = attachmentDao.getById(attachmentId) ?: return
        attachmentDao.deleteById(attachmentId)
        withContext(Dispatchers.IO) { File(directory, attachment.fileName).delete() }
    }

    suspend fun getForReminder(reminderId: Long): List<AttachmentEntity> =
        attachmentDao.getForReminder(reminderId)

    /**
     * Deletes image files no row points at any more.
     *
     * Necessary because the files are not in the database. Deleting a reminder
     * for good cascades its attachment rows away, and the recycle-bin purge
     * does the same on a timer — but neither touches the filesystem, so without
     * this sweep every photo ever attached would stay on the phone forever.
     *
     * Runs on app start. It is a directory listing and a set lookup; on a
     * personal reminder app that is nothing.
     *
     * Recently-written files are skipped. A photo attached to a *new* reminder
     * exists on disk before the reminder is saved and so has no row pointing at
     * it yet — the sweep racing that window would delete the picture out from
     * under the sheet the user is still filling in.
     */
    suspend fun purgeOrphanFiles(): Int = withContext(Dispatchers.IO) {
        val referenced = attachmentDao.allFileNames().toSet()
        val cutoff = System.currentTimeMillis() - GRACE_PERIOD_MILLIS
        var removed = 0
        directory.listFiles().orEmpty().forEach { file ->
            if (file.name !in referenced && file.lastModified() < cutoff && file.delete()) {
                removed++
            }
        }
        removed
    }

    private companion object {
        const val DIRECTORY_NAME = "attachments"
        const val TAG = "AttachmentRepository"

        /**
         * How long a file is left alone before the sweep will consider it
         * abandoned. Long enough to cover picking a photo, writing a title and
         * saving; short enough that a genuinely orphaned file doesn't linger.
         */
        const val GRACE_PERIOD_MILLIS = 60L * 60L * 1000L
    }
}
