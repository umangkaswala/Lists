package com.stackpointer.lists.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// Corner scale from the design spec: xs 4 · s 8 · m 12 · l 16 · lInc 20 · xl 28 · full.
// MaterialTheme.shapes only exposes 5 slots, so the full scale is also kept here as
// named constants for components that need the "large increased" (20dp, e.g. dialog
// buttons, list-group outer corner) or "full" (pill/circle) steps directly.
object ListsCorner {
    val extraSmall = 4.dp
    val small = 8.dp
    val medium = 12.dp
    val large = 16.dp
    val largeIncreased = 20.dp
    val extraLarge = 28.dp

    // Grouped reminder-card corners: 20 outer / 4 inner, per the design spec.
    val listGroupOuter = 20.dp
    val listGroupInner = 4.dp
}

val ListsShapes = Shapes(
    extraSmall = RoundedCornerShape(ListsCorner.extraSmall),
    small = RoundedCornerShape(ListsCorner.small),
    medium = RoundedCornerShape(ListsCorner.medium),
    large = RoundedCornerShape(ListsCorner.large),
    extraLarge = RoundedCornerShape(ListsCorner.extraLarge)
)
