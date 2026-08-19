package com.stackpointer.lists.data

import com.stackpointer.lists.data.entity.ReminderListEntity
import com.stackpointer.lists.data.entity.fallbackListFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Deleting a list now moves its reminders somewhere rather than destroying
 * them, and this picks the somewhere. Worth pinning down: the confirmation
 * dialog names the list it returns, so a disagreement between the two would
 * tell the user their reminders went to the wrong place.
 */
class FallbackListTest {

    private fun list(id: Long, name: String, position: Int, isDefault: Boolean = false) =
        ReminderListEntity(
            id = id,
            name = name,
            colorArgb = 0,
            position = position,
            isDefault = isDefault,
            createdAt = 0
        )

    @Test
    fun `prefers the default list even when it is not first`() {
        val lists = listOf(
            list(1, "Work", position = 0),
            list(2, "Personal", position = 1, isDefault = true),
            list(3, "Shopping", position = 2)
        )
        assertEquals(2L, fallbackListFor(lists, lists[0])?.id)
    }

    @Test
    fun `falls back to the topmost list when none is marked default`() {
        val lists = listOf(
            list(1, "Shopping", position = 2),
            list(2, "Work", position = 0),
            list(3, "Errands", position = 1)
        )
        assertEquals(2L, fallbackListFor(lists, lists[0])?.id)
    }

    @Test
    fun `never returns the list being deleted`() {
        val lists = listOf(
            list(1, "Personal", position = 0, isDefault = true),
            list(2, "Work", position = 1)
        )
        // The UI stops anyone deleting the default list, but if that ever
        // changed, reassigning its reminders to itself would delete them.
        assertEquals(2L, fallbackListFor(lists, lists[0])?.id)
    }

    @Test
    fun `returns null when the last list is being deleted`() {
        val only = list(1, "Personal", position = 0, isDefault = true)
        assertNull(fallbackListFor(listOf(only), only))
    }
}
