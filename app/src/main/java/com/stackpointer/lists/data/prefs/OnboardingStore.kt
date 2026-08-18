package com.stackpointer.lists.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.onboardingDataStore by preferencesDataStore(name = "onboarding")

/**
 * Remembers whether the permission-priming screen (design S01) has been seen.
 *
 * Deliberately *not* "have the permissions been granted" — that's read live
 * from the OS every time, because the user can revoke a permission in system
 * Settings at any moment. This flag only answers "should we open on the
 * onboarding screen", so tapping Skip is respected and the screen doesn't
 * reappear on every launch.
 */
class OnboardingStore(private val context: Context) {

    private val key = booleanPreferencesKey("completed")

    val isCompleted: Flow<Boolean> = context.onboardingDataStore.data.map { prefs ->
        prefs[key] ?: false
    }

    suspend fun markCompleted() {
        context.onboardingDataStore.edit { it[key] = true }
    }
}
