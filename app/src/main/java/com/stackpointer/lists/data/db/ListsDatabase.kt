package com.stackpointer.lists.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.stackpointer.lists.data.dao.AttachmentDao
import com.stackpointer.lists.data.dao.ChecklistItemDao
import com.stackpointer.lists.data.dao.CompletionDao
import com.stackpointer.lists.data.dao.PlaceDao
import com.stackpointer.lists.data.dao.ReminderDao
import com.stackpointer.lists.data.dao.ReminderListDao
import com.stackpointer.lists.data.entity.AttachmentEntity
import com.stackpointer.lists.data.entity.ChecklistItemEntity
import com.stackpointer.lists.data.entity.CompletionEntity
import com.stackpointer.lists.data.entity.PlaceEntity
import com.stackpointer.lists.data.entity.ReminderEntity
import com.stackpointer.lists.data.entity.ReminderListEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean

@Database(
    entities = [
        ReminderEntity::class,
        ReminderListEntity::class,
        AttachmentEntity::class,
        ChecklistItemEntity::class,
        CompletionEntity::class,
        PlaceEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class ListsDatabase : RoomDatabase() {
    abstract fun reminderDao(): ReminderDao
    abstract fun reminderListDao(): ReminderListDao
    abstract fun checklistItemDao(): ChecklistItemDao
    abstract fun completionDao(): CompletionDao
    abstract fun placeDao(): PlaceDao
    abstract fun attachmentDao(): AttachmentDao

    companion object {
        @Volatile
        private var instance: ListsDatabase? = null

        fun get(context: Context): ListsDatabase {
            return instance ?: synchronized(this) {
                instance ?: build(context).also { instance = it }
            }
        }

        private fun build(context: Context): ListsDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                ListsDatabase::class.java,
                "lists.db"
            )
                // Real migrations where one exists. The destructive fallback
                // below stays as the net for any *earlier* schema bump that
                // never got one -- it only fires when no path is registered.
                .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .addCallback(SeedCallback(context))
                .build()
        }

        /**
         * Adds the completion log (see CompletionEntity). Written by hand
         * rather than left to the destructive fallback because by this point
         * the app is on a real phone with real reminders on it -- wiping them
         * to add a history table would be a poor trade.
         *
         * Already-completed reminders are backfilled into it. Their dueAt is
         * still the date they were measured against -- only *repeating*
         * reminders have their dueAt rolled forward, and a repeating reminder
         * is never isCompleted until its series ends -- so the punctuality
         * these rows produce is real, not invented.
         *
         * The backfill also keeps two screens honest about each other: without
         * it "Delete all completed" would bin reminders the Completed list had
         * never shown, because that list is built from this table.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `completions` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`reminderId` INTEGER NOT NULL, " +
                        "`completedAt` INTEGER NOT NULL, " +
                        "`dueAt` INTEGER, " +
                        "`wasAllDay` INTEGER NOT NULL, " +
                        "`nextDueAt` INTEGER, " +
                        "FOREIGN KEY(`reminderId`) REFERENCES `reminders`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_completions_reminderId` ON `completions` (`reminderId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_completions_completedAt` ON `completions` (`completedAt`)")
                db.execSQL(
                    "INSERT INTO `completions` " +
                        "(`reminderId`, `completedAt`, `dueAt`, `wasAllDay`, `nextDueAt`) " +
                        "SELECT `id`, `completedAt`, `dueAt`, `isAllDay`, `dueAt` FROM `reminders` " +
                        "WHERE `isCompleted` = 1 AND `completedAt` IS NOT NULL"
                )
            }
        }

        /**
         * Turns the schema-only `places` table into a working one: reminders
         * gain the trigger, the optional time window, and an index on placeId.
         *
         * The `places` table itself already exists -- it has been in the
         * @Database entity list since Phase 1 precisely so this migration
         * wouldn't have to create it.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `reminders` ADD COLUMN `placeTrigger` TEXT")
                db.execSQL("ALTER TABLE `reminders` ADD COLUMN `placeWindowStartMinute` INTEGER")
                db.execSQL("ALTER TABLE `reminders` ADD COLUMN `placeWindowEndMinute` INTEGER")
                db.execSQL("ALTER TABLE `reminders` ADD COLUMN `placeWindowDays` TEXT")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_reminders_placeId` ON `reminders` (`placeId`)")
            }
        }

        /**
         * Adds the photo-attachment table. Only file names live here; the
         * images themselves are copied into the app's own files directory (see
         * AttachmentRepository), so there is nothing to backfill.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `attachments` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`reminderId` INTEGER NOT NULL, " +
                        "`fileName` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`reminderId`) REFERENCES `reminders`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_attachments_reminderId` " +
                        "ON `attachments` (`reminderId`)"
                )
            }
        }
    }
}

private class SeedCallback(private val context: Context) : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        seed()
    }

    // A destructive migration recreates the tables without calling onCreate, so
    // bumping the schema version used to leave the app empty on next launch
    // rather than reseeded.
    override fun onDestructiveMigration(db: SupportSQLiteDatabase) {
        super.onDestructiveMigration(db)
        seed()
    }

    // Both callbacks can fire for one launch, and each seeds on its own IO
    // coroutine — a "is the table empty?" check would be a check-then-act race
    // between them and could seed the samples twice. Claiming this flag first
    // is what actually makes it happen once.
    private val seedClaimed = AtomicBoolean(false)

    private fun seed() {
        if (!seedClaimed.compareAndSet(false, true)) return
        CoroutineScope(Dispatchers.IO).launch {
            val db = ListsDatabase.get(context)
            if (db.reminderDao().countActive() == 0) seedDatabase(db)
        }
    }
}

private suspend fun seedDatabase(db: ListsDatabase) {
    val now = Instant.now().toEpochMilli()
    val personalListId = db.reminderListDao().insert(
        ReminderListEntity(
            name = "Personal",
            colorArgb = SeedColors.rose,
            position = 0,
            isDefault = true,
            createdAt = now
        )
    )
    val workListId = db.reminderListDao().insert(
        ReminderListEntity(
            name = "Work",
            colorArgb = SeedColors.teal,
            position = 1,
            createdAt = now
        )
    )

    fun at(offset: Duration): Long = Instant.now().plus(offset).toEpochMilli()

    val groceriesId = db.reminderDao().insert(
        ReminderEntity(
            listId = personalListId,
            title = "Pick up groceries",
            note = "Milk, eggs, coffee",
            dueAt = at(Duration.ofHours(-3)),
            createdAt = now
        )
    )
    db.checklistItemDao().insert(ChecklistItemEntity(reminderId = groceriesId, text = "Milk", position = 0))
    db.checklistItemDao().insert(ChecklistItemEntity(reminderId = groceriesId, text = "Eggs", position = 1))
    db.checklistItemDao().insert(
        ChecklistItemEntity(reminderId = groceriesId, text = "Coffee", isCompleted = true, position = 2)
    )

    db.reminderDao().insert(
        ReminderEntity(
            listId = workListId,
            title = "Send the quarterly report",
            dueAt = at(Duration.ofHours(2)),
            isImportant = true,
            createdAt = now
        )
    )
    db.reminderDao().insert(
        ReminderEntity(
            listId = personalListId,
            title = "Call the dentist",
            note = "Ask about the Thursday slot",
            dueAt = at(Duration.ofHours(5)),
            createdAt = now
        )
    )
    db.reminderDao().insert(
        ReminderEntity(
            listId = workListId,
            title = "Review pull requests",
            dueAt = at(Duration.ofDays(1)),
            createdAt = now
        )
    )
    // A repeating sample so the Repeat editor and the reschedule-on-complete
    // behaviour are visible without having to create one first.
    val binsDueAt = at(Duration.ofHours(6))
    db.reminderDao().insert(
        ReminderEntity(
            listId = personalListId,
            title = "Bins out",
            dueAt = binsDueAt,
            seriesStartAt = binsDueAt,
            repeatRule = "FREQ=WEEKLY;BYDAY=TU",
            createdAt = now
        )
    )

    db.reminderDao().insert(
        ReminderEntity(
            listId = personalListId,
            title = "Water the plants",
            isCompleted = true,
            completedAt = at(Duration.ofHours(-1)),
            createdAt = now
        )
    )
}

private object SeedColors {
    const val rose = 0xFFA03E28.toInt()
    const val teal = 0xFF006A60.toInt()
}
