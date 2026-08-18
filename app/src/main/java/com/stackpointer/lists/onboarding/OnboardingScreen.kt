package com.stackpointer.lists.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
// The design uses the "Material Symbols Rounded" family throughout, so filled
// glyphs come from Icons.Rounded (as on Home/Today). Compose has no rounded
// *outlined* family, so the unfilled glyphs fall back to Icons.Outlined.
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Screen S01 of the Claude Design spec ("Lists - M3 Expressive"). Pure
// value-in / value-out: no permission logic here, all of that lives with
// whoever hosts this screen (see the parameter doc on OnboardingScreen).

/**
 * Onboarding permission-priming screen. Displays the current grant state of
 * three permissions and reports taps via callbacks; holds no Android
 * permission APIs or ViewModel of its own.
 */
@Composable
fun OnboardingScreen(
    notificationsGranted: Boolean,
    exactAlarmsGranted: Boolean,
    locationGranted: Boolean,
    onRequestNotifications: () -> Unit,
    onRequestExactAlarms: () -> Unit,
    onRequestLocation: () -> Unit,
    onContinue: () -> Unit,
    onSkip: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(horizontal = 24.dp)
        ) {
            Spacer(Modifier.height(24.dp))

            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.NotificationsActive,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(44.dp)
                )
            }

            Spacer(Modifier.height(32.dp))

            Text(
                text = "Three permissions and Lists can do its job",
                fontSize = 36.sp,
                lineHeight = 44.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.sp,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text = "You can change any of these later in Settings. Location is only " +
                    "needed if you use place reminders.",
                fontSize = 16.sp,
                lineHeight = 24.sp,
                letterSpacing = 0.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(32.dp))

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PermissionRow(
                    icon = Icons.Outlined.Notifications,
                    title = "Notifications",
                    subtitle = "So a reminder can reach you at the time you set.",
                    granted = notificationsGranted,
                    onClick = onRequestNotifications
                )
                PermissionRow(
                    icon = Icons.Outlined.Alarm,
                    title = "Alarms & reminders",
                    subtitle = "Lets an alert fire at the exact minute, even in Doze.",
                    granted = exactAlarmsGranted,
                    onClick = onRequestExactAlarms
                )
                PermissionRow(
                    icon = Icons.Outlined.LocationOn,
                    title = "Location",
                    subtitle = "Optional. Needed for “when I arrive” and “when I leave”.",
                    granted = locationGranted,
                    onClick = onRequestLocation
                )
            }

            Spacer(Modifier.height(24.dp))
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            // navigationBarsPadding, not systemBarsPadding: the latter also adds
            // status-bar padding, which here would open a ~24dp gap above the
            // Continue button for no reason (this Column is at the bottom).
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(24.dp)
        ) {
            Button(
                onClick = onContinue,
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text(
                    text = "Continue",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.15.sp
                )
            }

            TextButton(
                onClick = onSkip,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text(
                    text = "Skip for now",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.1.sp
                )
            }
        }
    }
}

@Composable
private fun PermissionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    granted: Boolean,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .then(
                if (granted) Modifier else Modifier.clickable(onClick = onClick)
            )
            .padding(20.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.15.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                letterSpacing = 0.25.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        if (granted) {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = "Granted",
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(22.dp)
            )
        } else {
            Icon(
                imageVector = Icons.Outlined.RadioButtonUnchecked,
                contentDescription = "Not granted",
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
