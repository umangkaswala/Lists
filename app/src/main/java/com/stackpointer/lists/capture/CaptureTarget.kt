package com.stackpointer.lists.capture

sealed class CaptureTarget {
    data class New(val prefillText: String = "") : CaptureTarget()
    data class Edit(val reminderId: Long) : CaptureTarget()
}
