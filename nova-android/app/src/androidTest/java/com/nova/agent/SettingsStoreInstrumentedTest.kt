package com.nova.agent

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nova.agent.data.AppSettings
import com.nova.agent.data.SettingsStore
import com.nova.agent.data.THEME_ALIGNMENT_VERSION
import com.nova.agent.data.THEME_ALIGNMENT_VERSION_KEY
import com.nova.agent.data.dataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsStoreInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val themeKey = stringPreferencesKey("theme_id")
    private val alignmentVersionKey = intPreferencesKey(THEME_ALIGNMENT_VERSION_KEY)
    private val store = SettingsStore(context)

    @After
    fun clearThemePreference() {
        runBlocking {
            context.dataStore.edit { preferences ->
                preferences.remove(themeKey)
                preferences.remove(alignmentVersionKey)
            }
        }
    }

    @Test
    fun legacyThemeIdsAreMigratedWhenLoadedAndSaved() = runBlocking {
        context.dataStore.edit { preferences ->
            preferences[themeKey] = "kizil"
            preferences[alignmentVersionKey] = THEME_ALIGNMENT_VERSION
        }
        assertEquals("ruby", store.load().themeId)

        store.save(AppSettings(themeId = "aperture"))
        assertEquals("amethyst", store.load().themeId)
    }

    @Test
    fun unknownThemeFallsBackToSharedDefault() = runBlocking {
        store.save(AppSettings(themeId = "not-a-theme"))
        assertEquals("amethyst", store.load().themeId)
    }

    @Test
    fun firstLoadAlignsExistingAmberThemeWithLauncher() = runBlocking {
        context.dataStore.edit { preferences ->
            preferences[themeKey] = "amber"
            preferences.remove(alignmentVersionKey)
        }

        assertEquals("amethyst", store.load().themeId)
        val storedVersion = context.dataStore.data.first()[alignmentVersionKey]
        assertEquals(THEME_ALIGNMENT_VERSION, storedVersion)
    }

    @Test
    fun alignmentRunsOnceAndPreservesLaterAmberSelection() = runBlocking {
        context.dataStore.edit { preferences ->
            preferences[themeKey] = "amber"
            preferences.remove(alignmentVersionKey)
        }

        assertEquals("amethyst", store.load().themeId)
        store.save(AppSettings(themeId = "amber"))
        assertEquals("amber", store.load().themeId)
    }
}
