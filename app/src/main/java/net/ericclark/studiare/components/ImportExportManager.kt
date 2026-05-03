package net.ericclark.studiare.components

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.android.gms.tasks.Task
import com.opencsv.CSVReaderBuilder
import com.opencsv.CSVWriter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import net.ericclark.studiare.data.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.StringReader
import java.io.StringWriter
import java.util.Locale
import java.util.UUID
import kotlin.math.max
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

/**
 * Handles all logic related to importing and exporting Decks and Cards.
 * Acts as a delegate for the FlashcardViewModel.
 */
class ImportExportManager(
    private val db: FirebaseFirestore,
    private val preferenceManager: net.ericclark.studiare.PreferenceManager,
    private val viewModelScope: CoroutineScope,
    private val userIdProvider: () -> String?,
    private val getLocalDecks: () -> List<Deck>,
    private val getLocalCards: () -> List<Card>,
    private val onProcessingChanged: (Boolean) -> Unit,
    private val onOverwriteConfirmationChanged: (OverwriteConfirmationData?) -> Unit,
    private val getOverwriteConfirmation: () -> OverwriteConfirmationData?,
    private val safeWrite: suspend (Task<*>) -> Unit,
    private val saveDeckToFirestore: (Deck) -> Unit,
    private val saveCardToFirestore: (Card) -> Unit,
    private val onError: (String) -> Unit = {}
) {
    private val TAG = "ImportExportManager"

    fun getDecksAsString(decksToExport: List<DeckWithCards>, format: String): String {
        viewModelScope.launch { preferenceManager.updateLastExportTimestamp() }
        return if (format == "CSV") getCsvForDecks(decksToExport) else getJsonForDecks(decksToExport)
    }

    private fun getJsonForDecks(decksToExport: List<DeckWithCards>): String {
        val jsonArray = JSONArray()
        // Sort so parents come before sets (parentDeckId == null is false, so false comes first)
        decksToExport.sortedBy { it.deck.parentDeckId != null }.forEach { deckWithCards ->
            val deck = deckWithCards.deck
            val deckObject = JSONObject()

            // Explicitly call put on deckObject using deck properties
            deckObject.put("name", deck.name)
            deckObject.put("id", deck.id)
            deckObject.put("parentDeckId", deck.parentDeckId)
            deckObject.put("createdAt", deck.createdAt)
            deckObject.put("updatedAt", deck.updatedAt)
            deckObject.put("averageQuizScore", deck.averageQuizScore)
            deckObject.put("normalizationType", deck.normalizationType.value)
            deckObject.put("sortType", deck.deckSortMode.value)
            deckObject.put("isStarred", deck.isStarred)
            deckObject.put("frontLanguage", deck.frontLanguage)
            deckObject.put("backLanguage", deck.backLanguage)
            // NEW FIELDS
            deckObject.put("description", deck.description)
            deckObject.put("dailyNewCardLimit", deck.dailyNewCardLimit)
            deckObject.put("dailyReviewLimit", deck.dailyReviewLimit)

            val gson = Gson()
            if (deck.frontNoteTemplates.isNotEmpty()) deckObject.put("frontNoteTemplates", JSONArray(gson.toJson(deck.frontNoteTemplates)))
            if (deck.backNoteTemplates.isNotEmpty()) deckObject.put("backNoteTemplates", JSONArray(gson.toJson(deck.backNoteTemplates)))

            val cardsArray = JSONArray()
            deckWithCards.cards.forEach { card ->
                val cardObject = JSONObject()
                cardObject.put("id", card.id)
                cardObject.put("front", card.front)
                card.frontRichText?.let { cardObject.put("frontRichText", it) }
                cardObject.put("back", card.back)
                card.backRichText?.let { cardObject.put("backRichText", it) }

                // Serialize NoteField Lists
                if (card.frontNotes.isNotEmpty()) cardObject.put("frontNotes", JSONArray(gson.toJson(card.frontNotes)))
                if (card.backNotes.isNotEmpty()) cardObject.put("backNotes", JSONArray(gson.toJson(card.backNotes)))
                cardObject.put("difficulty", card.difficulty)
                card.reviewedAt?.let { cardObject.put("reviewedAt", it) }
                cardObject.put("isKnown", card.isKnown)
                cardObject.put("tags", JSONArray(card.tags))
                // NEW FIELDS
                cardObject.put("createdAt", card.createdAt)
                cardObject.put("updatedAt", card.updatedAt)
                cardObject.put("isSuspended", card.isSuspended)
                cardObject.put("flag", card.flag)

                cardsArray.put(cardObject)
            }
            deckObject.put("cards", cardsArray)

            jsonArray.put(deckObject)
        }
        return jsonArray.toString(2)
    }

    private fun getCsvForDecks(decksToExport: List<DeckWithCards>): String {
        val stringWriter = StringWriter()
        val csvWriter = CSVWriter(stringWriter)
        val gson = Gson()
        // Header
        csvWriter.writeNext(arrayOf(
            "deckId", "deckName", "parentDeckId", "isStarred",
            "cardId", "front", "back", "frontNotes", "backNotes",
            "difficulty", "reviewedAt", "isKnown", "frontLanguage", "backLanguage", "tags",
            "createdAt", "updatedAt", "defaultSortOrder", "isSuspended", "flag",
            "frontRichText", "backRichText" // Added to end for backwards compatibility
        ))
        decksToExport.forEach { deckWithCards ->
            deckWithCards.cards.forEach { card ->
                csvWriter.writeNext(arrayOf(
                    deckWithCards.deck.id, deckWithCards.deck.name, deckWithCards.deck.parentDeckId ?: "", deckWithCards.deck.isStarred.toString(),
                    card.id, card.front, card.back,
                    if (card.frontNotes.isNotEmpty()) gson.toJson(card.frontNotes) else "",
                    if (card.backNotes.isNotEmpty()) gson.toJson(card.backNotes) else "",
                    card.difficulty.toString(), card.reviewedAt?.toString() ?: "", card.isKnown.toString(),
                    deckWithCards.deck.frontLanguage, deckWithCards.deck.backLanguage,
                    card.tags.joinToString(";"),
                    card.createdAt.toString(), card.updatedAt.toString(), card.isSuspended.toString(), card.flag.toString(),
                    card.frontRichText ?: "", card.backRichText ?: ""
                ))
            }
        }
        return stringWriter.toString()
    }

    fun importDecksFromString(content: String, mimeType: String?) {
        viewModelScope.launch { preferenceManager.updateLastImportTimestamp() }
        onProcessingChanged(true) // Start loading immediately
        val trimmedContent = content.trim()
        Log.d(TAG, "Starting import, content length: ${trimmedContent.length}, type: $mimeType")

        if (trimmedContent.startsWith("[") || trimmedContent.startsWith("{")) {
            parseAndCheckForJsonOverwrite(trimmedContent)
        } else {
            importDecksFromCsv(trimmedContent)
        }
    }

    private fun parseJsonNotes(jsonArrayOpt: JSONArray?, stringOpt: String?): List<NoteField> {
        val gson = Gson()
        if (jsonArrayOpt != null) {
            val type = object : TypeToken<List<NoteField>>() {}.type
            return gson.fromJson(jsonArrayOpt.toString(), type) ?: emptyList()
        } else if (!stringOpt.isNullOrBlank()) {
            return listOf(NoteField(name = "Note", content = stringOpt, type = MediaType.PLAIN_TEXT))
        }
        return emptyList()
    }

    private fun parseAndCheckForJsonOverwrite(jsonString: String) {
        viewModelScope.launch(Dispatchers.IO) {
            var handedOffToDialog = false
            try {
                val allParsedCards = mutableMapOf<String, Card>()
                val parsedDecks = mutableListOf<ParsedDeck>()
                val parsedDeckIds = mutableListOf<String>()
                val jsonArray = JSONArray(jsonString)

                for (i in 0 until jsonArray.length()) {
                    yield()
                    val deckObject = jsonArray.getJSONObject(i)
                    val oldDeckId = deckObject.getString("id")
                    parsedDeckIds.add(oldDeckId)

                    val cardIdsForDeck = mutableListOf<String>()
                    if (deckObject.has("cardIds")) {
                        val ids = deckObject.getJSONArray("cardIds")
                        for(j in 0 until ids.length()) cardIdsForDeck.add(ids.getString(j))
                    } else if (deckObject.has("cards")) {
                        val cardsArray = deckObject.getJSONArray("cards")
                        for (j in 0 until cardsArray.length()) {
                            val co = cardsArray.getJSONObject(j)
                            val cid = co.optString("id", UUID.randomUUID().toString())
                            cardIdsForDeck.add(cid)

                            val tagsList = mutableListOf<String>()
                            val tagsArray = co.optJSONArray("tags")
                            if (tagsArray != null) {
                                for (k in 0 until tagsArray.length()) {
                                    tagsList.add(tagsArray.getString(k))
                                }
                            }

                            if (!allParsedCards.containsKey(cid)) {
                                val frontNotesArray = co.optJSONArray("frontNotes")
                                val frontNotesStr = if (frontNotesArray == null) co.optString("frontNotes", "") else null
                                val parsedFrontNotes = parseJsonNotes(frontNotesArray, frontNotesStr)

                                val backNotesArray = co.optJSONArray("backNotes")
                                val backNotesStr = if (backNotesArray == null) co.optString("backNotes", "") else null
                                val parsedBackNotes = parseJsonNotes(backNotesArray, backNotesStr)

                                allParsedCards[cid] =
                                    Card(
                                        id = cid,
                                        front = co.optString("front", ""),
                                        frontRichText = co.optString("frontRichText", "").takeIf { it.isNotBlank() },
                                        back = co.optString("back", ""),
                                        backRichText = co.optString("backRichText", "").takeIf { it.isNotBlank() },
                                        frontNotes = parsedFrontNotes,
                                        backNotes = parsedBackNotes,
                                        difficulty = DifficultySetting.fromInt(co.optInt("difficulty", 1)),
                                        reviewedAt = co.optLong("reviewedAt", 0L).takeIf { it > 0 },
                                        isKnown = co.optBoolean("isKnown", false),
                                        tags = tagsList,
                                        // PARSE NEW FIELDS
                                        createdAt = co.optLong("createdAt", System.currentTimeMillis()),
                                        updatedAt = co.optLong("updatedAt", System.currentTimeMillis()),
                                        isSuspended = co.optBoolean("isSuspended", false),
                                        flag = CardFlag.fromInt(co.optInt("flag", 0))
                                    )
                            }
                        }
                    }

                    val frontTemplatesArray = deckObject.optJSONArray("frontNoteTemplates")
                    val parsedFrontTemplates = if (frontTemplatesArray != null) Gson().fromJson<List<NoteField>>(frontTemplatesArray.toString(), object : TypeToken<List<NoteField>>() {}.type) ?: emptyList() else emptyList()

                    val backTemplatesArray = deckObject.optJSONArray("backNoteTemplates")
                    val parsedBackTemplates = if (backTemplatesArray != null) Gson().fromJson<List<NoteField>>(backTemplatesArray.toString(), object : TypeToken<List<NoteField>>() {}.type) ?: emptyList() else emptyList()

                    // ADDED: Parsing languages with defaults
                    val deck = Deck(
                        id = oldDeckId,
                        name = deckObject.optString("name", "Unnamed Deck"),
                        parentDeckId = deckObject.optString("parentDeckId", "")
                            .takeIf { it.isNotEmpty() },
                        frontNoteTemplates = parsedFrontTemplates,
                        backNoteTemplates = parsedBackTemplates,
                        createdAt = deckObject.optLong("createdAt", System.currentTimeMillis()),
                        updatedAt = deckObject.optLong("updatedAt", System.currentTimeMillis()),
                        averageQuizScore = deckObject.optDouble("averageQuizScore", -1.0).toFloat()
                            .takeIf { it != -1.0f },
                        normalizationType = NormalizationType.fromInt(deckObject.optInt("normalizationType", 0)),
                        deckSortMode = DeckSortMode.fromInt(deckObject.optInt("sortType", 0)),
                        isStarred = deckObject.optBoolean("isStarred", false),
                        cardIds = cardIdsForDeck,
                        frontLanguage = deckObject.optString("frontLanguage", Locale.getDefault().language),
                        backLanguage = deckObject.optString("backLanguage", Locale.getDefault().language),
                        // PARSE NEW FIELDS
                        description = deckObject.optString("description", ""),
                        dailyNewCardLimit = deckObject.optInt("dailyNewCardLimit", 20),
                        dailyReviewLimit = deckObject.optInt("dailyReviewLimit", 200)
                    )
                    parsedDecks.add(
                        ParsedDeck(
                            deck,
                            cardIdsForDeck,
                            oldDeckId
                        )
                    )
                }

                val existingDecksInDb = getLocalDecks().filter { it.id in parsedDeckIds }
                if (existingDecksInDb.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        onOverwriteConfirmationChanged(
                            OverwriteConfirmationData(
                                existingDecksInDb,
                                parsedDecks,
                                allParsedCards
                            )
                        )
                        onProcessingChanged(false)
                        handedOffToDialog = true
                    }
                } else {
                    importParsedData(parsedDecks, allParsedCards, emptyMap())
                }
            } catch (e: Exception) {
                Log.e(TAG, "JSON Parse failed", e)
                withContext(Dispatchers.Main) { onError(e.stackTraceToString()) }
            } finally {
                if (!handedOffToDialog) withContext(Dispatchers.Main) { onProcessingChanged(false) }
            }
        }
    }

    fun cancelImport() {
        onOverwriteConfirmationChanged(null)
    }

    fun proceedWithImport(selectedIdsToOverwrite: List<String>) {
        val confirmationData = getOverwriteConfirmation() ?: return
        onOverwriteConfirmationChanged(null)
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { onProcessingChanged(true) } // Resume processing
            try {
                Log.d(TAG, "Processing overwrite import")
                val existingDecksMap = getLocalDecks().associateBy { it.id }
                val existingCardsMap = getLocalCards().associateBy { it.id }

                // 1. Overwrites
                val overwrites = confirmationData.parsedDecks.filter { it.oldDeckId in selectedIdsToOverwrite }
                overwrites.forEach { parsed ->
                    yield()
                    val imported = parsed.deck
                    val existing = existingDecksMap[imported.id]!!
                    val mergedCardIds = (existing.cardIds + imported.cardIds).distinct()
                    val finalDeck = imported.copy(
                        createdAt = existing.createdAt,
                        updatedAt = max(existing.updatedAt, imported.updatedAt),
                        cardIds = mergedCardIds
                    )

                    saveDeckToFirestore(finalDeck)

                    parsed.cardIds.mapNotNull { confirmationData.allParsedCards[it] }.forEach { importedCard ->
                        val exCard = existingCardsMap[importedCard.id]
                        val finalCard = if(exCard != null) importedCard.copy(reviewedAt = max(exCard.reviewedAt ?: 0L, importedCard.reviewedAt ?: 0L).takeIf { it > 0L }) else importedCard
                        saveCardToFirestore(finalCard)
                    }
                }

                // 2 & 3. New Decks
                val others = confirmationData.parsedDecks.filter { it.oldDeckId !in selectedIdsToOverwrite }
                if (others.isNotEmpty()) {
                    val remapping = mutableMapOf<String, String>()
                    others.forEach { if(existingDecksMap.containsKey(it.oldDeckId)) remapping[it.oldDeckId] = UUID.randomUUID().toString() }
                    importParsedData(others, confirmationData.allParsedCards, remapping)
                }
                Log.d(TAG, "Overwrite import complete")
            } catch (e: Exception) {
                Log.e(TAG, "Overwrite import failed", e)
            } finally {
                withContext(Dispatchers.Main) { onProcessingChanged(false) }
            }
        }
    }

    private suspend fun importParsedData(parsedDecks: List<ParsedDeck>, allParsedCards: Map<String, Card>, oldToNewIdMap: Map<String, String>) {
        val isRemapping = oldToNewIdMap.isNotEmpty()

        Log.d(TAG, "Importing parsed data. Decks: ${parsedDecks.size}, Cards: ${allParsedCards.size}")

        val cardIds = parsedDecks.flatMap { it.cardIds }.toSet()
        val cardIdRemap = if(isRemapping) cardIds.associateWith { UUID.randomUUID().toString() } else emptyMap()

        val cardsToSave = cardIds.mapNotNull { allParsedCards[it] }.map { card ->
            if(isRemapping) card.copy(id = cardIdRemap[card.id]!!) else card
        }

        // Save cards to Room via callback
        cardsToSave.forEach { saveCardToFirestore(it) }

        val finalizedDecks = parsedDecks.map { parsed ->
            val finalId = if(isRemapping) oldToNewIdMap[parsed.oldDeckId] ?: parsed.oldDeckId else parsed.oldDeckId
            val finalParent = if(isRemapping && parsed.deck.parentDeckId != null) oldToNewIdMap[parsed.deck.parentDeckId] ?: parsed.deck.parentDeckId else parsed.deck.parentDeckId
            val finalCardIds = if(isRemapping) parsed.cardIds.map { cardIdRemap[it]!! } else parsed.cardIds

            parsed.deck.copy(id = finalId, parentDeckId = finalParent, cardIds = finalCardIds)
        }

        val decksToSaveMap = finalizedDecks.associateBy { it.id }.toMutableMap()
        val existingDecksMap = getLocalDecks().associateBy { it.id }
        val extraParentsToUpdate = mutableMapOf<String, Deck>()

        finalizedDecks.forEach { deck ->
            if (deck.parentDeckId != null) {
                val parentId = deck.parentDeckId
                if (decksToSaveMap.containsKey(parentId)) {
                    val parent = decksToSaveMap[parentId]!!
                    val mergedIds = (parent.cardIds + deck.cardIds).distinct()
                    decksToSaveMap[parentId] = parent.copy(cardIds = mergedIds)
                }
                else if (existingDecksMap.containsKey(parentId)) {
                    val parent = extraParentsToUpdate[parentId] ?: existingDecksMap[parentId]!!
                    val mergedIds = (parent.cardIds + deck.cardIds).distinct()
                    extraParentsToUpdate[parentId] = parent.copy(cardIds = mergedIds)
                }
            }
        }

        // Save decks to Room via callback
        val allDecksToSave = decksToSaveMap.values + extraParentsToUpdate.values
        allDecksToSave.forEach { saveDeckToFirestore(it) }

        Log.d(TAG, "Import parsed data complete")
    }

    private fun parseCsvNotes(value: String): List<NoteField> {
        if (value.isBlank()) return emptyList()
        return try {
            if (value.trim().startsWith("[")) {
                Gson().fromJson(value, object : TypeToken<List<NoteField>>() {}.type) ?: emptyList()
            } else {
                listOf(NoteField(name = "Note", content = value, type = MediaType.PLAIN_TEXT))
            }
        } catch (e: Exception) {
            listOf(NoteField(name = "Note", content = value, type = MediaType.PLAIN_TEXT))
        }
    }

    private fun importDecksFromCsv(csvString: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val reader = CSVReaderBuilder(StringReader(csvString)).withSkipLines(1).build()
                val records = reader.readAll()
                if (records.isEmpty()) return@launch

                val decksMap = mutableMapOf<String, Deck>()
                val cardsMap = mutableMapOf<String, Card>()
                records.forEach { row ->
                    yield()
                    val dId = row[0]; val dName = row[1]; val pId = row[2].takeIf { it.isNotBlank() }; val star = row[3].toBoolean()
                    val cId = row[4]; val front = row[5]; val back = row[6]
                    val diff = row[9].toIntOrNull() ?: 1; val isKnown = if(row.size > 11) row[11].toBoolean() else false

                    val frontLang = if(row.size > 12) row[12] else Locale.getDefault().language
                    val backLang = if(row.size > 13) row[13] else Locale.getDefault().language
                    val tags = if(row.size > 14) row[14].split(";").filter { it.isNotBlank() } else emptyList()

                    // Parse new fields if available
                    val createdAt = if(row.size > 15) row[15].toLongOrNull() ?: System.currentTimeMillis() else System.currentTimeMillis()
                    val updatedAt = if(row.size > 16) row[16].toLongOrNull() ?: System.currentTimeMillis() else System.currentTimeMillis()
                    val defaultSortOrder = if(row.size > 17) row[17].toLongOrNull() ?: 0L else 0L
                    val isSuspended = if(row.size > 18) row[18].toBoolean() else false
                    val flag = if(row.size > 19) row[19].toIntOrNull() ?: 0 else 0

                    val deck = decksMap.getOrPut(dId) {
                        Deck(
                            id = UUID.randomUUID().toString(),
                            name = dName,
                            parentDeckId = pId,
                            isStarred = star,
                            cardIds = emptyList(),
                            frontLanguage = frontLang,
                            backLanguage = backLang
                        )
                    }

                    val frontRich = if (row.size > 20) row[20].takeIf { it.isNotBlank() } else null
                    val backRich = if (row.size > 21) row[21].takeIf { it.isNotBlank() } else null

                    val card = cardsMap.getOrPut(cId) {
                        Card(
                            id = UUID.randomUUID().toString(),
                            front = front,
                            frontRichText = frontRich,
                            back = back,
                            backRichText = backRich,
                            frontNotes = parseCsvNotes(row[7]),
                            backNotes = parseCsvNotes(row[8]),
                            difficulty = DifficultySetting.fromInt(diff),
                            isKnown = isKnown,
                            tags = tags,
                            createdAt = createdAt,
                            updatedAt = updatedAt,
                            isSuspended = isSuspended,
                            flag = CardFlag.fromInt(flag)
                        )
                    }
                    decksMap[dId] = deck.copy(cardIds = deck.cardIds + card.id)
                }

                cardsMap.values.forEach { saveCardToFirestore(it) }
                val finalDecksToSave = decksMap.values.map { deck -> if (deck.parentDeckId != null && decksMap.containsKey(deck.parentDeckId)) deck.copy(parentDeckId = decksMap[deck.parentDeckId]?.id) else deck }
                val decksToSaveMap = finalDecksToSave.associateBy { it.id }.toMutableMap()
                val extraParentsToUpdate = mutableMapOf<String, Deck>()
                val existingDecksMap = getLocalDecks().associateBy { it.id }
                finalDecksToSave.forEach { deck -> if (deck.parentDeckId != null) { val parentId = deck.parentDeckId; if (decksToSaveMap.containsKey(parentId)) { val parent = decksToSaveMap[parentId]!!; decksToSaveMap[parentId] = parent.copy(cardIds = (parent.cardIds + deck.cardIds).distinct()) } else if (existingDecksMap.containsKey(parentId)) { val parent = extraParentsToUpdate[parentId] ?: existingDecksMap[parentId]!!; extraParentsToUpdate[parentId] = parent.copy(cardIds = (parent.cardIds + deck.cardIds).distinct()) } } }
                (decksToSaveMap.values + extraParentsToUpdate.values).forEach { yield(); saveDeckToFirestore(it) }

            } catch (e: Exception) {
                Log.e(TAG, "CSV Import failed", e)
                withContext(Dispatchers.Main) { onError(e.stackTraceToString()) }
            } finally {
                withContext(Dispatchers.Main) { onProcessingChanged(false) }
            }
        }
    }

    suspend fun analyzeAnkiPackage(context: Context, sourceUri: Uri): Pair<String, List<Pair<String, net.ericclark.studiare.data.MediaType>>> {
        return withContext(Dispatchers.IO) {
            var stagingDir: File? = null
            var ankiDb: SQLiteDatabase? = null

            // Map to store Model ID -> List of Field Names
            val modelFieldMap = mutableMapOf<Long, List<String>>()
            // Map to store Field Name -> Detected MediaType
            val fieldTypes = mutableMapOf<String, net.ericclark.studiare.data.MediaType>()
            var defaultDeckName = "Imported Deck"

            try {
                stagingDir = File(context.cacheDir, "anki_analyze_${UUID.randomUUID()}")
                if (!stagingDir.exists()) stagingDir.mkdirs()

                // Unzip only the SQLite database to save time
                context.contentResolver.openInputStream(sourceUri)?.use { input ->
                    java.util.zip.ZipInputStream(input).use { zis ->
                        var entry = zis.nextEntry
                        while (entry != null) {
                            if (entry.name == "collection.anki21" || entry.name == "collection.anki2") {
                                File(stagingDir, "collection.anki21").outputStream().use { zis.copyTo(it) }
                                break
                            }
                            entry = zis.nextEntry
                        }
                    }
                }

                val dbFile = File(stagingDir, "collection.anki21")
                if (dbFile.exists()) {
                    ankiDb = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)

                    // --- NEW: Extract Deck Name ---
                    ankiDb.rawQuery("SELECT decks FROM col LIMIT 1", null).use { cursor ->
                        if (cursor.moveToFirst()) {
                            val decksObj = org.json.JSONObject(cursor.getString(0))
                            for (key in decksObj.keys()) {
                                val deck = decksObj.getJSONObject(key)
                                val name = deck.optString("name", "")
                                // Grab the first non-default deck name, splitting by Anki's subdeck separator
                                if (name.isNotBlank() && name != "Default") {
                                    defaultDeckName = name.split("::").last()
                                    break
                                } else if (name == "Default" && defaultDeckName == "Imported Deck") {
                                    defaultDeckName = name
                                }
                            }
                        }
                    }

                    // 1. Get field names from models
                    ankiDb.rawQuery("SELECT models FROM col LIMIT 1", null).use { cursor ->
                        if (cursor.moveToFirst()) {
                            val modelsObj = org.json.JSONObject(cursor.getString(0))
                            modelsObj.keys().forEach { modelId ->
                                val flds = modelsObj.getJSONObject(modelId).getJSONArray("flds")
                                val names = mutableListOf<String>()
                                for (i in 0 until flds.length()) {
                                    val fieldName = flds.getJSONObject(i).getString("name")
                                    names.add(fieldName)
                                    // Initialize all fields as plain text by default
                                    if (!fieldTypes.containsKey(fieldName)) {
                                        fieldTypes[fieldName] = net.ericclark.studiare.data.MediaType.PLAIN_TEXT
                                    }
                                }
                                modelFieldMap[modelId.toLong()] = names
                            }
                        }
                    }

                    // 2. Scan notes to detect the actual MediaType for each field
                    ankiDb.rawQuery("SELECT mid, flds FROM notes", null).use { notesCursor ->
                        while (notesCursor.moveToNext()) {
                            val mid = notesCursor.getLong(0)
                            val fldsArray = notesCursor.getString(1).split("\u001F")
                            val fieldNamesForModel = modelFieldMap[mid] ?: continue

                            for (i in 0 until minOf(fldsArray.size, fieldNamesForModel.size)) {
                                val fieldName = fieldNamesForModel[i]
                                val content = fldsArray[i].trim()
                                if (content.isEmpty()) continue

                                val currentType = fieldTypes[fieldName] ?: net.ericclark.studiare.data.MediaType.PLAIN_TEXT

                                // Detect type based on content
                                val detectedType = when {
                                    // 1. Raw Anki Audio/Video tags (e.g. [sound:file.mp3])
                                    Regex("^\\[sound:(.*?)\\]$", RegexOption.IGNORE_CASE).matches(content) -> net.ericclark.studiare.data.MediaType.AUDIO

                                    // 2. Pure Image tags (Anki stores images natively as HTML <img>)
                                    Regex("^<img[^>]+src=[\"']([^\"']+)[\"'][^>]*>$", RegexOption.IGNORE_CASE).matches(content) -> net.ericclark.studiare.data.MediaType.IMAGE

                                    // 3. Standard HTML Media tags (if already converted or imported differently)
                                    Regex("^<audio[^>]+src=[\"']([^\"']+)[\"'][^>]*></audio>$", RegexOption.IGNORE_CASE).matches(content) -> net.ericclark.studiare.data.MediaType.AUDIO
                                    Regex("^<video[^>]+src=[\"']([^\"']+)[\"'][^>]*></video>$", RegexOption.IGNORE_CASE).matches(content) -> net.ericclark.studiare.data.MediaType.VIDEO

                                    // 4. General HTML Content
                                    Regex("<[^>]*>").containsMatchIn(content) -> net.ericclark.studiare.data.MediaType.HTML

                                    // 5. Raw Web Links
                                    android.util.Patterns.WEB_URL.matcher(content).matches() -> net.ericclark.studiare.data.MediaType.WEB_LINK

                                    // 6. Fallback
                                    else -> net.ericclark.studiare.data.MediaType.PLAIN_TEXT
                                }

                                // Upgrade the type if we find a richer format than what is currently stored
                                if (detectedType != net.ericclark.studiare.data.MediaType.PLAIN_TEXT) {
                                    // Audio/Image/Video override HTML/Text
                                    if (detectedType == net.ericclark.studiare.data.MediaType.AUDIO || detectedType == net.ericclark.studiare.data.MediaType.IMAGE || detectedType == net.ericclark.studiare.data.MediaType.VIDEO) {
                                        fieldTypes[fieldName] = detectedType
                                    }
                                    // HTML/WebLink override Plain Text
                                    else if ((detectedType == net.ericclark.studiare.data.MediaType.HTML || detectedType == net.ericclark.studiare.data.MediaType.WEB_LINK) && currentType == net.ericclark.studiare.data.MediaType.PLAIN_TEXT) {
                                        fieldTypes[fieldName] = detectedType
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to analyze Anki package", e)
            } finally {
                ankiDb?.close()
                stagingDir?.deleteRecursively()
            }

            //Return both the deck name and the list of fields ---
            Pair(defaultDeckName, fieldTypes.map { Pair(it.key, it.value) })
        }
    }

    suspend fun importFromAnkiPackage(
        context: Context,
        ankiPackageUri: Uri,
        fieldMappings: List<net.ericclark.studiare.screens.AnkiMappingConfig>?
    ) {
        viewModelScope.launch { preferenceManager.updateLastImportTimestamp() }
        onProcessingChanged(true)

        withContext(Dispatchers.IO) {
            var stagingDir: File? = null
            var ankiDb: SQLiteDatabase? = null
            try {
                Log.d(TAG, "Starting Anki import from URI: $ankiPackageUri")

                // 1. Setup Staging Directory
                stagingDir = File(context.cacheDir, "anki_staging_${UUID.randomUUID()}")
                if (!stagingDir.exists()) stagingDir.mkdirs()



                // 2. Unzip the Archive
                context.contentResolver.openInputStream(ankiPackageUri)?.use { inputStream ->
                    ZipInputStream(inputStream).use { zis ->
                        var entry = zis.nextEntry
                        while (entry != null) {
                            val outFile = File(stagingDir, entry.name)
                            FileOutputStream(outFile).use { fos ->
                                zis.copyTo(fos)
                            }
                            entry = zis.nextEntry
                        }
                    }
                }

                // 3. Locate and Open the Database
                // Modern Anki uses collection.anki21, older uses collection.anki2
                val dbFile = File(stagingDir, "collection.anki21").takeIf { it.exists() }
                    ?: File(stagingDir, "collection.anki2")

                if (!dbFile.exists()) {
                    throw Exception("Invalid Anki package: collection database not found.")
                }

                ankiDb = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)

                // 4. Parse Media Mapping
                val mediaFile = File(stagingDir, "media")
                val mediaMap = mutableMapOf<String, String>()
                if (mediaFile.exists()) {
                    val mediaJson = JSONObject(mediaFile.readText())
                    mediaJson.keys().forEach { key ->
                        mediaMap[key] = mediaJson.getString(key)
                    }
                }

                // 5. Query and Flatten the Database
                parseAnkiDatabase(context, ankiDb, mediaMap, stagingDir, fieldMappings)

            } catch (e: Exception) {
                Log.e(TAG, "Anki Import failed", e)
                withContext(Dispatchers.Main) { onError(e.message ?: "Anki import failed") }
            } finally {
                ankiDb?.close()
                stagingDir?.deleteRecursively() // 6. Cleanup
                withContext(Dispatchers.Main) { onProcessingChanged(false) }
            }
        }
    }

    private suspend fun parseAnkiDatabase(
        context: Context,
        ankiDb: SQLiteDatabase,
        mediaMap: Map<String, String>,
        stagingDir: File,
        fieldMappings: List<net.ericclark.studiare.screens.AnkiMappingConfig>?
    ) {
        // --- NEW: Get Deck Metadata ---
        val ankiDeckNames = extractAnkiDecks(ankiDb)

        // Build a map of Model ID -> List of Field Names so we know the order
        val modelFieldMap = mutableMapOf<Long, List<String>>()
        ankiDb.rawQuery("SELECT models FROM col LIMIT 1", null).use { cursor ->
            if (cursor.moveToFirst()) {
                val modelsObj = org.json.JSONObject(cursor.getString(0))
                modelsObj.keys().forEach { modelId ->
                    val flds = modelsObj.getJSONObject(modelId).getJSONArray("flds")
                    val names = mutableListOf<String>()
                    for (i in 0 until flds.length()) names.add(flds.getJSONObject(i).getString("name"))
                    modelFieldMap[modelId.toLong()] = names
                }
            }
        }

        val query = """
            SELECT c.id AS cardId, c.did AS deckId, n.flds AS fields, n.tags AS tags, n.mid AS modelId
            FROM cards c
            JOIN notes n ON c.nid = n.id
            GROUP BY n.id 
        """

        val cursor = ankiDb.rawQuery(query, null)
        val allParsedCards = mutableMapOf<String, Card>()
        val parsedDecksMap = mutableMapOf<String, MutableList<String>>()

        val resolvedMediaCache = mutableMapOf<String, String>() // Media Cache

        // --- Helper to parse precise media types ---
        fun parseNoteFieldContent(html: String): Pair<MediaType, String> {
            val trimmed = html.trim()
            if (trimmed.isEmpty()) return MediaType.PLAIN_TEXT to ""

            // Check for pure Audio
            val audioMatch = Regex("^<audio[^>]+src=[\"']([^\"']+)[\"'][^>]*></audio>$", RegexOption.IGNORE_CASE).matchEntire(trimmed)
            if (audioMatch != null) return MediaType.AUDIO to audioMatch.groupValues[1]

            // Check for pure Image
            val imageMatch = Regex("^<img[^>]+src=[\"']([^\"']+)[\"'][^>]*>$", RegexOption.IGNORE_CASE).matchEntire(trimmed)
            if (imageMatch != null) return MediaType.IMAGE to imageMatch.groupValues[1]

            // Check for pure Video
            val videoMatch = Regex("^<video[^>]+src=[\"']([^\"']+)[\"'][^>]*></video>$", RegexOption.IGNORE_CASE).matchEntire(trimmed)
            if (videoMatch != null) return MediaType.VIDEO to videoMatch.groupValues[1]

            // Check for Plain Text
            val hasHtmlTags = Regex("<[^>]*>").containsMatchIn(trimmed)
            if (!hasHtmlTags) {
                if (android.util.Patterns.WEB_URL.matcher(trimmed).matches()) {
                    return MediaType.WEB_LINK to trimmed
                }
                return MediaType.PLAIN_TEXT to trimmed
            }

            // Default to HTML
            return MediaType.HTML to trimmed
        }

        while (cursor.moveToNext()) {
            yield()
            val deckId = cursor.getLong(cursor.getColumnIndexOrThrow("deckId")).toString()
            val fieldsRaw = cursor.getString(cursor.getColumnIndexOrThrow("fields"))
            val tagsRaw = cursor.getString(cursor.getColumnIndexOrThrow("tags"))
            val modelId = cursor.getLong(cursor.getColumnIndexOrThrow("modelId"))

            val fieldsArray = fieldsRaw.split("\u001F")
            val fieldNames = modelFieldMap[modelId] ?: emptyList()

            // If mappings are missing, gracefully fall back to creating 1 deck using Anki's names
            val configsToProcess = if (fieldMappings.isNullOrEmpty()) {
                listOf(net.ericclark.studiare.screens.AnkiMappingConfig(
                    deckName = ankiDeckNames[deckId] ?: "Anki Import",
                    mapping = emptyMap()
                ))
            } else {
                fieldMappings
            }

            // Iterate over every deck the user configured in the dialog
            for (config in configsToProcess) {
                var frontHtml = ""
                var backHtml = ""
                val frontNotes = mutableListOf<net.ericclark.studiare.data.NoteField>()
                val backNotes = mutableListOf<net.ericclark.studiare.data.NoteField>()

                if (config.mapping.isEmpty()) {
                    frontHtml = fieldsArray.getOrNull(0) ?: ""
                    backHtml = fieldsArray.getOrNull(1) ?: ""
                    if (fieldsArray.size > 2) {
                        val extraContent = fieldsArray.drop(2).joinToString("<br><br>")
                        if (extraContent.isNotBlank()) {
                            backNotes.add(net.ericclark.studiare.data.NoteField(name = "Extra Anki Fields", content = extraContent, type = net.ericclark.studiare.data.MediaType.HTML))
                        }
                    }
                } else {
                    fun buildSide(dest: net.ericclark.studiare.screens.MapperDestination): String {
                        val builder = StringBuilder()
                        config.mapping[dest]?.forEach { item ->
                            if (item.isCustomText) {
                                builder.append(item.text).append("<br>")
                            } else {
                                val fieldIndex = fieldNames.indexOf(item.text)
                                if (fieldIndex != -1) builder.append(fieldsArray.getOrNull(fieldIndex) ?: "").append("<br>")
                            }
                        }
                        return builder.toString().removeSuffix("<br>")
                    }

                    frontHtml = buildSide(net.ericclark.studiare.screens.MapperDestination.FRONT)
                    backHtml = buildSide(net.ericclark.studiare.screens.MapperDestination.BACK)

                    config.mapping[net.ericclark.studiare.screens.MapperDestination.FRONT_NOTES]?.forEach { item ->
                        if (!item.isCustomText) {
                            val fieldIndex = fieldNames.indexOf(item.text)
                            if (fieldIndex != -1) {
                                val content = fieldsArray.getOrNull(fieldIndex) ?: ""
                                if (content.isNotBlank()) frontNotes.add(net.ericclark.studiare.data.NoteField(name = item.text, content = content, type = item.type))
                            }
                        }
                    }

                    config.mapping[net.ericclark.studiare.screens.MapperDestination.BACK_NOTES]?.forEach { item ->
                        if (!item.isCustomText) {
                            val fieldIndex = fieldNames.indexOf(item.text)
                            if (fieldIndex != -1) {
                                val content = fieldsArray.getOrNull(fieldIndex) ?: ""
                                if (content.isNotBlank()) backNotes.add(net.ericclark.studiare.data.NoteField(name = item.text, content = content, type = item.type))
                            }
                        }
                    }
                }

                // If this specific config resulted in completely blank outputs (e.g., fields didn't match this note type), skip creating a blank card for this deck
                if (frontHtml.isBlank() && backHtml.isBlank() && frontNotes.isEmpty() && backNotes.isEmpty()) {
                    continue
                }

                // --- Run constructed HTML through the Media Resolver ---
                frontHtml = resolveAnkiMedia(context, frontHtml, mediaMap, stagingDir, resolvedMediaCache)
                backHtml = resolveAnkiMedia(context, backHtml, mediaMap, stagingDir, resolvedMediaCache)

                val resolvedFrontNotes = frontNotes.map { note ->
                    val resolvedHtml = resolveAnkiMedia(context, note.content, mediaMap, stagingDir, resolvedMediaCache)
                    val (parsedType, parsedContent) = parseNoteFieldContent(resolvedHtml)
                    val finalType = if (config.mapping.isNotEmpty()) note.type else parsedType
                    note.copy(content = parsedContent, type = finalType)
                }

                val resolvedBackNotes = backNotes.map { note ->
                    val resolvedHtml = resolveAnkiMedia(context, note.content, mediaMap, stagingDir, resolvedMediaCache)
                    val (parsedType, parsedContent) = parseNoteFieldContent(resolvedHtml)
                    val finalType = if (config.mapping.isNotEmpty()) note.type else parsedType
                    note.copy(content = parsedContent, type = finalType)
                }

                val tagsList = tagsRaw.trim().split(" ").filter { it.isNotBlank() }
                val newCardId = UUID.randomUUID().toString()

                val hasFrontTags = Regex("<[^>]*>").containsMatchIn(frontHtml)
                val hasBackTags = Regex("<[^>]*>").containsMatchIn(backHtml)

                val card = Card(
                    id = newCardId,
                    front = if (hasFrontTags) frontHtml.replace(Regex("<[^>]*>"), "").replace("&nbsp;", " ").trim() else frontHtml.trim(),
                    frontRichText = if (hasFrontTags && frontHtml.isNotBlank()) frontHtml else null,
                    back = if (hasBackTags) backHtml.replace(Regex("<[^>]*>"), "").replace("&nbsp;", " ").trim() else backHtml.trim(),
                    backRichText = if (hasBackTags && backHtml.isNotBlank()) backHtml else null,
                    frontNotes = resolvedFrontNotes,
                    backNotes = resolvedBackNotes,
                    difficulty = net.ericclark.studiare.data.DifficultySetting.ONE,
                    isKnown = false,
                    tags = tagsList,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    isSuspended = false,
                    flag = net.ericclark.studiare.data.CardFlag.NONE
                )

                parsedDecksMap.getOrPut(config.deckName) { mutableListOf() }.add(newCardId)
                allParsedCards[newCardId] = card
            }
        }
        cursor.close()

        // --- NEW: Map to ParsedDecks and hand off to the Import Pipeline ---
        val parsedDecks = parsedDecksMap.map { (deckKey, cardIds) ->
            // FIX: If the key isn't found in Anki's original map, it means it's the custom name the user typed!
            val deckName = ankiDeckNames[deckKey]?.replace("::", " - ") ?: deckKey
            val deck = Deck(
                id = deckKey, // We use the Anki ID/Name temporarily for overwrite checking
                name = deckName,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                cardIds = cardIds
            )
            ParsedDeck(deck, cardIds, deckKey)
        }

        // Hand off to the exact same pipeline used by JSON imports
        val existingDecksInDb = getLocalDecks().filter { dbDeck -> parsedDecks.any { it.oldDeckId == dbDeck.id } }
        if (existingDecksInDb.isNotEmpty()) {
            withContext(Dispatchers.Main) {
                onOverwriteConfirmationChanged(OverwriteConfirmationData(existingDecksInDb, parsedDecks, allParsedCards))
            }
        } else {
            importParsedData(parsedDecks, allParsedCards, emptyMap())
        }

        Log.d(TAG, "Flattened and imported ${allParsedCards.size} cards from Anki database.")
    }

    private fun extractAnkiDecks(ankiDb: SQLiteDatabase): Map<String, String> {
        val ankiDecks = mutableMapOf<String, String>()
        val cursor = ankiDb.rawQuery("SELECT decks FROM col LIMIT 1", null)

        if (cursor.moveToFirst()) {
            try {
                val decksJsonStr = cursor.getString(0)
                val decksJson = JSONObject(decksJsonStr)
                decksJson.keys().forEach { key ->
                    val deckObj = decksJson.getJSONObject(key)
                    ankiDecks[key] = deckObj.getString("name")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse Anki deck metadata", e)
            }
        }
        cursor.close()
        return ankiDecks
    }

    private suspend fun resolveAnkiMedia(
        context: Context,
        rawHtml: String,
        mediaMap: Map<String, String>,
        stagingDir: File,
        resolvedCache: MutableMap<String, String>
    ): String {
        var resolvedHtml = rawHtml

        // Find the numeric key for a given original filename
        fun getMediaKey(filename: String): String? {
            return mediaMap.entries.find { it.value == filename }?.key
        }

        // Helper to copy file to app's internal media directory and return absolute path
        fun copyMediaToLocal(filename: String): String? {
            if (resolvedCache.containsKey(filename)) return resolvedCache[filename]

            val key = getMediaKey(filename) ?: return null
            val sourceFile = File(stagingDir, key)
            if (!sourceFile.exists()) return null

            val mediaDir = File(context.filesDir, "media")
            if (!mediaDir.exists()) mediaDir.mkdirs()

            val destFile = File(mediaDir, filename)
            if (!destFile.exists()) {
                sourceFile.copyTo(destFile)
            }

            val absolutePath = destFile.absolutePath
            resolvedCache[filename] = absolutePath
            return absolutePath
        }

        // 1. Resolve Audio Tags: [sound:filename.mp3] -> <audio src="...">
        val soundRegex = Regex("\\[sound:(.*?)\\]")
        resolvedHtml = soundRegex.replace(resolvedHtml) { matchResult ->
            val filename = matchResult.groupValues[1]
            val localPath = copyMediaToLocal(filename)
            if (localPath != null) "<audio src=\"$localPath\" controls></audio>" else matchResult.value
        }

        // 2. Resolve Image Tags: <img src="filename.jpg"> -> <img src="file:///...">
        val imgRegex = Regex("<img[^>]+src=[\"'](.*?)[\"'][^>]*>")
        resolvedHtml = imgRegex.replace(resolvedHtml) { matchResult ->
            val filename = matchResult.groupValues[1]
            val localPath = copyMediaToLocal(filename)
            if (localPath != null) matchResult.value.replace(filename, localPath) else matchResult.value
        }

        return resolvedHtml
    }

    suspend fun exportToAnkiPackage(context: Context, decksToExport: List<DeckWithCards>, destinationUri: Uri) {
        viewModelScope.launch { preferenceManager.updateLastExportTimestamp() }
        onProcessingChanged(true)

        withContext(Dispatchers.IO) {
            var stagingDir: File? = null
            var ankiDb: SQLiteDatabase? = null
            try {
                Log.d(TAG, "Starting Anki export to URI: $destinationUri")

                // 1. Setup Staging Directory
                stagingDir = File(context.cacheDir, "anki_export_${UUID.randomUUID()}")
                if (!stagingDir.exists()) stagingDir.mkdirs()

                // Sanitize in-memory copy to guarantee valid Anki schemas
                val sanitizedDecks = decksToExport.map { deckWithCards ->
                    val sanitizedCards = deckWithCards.cards.map { card ->
                        var fieldCounter = 1
                        val usedNames = mutableSetOf<String>()

                        fun sanitizeNotes(notes: List<NoteField>): List<NoteField> {
                            return notes.map { note ->
                                var finalName = note.name.trim()
                                if (finalName.isBlank()) finalName = "Field $fieldCounter"
                                while (usedNames.contains(finalName) || finalName == "Front" || finalName == "Back") {
                                    fieldCounter++
                                    finalName = if (note.name.trim().isBlank()) "Field $fieldCounter" else "${note.name.trim()} $fieldCounter"
                                }
                                usedNames.add(finalName)
                                note.copy(name = finalName)
                            }
                        }

                        card.copy(
                            frontNotes = sanitizeNotes(card.frontNotes),
                            backNotes = sanitizeNotes(card.backNotes)
                        )
                    }
                    deckWithCards.copy(cards = sanitizedCards)
                }

                // 2. Media Parsing Strategy
                val mediaMap = mutableMapOf<String, String>()
                var mediaCounter = 0

                fun processHtmlForExport(html: String?): String {
                    if (html.isNullOrBlank()) return ""
                    var processed = html

                    val srcRegex = Regex("src=[\"'](.*?)[\"']")
                    processed = srcRegex.replace(processed) { matchResult ->
                        val src = matchResult.groupValues[1]
                        if (src.startsWith("http")) return@replace matchResult.value // Ignore web URLs

                        val numericKey = mediaCounter.toString()
                        val ext = src.substringAfterLast('.', "jpg").substringBefore('?')
                        val ankiFilename = "media_$numericKey.$ext"
                        mediaCounter++

                        mediaMap[numericKey] = ankiFilename

                        try {
                            val inputStream = if (src.startsWith("/")) {
                                File(src).inputStream()
                            } else {
                                context.contentResolver.openInputStream(Uri.parse(src))
                            }
                            inputStream?.use { input ->
                                File(stagingDir, numericKey).outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }
                        } catch(e: Exception) { Log.e(TAG, "Failed to copy media for export: $src", e) }

                        "src=\"$ankiFilename\""
                    }

                    processed = processed.replace(Regex("<audio[^>]*src=[\"'](.*?)[\"'][^>]*>.*?</audio>", RegexOption.IGNORE_CASE)) { "[sound:${it.groupValues[1]}]" }
                    processed = processed.replace(Regex("<video[^>]*src=[\"'](.*?)[\"'][^>]*>.*?</video>", RegexOption.IGNORE_CASE)) { "[sound:${it.groupValues[1]}]" }
                    return processed
                }

                fun formatNoteContent(note: NoteField): String {
                    return when (note.type) {
                        MediaType.IMAGE -> "<img src=\"${note.content}\">"
                        MediaType.AUDIO -> "<audio src=\"${note.content}\"></audio>"
                        MediaType.VIDEO -> "<video src=\"${note.content}\"></video>"
                        else -> note.content.replace("\n", "<br>")
                    }
                }

                // 3. Build the Database Schema
                val dbFile = File(stagingDir, "collection.anki21")
                ankiDb = SQLiteDatabase.openOrCreateDatabase(dbFile, null)

                ankiDb.execSQL("CREATE TABLE col (id integer primary key, crt integer not null, mod integer not null, scm integer not null, ver integer not null, dty integer not null, usn integer not null, ls integer not null, conf text not null, models text not null, decks text not null, dconf text not null, tags text not null);")
                ankiDb.execSQL("CREATE TABLE notes (id integer primary key, guid text not null, mid integer not null, mod integer not null, usn integer not null, tags text not null, flds text not null, sfld integer not null, csum integer not null, flags integer not null, data text not null);")
                ankiDb.execSQL("CREATE TABLE cards (id integer primary key, nid integer not null, did integer not null, ord integer not null, mod integer not null, usn integer not null, type integer not null, queue integer not null, due integer not null, ivl integer not null, factor integer not null, reps integer not null, lapses integer not null, \"left\" integer not null, odue integer not null, odid integer not null, flags integer not null, data text not null);")
                ankiDb.execSQL("CREATE TABLE graves (usn integer not null, oid integer not null, type integer not null);")
                ankiDb.execSQL("CREATE TABLE revlog (id integer primary key, cid integer not null, usn integer not null, ease integer not null, ivl integer not null, lastIvl integer not null, factor integer not null, time integer not null, type integer not null);")

                // 4. Generate Dynamic Model based on Note Fields
                val currentTime = System.currentTimeMillis() / 1000
                val defaultModelId = 1342697561419L

                val uniqueFrontNotes = mutableSetOf<String>()
                val uniqueBackNotes = mutableSetOf<String>()

                sanitizedDecks.forEach { deckWithCards ->
                    deckWithCards.cards.forEach { card ->
                        card.frontNotes.forEach { uniqueFrontNotes.add(it.name) }
                        card.backNotes.forEach { uniqueBackNotes.add(it.name) }
                    }
                }

                // Prevent collisions with Anki's required core fields
                uniqueFrontNotes.remove("Front")
                uniqueFrontNotes.remove("Back")
                uniqueBackNotes.remove("Front")
                uniqueBackNotes.remove("Back")

                val finalFrontFields = uniqueFrontNotes.toList()
                val finalBackFields = uniqueBackNotes.map { name ->
                    if (uniqueFrontNotes.contains(name)) "$name (Back)" else name // Prevent cross-side name collisions
                }

                val fldsJsonArray = JSONArray()
                fun addFieldJson(name: String, ord: Int) {
                    val fObj = JSONObject()
                    fObj.put("name", name)
                    fObj.put("ord", ord)
                    fObj.put("sticky", false)
                    fObj.put("rtl", false)
                    fObj.put("font", "Arial")
                    fObj.put("size", 20)
                    fldsJsonArray.put(fObj)
                }

                var ordCounter = 0
                addFieldJson("Front", ordCounter++)
                addFieldJson("Back", ordCounter++)

                // Build templates dynamically using Anki conditional tags
                val qfmtBuilder = StringBuilder("{{Front}}")
                finalFrontFields.forEach { name ->
                    addFieldJson(name, ordCounter++)
                    qfmtBuilder.append("\n{{#$name}}<br><br><b>$name:</b><br>{{$name}}{{/$name}}")
                }

                val afmtBuilder = StringBuilder("{{FrontSide}}\n\n<hr id=answer>\n\n{{Back}}")
                finalBackFields.forEach { name ->
                    addFieldJson(name, ordCounter++)
                    afmtBuilder.append("\n{{#$name}}<br><br><b>$name:</b><br>{{$name}}{{/$name}}")
                }

                val tmplObj = JSONObject()
                tmplObj.put("name", "Card 1")
                tmplObj.put("ord", 0)
                tmplObj.put("qfmt", qfmtBuilder.toString())
                tmplObj.put("afmt", afmtBuilder.toString())
                tmplObj.put("bqfmt", "")
                tmplObj.put("bafmt", "")
                tmplObj.put("did", JSONObject.NULL)

                val modelObj = JSONObject()
                modelObj.put("id", defaultModelId)
                modelObj.put("name", "Studiare Custom")
                modelObj.put("type", 0)
                modelObj.put("mod", currentTime)
                modelObj.put("usn", -1)
                modelObj.put("sortf", 0)
                modelObj.put("did", JSONObject.NULL)
                modelObj.put("tmpls", JSONArray().put(tmplObj))
                modelObj.put("flds", fldsJsonArray)
                modelObj.put("css", ".card { font-family: arial; font-size: 20px; text-align: center; color: black; background-color: white; }")
                modelObj.put("req", JSONArray().put(JSONArray().put(0).put("all").put(JSONArray().put(0))))
                modelObj.put("tags", JSONArray())

                val modelsJson = JSONObject().put(defaultModelId.toString(), modelObj).toString()

                val decksJsonObj = JSONObject()
                decksJsonObj.put("1", JSONObject("""{"id": 1, "mod": $currentTime, "name": "Default", "usn": -1, "lrnToday": [0, 0], "revToday": [0, 0], "newToday": [0, 0], "timeToday": [0, 0], "collapsed": false, "browserCollapsed": false, "desc": "", "dyn": 0, "conf": 1}"""))

                var currentAnkiDeckId = 1L
                val deckIdMap = mutableMapOf<String, Long>()

                sanitizedDecks.forEach { deckWithCards ->
                    currentAnkiDeckId++
                    val did = currentAnkiDeckId
                    deckIdMap[deckWithCards.deck.id] = did
                    val safeName = deckWithCards.deck.name.replace("\"", "\\\"")
                    decksJsonObj.put(did.toString(), JSONObject("""{"id": $did, "mod": $currentTime, "name": "$safeName", "usn": -1, "lrnToday": [0, 0], "revToday": [0, 0], "newToday": [0, 0], "timeToday": [0, 0], "collapsed": false, "browserCollapsed": false, "desc": "", "dyn": 0, "conf": 1}"""))
                }

                val dconfJson = """{"1": {"id": 1, "mod": 0, "name": "Default", "usn": 0, "maxTaken": 60, "autoplay": true, "timer": 0, "replayq": true, "new": {"delays": [1, 10], "ints": [1, 4, 7], "initialFactor": 2500, "bury": true, "order": 1, "perDay": 20}, "rev": {"perDay": 200, "ease4": 1.3, "ivlFct": 1.0, "maxIvl": 36500, "bury": true, "minSpace": 1}, "lapse": {"delays": [10], "mult": 0.0, "minInt": 1, "leechFails": 8, "leechAction": 0}, "dyn": false}}"""

                ankiDb.execSQL("INSERT INTO col (id, crt, mod, scm, ver, dty, usn, ls, conf, models, decks, dconf, tags) VALUES (1, $currentTime, $currentTime, $currentTime, 11, 0, 0, 0, '{}', ?, ?, ?, '{}')", arrayOf(modelsJson, decksJsonObj.toString(), dconfJson))

                // 5. Hydrate Cards & Notes
                var noteIdCounter = System.currentTimeMillis()

                sanitizedDecks.forEach { deckWithCards ->
                    val did = deckIdMap[deckWithCards.deck.id] ?: 1L
                    deckWithCards.cards.forEach { card ->

                        // Base fields
                        val frontHtml = processHtmlForExport(card.frontRichText ?: card.front)
                        val backHtml = processHtmlForExport(card.backRichText ?: card.back)

                        val fieldValues = mutableListOf<String>()
                        fieldValues.add(frontHtml)
                        fieldValues.add(backHtml)

                        // Map card's front notes into correct Anki columns
                        val cardFrontNotesMap = card.frontNotes.associateBy { it.name }
                        finalFrontFields.forEach { fieldName ->
                            val note = cardFrontNotesMap[fieldName]
                            if (note != null) {
                                val contentHtml = processHtmlForExport(formatNoteContent(note))
                                fieldValues.add(contentHtml)
                            } else {
                                fieldValues.add("")
                            }
                        }

                        // Map card's back notes into correct Anki columns
                        val cardBackNotesMap = card.backNotes.associateBy {
                            if (uniqueFrontNotes.contains(it.name)) "${it.name} (Back)" else it.name
                        }
                        finalBackFields.forEach { fieldName ->
                            val note = cardBackNotesMap[fieldName]
                            if (note != null) {
                                val contentHtml = processHtmlForExport(formatNoteContent(note))
                                fieldValues.add(contentHtml)
                            } else {
                                fieldValues.add("")
                            }
                        }

                        // Anki requires fields to be separated by the Unit Separator char
                        val flds = fieldValues.joinToString("\u001F")
                        val tags = card.tags.joinToString(" ")
                        val noteId = noteIdCounter++
                        val cardId = noteIdCounter++
                        val guid = UUID.randomUUID().toString().substring(0, 10)

                        ankiDb.execSQL("INSERT INTO notes (id, guid, mid, mod, usn, tags, flds, sfld, csum, flags, data) VALUES (?, ?, ?, ?, -1, ?, ?, ?, 0, 0, '')",
                            arrayOf(noteId, guid, defaultModelId, currentTime, tags, flds, frontHtml))

                        ankiDb.execSQL("INSERT INTO cards (id, nid, did, ord, mod, usn, type, queue, due, ivl, factor, reps, lapses, \"left\", odue, odid, flags, data) VALUES (?, ?, ?, 0, ?, -1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, '')",
                            arrayOf(cardId, noteId, did, currentTime))
                    }
                }
                ankiDb.close()

                // 6. Generate Media Mapping JSON
                val mediaJsonObj = JSONObject()
                mediaMap.forEach { (numericKey, filename) ->
                    mediaJsonObj.put(numericKey, filename)
                }
                File(stagingDir, "media").writeText(mediaJsonObj.toString())

                // 7. Zip Output Archive
                context.contentResolver.openOutputStream(destinationUri)?.use { outStream ->
                    java.util.zip.ZipOutputStream(outStream).use { zos ->
                        stagingDir.listFiles()?.forEach { file ->
                            zos.putNextEntry(java.util.zip.ZipEntry(file.name))
                            file.inputStream().use { it.copyTo(zos) }
                            zos.closeEntry()
                        }
                    }
                }

                Log.d(TAG, "Anki export completed successfully.")
            } catch (e: Exception) {
                Log.e(TAG, "Anki Export failed", e)
                withContext(Dispatchers.Main) { onError(e.message ?: "Anki export failed") }
            } finally {
                ankiDb?.close()
                stagingDir?.deleteRecursively()
                withContext(Dispatchers.Main) { onProcessingChanged(false) }
            }
        }
    }
}