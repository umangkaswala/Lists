package com.stackpointer.lists.home

data class ReminderCardUiModel(
    val id: Long,
    val title: String,
    val metaText: String?,
    val isOverdue: Boolean,
    val isCompleted: Boolean,
    val isImportant: Boolean,
    val repeats: Boolean = false,
    val checklistTotal: Int = 0,
    val checklistDone: Int = 0
) {
    val hasChecklist: Boolean get() = checklistTotal > 0
    val checklistProgress: Float
        get() = if (checklistTotal == 0) 0f else checklistDone.toFloat() / checklistTotal
}

data class ReminderSection(
    val label: String,
    val isError: Boolean,
    val reminders: List<ReminderCardUiModel>
)

data class ListTileUiModel(
    val id: Long,
    val name: String,
    val colorArgb: Int,
    val activeCount: Int
)

data class HomeUiState(
    val isLoading: Boolean = true,
    val overdueCount: Int = 0,
    val todayCount: Int = 0,
    val listTiles: List<ListTileUiModel> = emptyList(),
    val selectedListId: Long? = null,
    val sections: List<ReminderSection> = emptyList(),
    /**
     * Whether the reminders table itself is empty — *not* whether [sections] is.
     * A list filter with nothing open in it empties the sections while the
     * database is full, and treating that as "empty" would hide the very filter
     * chips needed to get back out of it.
     */
    val hasNoReminders: Boolean = false
) {
    /**
     * The first-run state of design S03: "No tiles and no filter chips while the
     * database is empty."
     *
     * Gated on [isLoading] so it can only be true once the database has actually
     * answered, matching TodayUiState / CompletedUiState / RecycleBinUiState,
     * which all define it this way. Home was the odd one out: it answered "not
     * empty" while still loading, so a cold start painted "Today 0 · 0 overdue ·
     * 0 to go" over empty list tiles for as long as the first query took — about
     * a second on the emulator, i.e. a screenful of zeros presented as fact.
     */
    val isEmpty: Boolean get() = !isLoading && hasNoReminders
}
