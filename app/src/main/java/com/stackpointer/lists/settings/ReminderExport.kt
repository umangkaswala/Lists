package com.stackpointer.lists.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.stackpointer.lists.data.dao.ChecklistItemDao
import com.stackpointer.lists.data.dao.PlaceDao
import com.stackpointer.lists.data.dao.ReminderDao
import com.stackpointer.lists.data.dao.ReminderListDao
import com.stackpointer.lists.data.entity.ChecklistItemEntity
import com.stackpointer.lists.data.entity.PlaceEntity
import com.stackpointer.lists.data.entity.ReminderEntity
import com.stackpointer.lists.data.entity.ReminderListEntity
import com.stackpointer.lists.recurrence.RRule
import com.stackpointer.lists.recurrence.rruleShortLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Settings S16: "Export my reminders — JSON or plain text". */
enum class ExportFormat(val label: String, val extension: String, val mimeType: String) {
    // text/plain for both. A JSON mime type makes several share targets refuse
    // the file outright, and the extension already says what it is.
    JSON("JSON", "json", "text/plain"),
    TEXT("Plain text", "txt", "text/plain")
}

/**
 * Writes the user's reminders to a file and hands it to the system share sheet.
 *
 * A file rather than a big `EXTRA_TEXT` string: an intent's payload has to fit
 * in a one-megabyte Binder transaction shared with everything else in flight,
 * and a few hundred reminders with notes will get there. The file goes in the
 * cache directory, which is the OS's to reclaim — once it has been handed on,
 * our copy is spent.
 *
 * Deliberately *not* a backup: it excludes photos, which are files rather than
 * rows, and there is no import. It answers "can I get my data out", which is
 * the question a local-only app most owes an answer to.
 */
