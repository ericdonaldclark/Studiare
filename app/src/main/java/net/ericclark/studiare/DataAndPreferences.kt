package net.ericclark.studiare

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.UUID
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.serialization.Serializable
import net.ericclark.studiare.data.*
import net.ericclark.studiare.data.ActiveSession
import net.ericclark.studiare.data.CrosswordWord

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

object ThemeMode {
    const val LIGHT = 0
    const val DARK = 1
    const val BLACK_AND_WHITE = 2
}

class PreferenceManager(context: Context) {
    private val dataStore = context.dataStore

    companion object {
        val IS_DARK_MODE = booleanPreferencesKey("is_dark_mode")
        val THEME_MODE = intPreferencesKey("theme_mode")
        val ACTIVE_SESSIONS = stringPreferencesKey("active_sessions_list")
        val LAST_EXPORT_TIMESTAMP = longPreferencesKey("last_export_timestamp")
        val LAST_IMPORT_TIMESTAMP = longPreferencesKey("last_import_timestamp")
        val HAS_PROMPTED_HD_LANGUAGES = booleanPreferencesKey("has_prompted_hd_languages")
        // NEW: Key to track downloaded languages
        val DOWNLOADED_HD_LANGUAGES = stringSetPreferencesKey("downloaded_hd_languages")
        val MEMORY_GRID_COLUMNS_PORTRAIT = intPreferencesKey("memory_grid_columns_portrait")
        val MEMORY_GRID_COLUMNS_LANDSCAPE = intPreferencesKey("memory_grid_columns_landscape")
    }

    val themeModeFlow: Flow<Int> = dataStore.data.map { preferences ->
        val mode = preferences[THEME_MODE]
        if (mode != null) {
            mode
        } else {
            val isDark = preferences[IS_DARK_MODE] ?: true
            if (isDark) ThemeMode.DARK else ThemeMode.LIGHT
        }
    }

    val downloadedHdLanguagesFlow: Flow<Set<String>> = dataStore.data.map { preferences ->
        preferences[DOWNLOADED_HD_LANGUAGES] ?: emptySet()
    }

    // Flow for Portrait Columns (Default 3)
    val memoryGridColumnsPortraitFlow: Flow<Int> = dataStore.data.map { preferences ->
        preferences[MEMORY_GRID_COLUMNS_PORTRAIT] ?: 3
    }

    // Flow for Landscape Columns (Default 5)
    val memoryGridColumnsLandscapeFlow: Flow<Int> = dataStore.data.map { preferences ->
        preferences[MEMORY_GRID_COLUMNS_LANDSCAPE] ?: 5
    }

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

    val lastExportTimestampFlow: Flow<Long> = dataStore.data.map { preferences ->
        preferences[LAST_EXPORT_TIMESTAMP] ?: 0L
    }

    val lastImportTimestampFlow: Flow<Long> = dataStore.data.map { preferences ->
        preferences[LAST_IMPORT_TIMESTAMP] ?: 0L
    }

    suspend fun setThemeMode(mode: Int) {
        dataStore.edit { settings ->
            settings[THEME_MODE] = mode
            settings[IS_DARK_MODE] = (mode == ThemeMode.DARK || mode == ThemeMode.BLACK_AND_WHITE)
        }
    }

    val hasPromptedHdLanguagesFlow: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[HAS_PROMPTED_HD_LANGUAGES] ?: false
    }

    // NEW: Function to update the prompt status
    suspend fun setHdAudioPrompted(prompted: Boolean) {
        dataStore.edit { settings ->
            settings[HAS_PROMPTED_HD_LANGUAGES] = prompted
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

                    sessions.add(
                        ActiveSession(
                            id = json.getString("id"),
                            deckId = json.getString("deckId"),
                            mode = json.getString("mode"),
                            isWeighted = json.getBoolean("isWeighted"),
                            difficulties = difficulties,
                            totalCards = json.getInt("totalCards"),
                            shuffledCardIds = cardIds,
                            quizPromptSide = json.optString("quizPromptSide", "Question"),
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
                            cardOrder = json.optString("cardOrder", "Random"),
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
                            showCorrectWords = json.optBoolean("showCorrectWords", true)
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
                    put("mode", session.mode)
                    put("isWeighted", session.isWeighted)
                    put("difficulties", JSONArray(session.difficulties))
                    put("totalCards", session.totalCards)
                    put("shuffledCardIds", JSONArray(session.shuffledCardIds))
                    put("quizPromptSide", session.quizPromptSide)
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
                    put("cardOrder", session.cardOrder)
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