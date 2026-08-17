package com.stackpointer.lists.home

data class ReminderCardUiModel(
    val id: Long,
    val title: String,
    val metaText: String?,
    val isOverdue: Boolean,
    val isCompleted: Boolean,
    val isImportant: Boolean
)

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
    val isEmpty: Boolean = false
)
