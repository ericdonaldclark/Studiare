package net.ericclark.studiare

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import net.ericclark.studiare.data.ActiveSession
import net.ericclark.studiare.data.CrosswordWord
import kotlinx.coroutines.flow.distinctUntilChanged
import net.ericclark.studiare.data.*

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

object ThemeMode {
    const val LIGHT = 0
    const val DARK = 1
    const val BLACK_AND_WHITE = 2
    const val CUSTOM = 3
}

object SpacingMode {
    const val COMPACT = 0
    const val NORMAL = 1
    const val COMFORTABLE = 2
}

object AnimationMode {
    const val SUBTLE = 0
    const val NORMAL = 1
    const val EXAGGERATED = 2
}

/** One stored preference as the saved-preferences table shows it. [value] is null when nothing is stored. */
data class PreferenceEntry(val name: String, val type: String, val value: String?, val defaultValue: String?, val editable: Boolean)

class PreferenceManager(context: Context) {
    private val dataStore = context.dataStore

    companion object {
        val IS_DARK_MODE = booleanPreferencesKey("is_dark_mode")
        val THEME_MODE = intPreferencesKey("theme_mode")
        val ACTIVE_SESSIONS = stringPreferencesKey("active_sessions_list")
        val LAST_EXPORT_TIMESTAMP = longPreferencesKey("last_export_timestamp")
        val LAST_IMPORT_TIMESTAMP = longPreferencesKey("last_import_timestamp")
        val HAS_PROMPTED_HD_LANGUAGES = booleanPreferencesKey("has_prompted_hd_languages")
        val HD_PROMPT_DISMISSED_LANGUAGES = stringSetPreferencesKey("hd_prompt_dismissed_languages")
        val NOTIFICATION_PROMPT_SHOWN = booleanPreferencesKey("notification_prompt_shown")
        // NEW: Key to track downloaded languages
        val DOWNLOADED_HD_LANGUAGES = stringSetPreferencesKey("downloaded_hd_languages")
        // Whisper speech-recognition model: which size ("tiny"/"base"/"small") the user has
        // downloaded, if any, and whether they've already been asked to pick one.
        val WHISPER_MODEL_SIZE = stringPreferencesKey("whisper_model_size")
        val HAS_PROMPTED_WHISPER_MODEL = booleanPreferencesKey("has_prompted_whisper_model")
        val MEMORY_GRID_COLUMNS_PORTRAIT = intPreferencesKey("memory_grid_columns_portrait")
        val MEMORY_GRID_COLUMNS_LANDSCAPE = intPreferencesKey("memory_grid_columns_landscape")
        val SPACING_MODE = intPreferencesKey("spacing_mode")
        val ANIMATION_MODE = intPreferencesKey("animation_mode")
        // Superseded by DECK_SETS_DISPLAY_MODE below; kept only so that setting's flow can fold
        // whatever a returning user had already chosen into the new 3-state value the first time
        // it's read (see deckSetsDisplayModeFlow).
        val DISPLAY_SETS_UNDER_DECKS = booleanPreferencesKey("display_sets_under_decks")
        val GRID_LARGE_SCREEN_LAYOUT = booleanPreferencesKey("grid_large_screen_layout")
        val DECK_SETS_DISPLAY_MODE = intPreferencesKey("deck_sets_display_mode")
        val TREE_LARGE_SCREEN_LAYOUT = booleanPreferencesKey("tree_large_screen_layout")
        val GRID_LOADING_INDICATOR = booleanPreferencesKey("grid_loading_indicator")
        val TREE_LOADING_INDICATOR = booleanPreferencesKey("tree_loading_indicator")
        val REDUCE_MOTION = booleanPreferencesKey("reduce_motion")
        val DISABLE_CARD_FLIP_ANIMATIONS = booleanPreferencesKey("disable_card_flip_animations")
        val ALWAYS_OPEN_BULK_EDITOR = booleanPreferencesKey("always_open_bulk_editor")
        val LAST_STUDY_CATEGORY = stringPreferencesKey("last_study_category")
        val SORT_TAGS_BY_DATE_CREATED = booleanPreferencesKey("sort_tags_by_date_created")
        val SHORTCUTS_CURRENT_SCREEN_ONLY = booleanPreferencesKey("shortcuts_current_screen_only")
        val SHOW_SHORTCUTS_BUTTON = booleanPreferencesKey("show_shortcuts_button")
        val SHOW_SIZE_OVERLAY = booleanPreferencesKey("show_size_overlay")
        val IS_DEBUG = booleanPreferencesKey("is_debug")
        val SHORTCUT_REMAPS = stringPreferencesKey("shortcut_remaps")
        val CUSTOM_PRIMARY = stringPreferencesKey("custom_primary")
        val CUSTOM_SECONDARY = stringPreferencesKey("custom_secondary")
        val CUSTOM_TERTIARY = stringPreferencesKey("custom_tertiary")
        val CUSTOM_BACKGROUND = stringPreferencesKey("custom_background")

        // Sync Preferences
        val SYNC_DECKS_AND_CARDS = booleanPreferencesKey("sync_decks_and_cards")
        val SYNC_REVIEW_DATA = booleanPreferencesKey("sync_review_data")
        val SYNC_SAVED_SESSIONS = booleanPreferencesKey("sync_saved_sessions")
        val SYNC_ONLY_ON_WIFI = booleanPreferencesKey("sync_only_on_wifi")
        val DECK_SORT_MODE = intPreferencesKey("deck_sort_mode")
        val DECK_SET_COUNTS_SNAPSHOT = stringPreferencesKey("deck_set_counts_snapshot")
        val MODE_DEFAULT_SETTINGS = stringPreferencesKey("mode_default_settings")
        val SELECTED_COLLECTION_ID = stringPreferencesKey("selected_collection_id")
        val DECK_VIEW_MODE = intPreferencesKey("deck_view_mode")

        // Study Hub active-session sort/grouping (global, same for every deck)
        val GROUP_BY_CATEGORY = booleanPreferencesKey("group_by_category")
        val GROUP_BY_MODE = booleanPreferencesKey("group_by_mode")
        val CATEGORY_SORT_MODE = intPreferencesKey("category_sort_mode")
        val CATEGORY_SORT_DIRECTION = stringPreferencesKey("category_sort_direction")
        val MODE_SORT_MODE = intPreferencesKey("mode_sort_mode")
        val MODE_SORT_DIRECTION = stringPreferencesKey("mode_sort_direction")
        val SESSION_TILE_SORT_MODE = intPreferencesKey("session_tile_sort_mode")
        val SESSION_TILE_SORT_DIRECTION = stringPreferencesKey("session_tile_sort_direction")
    }

    val themeModeFlow: Flow<Int> = dataStore.data.map { preferences ->
        val mode = preferences[THEME_MODE]
        if (mode != null) {
            mode
        } else {
            val isDark = preferences[IS_DARK_MODE] ?: true
            if (isDark) ThemeMode.DARK else ThemeMode.LIGHT
        }
    }.distinctUntilChanged()

