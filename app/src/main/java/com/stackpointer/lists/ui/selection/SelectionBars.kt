package com.stackpointer.lists.ui.selection

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stackpointer.lists.ui.theme.ListsCorner

/**
 * The multi-select chrome from design S14, shared rather than copied: the same
 * mode is reachable from the recycle bin and from the Completed list, and the
 * design's note that it is "the same selection mode" is the whole point of it —
 * two lookalike implementations would drift apart the first time either changed.
 */
@Composable
fun SelectionTopBar(
    selectedCount: Int,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    trailing: @Composable () -> Unit = {}
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 4.dp)
    ) {
        IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Rounded.Close, contentDescription = "Leave selection mode")
        }
        Text(
            text = "$selectedCount selected",
            fontSize = 22.sp,
            lineHeight = 28.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f).padding(start = 8.dp)
        )
        IconButton(onClick = onSelectAll, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Rounded.SelectAll, contentDescription = "Select all")
        }
        trailing()
    }
}

/**
 * The 80dp bottom bar: a filled primary action and an outlined destructive one.
 * Both stay visible but go inert with nothing selected, so the bar doesn't jump
 * the list content up and down as the selection empties.
 */
@Composable
fun SelectionActionBar(
    primaryLabel: String,
    primaryIcon: ImageVector,
    onPrimary: () -> Unit,
    destructiveLabel: String,
    destructiveIcon: ImageVector,
    onDestructive: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(ListsCorner.extraLarge),
        tonalElevation = 2.dp,
        shadowElevation = 2.dp,
        modifier = modifier.fillMaxWidth().height(80.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 12.dp)
        ) {
            Surface(
                onClick = onPrimary,
                enabled = enabled,
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(ListsCorner.large),
                modifier = Modifier.weight(1f).height(56.dp)
            ) {
                ActionContent(primaryLabel, primaryIcon, enabled)
            }
            Surface(
                onClick = onDestructive,
                enabled = enabled,
                color = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.error,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                shape = RoundedCornerShape(ListsCorner.large),
                modifier = Modifier.weight(1f).height(56.dp)
            ) {
                ActionContent(destructiveLabel, destructiveIcon, enabled)
            }
        }
    }
}

@Composable
private fun ActionContent(label: String, icon: ImageVector, enabled: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        modifier = Modifier.fillMaxSize()
    ) {
        val alpha = if (enabled) 1f else 0.38f
        Icon(
            icon,
            contentDescription = null,
            tint = LocalContentColor.current.copy(alpha = alpha),
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = LocalContentColor.current.copy(alpha = alpha)
        )
    }
}
