package com.stackpointer.lists.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.searchHistoryDataStore by preferencesDataStore(name = "search_history")

/**
 * The Search screen's "Recent searches" chips.
 *
 * Stored in DataStore rather than Room: it's a short user preference, not app
 * data, and it shouldn't ride along in any future export of the reminders
 * themselves. Capped at [MAX_ENTRIES] — the design shows a single row of chips.
 */
class SearchHistoryStore(private val context: Context) {

    private val key = stringPreferencesKey("recent_queries")

    val recentQueries: Flow<List<String>> = context.searchHistoryDataStore.data.map { prefs ->
        prefs[key].orEmpty()
            .split(SEPARATOR)
            .filter { it.isNotBlank() }
    }

    suspend fun record(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        context.searchHistoryDataStore.edit { prefs ->
            val existing = prefs[key].orEmpty().split(SEPARATOR).filter { it.isNotBlank() }
            // Re-searching an old term moves it to the front rather than
            // duplicating it, case-insensitively so "Milk" doesn't sit next
            // to "milk".
            val updated = (listOf(trimmed) + existing.filterNot { it.equals(trimmed, ignoreCase = true) })
                .take(MAX_ENTRIES)
            prefs[key] = updated.joinToString(SEPARATOR)
        }
    }

    /** Design S12: recent-search chips are removed by long-pressing them. */
    suspend fun remove(query: String) {
        context.searchHistoryDataStore.edit { prefs ->
            val remaining = prefs[key].orEmpty()
                .split(SEPARATOR)
                .filter { it.isNotBlank() && !it.equals(query, ignoreCase = true) }
            if (remaining.isEmpty()) prefs.remove(key) else prefs[key] = remaining.joinToString(SEPARATOR)
        }
    }

    suspend fun clear() {
        context.searchHistoryDataStore.edit { it.remove(key) }
    }

    private companion object {
        // Queries are typed into a single-line field, so a newline can never
        // appear inside one and can safely delimit entries.
        const val SEPARATOR = "\n"
        const val MAX_ENTRIES = 8
    }
}