    val spacingModeFlow: Flow<Int> = dataStore.data.map { preferences ->
        preferences[SPACING_MODE] ?: SpacingMode.COMFORTABLE
    }

    val animationModeFlow: Flow<Int> = dataStore.data.map { preferences ->
        preferences[ANIMATION_MODE] ?: AnimationMode.NORMAL
    }.distinctUntilChanged()

    // Null means "never explicitly chosen" (neither this key nor the pre-merge booleans it
    // replaced), which the UI resolves against the current window size instead of a fixed
    // default — see DeckSetsDisplayMode.resolve(). A returning user who had explicitly set
    // either pre-merge boolean still gets those folded into a concrete value here, same as
    // before; only a genuinely first-run user sees the size-aware default.
    val deckSetsDisplayModeFlow: Flow<Int?> = dataStore.data.map { preferences ->
        val explicit = preferences[DECK_SETS_DISPLAY_MODE]
        if (explicit != null) {
            explicit
        } else if (preferences.contains(DISPLAY_SETS_UNDER_DECKS) || preferences.contains(GRID_LARGE_SCREEN_LAYOUT)) {
            val displaySets = preferences[DISPLAY_SETS_UNDER_DECKS] ?: true
            val gridLarge = preferences[GRID_LARGE_SCREEN_LAYOUT] ?: true
            when {
                !displaySets -> DeckSetsDisplayMode.OFF.value
                gridLarge -> DeckSetsDisplayMode.BESIDE_DECKS.value
                else -> DeckSetsDisplayMode.UNDER_DECKS.value
            }
        } else {
            null
        }
    }.distinctUntilChanged()

    // Null means "never explicitly chosen" — resolved against the current window size instead
    // of a fixed default; see DeckSetsDisplayMode.resolveTreeDirection().
    val treeLargeScreenLayoutFlow: Flow<Boolean?> = dataStore.data.map { it[TREE_LARGE_SCREEN_LAYOUT] }.distinctUntilChanged()
    val gridLoadingIndicatorFlow: Flow<Boolean> = dataStore.data.map { it[GRID_LOADING_INDICATOR] ?: true }.distinctUntilChanged()
    val treeLoadingIndicatorFlow: Flow<Boolean> = dataStore.data.map { it[TREE_LOADING_INDICATOR] ?: true }.distinctUntilChanged()
    val reduceMotionFlow: Flow<Boolean> = dataStore.data.map { it[REDUCE_MOTION] ?: false }.distinctUntilChanged()
    val disableCardFlipAnimationsFlow: Flow<Boolean> = dataStore.data.map { it[DISABLE_CARD_FLIP_ANIMATIONS] ?: false }.distinctUntilChanged()
    val alwaysOpenBulkEditorFlow: Flow<Boolean> = dataStore.data.map { it[ALWAYS_OPEN_BULK_EDITOR] ?: false }.distinctUntilChanged()
    val lastStudyCategoryFlow: Flow<String?> = dataStore.data.map { it[LAST_STUDY_CATEGORY] }.distinctUntilChanged()
    val sortTagsByDateCreatedFlow: Flow<Boolean> = dataStore.data.map { it[SORT_TAGS_BY_DATE_CREATED] ?: false }.distinctUntilChanged()
    val shortcutsCurrentScreenOnlyFlow: Flow<Boolean> = dataStore.data.map { it[SHORTCUTS_CURRENT_SCREEN_ONLY] ?: true }.distinctUntilChanged()
    val showShortcutsButtonFlow: Flow<Boolean> = dataStore.data.map { it[SHOW_SHORTCUTS_BUTTON] ?: true }.distinctUntilChanged()
    val showSizeOverlayFlow: Flow<Boolean> = dataStore.data.map { it[SHOW_SIZE_OVERLAY] ?: false }.distinctUntilChanged()
    val isDebugFlow: Flow<Boolean> = dataStore.data.map { it[IS_DEBUG] ?: false }.distinctUntilChanged()
    val shortcutRemapsFlow: Flow<Map<String, Long>> = dataStore.data.map { prefs ->
        val json = prefs[SHORTCUT_REMAPS] ?: return@map emptyMap()
        runCatching {
            val obj = JSONObject(json)
            obj.keys().asSequence().associateWith { key -> obj.getLong(key) }
        }.getOrDefault(emptyMap())
    }.distinctUntilChanged()

    val downloadedHdLanguagesFlow: Flow<Set<String>> = dataStore.data.map { preferences ->
        preferences[DOWNLOADED_HD_LANGUAGES] ?: emptySet()
    }.distinctUntilChanged()

    // null = no Whisper model downloaded/chosen yet.
    val whisperModelSizeFlow: Flow<String?> = dataStore.data.map { preferences ->
        preferences[WHISPER_MODEL_SIZE]
    }.distinctUntilChanged()

