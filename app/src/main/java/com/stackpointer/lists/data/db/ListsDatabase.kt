package com.stackpointer.lists.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.stackpointer.lists.data.dao.ChecklistItemDao
import com.stackpointer.lists.data.dao.ReminderDao
import com.stackpointer.lists.data.dao.ReminderListDao
import com.stackpointer.lists.data.entity.ChecklistItemEntity
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
        ChecklistItemEntity::class,
        PlaceEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class ListsDatabase : RoomDatabase() {
    abstract fun reminderDao(): ReminderDao
    abstract fun reminderListDao(): ReminderListDao
    abstract fun checklistItemDao(): ChecklistItemDao

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
                // Pre-launch app under active development: a schema bump just
                // wipes local sample data rather than needing a real migration.
                .fallbackToDestructiveMigration(dropAllTables = true)
                .addCallback(SeedCallback(context))
                .build()
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
