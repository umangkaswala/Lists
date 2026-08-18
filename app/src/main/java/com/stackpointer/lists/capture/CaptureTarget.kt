package com.stackpointer.lists.capture

/**
 * What the Capture sheet was opened to do.
 *
 * [initialMode] lets a caller land straight on one of the sub-editors instead
 * of the typing view. Design S11 says of Detail's property rows: "Tapping a row
 * opens that editor directly" — without this, the best a row could do was open
 * the sheet and leave the user to find the right icon themselves.
 */
sealed class CaptureTarget {
    /** Which sub-editor to show first, or null for the normal typing view. */
    abstract val initialMode: CaptureMode?

    data class New(
        val prefillText: String = "",
        /**
         * A due date the sheet starts with — design S04's "Add to Today" pill,
         * which pre-fills today's date chip. It is a *default*, not a choice:
         * typing "tomorrow" still moves it (see `CaptureViewModel.updateTitle`).
         */
        val prefillDueAt: Long? = null,
        override val initialMode: CaptureMode? = null
    ) : CaptureTarget()

    data class Edit(
        val reminderId: Long,
        override val initialMode: CaptureMode? = null
    ) : CaptureTarget()
}