    val hasPromptedWhisperModelFlow: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[HAS_PROMPTED_WHISPER_MODEL] ?: false
    }.distinctUntilChanged()

    suspend fun setWhisperModelSize(size: String?) {
        dataStore.edit { settings ->
            if (size == null) settings.remove(WHISPER_MODEL_SIZE) else settings[WHISPER_MODEL_SIZE] = size
        }
    }

    suspend fun setHasPromptedWhisperModel(prompted: Boolean) {
        dataStore.edit { settings -> settings[HAS_PROMPTED_WHISPER_MODEL] = prompted }
    }

    // Flow for Portrait Columns (Default 3)
    val memoryGridColumnsPortraitFlow: Flow<Int> = dataStore.data.map { preferences ->
        preferences[MEMORY_GRID_COLUMNS_PORTRAIT] ?: 3
    }.distinctUntilChanged()

    // Flow for Landscape Columns (Default 5)
    val memoryGridColumnsLandscapeFlow: Flow<Int> = dataStore.data.map { preferences ->
        preferences[MEMORY_GRID_COLUMNS_LANDSCAPE] ?: 5
    }.distinctUntilChanged()

    val deckSortModeFlow: Flow<Int> = dataStore.data.map { preferences ->
        preferences[DECK_SORT_MODE] ?: DeckSortMode.A_TO_Z.value
    }.distinctUntilChanged()
    val deckViewModeFlow: Flow<Int> = dataStore.data.map { preferences ->
        preferences[DECK_VIEW_MODE] ?: 0 // 0 for GRID, 1 for TREE
    }.distinctUntilChanged()

    // Study Hub active-session sort/grouping (global, same for every deck)
    val groupByCategoryFlow: Flow<Boolean> = dataStore.data.map { it[GROUP_BY_CATEGORY] ?: true }.distinctUntilChanged()
    val groupByModeFlow: Flow<Boolean> = dataStore.data.map { it[GROUP_BY_MODE] ?: true }.distinctUntilChanged()
    // Default (nothing stored yet) is "most recent first" at both levels — the category holding the
    // most recently accessed session floats to the top, then within it the mode holding the most
    // recently accessed session, and so on — not the old fixed Learn/Practice/Quiz/Games order.
    val categorySortModeFlow: Flow<Int> = dataStore.data.map { it[CATEGORY_SORT_MODE] ?: GroupSortMode.MOST_RECENT.value }.distinctUntilChanged()
    val categorySortDirectionFlow: Flow<String> = dataStore.data.map { it[CATEGORY_SORT_DIRECTION] ?: Direction.DESC.name }.distinctUntilChanged()
    val modeSortModeFlow: Flow<Int> = dataStore.data.map { it[MODE_SORT_MODE] ?: GroupSortMode.MOST_RECENT.value }.distinctUntilChanged()
    val modeSortDirectionFlow: Flow<String> = dataStore.data.map { it[MODE_SORT_DIRECTION] ?: Direction.DESC.name }.distinctUntilChanged()
    val sessionTileSortModeFlow: Flow<Int> = dataStore.data.map { it[SESSION_TILE_SORT_MODE] ?: SessionTileSortMode.LAST_ACCESSED.value }.distinctUntilChanged()
    val sessionTileSortDirectionFlow: Flow<String> = dataStore.data.map { it[SESSION_TILE_SORT_DIRECTION] ?: Direction.DESC.name }.distinctUntilChanged()

    // Flows for Custom Colors (Defaulting to standard M3 Purple/Teal if not set)
    val customPrimaryFlow: Flow<String> = dataStore.data.map { it[CUSTOM_PRIMARY] ?: "#6750A4" }.distinctUntilChanged()
    val customSecondaryFlow: Flow<String> = dataStore.data.map { it[CUSTOM_SECONDARY] ?: "#625B71" }.distinctUntilChanged()
    val customTertiaryFlow: Flow<String> = dataStore.data.map { it[CUSTOM_TERTIARY] ?: "#7D5260" }.distinctUntilChanged()
    val customBackgroundFlow: Flow<String> = dataStore.data.map { it[CUSTOM_BACKGROUND] ?: "#FFFBFE" }.distinctUntilChanged()

    // Sync Preference Flows (Default to true)
    val syncDecksAndCardsFlow: Flow<Boolean> = dataStore.data.map { it[SYNC_DECKS_AND_CARDS] ?: true }.distinctUntilChanged()
    val syncReviewDataFlow: Flow<Boolean> = dataStore.data.map { it[SYNC_REVIEW_DATA] ?: true }.distinctUntilChanged()
    val syncSavedSessionsFlow: Flow<Boolean> = dataStore.data.map { it[SYNC_SAVED_SESSIONS] ?: true }.distinctUntilChanged()
    val syncOnlyOnWifiFlow: Flow<Boolean> = dataStore.data.map { it[SYNC_ONLY_ON_WIFI] ?: true }.distinctUntilChanged()
    suspend fun addDownloadedHdLanguages(languages: List<String>) {
        dataStore.edit { settings ->
            val current = settings[DOWNLOADED_HD_LANGUAGES] ?: emptySet()
            settings[DOWNLOADED_HD_LANGUAGES] = current + languages
        }
    }

    suspend fun removeDownloadedHdLanguage(language: String) {
        dataStore.edit { settings ->
            val current = settings[DOWNLOADED_HD_LANGUAGES] ?: emptySet()
            settings[DOWNLOADED_HD_LANGUAGES] = current - language
        }
    }

    suspend fun clearDownloadedHdLanguages() {
        dataStore.edit { settings ->
            settings.remove(DOWNLOADED_HD_LANGUAGES)
        }
    }

    suspend fun setMemoryGridColumns(portrait: Int, landscape: Int) {
        dataStore.edit { settings ->
            settings[MEMORY_GRID_COLUMNS_PORTRAIT] = portrait
            settings[MEMORY_GRID_COLUMNS_LANDSCAPE] = landscape
        }
    }

    suspend fun setDeckSortMode(mode: Int) {
        dataStore.edit { settings ->
            settings[DECK_SORT_MODE] = mode
        }
    }
    suspend fun setDeckViewMode(mode: Int) {
        dataStore.edit { settings ->
            settings[DECK_VIEW_MODE] = mode
        }
    }

    suspend fun setGroupByCategory(value: Boolean) {
        dataStore.edit { settings -> settings[GROUP_BY_CATEGORY] = value }
    }
    suspend fun setGroupByMode(value: Boolean) {
        dataStore.edit { settings -> settings[GROUP_BY_MODE] = value }
    }
    suspend fun setCategorySortMode(mode: Int) {
        dataStore.edit { settings -> settings[CATEGORY_SORT_MODE] = mode }
    }
    suspend fun setCategorySortDirection(direction: String) {
        dataStore.edit { settings -> settings[CATEGORY_SORT_DIRECTION] = direction }
    }
    suspend fun setModeSortMode(mode: Int) {
        dataStore.edit { settings -> settings[MODE_SORT_MODE] = mode }
    }
    suspend fun setModeSortDirection(direction: String) {
        dataStore.edit { settings -> settings[MODE_SORT_DIRECTION] = direction }
    }
    suspend fun setSessionTileSortMode(mode: Int) {
        dataStore.edit { settings -> settings[SESSION_TILE_SORT_MODE] = mode }
    }
    suspend fun setSessionTileSortDirection(direction: String) {
        dataStore.edit { settings -> settings[SESSION_TILE_SORT_DIRECTION] = direction }
    }

    val lastExportTimestampFlow: Flow<Long> = dataStore.data.map { preferences ->
        preferences[LAST_EXPORT_TIMESTAMP] ?: 0L
    }

    val lastImportTimestampFlow: Flow<Long> = dataStore.data.map { preferences ->
        preferences[LAST_IMPORT_TIMESTAMP] ?: 0L
    }

    val selectedCollectionIdFlow: Flow<String?> = dataStore.data.map { preferences ->
        preferences[SELECTED_COLLECTION_ID]
    }.distinctUntilChanged()

    suspend fun setSelectedCollectionId(id: String?) {
        dataStore.edit { settings ->
            if (id == null) {
                settings.remove(SELECTED_COLLECTION_ID)
            } else {
                settings[SELECTED_COLLECTION_ID] = id
            }
        }
    }

    val deckSetCountsSnapshotFlow: Flow<Map<String, List<Int>>> = dataStore.data.map { preferences ->
        val jsonString = preferences[DECK_SET_COUNTS_SNAPSHOT]
        if (jsonString.isNullOrEmpty()) {
            emptyMap()
        } else {
            try {
                val map = mutableMapOf<String, List<Int>>()
                val jsonObject = JSONObject(jsonString)
                jsonObject.keys().forEach { key ->
                    val array = jsonObject.getJSONArray(key)
                    map[key] = List(array.length()) { array.getInt(it) }
                }
                map
            } catch (e: Exception) {
                emptyMap()
            }
        }
    }.distinctUntilChanged()

    suspend fun setDeckSetCountsSnapshot(snapshotMap: Map<String, List<Int>>) {
        dataStore.edit { settings ->
            val jsonObject = JSONObject()
            snapshotMap.forEach { (key, counts) ->
                jsonObject.put(key, JSONArray(counts))
            }
            settings[DECK_SET_COUNTS_SNAPSHOT] = jsonObject.toString()
        }
    }

    // Per-(category, mode) defaults for CreateStudySessionDialog's mode-specific options — a JSON
    // object keyed by StudyCategory.name, each value a JSON object keyed by SessionMode.name, each
    // leaf a JSON object of only the non-null ModeDefaultSettings fields for that pair.
    val modeDefaultSettingsFlow: Flow<Map<Pair<StudyCategory, SessionMode>, ModeDefaultSettings>> = dataStore.data.map { preferences ->
        val jsonString = preferences[MODE_DEFAULT_SETTINGS]
        if (jsonString.isNullOrEmpty()) {
            emptyMap()
        } else {
            try {
                val result = mutableMapOf<Pair<StudyCategory, SessionMode>, ModeDefaultSettings>()
                val outer = JSONObject(jsonString)
                outer.keys().forEach { categoryKey ->
                    val category = runCatching { StudyCategory.valueOf(categoryKey) }.getOrNull() ?: return@forEach
                    val inner = outer.getJSONObject(categoryKey)
                    inner.keys().forEach { modeKey ->
                        val mode = runCatching { SessionMode.valueOf(modeKey) }.getOrNull() ?: return@forEach
                        val leaf = inner.getJSONObject(modeKey)
                                                result[category to mode] = ModeDefaultSettings.fromJson(leaf)
                    }
                }
                result
            } catch (e: Exception) {
                emptyMap()
            }
        }
    }.distinctUntilChanged()

    suspend fun setModeDefaultSettings(category: StudyCategory, mode: SessionMode, settings: ModeDefaultSettings) {
        dataStore.edit { preferences ->
            val outer = JSONObject(preferences[MODE_DEFAULT_SETTINGS] ?: "{}")
            val inner = if (outer.has(category.name)) outer.getJSONObject(category.name) else JSONObject().also { outer.put(category.name, it) }
            val leaf = settings.toJson()
            inner.put(mode.name, leaf)
            preferences[MODE_DEFAULT_SETTINGS] = outer.toString()
        }
    }

    suspend fun setThemeMode(mode: Int) {
        dataStore.edit { settings ->
            settings[THEME_MODE] = mode
            settings[IS_DARK_MODE] = (mode == ThemeMode.DARK || mode == ThemeMode.BLACK_AND_WHITE)
        }
    }

    suspend fun setSpacingMode(mode: Int) {
        dataStore.edit { settings ->
            settings[SPACING_MODE] = mode
        }
    }

    suspend fun setAnimationMode(mode: Int) {
        dataStore.edit { settings ->
            settings[ANIMATION_MODE] = mode
        }
    }

    suspend fun setDeckSetsDisplayMode(mode: Int) { dataStore.edit { it[DECK_SETS_DISPLAY_MODE] = mode } }
    suspend fun setTreeLargeScreenLayout(enabled: Boolean) { dataStore.edit { it[TREE_LARGE_SCREEN_LAYOUT] = enabled } }
    suspend fun setGridLoadingIndicator(enabled: Boolean) { dataStore.edit { it[GRID_LOADING_INDICATOR] = enabled } }
    suspend fun setTreeLoadingIndicator(enabled: Boolean) { dataStore.edit { it[TREE_LOADING_INDICATOR] = enabled } }
    suspend fun setReduceMotion(enabled: Boolean) { dataStore.edit { it[REDUCE_MOTION] = enabled } }
    suspend fun setDisableCardFlipAnimations(enabled: Boolean) { dataStore.edit { it[DISABLE_CARD_FLIP_ANIMATIONS] = enabled } }
    suspend fun setAlwaysOpenBulkEditor(enabled: Boolean) { dataStore.edit { it[ALWAYS_OPEN_BULK_EDITOR] = enabled } }
    suspend fun setLastStudyCategory(name: String) { dataStore.edit { it[LAST_STUDY_CATEGORY] = name } }
    suspend fun setSortTagsByDateCreated(enabled: Boolean) { dataStore.edit { it[SORT_TAGS_BY_DATE_CREATED] = enabled } }
    suspend fun setShortcutsCurrentScreenOnly(enabled: Boolean) { dataStore.edit { it[SHORTCUTS_CURRENT_SCREEN_ONLY] = enabled } }
    suspend fun setShowShortcutsButton(enabled: Boolean) { dataStore.edit { it[SHOW_SHORTCUTS_BUTTON] = enabled } }
    suspend fun setShowSizeOverlay(enabled: Boolean) { dataStore.edit { it[SHOW_SIZE_OVERLAY] = enabled } }
    suspend fun setIsDebug(enabled: Boolean) { dataStore.edit { it[IS_DEBUG] = enabled } }
    suspend fun setShortcutRemap(id: String, keyCode: Long?) {
        dataStore.edit { prefs ->
            val obj = JSONObject(prefs[SHORTCUT_REMAPS] ?: "{}")
            if (keyCode == null) obj.remove(id) else obj.put(id, keyCode)
            prefs[SHORTCUT_REMAPS] = obj.toString()
        }
    }

    val hasPromptedHdLanguagesFlow: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[HAS_PROMPTED_HD_LANGUAGES] ?: false
    }

    // Languages the voice prompt was dismissed for ("Dismiss for these languages")
    val hdPromptDismissedLanguagesFlow: Flow<Set<String>> = dataStore.data.map { it[HD_PROMPT_DISMISSED_LANGUAGES] ?: emptySet() }.distinctUntilChanged()
    suspend fun addHdPromptDismissedLanguages(codes: Collection<String>) {
        dataStore.edit { settings -> settings[HD_PROMPT_DISMISSED_LANGUAGES] = (settings[HD_PROMPT_DISMISSED_LANGUAGES] ?: emptySet()) + codes }
    }

    val notificationPromptShownFlow: Flow<Boolean> = dataStore.data.map { it[NOTIFICATION_PROMPT_SHOWN] ?: false }.distinctUntilChanged()
    suspend fun setNotificationPromptShown(shown: Boolean) { dataStore.edit { it[NOTIFICATION_PROMPT_SHOWN] = shown } }

    // --- Saved preferences (debug table) ---
    // Every key declared in the companion object is found by reflection, so a new key shows up in the table with no
    // extra code. Stored keys the reflection doesn't see are listed too, from the data itself.
    private val readOnlyPreferenceNames = setOf("active_sessions_list", "deck_set_counts_snapshot", "downloaded_hd_languages")

    private val declaredPreferences: Map<String, Pair<Preferences.Key<*>, String>> by lazy {
        PreferenceManager::class.java.declaredFields
            .filter { Preferences.Key::class.java.isAssignableFrom(it.type) }
            .mapNotNull { field ->
                try {
                    field.isAccessible = true
                    val key = field.get(null) as? Preferences.Key<*> ?: return@mapNotNull null
                    val generic = (field.genericType as? java.lang.reflect.ParameterizedType)?.actualTypeArguments?.get(0)
                    val rawType = when (generic) {
                        is java.lang.reflect.ParameterizedType -> generic.rawType as Class<*>
                        is Class<*> -> generic
                        else -> return@mapNotNull null
                    }
                    key.name to (key to typeNameOf(rawType))
                } catch (e: Exception) {
                    null
                }
            }
            .toMap()
    }

    private fun typeNameOf(type: Class<*>): String = when (type) {
        java.lang.Boolean::class.java -> "Boolean"
        java.lang.Integer::class.java -> "Int"
        java.lang.Long::class.java -> "Long"
        java.lang.Float::class.java -> "Float"
        java.lang.Double::class.java -> "Double"
        java.lang.String::class.java -> "String"
        java.util.Set::class.java -> "Set<String>"
        else -> type.simpleName
    }

    private fun typeNameOfValue(value: Any?): String = when (value) {
        is Boolean -> "Boolean"
        is Int -> "Int"
        is Long -> "Long"
        is Float -> "Float"
        is Double -> "Double"
        is Set<*> -> "Set<String>"
        else -> "String"
    }

    private fun formatPreferenceValue(value: Any?): String = when (value) {
        is Set<*> -> value.map { it.toString() }.sorted().joinToString(", ")
        else -> value.toString()
    }

    /**
     * The value each reader falls back to, as the saved-preferences table shows it. The defaults are written in the
     * flow code above, not on the keys, so they are listed here. A key missing from this map shows no default.
     */
    private val preferenceDefaults: Map<String, String> = mapOf(
        IS_DARK_MODE.name to "true",
        SPACING_MODE.name to SpacingMode.COMFORTABLE.toString(),
        ANIMATION_MODE.name to AnimationMode.NORMAL.toString(),
        DISPLAY_SETS_UNDER_DECKS.name to "true",
        GRID_LARGE_SCREEN_LAYOUT.name to "true",
        GRID_LOADING_INDICATOR.name to "true",
        TREE_LOADING_INDICATOR.name to "true",
        REDUCE_MOTION.name to "false",
        DISABLE_CARD_FLIP_ANIMATIONS.name to "false",
        ALWAYS_OPEN_BULK_EDITOR.name to "false",
        SORT_TAGS_BY_DATE_CREATED.name to "false",
        SHORTCUTS_CURRENT_SCREEN_ONLY.name to "true",
        SHOW_SHORTCUTS_BUTTON.name to "true",
        SHOW_SIZE_OVERLAY.name to "false",
        IS_DEBUG.name to "false",
        SHORTCUT_REMAPS.name to "{}",
        DOWNLOADED_HD_LANGUAGES.name to "",
        HAS_PROMPTED_WHISPER_MODEL.name to "false",
        MEMORY_GRID_COLUMNS_PORTRAIT.name to "3",
        MEMORY_GRID_COLUMNS_LANDSCAPE.name to "5",
        DECK_SORT_MODE.name to DeckSortMode.A_TO_Z.value.toString(),
        DECK_VIEW_MODE.name to "0",
        GROUP_BY_CATEGORY.name to "true",
        GROUP_BY_MODE.name to "true",
        CATEGORY_SORT_MODE.name to GroupSortMode.MOST_RECENT.value.toString(),
        CATEGORY_SORT_DIRECTION.name to Direction.DESC.name,
        MODE_SORT_MODE.name to GroupSortMode.MOST_RECENT.value.toString(),
        MODE_SORT_DIRECTION.name to Direction.DESC.name,
        SESSION_TILE_SORT_MODE.name to SessionTileSortMode.LAST_ACCESSED.value.toString(),
        SESSION_TILE_SORT_DIRECTION.name to Direction.DESC.name,
        CUSTOM_PRIMARY.name to "#6750A4",
        CUSTOM_SECONDARY.name to "#625B71",
        CUSTOM_TERTIARY.name to "#7D5260",
        CUSTOM_BACKGROUND.name to "#FFFBFE",
        SYNC_DECKS_AND_CARDS.name to "true",
        SYNC_REVIEW_DATA.name to "true",
        SYNC_SAVED_SESSIONS.name to "true",
        SYNC_ONLY_ON_WIFI.name to "true",
        MODE_DEFAULT_SETTINGS.name to "{}",
        HAS_PROMPTED_HD_LANGUAGES.name to "false",
        NOTIFICATION_PROMPT_SHOWN.name to "false",
        LAST_EXPORT_TIMESTAMP.name to "0",
        LAST_IMPORT_TIMESTAMP.name to "0",
    )

    /** Every preference: declared keys plus any stored ones, sorted by name. Updates live with the DataStore. */
    val preferenceEntries: Flow<List<PreferenceEntry>> = dataStore.data.map { prefs ->
        val stored = prefs.asMap().mapKeys { it.key.name }
        (declaredPreferences.keys + stored.keys).sorted().map { name ->
            val value = stored[name]
            PreferenceEntry(
                name = name,
                type = declaredPreferences[name]?.second ?: typeNameOfValue(value),
                value = value?.let { formatPreferenceValue(it) },
                defaultValue = preferenceDefaults[name],
                editable = name !in readOnlyPreferenceNames
            )
        }
    }

    /** Writes [text], parsed as [type] (the caller validates it first). */
    suspend fun setPreference(name: String, type: String, text: String) {
        dataStore.edit { prefs ->
            when (type) {
                "Boolean" -> prefs[booleanPreferencesKey(name)] = text.toBooleanStrict()
                "Int" -> prefs[intPreferencesKey(name)] = text.trim().toInt()
                "Long" -> prefs[longPreferencesKey(name)] = text.trim().toLong()
                "Float" -> prefs[floatPreferencesKey(name)] = text.trim().toFloat()
                "Double" -> prefs[doublePreferencesKey(name)] = text.trim().toDouble()
                "String" -> prefs[stringPreferencesKey(name)] = text
                "Set<String>" -> prefs[stringSetPreferencesKey(name)] =
                    text.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
            }
        }
    }

    /** Removes the stored value, so the preference goes back to its default. */
    suspend fun clearPreference(name: String, type: String) {
        dataStore.edit { prefs -> removeTyped(prefs, name, type) }
    }

    /** Clears every editable preference in one write. Read-only entries are left alone. */
    suspend fun clearAllEditablePreferences(entries: List<PreferenceEntry>) {
        dataStore.edit { prefs ->
            entries.filter { it.editable }.forEach { removeTyped(prefs, it.name, it.type) }
        }
    }

    private fun removeTyped(prefs: MutablePreferences, name: String, type: String) {
        when (type) {
            "Boolean" -> prefs.remove(booleanPreferencesKey(name))
            "Int" -> prefs.remove(intPreferencesKey(name))
            "Long" -> prefs.remove(longPreferencesKey(name))
            "Float" -> prefs.remove(floatPreferencesKey(name))
            "Double" -> prefs.remove(doublePreferencesKey(name))
            "String" -> prefs.remove(stringPreferencesKey(name))
            "Set<String>" -> prefs.remove(stringSetPreferencesKey(name))
        }
    }

    // NEW: Function to update the prompt status
    suspend fun setHdAudioPrompted(prompted: Boolean) {
        dataStore.edit { settings ->
            settings[HAS_PROMPTED_HD_LANGUAGES] = prompted
            // Resetting the prompt (prompted = false) also brings back the languages it was dismissed for
            if (!prompted) settings[HD_PROMPT_DISMISSED_LANGUAGES] = emptySet()
        }
    }

    suspend fun updateLastExportTimestamp() {
        dataStore.edit { settings ->
            settings[LAST_EXPORT_TIMESTAMP] = System.currentTimeMillis()
        }
    }

    suspend fun updateLastImportTimestamp() {
        dataStore.edit { settings ->
            settings[LAST_IMPORT_TIMESTAMP] = System.currentTimeMillis()
        }
    }

    // Sync Preference Setters
    suspend fun setSyncDecksAndCards(enabled: Boolean) {
        dataStore.edit { it[SYNC_DECKS_AND_CARDS] = enabled }
    }

    suspend fun setSyncReviewData(enabled: Boolean) {
        dataStore.edit { it[SYNC_REVIEW_DATA] = enabled }
    }

    suspend fun setSyncSavedSessions(enabled: Boolean) {
        dataStore.edit { it[SYNC_SAVED_SESSIONS] = enabled }
    }

    suspend fun setSyncOnlyOnWifi(enabled: Boolean) {
        dataStore.edit { it[SYNC_ONLY_ON_WIFI] = enabled }
    }

    // Function to save custom colors
    suspend fun setCustomThemeColors(primary: String, secondary: String, tertiary: String, background: String) {
        dataStore.edit { settings ->
            settings[CUSTOM_PRIMARY] = primary
            settings[CUSTOM_SECONDARY] = secondary
            settings[CUSTOM_TERTIARY] = tertiary
            settings[CUSTOM_BACKGROUND] = background
        }
    }

    val activeSessionsFlow: Flow<List<ActiveSession>> = dataStore.data.map { preferences ->
        val jsonString = preferences[ACTIVE_SESSIONS]
        if (jsonString.isNullOrEmpty()) {
            emptyList()
        } else {
            try {
                val sessions = mutableListOf<ActiveSession>()
                val jsonArray = JSONArray(jsonString)
                for (i in 0 until jsonArray.length()) {
                    val json = jsonArray.getJSONObject(i)

                    val cardIdsJson = json.getJSONArray("shuffledCardIds")
                    val cardIds = List(cardIdsJson.length()) { cardIdsJson.getString(it) }

                    val wrongSelectionsJson = json.getJSONArray("wrongSelections")
                    val wrongSelections = List(wrongSelectionsJson.length()) { wrongSelectionsJson.getString(it) }

                    val difficultiesJson = json.getJSONArray("difficulties")
                    val difficulties = List(difficultiesJson.length()) { difficultiesJson.getInt(it) }

                    val matchingCardIdsOnScreenJson = json.optJSONArray("matchingCardIdsOnScreen") ?: JSONArray()
                    val matchingCardIdsOnScreen = List(matchingCardIdsOnScreenJson.length()) { matchingCardIdsOnScreenJson.getString(it) }

                    val matchedPairsJson = json.optJSONArray("matchedPairs") ?: JSONArray()
                    val matchedPairs = List(matchedPairsJson.length()) { matchedPairsJson.getString(it) }

                    val incorrectCardIdsJson = json.optJSONArray("incorrectCardIds") ?: JSONArray()
                    val incorrectCardIds = List(incorrectCardIdsJson.length()) { incorrectCardIdsJson.getString(it) }

                    // Deserialize picker options
                    val pickerOptionsJson = json.optJSONArray("pickerOptions") ?: JSONArray()
                    val pickerOptions = List(pickerOptionsJson.length()) { pickerOptionsJson.getString(it) }

                    val mcOptionsJson = json.optJSONObject("mcOptions")
                    val mcOptions = mcOptionsJson?.keys()?.asSequence()?.associateWith { cardId ->
                        val optionIdsJson = mcOptionsJson.getJSONArray(cardId)
                        List(optionIdsJson.length()) { optionIdsJson.getString(it) }
                    } ?: emptyMap()

                    val cwWords = mutableListOf<CrosswordWord>()
                    val cwWordsJsonArray = json.optJSONArray("crosswordWords")
                    if (cwWordsJsonArray != null) {
                        for (k in 0 until cwWordsJsonArray.length()) {
                            val wObj = cwWordsJsonArray.getJSONObject(k)
                            cwWords.add(
                                CrosswordWord(
                                    id = wObj.getString("id"),
                                    word = wObj.getString("word"),
                                    clue = wObj.getString("clue"),
                                    startX = wObj.getInt("startX"),
                                    startY = wObj.getInt("startY"),
                                    isAcross = wObj.getBoolean("isAcross"),
                                    number = wObj.getInt("number")
                                )
                            )
                        }
                    }

                    val cwInputs = mutableMapOf<String, String>()
                    val cwInputsJson = json.optJSONObject("crosswordUserInputs")
                    cwInputsJson?.keys()?.forEach { key ->
                        cwInputs[key] = cwInputsJson.getString(key)
                    }

                    // Parse attemptedCardIds
                    val attemptedCardIdsJson = json.optJSONArray("attemptedCardIds") ?: JSONArray()
                    val attemptedCardIds = List(attemptedCardIdsJson.length()) { attemptedCardIdsJson.getString(it) }

                    // Parse enums
                    val modeString = json.optString("mode", "FLASHCARD")
                    val backupIsGraded = json.optBoolean("isGraded", false)
                    // Same remaps as Room's MIGRATION_11_12/12_13: an older backup can still say
                    // "AUDIO"+graded (pre audio-mode-split) or one of the original 4 split-out
                    // modes (pre 4→2 consolidation) — see AppDatabase.kt for why each is lossless.
                    val parsedMode = when {
                        modeString.equals("AUDIO", ignoreCase = true) && backupIsGraded -> SessionMode.SPOKEN_LISTEN
                        modeString.uppercase() in setOf("SPEECH_TO_TEXT", "LISTEN_TYPE") -> SessionMode.TYPED_LISTEN
                        modeString.uppercase() in setOf("TEXT_TO_SPEECH", "LISTEN_SPEAK") -> SessionMode.SPOKEN_LISTEN
                        else -> modeString.toSessionMode()
                    }

                    val schedulingModeString = json.optString("schedulingMode", "NORMAL")
                    val parsedSchedulingMode = schedulingModeString.toSchedulingMode()

                    val cardOrderString = json.optString("cardOrder", "RANDOM")
                    val parsedCardOrder = cardOrderString.toSortMode()

                    val promptSideString = json.optString("quizPromptSide", "Front")

                    val parsedPromptSide = promptSideString.toCardSide()


                    sessions.add(
                        ActiveSession(
                            id = json.getString("id"),
                            deckId = json.getString("deckId"),
                            mode = parsedMode,
                            isWeighted = json.getBoolean("isWeighted"),
                            difficulties = difficulties,
                            totalCards = json.getInt("totalCards"),
                            shuffledCardIds = cardIds,
                            quizPromptSide = parsedPromptSide,
                            currentCardIndex = json.getInt("currentCardIndex"),
                            wrongSelections = wrongSelections,
                            correctAnswerFound = json.getBoolean("correctAnswerFound"),
                            showQuestion = json.getBoolean("showQuestion"),
                            isFlipped = json.getBoolean("isFlipped"),
                            firstTryCorrectCount = json.getInt("firstTryCorrectCount"),
                            hasAttempted = json.getBoolean("hasAttempted"),
                            createdAt = json.optLong("createdAt", System.currentTimeMillis()),
                            lastAccessed = json.optLong("lastAccessed", System.currentTimeMillis()),
                            numberOfAnswers = json.optInt("numberOfAnswers", 4),
                            showCorrectLetters = json.optBoolean("showCorrectLetters", false),
                            limitAnswerPool = json.optBoolean("limitAnswerPool", true),
                            cardOrder = parsedCardOrder,
                            mcOptions = mcOptions,
                            pickerOptions = pickerOptions,
                            matchingCardIdsOnScreen = matchingCardIdsOnScreen,
                            matchedPairs = matchedPairs,
                            incorrectCardIds = incorrectCardIds,
                            isGraded = json.optBoolean("isGraded", false),
                            allowMultipleGuesses = json.optBoolean("allowMultipleGuesses", true),
                            enableStt = json.optBoolean("enableStt", false),
                            hideAnswerText = json.optBoolean("hideAnswerText", false),
                            attemptedCardIds = attemptedCardIds,
                            fingersAndToes = json.optBoolean("fingersAndToes", false),
                            crosswordWords = cwWords,
                            crosswordUserInputs = cwInputs,
                            crosswordGridWidth = json.optInt("crosswordGridWidth", 0),
                            crosswordGridHeight = json.optInt("crosswordGridHeight", 0),
                            showCorrectWords = json.optBoolean("showCorrectWords", true),
                            schedulingMode = parsedSchedulingMode,
                            audioPlaybackSpeed = json.optDouble("audioPlaybackSpeed", 1.0).toFloat(),
                            audioReplayCount = json.optInt("audioReplayCount", 1),
                            audioAutoAdvance = json.optBoolean("audioAutoAdvance", true),
                            audioAnswerDelaySeconds = json.optDouble("audioAnswerDelaySeconds", 2.0),
                            audioNextCardDelaySeconds = json.optDouble("audioNextCardDelaySeconds", 2.0),
                            gridDensity = json.optInt("gridDensity", 2),
                            freeformShowBothSides = json.optBoolean("freeformShowBothSides", true),
                            freeformSwipeNavigation = json.optBoolean("freeformSwipeNavigation", true),
                            typingIgnoreFormatting = json.optBoolean("typingIgnoreFormatting", true),
                            typingAutoSubmit = json.optBoolean("typingAutoSubmit", false),
                            typingDisableAutocorrect = json.optBoolean("typingDisableAutocorrect", true),
                            typingShowLengthHint = json.optBoolean("typingShowLengthHint", true),
                            flashcardAutoFlipSeconds = json.optInt("flashcardAutoFlipSeconds", 0),
                            flashcardDoubleTapToFlip = json.optBoolean("flashcardDoubleTapToFlip", false),
                            flashcardRandomizeFirstSide = json.optBoolean("flashcardRandomizeFirstSide", false),
                            requireConfirmTap = json.optBoolean("requireConfirmTap", false),
                            autoAdvanceAfterCorrect = json.optBoolean("autoAdvanceAfterCorrect", false),
                            autoAdvanceDelaySeconds = json.optDouble("autoAdvanceDelaySeconds", 1.0),
                            autoListen = json.optBoolean("autoListen", false),
                            listenStartSound = json.optBoolean("listenStartSound", true),
                            listenCorrectSound = json.optBoolean("listenCorrectSound", true),
                            listenIncorrectSound = json.optBoolean("listenIncorrectSound", true),
                            speakingFrontSpeed = json.optDouble("speakingFrontSpeed", 1.0).toFloat(),
                            speakingBackSpeed = json.optDouble("speakingBackSpeed", 1.0).toFloat(),
                            listResetPosition = json.optBoolean("listResetPosition", false),
                            listDimWrongGuesses = json.optBoolean("listDimWrongGuesses", false),
                            listRemoveGuessed = json.optBoolean("listRemoveGuessed", false),
                            matchingHighlightStyle = json.optString("matchingHighlightStyle", "FILL"),
                            matchingWrongDelayMs = json.optInt("matchingWrongDelayMs", 1000),
                            matchingCorrectHighlightMs = json.optInt("matchingCorrectHighlightMs", 0),
                            matchingShowCorrectDialog = json.optBoolean("matchingShowCorrectDialog", false),
                            anagramFirstLetterHint = json.optBoolean("anagramFirstLetterHint", false),
                            anagramUppercase = json.optBoolean("anagramUppercase", true),
                            anagramColorVowels = json.optBoolean("anagramColorVowels", false),
                            crosswordHighlightWord = json.optBoolean("crosswordHighlightWord", true),
                            crosswordAutoAdvanceCell = json.optBoolean("crosswordAutoAdvanceCell", false),
                            crosswordCompactClues = json.optBoolean("crosswordCompactClues", false),
                            crosswordFeedbackMode = json.optString("crosswordFeedbackMode", "LETTER"),
                            hangmanMaxMistakes = json.optInt("hangmanMaxMistakes", 7),
                            hangmanRevealSpeedMs = json.optInt("hangmanRevealSpeedMs", 300),
                            hangmanHideVisual = json.optBoolean("hangmanHideVisual", false),
                            memoryFlipAnimation = json.optBoolean("memoryFlipAnimation", true),
                            memoryGrayMatched = json.optBoolean("memoryGrayMatched", false),
                            memoryPeekSeconds = json.optInt("memoryPeekSeconds", 0),
                            memoryWrongPairMs = json.optInt("memoryWrongPairMs", 0),
                            wordSearchHideFound = json.optBoolean("wordSearchHideFound", false),
                            wordSearchHighlightColor = json.optInt("wordSearchHighlightColor", -14498466)
                        )
                    )
                }
                sessions
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    suspend fun saveActiveSessions(sessions: List<ActiveSession>) {
        dataStore.edit { settings ->
            val jsonArray = JSONArray()
            sessions.forEach { session ->
                val json = JSONObject().apply {
                    put("id", session.id)
                    put("deckId", session.deckId)
                    put("mode", session.mode.name)
                    put("isWeighted", session.isWeighted)
                    put("difficulties", JSONArray(session.difficulties))
                    put("totalCards", session.totalCards)
                    put("shuffledCardIds", JSONArray(session.shuffledCardIds))
                    put("quizPromptSide", session.quizPromptSide.name)
                    put("currentCardIndex", session.currentCardIndex)
                    put("wrongSelections", JSONArray(session.wrongSelections))
                    put("correctAnswerFound", session.correctAnswerFound)
                    put("showQuestion", session.showQuestion)
                    put("isFlipped", session.isFlipped)
                    put("firstTryCorrectCount", session.firstTryCorrectCount)
                    put("hasAttempted", session.hasAttempted)
                    put("createdAt", session.createdAt)
                    put("lastAccessed", session.lastAccessed)
                    put("numberOfAnswers", session.numberOfAnswers)
                    put("showCorrectLetters", session.showCorrectLetters)
                    put("limitAnswerPool", session.limitAnswerPool)
                    put("cardOrder", session.cardOrder.name)
                    put("mcOptions", JSONObject(session.mcOptions.mapValues { JSONArray(it.value) }))
                    put("pickerOptions", JSONArray(session.pickerOptions))
                    put("matchingCardIdsOnScreen", JSONArray(session.matchingCardIdsOnScreen))
                    put("matchedPairs", JSONArray(session.matchedPairs))
                    put("incorrectCardIds", JSONArray(session.incorrectCardIds))
                    put("isGraded", session.isGraded)
                    put("allowMultipleGuesses", session.allowMultipleGuesses)
                    put("enableStt", session.enableStt)
                    put("hideAnswerText", session.hideAnswerText)
                    put("attemptedCardIds", JSONArray(session.attemptedCardIds))
                    put("fingersAndToes", session.fingersAndToes)
                    put("maxMemoryTiles", session.maxMemoryTiles)
                    put("memorySelectedId1", session.memorySelectedId1)
                    put("memorySelectedSide1", session.memorySelectedSide1)
                    put("memorySelectedId2", session.memorySelectedId2)
                    put("memorySelectedSide2", session.memorySelectedSide2)
                    put("showCorrectWords", session.showCorrectWords)
                    put("schedulingMode", session.schedulingMode.name)
                    put("audioPlaybackSpeed", session.audioPlaybackSpeed.toDouble())
                    put("audioReplayCount", session.audioReplayCount)
                    put("audioAutoAdvance", session.audioAutoAdvance)
                    put("audioAnswerDelaySeconds", session.audioAnswerDelaySeconds)
                    put("audioNextCardDelaySeconds", session.audioNextCardDelaySeconds)
                    put("gridDensity", session.gridDensity)
                    put("freeformShowBothSides", session.freeformShowBothSides)
                    put("freeformSwipeNavigation", session.freeformSwipeNavigation)
                    put("typingIgnoreFormatting", session.typingIgnoreFormatting)
                    put("typingAutoSubmit", session.typingAutoSubmit)
                    put("typingDisableAutocorrect", session.typingDisableAutocorrect)
                    put("typingShowLengthHint", session.typingShowLengthHint)
                    put("flashcardAutoFlipSeconds", session.flashcardAutoFlipSeconds)
                    put("flashcardDoubleTapToFlip", session.flashcardDoubleTapToFlip)
                    put("flashcardRandomizeFirstSide", session.flashcardRandomizeFirstSide)
                    put("requireConfirmTap", session.requireConfirmTap)
                    put("autoAdvanceAfterCorrect", session.autoAdvanceAfterCorrect)
                    put("autoAdvanceDelaySeconds", session.autoAdvanceDelaySeconds)
                    put("autoListen", session.autoListen)
                    put("listenStartSound", session.listenStartSound)
                    put("listenCorrectSound", session.listenCorrectSound)
                    put("listenIncorrectSound", session.listenIncorrectSound)
                    put("speakingFrontSpeed", session.speakingFrontSpeed.toDouble())
                    put("speakingBackSpeed", session.speakingBackSpeed.toDouble())
                    put("listResetPosition", session.listResetPosition)
                    put("listDimWrongGuesses", session.listDimWrongGuesses)
                    put("listRemoveGuessed", session.listRemoveGuessed)
                    put("matchingHighlightStyle", session.matchingHighlightStyle)
                    put("matchingWrongDelayMs", session.matchingWrongDelayMs)
                    put("matchingCorrectHighlightMs", session.matchingCorrectHighlightMs)
                    put("matchingShowCorrectDialog", session.matchingShowCorrectDialog)
                    put("anagramFirstLetterHint", session.anagramFirstLetterHint)
                    put("anagramUppercase", session.anagramUppercase)
                    put("anagramColorVowels", session.anagramColorVowels)
                    put("crosswordHighlightWord", session.crosswordHighlightWord)
                    put("crosswordAutoAdvanceCell", session.crosswordAutoAdvanceCell)
                    put("crosswordCompactClues", session.crosswordCompactClues)
                    put("crosswordFeedbackMode", session.crosswordFeedbackMode)
                    put("hangmanMaxMistakes", session.hangmanMaxMistakes)
                    put("hangmanRevealSpeedMs", session.hangmanRevealSpeedMs)
                    put("hangmanHideVisual", session.hangmanHideVisual)
                    put("memoryFlipAnimation", session.memoryFlipAnimation)
                    put("memoryGrayMatched", session.memoryGrayMatched)
                    put("memoryPeekSeconds", session.memoryPeekSeconds)
                    put("memoryWrongPairMs", session.memoryWrongPairMs)
                    put("wordSearchHideFound", session.wordSearchHideFound)
                    put("wordSearchHighlightColor", session.wordSearchHighlightColor)

                    // --- NEW: Serialize Crossword Data ---
                    val cwWordsArray = JSONArray()
                    session.crosswordWords.forEach { w ->
                        val wObj = JSONObject()
                        wObj.put("id", w.id)
                        wObj.put("word", w.word)
                        wObj.put("clue", w.clue)
                        wObj.put("startX", w.startX)
                        wObj.put("startY", w.startY)
                        wObj.put("isAcross", w.isAcross)
                        wObj.put("number", w.number)
                        cwWordsArray.put(wObj)
                    }
                    put("crosswordWords", cwWordsArray)
                    put("crosswordUserInputs", JSONObject(session.crosswordUserInputs))
                    put("crosswordGridWidth", session.crosswordGridWidth)
                    put("crosswordGridHeight", session.crosswordGridHeight)
                }
                jsonArray.put(json)
            }
            settings[ACTIVE_SESSIONS] = jsonArray.toString()
        }
    }
}