class ReminderExporter(
    context: Context,
    private val reminderDao: ReminderDao,
    private val listDao: ReminderListDao,
    private val checklistDao: ChecklistItemDao,
    private val placeDao: PlaceDao
) {

    private val appContext = context.applicationContext
    private val zone: ZoneId = ZoneId.systemDefault()

    /**
     * Explicitly on the IO dispatcher. The callers are view-model scopes, which
     * run on the main thread — and building a few hundred reminders into JSON
     * and writing the file are both long enough to drop frames.
     */
    suspend fun export(format: ExportFormat): Intent? = withContext(Dispatchers.IO) {
        val lists = listDao.getAll().first()
        // Soft-deleted rows are in the recycle bin, which is the user saying
        // they don't want them. Completed ones stay: they're history, not litter.
        val reminders = reminderDao.getAllForScheduling().filter { it.deletedAt == null }
        val places = placeDao.getAll()
        val checklists = reminders.associate { it.id to checklistDao.getItemsOnce(it.id) }

        val content = when (format) {
            ExportFormat.JSON -> buildJson(lists, reminders, places, checklists)
            ExportFormat.TEXT -> buildText(lists, reminders, places, checklists)
        }

        val uri = writeToCache(content, format) ?: return@withContext null
        Intent(Intent.ACTION_SEND).apply {
            type = format.mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Lists export")
            // Without this the receiving app has no permission to open the URI
            // at all — a FileProvider grant is per-intent, not per-app.
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun writeToCache(content: String, format: ExportFormat): Uri? = runCatching {
        val dir = File(appContext.cacheDir, "exports").apply { mkdirs() }
        // One fixed name per format, overwritten each time: exporting twice
        // should not slowly fill the cache with near-identical files.
        val file = File(dir, "lists-export.${format.extension}")
        file.writeText(content)
        FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", file)
    }.getOrNull()

    private fun buildJson(
        lists: List<ReminderListEntity>,
        reminders: List<ReminderEntity>,
        places: List<PlaceEntity>,
        checklists: Map<Long, List<ChecklistItemEntity>>
    ): String {
        val placesById = places.associateBy { it.id }
        val root = JSONObject()
        root.put("exportedAt", isoInstant(System.currentTimeMillis()))
        root.put("app", "Lists")
        // Named so a future importer can refuse a shape it doesn't understand
        // rather than half-reading it.
        root.put("formatVersion", 1)

        val listsJson = JSONArray()
        lists.forEach { list ->
            listsJson.put(
                JSONObject()
                    .put("name", list.name)
                    .put("isDefault", list.isDefault)
                    .put(
                        "reminders",
                        remindersJson(reminders.filter { it.listId == list.id }, placesById, checklists)
                    )
            )
        }
        root.put("lists", listsJson)

        val placesJson = JSONArray()
        places.forEach { place ->
            placesJson.put(
                JSONObject()
                    .put("name", place.name)
                    .put("latitude", place.latitude)
                    .put("longitude", place.longitude)
                    .put("radiusMeters", place.radiusMeters)
                    .putOpt("address", place.address)
            )
        }
        root.put("places", placesJson)

        return root.toString(2)
    }

    private fun remindersJson(
        reminders: List<ReminderEntity>,
        placesById: Map<Long, PlaceEntity>,
        checklists: Map<Long, List<ChecklistItemEntity>>
    ): JSONArray {
        val array = JSONArray()
        reminders.forEach { reminder ->
            val json = JSONObject()
                .put("title", reminder.title)
                .putOpt("note", reminder.note)
                .put("isAllDay", reminder.isAllDay)
                .put("isCompleted", reminder.isCompleted)
                .put("isImportant", reminder.isImportant)
                .putOpt("dueAt", reminder.dueAt?.let { isoInstant(it) })
                .putOpt("completedAt", reminder.completedAt?.let { isoInstant(it) })
                .put("createdAt", isoInstant(reminder.createdAt))
                .putOpt("repeatRule", reminder.repeatRule)
            reminder.placeId?.let { id ->
                placesById[id]?.let { place ->
                    json.put(
                        "place",
                        JSONObject()
                            .put("name", place.name)
                            .putOpt("trigger", reminder.placeTrigger)
                    )
                }
            }
            val items = checklists[reminder.id].orEmpty()
            if (items.isNotEmpty()) {
                val itemsJson = JSONArray()
                items.forEach {
                    itemsJson.put(JSONObject().put("text", it.text).put("isCompleted", it.isCompleted))
                }
                json.put("checklist", itemsJson)
            }
            array.put(json)
        }
        return array
    }

    private fun buildText(
        lists: List<ReminderListEntity>,
        reminders: List<ReminderEntity>,
        places: List<PlaceEntity>,
        checklists: Map<Long, List<ChecklistItemEntity>>
    ): String {
        val placesById = places.associateBy { it.id }
        val out = StringBuilder()
        out.appendLine("Lists — exported ${friendlyDateTime(System.currentTimeMillis())}")
        out.appendLine()

        lists.forEach { list ->
            val inList = reminders.filter { it.listId == list.id }
            out.appendLine(list.name.uppercase(Locale.getDefault()))
            if (inList.isEmpty()) {
                out.appendLine("  (empty)")
            }
            inList.forEach { reminder ->
                // A tick or an empty box, so the state survives a format with
                // no columns to put it in.
                out.appendLine("  ${if (reminder.isCompleted) "[x]" else "[ ]"} ${reminder.title}")
                detailLines(reminder, placesById).forEach { out.appendLine("      $it") }
                checklists[reminder.id].orEmpty().forEach { item ->
                    out.appendLine("      ${if (item.isCompleted) "[x]" else "[ ]"} ${item.text}")
                }
            }
            out.appendLine()
        }
        return out.toString()
    }

    private fun detailLines(reminder: ReminderEntity, placesById: Map<Long, PlaceEntity>): List<String> {
        val lines = mutableListOf<String>()
        reminder.dueAt?.let {
            lines += if (reminder.isAllDay) {
                "Due: ${friendlyDate(it)} (all day)"
            } else {
                "Due: ${friendlyDateTime(it)}"
            }
        }
        RRule.parse(reminder.repeatRule)?.let { rule ->
            val anchor = Instant.ofEpochMilli(reminder.seriesStartAt ?: reminder.dueAt ?: 0L)
                .atZone(zone).toLocalDate()
            lines += "Repeats: ${rruleShortLabel(rule, anchor)}"
        }
        reminder.placeId?.let { id ->
            placesById[id]?.let { place ->
                val trigger = if (reminder.placeTrigger == "LEAVE") "when leaving" else "when arriving at"
                lines += "Place: $trigger ${place.name}"
            }
        }
        reminder.note?.takeIf { it.isNotBlank() }?.let { note ->
            // Indented under the reminder so a multi-line note doesn't read as
            // a new entry.
            lines += "Note: " + note.trim().replace("\n", "\n            ")
        }
        return lines
    }

    private fun isoInstant(epochMillis: Long): String = Instant.ofEpochMilli(epochMillis).toString()

    private fun friendlyDate(epochMillis: Long): String =
        localDateTime(epochMillis).format(DATE_FORMAT)

    private fun friendlyDateTime(epochMillis: Long): String =
        localDateTime(epochMillis).format(DATE_TIME_FORMAT)

    private fun localDateTime(epochMillis: Long): LocalDateTime =
        Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDateTime()

    private companion object {
        val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
        val DATE_TIME_FORMAT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.ENGLISH)
    }
}
