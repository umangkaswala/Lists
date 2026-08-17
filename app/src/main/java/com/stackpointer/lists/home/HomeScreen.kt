package com.stackpointer.lists.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.stackpointer.lists.ui.theme.ListsCorner

// Placeholder screen for Phase 0 — proves the theme (colors/type/shapes) renders
// correctly. Replaced with the real Home screen (search bar, tiles, reminder
// cards, capture pill) in Phase 1.
@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(text = "Lists", style = MaterialTheme.typography.displaySmall)
            Text(
                text = "Material 3 Expressive theme is wired up. Home, Today, Capture and " +
                    "everything else land in the next phases.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                shape = MaterialTheme.shapes.extraLarge
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(text = "Today", style = MaterialTheme.typography.titleLarge)
                    Text(text = "Primary container", style = MaterialTheme.typography.bodyMedium)
                }
            }

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                ),
                shape = MaterialTheme.shapes.large
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(text = "Places", style = MaterialTheme.typography.titleLarge)
                    Text(text = "Tertiary container", style = MaterialTheme.typography.bodyMedium)
                }
            }

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                shape = RoundedCornerShape(ListsCorner.listGroupOuter)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(text = "Reminder card", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = "surfaceContainerLow, grouped corners",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
