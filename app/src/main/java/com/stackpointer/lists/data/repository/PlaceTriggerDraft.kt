package com.stackpointer.lists.data.repository

/**
 * A reminder's place trigger as the Capture sheet has it, before it becomes
 * five columns on [com.stackpointer.lists.data.entity.ReminderEntity].
 *
 * Grouped rather than passed as five loose parameters because they are only
 * ever meaningful together: a window with no place is nothing, and a place with
 * no trigger direction can't be turned into a geofence.
 */
data class PlaceTriggerDraft(
    val placeId: Long,
    /** "ARRIVE" or "LEAVE". */
    val trigger: String,
    val windowStartMinute: Int? = null,
    val windowEndMinute: Int? = null,
    /** Comma-separated MO,TU,... — null means every day. */
    val windowDays: String? = null
)
