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
    private var db: FirebaseFirestore?,
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

    fun updateDb(newDb: FirebaseFirestore?) {
        this.db = newDb
    }

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

            val linkageObject = JSONObject()
            linkageObject.put("syncCardAdditions", deck.linkageSettings.syncCardAdditions)
            linkageObject.put("syncCardDeletions", deck.linkageSettings.syncCardDeletions)
            linkageObject.put("linkCardData", deck.linkageSettings.linkCardData)
            linkageObject.put("linkCardOrder", deck.linkageSettings.linkCardOrder)
            linkageObject.put("linkFieldConfig", deck.linkageSettings.linkFieldConfig)
            linkageObject.put("linkMetadata", deck.linkageSettings.linkMetadata)
            linkageObject.put("linkScoring", deck.linkageSettings.linkScoring)
            deckObject.put("linkageSettings", linkageObject)

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

                if (card.reviewLogs.isNotEmpty()) cardObject.put("reviewLogs", JSONArray(gson.toJson(card.reviewLogs)))
                card.absoluteDueDate?.let { cardObject.put("absoluteDueDate", it) }

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
            "frontRichText", "backRichText", "linkageSettings" // Added to end for backwards compatibility
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
                    card.frontRichText ?: "", card.backRichText ?: "",
                    gson.toJson(deckWithCards.deck.linkageSettings)
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

                                val reviewLogsArray = co.optJSONArray("reviewLogs")
                                val parsedLogs = if (reviewLogsArray != null) {
                                    val type = object : TypeToken<List<ReviewLog>>() {}.type
                                    Gson().fromJson<List<ReviewLog>>(reviewLogsArray.toString(), type) ?: emptyList()
                                } else emptyList()

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
                                        flag = CardFlag.fromInt(co.optInt("flag", 0)),
                                        reviewLogs = parsedLogs,
                                        absoluteDueDate = co.optLong("absoluteDueDate", 0L).takeIf { it > 0 }
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
                        dailyReviewLimit = deckObject.optInt("dailyReviewLimit", 200),
                        linkageSettings = deckObject.optJSONObject("linkageSettings")?.let { linkageObj ->
                            LinkageSettings(
                                syncCardAdditions = linkageObj.optBoolean("syncCardAdditions", true),
                                syncCardDeletions = linkageObj.optBoolean("syncCardDeletions", false),
                                linkCardData = linkageObj.optBoolean("linkCardData", true),
                                linkCardOrder = linkageObj.optBoolean("linkCardOrder", true),
                                linkFieldConfig = linkageObj.optBoolean("linkFieldConfig", true),
                                linkMetadata = linkageObj.optBoolean("linkMetadata", true),
                                linkScoring = linkageObj.optBoolean("linkScoring", true)
                            )
                        } ?: LinkageSettings()
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

                    val linkageJson = if (row.size > 22) row[22].takeIf { it.isNotBlank() } else null
                    val linkageSettings = if (linkageJson != null) {
                        try {
                            Gson().fromJson(linkageJson, LinkageSettings::class.java) ?: LinkageSettings()
                        } catch (e: Exception) { LinkageSettings() }
                    } else LinkageSettings()

                    val deck = decksMap.getOrPut(dId) {
                        Deck(
                            id = UUID.randomUUID().toString(),
                            name = dName,
                            parentDeckId = pId,
                            isStarred = star,
                            cardIds = emptyList(),
                            frontLanguage = frontLang,
                            backLanguage = backLang,
                            linkageSettings = linkageSettings
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

    suspend fun analyzeAnkiPackage(context: Context, sourceUri: Uri): List<Pair<String, List<Pair<String, net.ericclark.studiare.data.MediaType>>>> {
        return withContext(Dispatchers.IO) {
            var stagingDir: File? = null
            var ankiDb: SQLiteDatabase? = null
            val resultDecks = mutableListOf<Pair<String, List<Pair<String, net.ericclark.studiare.data.MediaType>>>>()

            try {
                stagingDir = File(context.cacheDir, "anki_analyze_${UUID.randomUUID()}")
                if (!stagingDir.exists()) stagingDir.mkdirs()

                // Extract Databases
                context.contentResolver.openInputStream(sourceUri)?.use { input ->
                    java.util.zip.ZipInputStream(input).use { zis ->
                        var entry = zis.nextEntry
                        while (entry != null) {
                            if (entry.name.startsWith("collection.anki2")) {
                                File(stagingDir, entry.name).outputStream().use { zis.copyTo(it) }
                            }
                            entry = zis.nextEntry
                        }
                    }
                }

                // Decompress Zstd
                val anki21bFile = File(stagingDir, "collection.anki21b")
                val anki21File = File(stagingDir, "collection.anki21")
                val anki2File = File(stagingDir, "collection.anki2")
                val finalDbFile = File(stagingDir, "collection.db")

                if (anki21bFile.exists()) {
                    try {
                        anki21bFile.inputStream().use { fileIn ->
                            com.github.luben.zstd.ZstdInputStream(fileIn).use { zstdIn ->
                                finalDbFile.outputStream().use { fileOut -> zstdIn.copyTo(fileOut) }
                            }
                        }
                    } catch (e: Exception) { Log.e(TAG, "Failed to decompress", e) }
                } else if (anki21File.exists()) { anki21File.renameTo(finalDbFile) }
                else if (anki2File.exists()) { anki2File.renameTo(finalDbFile) }

                if (finalDbFile.exists()) {
                    // --- NEW: Fix Anki's custom 'unicase' collation which crashes Android SQLite ---
                    try {
                        val fixDb = SQLiteDatabase.openDatabase(finalDbFile.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
                        fixDb.execSQL("PRAGMA writable_schema = 1")
                        fixDb.execSQL("UPDATE sqlite_master SET sql = replace(sql, 'COLLATE unicase', '') WHERE sql LIKE '%COLLATE unicase%'")
                        fixDb.execSQL("PRAGMA writable_schema = 0")
                        fixDb.close()
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not fix unicase collation in analyze", e)
                    }

                    ankiDb = SQLiteDatabase.openDatabase(finalDbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
                    val ankiDeckNames = extractAnkiDecks(ankiDb)

                    // 1. Identify which models belong to which decks
                    val deckToModels = mutableMapOf<String, MutableSet<Long>>()
                    val activeModelIds = mutableSetOf<Long>()
                    try {
                        ankiDb.rawQuery("SELECT c.did, n.mid FROM cards c JOIN notes n ON c.nid = n.id GROUP BY c.did, n.mid", null).use { cursor ->
                            while (cursor.moveToNext()) {
                                val did = cursor.getString(0)
                                val mid = cursor.getLong(1)
                                deckToModels.getOrPut(did) { mutableSetOf() }.add(mid)
                                activeModelIds.add(mid)
                            }
                        }
                    } catch (e: Exception) {}

                    // 2. Extract fields for models
                    val modelFieldMap = mutableMapOf<Long, List<String>>()
                    val fieldTypes = mutableMapOf<String, net.ericclark.studiare.data.MediaType>()

                    val extractedModelMap = extractAnkiFields(ankiDb)
                    extractedModelMap.forEach { (modelId, names) ->
                        if (activeModelIds.isEmpty() || activeModelIds.contains(modelId)) {
                            names.forEach { fieldName ->
                                if (!fieldTypes.containsKey(fieldName)) {
                                    fieldTypes[fieldName] = net.ericclark.studiare.data.MediaType.PLAIN_TEXT
                                }
                            }
                        }
                    }
                    modelFieldMap.putAll(extractedModelMap)

                    // 3. Scan notes to detect MediaType
                    val activeNotesQuery = """
                        SELECT n.mid, n.flds 
                        FROM notes n 
                        JOIN cards c ON n.id = c.nid 
                        GROUP BY n.id
                    """
                    ankiDb.rawQuery(activeNotesQuery, null).use { notesCursor ->
                        while (notesCursor.moveToNext()) {
                            val mid = notesCursor.getLong(0)
                            val fldsArray = notesCursor.getString(1).split("\u001F")
                            val fieldNamesForModel = modelFieldMap.getOrPut(mid) {
                                val generatedNames = fldsArray.indices.map { "Field ${it + 1}" }
                                generatedNames.forEach { if (!fieldTypes.containsKey(it)) fieldTypes[it] = net.ericclark.studiare.data.MediaType.PLAIN_TEXT }
                                generatedNames
                            }
                            for (i in 0 until minOf(fldsArray.size, fieldNamesForModel.size)) {
                                val fieldName = fieldNamesForModel[i]
                                val content = fldsArray[i].trim()
                                if (content.isEmpty()) continue
                                val currentType = fieldTypes[fieldName] ?: net.ericclark.studiare.data.MediaType.PLAIN_TEXT
                                val detectedType = parseNoteFieldContent(content).first

                                if (detectedType != net.ericclark.studiare.data.MediaType.PLAIN_TEXT) {
                                    if (detectedType == net.ericclark.studiare.data.MediaType.AUDIO || detectedType == net.ericclark.studiare.data.MediaType.IMAGE || detectedType == net.ericclark.studiare.data.MediaType.VIDEO) {
                                        fieldTypes[fieldName] = detectedType
                                    } else if ((detectedType == net.ericclark.studiare.data.MediaType.HTML || detectedType == net.ericclark.studiare.data.MediaType.WEB_LINK) && currentType == net.ericclark.studiare.data.MediaType.PLAIN_TEXT) {
                                        fieldTypes[fieldName] = detectedType
                                    }
                                }
                            }
                        }
                    }

                    // 4. Build the final per-deck list
                    for ((did, mids) in deckToModels) {
                        // FIX: Pass the raw name exactly as Anki has it (e.g. "Italiano::Conjugation")
                        val deckName = ankiDeckNames[did] ?: "Unknown Deck"
                        if (deckName.equals("Default", ignoreCase = true) && deckToModels.size > 1) continue

                        val fieldsForDeck = mutableSetOf<String>()
                        for (mid in mids) fieldsForDeck.addAll(modelFieldMap[mid] ?: emptyList())

                        if (fieldsForDeck.isNotEmpty()) {
                            val fieldTypePairs = fieldsForDeck.map { Pair(it, fieldTypes[it] ?: net.ericclark.studiare.data.MediaType.PLAIN_TEXT) }
                            resultDecks.add(Pair(deckName, fieldTypePairs))
                        }
                    }
                }
            } catch (e: Exception) { Log.e(TAG, "Failed to analyze Anki package", e) }
            finally { ankiDb?.close(); stagingDir?.deleteRecursively() }

            resultDecks
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

                stagingDir = File(context.cacheDir, "anki_staging_${UUID.randomUUID()}")
                if (!stagingDir.exists()) stagingDir.mkdirs()

                context.contentResolver.openInputStream(ankiPackageUri)?.use { input ->
                    java.util.zip.ZipInputStream(input).use { zis ->
                        var entry = zis.nextEntry
                        while (entry != null) {
                            if (entry.name.contains("..")) {
                                entry = zis.nextEntry
                                continue
                            }
                            val outFile = File(stagingDir, entry.name)
                            outFile.parentFile?.mkdirs()
                            if (!entry.isDirectory) {
                                outFile.outputStream().use { zis.copyTo(it) }
                            }
                            entry = zis.nextEntry
                        }
                    }
                }

                val anki21bFile = File(stagingDir, "collection.anki21b")
                val anki21File = File(stagingDir, "collection.anki21")
                val anki2File = File(stagingDir, "collection.anki2")
                val finalDbFile = File(stagingDir, "collection.db")

                if (anki21bFile.exists()) {
                    anki21bFile.inputStream().use { fileIn ->
                        com.github.luben.zstd.ZstdInputStream(fileIn).use { zstdIn ->
                            finalDbFile.outputStream().use { fileOut -> zstdIn.copyTo(fileOut) }
                        }
                    }
                } else if (anki21File.exists()) {
                    anki21File.renameTo(finalDbFile)
                } else if (anki2File.exists()) {
                    anki2File.renameTo(finalDbFile)
                }

                if (!finalDbFile.exists()) throw Exception("Invalid Anki package: collection database not found.")

                // --- NEW: Fix Anki's custom 'unicase' collation which crashes Android SQLite ---
                try {
                    val fixDb = SQLiteDatabase.openDatabase(finalDbFile.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
                    fixDb.execSQL("PRAGMA writable_schema = 1")
                    fixDb.execSQL("UPDATE sqlite_master SET sql = replace(sql, 'COLLATE unicase', '') WHERE sql LIKE '%COLLATE unicase%'")
                    fixDb.execSQL("PRAGMA writable_schema = 0")
                    fixDb.close()
                } catch (e: Exception) {
                    Log.w(TAG, "Could not fix unicase collation in import", e)
                }

                ankiDb = SQLiteDatabase.openDatabase(finalDbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)

                val mediaFile = File(stagingDir, "media")
                val mediaMap = mutableMapOf<String, String>()
                if (mediaFile.exists()) {
                    try {
                        val fileContent = mediaFile.readText().trim()
                        if (fileContent.startsWith("{")) {
                            val mediaJson = org.json.JSONObject(fileContent)
                            mediaJson.keys().forEach { key -> mediaMap[key] = mediaJson.getString(key) }
                        }
                    } catch (e: Exception) { Log.w(TAG, "Failed to parse Anki media file", e) }
                }

                parseAnkiDatabase(context, ankiDb, mediaMap, stagingDir, fieldMappings)

            } catch (e: Exception) {
                Log.e(TAG, "Anki Import failed", e)
                withContext(Dispatchers.Main) { onError(e.message ?: "Anki import failed") }
            } finally {
                ankiDb?.close()
                stagingDir?.deleteRecursively()
                withContext(Dispatchers.Main) { onProcessingChanged(false) }
            }
        }
    }

    // --- REFACTORED: Data Class for Tracking Configs ---
    data class DeckTarget(val config: net.ericclark.studiare.screens.AnkiMappingConfig, val originalAnkiName: String)

    // --- REFACTORED: 1. Main Orchestrator ---
    private suspend fun parseAnkiDatabase(
        context: Context,
        ankiDb: SQLiteDatabase,
        mediaMap: Map<String, String>,
        stagingDir: File,
        fieldMappings: List<net.ericclark.studiare.screens.AnkiMappingConfig>?
    ) {
        Log.d(TAG, "--- STARTING PARSE ANKI DATABASE ---")
        val ankiDeckNames = extractAnkiDecks(ankiDb)
        val modelFieldMap = extractAnkiFields(ankiDb)

        val query = """
            SELECT c.id AS cardId, c.did AS deckId, n.flds AS fields, n.tags AS tags, n.mid AS modelId
            FROM cards c
            JOIN notes n ON c.nid = n.id
            GROUP BY n.id 
        """

        val cursor = ankiDb.rawQuery(query, null)
        val allParsedCards = mutableMapOf<String, Card>()
        val parsedDecksMap = mutableMapOf<DeckTarget, MutableList<String>>()
        val resolvedMediaCache = mutableMapOf<String, String>()

        // Track Anki's ID to map the revlogs
        val ankiCidToUuid = mutableMapOf<Long, String>()

        while (cursor.moveToNext()) {
            yield()
            val ankiCid = cursor.getLong(cursor.getColumnIndexOrThrow("cardId"))
            val cardsWithTargets = buildCardsFromAnkiRow(
                context, cursor, ankiDeckNames, modelFieldMap, fieldMappings,
                mediaMap, stagingDir, resolvedMediaCache
            )

            cardsWithTargets.forEach { (target, card) ->
                parsedDecksMap.getOrPut(target) { mutableListOf() }.add(card.id)
                allParsedCards[card.id] = card
                ankiCidToUuid[ankiCid] = card.id
            }
        }
        cursor.close()

        // --- Parse Anki's Review Logs ---
        try {
            val revlogCursor = ankiDb.rawQuery("SELECT id, cid, ease, ivl, lastIvl, factor, time, type FROM revlog", null)
            while (revlogCursor.moveToNext()) {
                val timestamp = revlogCursor.getLong(0)
                val cid = revlogCursor.getLong(1)
                val ease = revlogCursor.getInt(2)
                val ivl = revlogCursor.getLong(3)
                val lastIvl = revlogCursor.getLong(4)
                val factor = revlogCursor.getDouble(5) // Anki stores as 2500 for 2.5
                val timeMs = revlogCursor.getLong(6)
                val type = revlogCursor.getInt(7)

                val uuid = ankiCidToUuid[cid]
                if (uuid != null && allParsedCards.containsKey(uuid)) {
                    val card = allParsedCards[uuid]!!
                    val log = ReviewLog(
                        id = timestamp, ease = ease, interval = ivl, lastInterval = lastIvl,
                        factor = factor / 1000.0, durationMs = timeMs, type = type
                    )
                    allParsedCards[uuid] = card.copy(reviewLogs = card.reviewLogs + log)
                }
            }
            revlogCursor.close()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse revlog, table might not exist in this Anki version", e)
        }

        Log.d(TAG, "Extracted ${allParsedCards.size} cards. Handing off to hierarchy builder.")
        val parsedDecks = buildDeckHierarchy(ankiDeckNames, parsedDecksMap)

        val existingDecksInDb = getLocalDecks().filter { dbDeck -> parsedDecks.any { it.oldDeckId == dbDeck.id } }
        if (existingDecksInDb.isNotEmpty()) {
            withContext(Dispatchers.Main) {
                onOverwriteConfirmationChanged(OverwriteConfirmationData(existingDecksInDb, parsedDecks, allParsedCards))
            }
        } else {
            importParsedData(parsedDecks, allParsedCards, emptyMap())
        }
    }

    // --- REFACTORED: 2. Row Builder ---
    private suspend fun buildCardsFromAnkiRow(
        context: Context,
        cursor: android.database.Cursor,
        ankiDeckNames: Map<String, String>,
        modelFieldMap: Map<Long, List<String>>,
        fieldMappings: List<net.ericclark.studiare.screens.AnkiMappingConfig>?,
        mediaMap: Map<String, String>,
        stagingDir: File,
        resolvedMediaCache: MutableMap<String, String>
    ): List<Pair<DeckTarget, Card>> {
        val deckId = cursor.getLong(cursor.getColumnIndexOrThrow("deckId")).toString()
        val fieldsRaw = cursor.getString(cursor.getColumnIndexOrThrow("fields"))
        val tagsRaw = cursor.getString(cursor.getColumnIndexOrThrow("tags"))
        val modelId = cursor.getLong(cursor.getColumnIndexOrThrow("modelId"))

        val originalAnkiName = ankiDeckNames[deckId] ?: "Unknown Deck"
        val rootAnkiName = originalAnkiName.split("::").first() // Add this extraction
        val fieldsArray = fieldsRaw.split("\u001F")
        val fieldNames = modelFieldMap[modelId] ?: fieldsArray.indices.map { "Field ${it + 1}" }

        // FIX: Look for exact match OR Root match
        val matchingConfigs = fieldMappings?.filter { config ->
            config.originalAnkiName == originalAnkiName || config.originalAnkiName == rootAnkiName
        }

        val configsToProcess = if (!matchingConfigs.isNullOrEmpty()) {
            matchingConfigs
        } else {
            listOf(net.ericclark.studiare.screens.AnkiMappingConfig(originalAnkiName = originalAnkiName, deckName = originalAnkiName.split("::").last().trim(), mapping = emptyMap()))
        }

        val results = mutableListOf<Pair<DeckTarget, Card>>()

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
                        backNotes.add(net.ericclark.studiare.data.NoteField(name = "Extra Fields", content = extraContent, type = net.ericclark.studiare.data.MediaType.HTML))
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

            if (frontHtml.isBlank() && backHtml.isBlank() && frontNotes.isEmpty() && backNotes.isEmpty()) {
                continue
            }

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

            results.add(Pair(DeckTarget(config, originalAnkiName), card))
        }

        return results
    }

    // --- REFACTORED: 3. Hierarchy Builder ---
    private fun buildDeckHierarchy(
        ankiDeckNames: Map<String, String>,
        parsedDecksMap: Map<DeckTarget, List<String>>
    ): List<ParsedDeck> {
        Log.d(TAG, "--- BUILDING DECK HIERARCHY ---")
        val finalDecks = mutableMapOf<String, ParsedDeck>()

        val targetsByConfig = parsedDecksMap.keys.groupBy { it.config }

        for ((config, targets) in targetsByConfig) {
            val rootDeckName = config.deckName
            val rootDeckId = UUID.randomUUID().toString()
            val configKeyPrefix = config.hashCode().toString()

            targets.forEach { target ->
                val originalAnkiName = target.originalAnkiName
                val cardIds = parsedDecksMap[target] ?: emptyList()
                val parts = originalAnkiName.split("::")

                if (parts.size == 1) {
                    val path = "$configKeyPrefix::ROOT"
                    if (finalDecks.containsKey(path)) {
                        val existing = finalDecks[path]!!
                        val merged = (existing.cardIds + cardIds).distinct()
                        finalDecks[path] = existing.copy(deck = existing.deck.copy(cardIds = merged), cardIds = merged)
                    } else {
                        val newDeck = Deck(
                            id = rootDeckId,
                            name = rootDeckName,
                            parentDeckId = null,
                            createdAt = System.currentTimeMillis(),
                            updatedAt = System.currentTimeMillis(),
                            cardIds = cardIds
                        )
                        // FIX: Pass newDeck.id instead of originalAnkiName to prevent ID mismatches
                        finalDecks[path] = ParsedDeck(newDeck, cardIds, newDeck.id)
                    }
                } else {
                    val setName = parts.drop(1).joinToString("::")
                    val path = "$configKeyPrefix::SET::$setName"

                    if (!finalDecks.containsKey("$configKeyPrefix::ROOT")) {
                        val newDeck = Deck(
                            id = rootDeckId,
                            name = rootDeckName,
                            parentDeckId = null,
                            createdAt = System.currentTimeMillis(),
                            updatedAt = System.currentTimeMillis(),
                            cardIds = emptyList()
                        )
                        // FIX: Pass newDeck.id instead of originalAnkiName to prevent ID mismatches
                        finalDecks["$configKeyPrefix::ROOT"] = ParsedDeck(newDeck, emptyList(), newDeck.id)
                    }

                    if (finalDecks.containsKey(path)) {
                        val existing = finalDecks[path]!!
                        val merged = (existing.cardIds + cardIds).distinct()
                        finalDecks[path] = existing.copy(deck = existing.deck.copy(cardIds = merged), cardIds = merged)
                    } else {
                        val newDeck = Deck(
                            id = UUID.randomUUID().toString(),
                            name = setName,
                            parentDeckId = rootDeckId,
                            createdAt = System.currentTimeMillis(),
                            updatedAt = System.currentTimeMillis(),
                            cardIds = cardIds
                        )
                        // FIX: Pass newDeck.id instead of originalAnkiName to prevent ID mismatches
                        finalDecks[path] = ParsedDeck(newDeck, cardIds, newDeck.id)
                    }
                }
            }
        }

        return finalDecks.values.toList()
    }

    // --- REFACTORED: 4. Content Type Parser ---
    private fun parseNoteFieldContent(html: String): Pair<net.ericclark.studiare.data.MediaType, String> {
        val trimmed = html.trim()
        if (trimmed.isEmpty()) return net.ericclark.studiare.data.MediaType.PLAIN_TEXT to ""

        val audioMatch = Regex("^<audio[^>]+src=[\"']([^\"']+)[\"'][^>]*></audio>$", RegexOption.IGNORE_CASE).matchEntire(trimmed)
        if (audioMatch != null) return net.ericclark.studiare.data.MediaType.AUDIO to audioMatch.groupValues[1]

        val imageMatch = Regex("^<img[^>]+src=[\"']([^\"']+)[\"'][^>]*>$", RegexOption.IGNORE_CASE).matchEntire(trimmed)
        if (imageMatch != null) return net.ericclark.studiare.data.MediaType.IMAGE to imageMatch.groupValues[1]

        val videoMatch = Regex("^<video[^>]+src=[\"']([^\"']+)[\"'][^>]*></video>$", RegexOption.IGNORE_CASE).matchEntire(trimmed)
        if (videoMatch != null) return net.ericclark.studiare.data.MediaType.VIDEO to videoMatch.groupValues[1]

        val hasHtmlTags = Regex("<[^>]*>").containsMatchIn(trimmed)
        if (!hasHtmlTags) {
            if (android.util.Patterns.WEB_URL.matcher(trimmed).matches()) {
                return net.ericclark.studiare.data.MediaType.WEB_LINK to trimmed
            }
            return net.ericclark.studiare.data.MediaType.PLAIN_TEXT to trimmed
        }

        return net.ericclark.studiare.data.MediaType.HTML to trimmed
    }

    private fun extractAnkiDecks(ankiDb: SQLiteDatabase): Map<String, String> {
        val decks = mutableMapOf<String, String>()

        try {
            ankiDb.rawQuery("SELECT id, name FROM decks", null).use { cursor ->
                while (cursor.moveToNext()) {
                    val rawName = cursor.getString(1)
                    decks[cursor.getString(0)] = rawName.replace("\u001F", "::").replace("\u001E", "::")
                }
            }
            if (decks.isNotEmpty()) return decks
        } catch (e: Exception) { }

        try {
            ankiDb.rawQuery("SELECT decks FROM col LIMIT 1", null).use { cursor ->
                if (cursor.moveToFirst()) {
                    val jsonStr = cursor.getString(0).trim()
                    if (jsonStr.startsWith("{")) {
                        val decksObj = org.json.JSONObject(jsonStr)
                        for (key in decksObj.keys()) {
                            val deck = decksObj.getJSONObject(key)
                            val rawName = deck.optString("name", "Unknown Deck")
                            decks[key] = rawName.replace("\u001F", "::").replace("\u001E", "::")
                        }
                    }
                }
            }
        } catch (e: Exception) { Log.e(TAG, "Failed to parse legacy decks", e) }

        return decks
    }

    private suspend fun resolveAnkiMedia(
        context: Context,
        rawHtml: String,
        mediaMap: Map<String, String>,
        stagingDir: File,
        resolvedCache: MutableMap<String, String>
    ): String {
        var resolvedHtml = rawHtml

        fun getMediaKey(filename: String): String? {
            return mediaMap.entries.find { it.value == filename }?.key
        }

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

        val soundRegex = Regex("\\[sound:(.*?)\\]")
        resolvedHtml = soundRegex.replace(resolvedHtml) { matchResult ->
            val filename = matchResult.groupValues[1]
            val localPath = copyMediaToLocal(filename)
            if (localPath != null) "<audio src=\"$localPath\" controls></audio>" else matchResult.value
        }

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

                stagingDir = File(context.cacheDir, "anki_export_${UUID.randomUUID()}")
                if (!stagingDir.exists()) stagingDir.mkdirs()

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

                val mediaMap = mutableMapOf<String, String>()
                var mediaCounter = 0

                fun processHtmlForExport(html: String?): String {
                    if (html.isNullOrBlank()) return ""
                    var processed = html

                    val srcRegex = Regex("src=[\"'](.*?)[\"']")
                    processed = srcRegex.replace(processed!!) { matchResult ->
                        val src = matchResult.groupValues[1]
                        if (src.startsWith("http")) return@replace matchResult.value

                        val numericKey = mediaCounter.toString()
                        val ext = src.substringAfterLast('.', "jpg").substringBefore('?')
                        val ankiFilename = "media_$numericKey.$ext"

                        var copySuccess = false
                        try {
                            val inputStream = if (src.startsWith("/")) {
                                File(src).inputStream()
                            } else {
                                context.contentResolver.openInputStream(Uri.parse(src))
                            }
                            if (inputStream != null) {
                                inputStream.use { input ->
                                    File(stagingDir, numericKey).outputStream().use { output ->
                                        input.copyTo(output)
                                    }
                                }
                                copySuccess = true
                            }
                        } catch(e: Exception) { Log.e(TAG, "Failed to copy media for export: $src", e) }

                        // Only register the media to Anki if the file was successfully staged
                        if (copySuccess) {
                            mediaMap[numericKey] = ankiFilename
                            mediaCounter++
                            "src=\"$ankiFilename\""
                        } else {
                            matchResult.value // Leave the broken URI, Anki will handle it gracefully without crashing
                        }
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

                val dbFile = File(stagingDir, "collection.anki21")
                ankiDb = SQLiteDatabase.openOrCreateDatabase(dbFile, null)

                ankiDb.execSQL("CREATE TABLE col (id integer primary key, crt integer not null, mod integer not null, scm integer not null, ver integer not null, dty integer not null, usn integer not null, ls integer not null, conf text not null, models text not null, decks text not null, dconf text not null, tags text not null);")
                ankiDb.execSQL("CREATE TABLE notes (id integer primary key, guid text not null, mid integer not null, mod integer not null, usn integer not null, tags text not null, flds text not null, sfld integer not null, csum integer not null, flags integer not null, data text not null);")
                ankiDb.execSQL("CREATE TABLE cards (id integer primary key, nid integer not null, did integer not null, ord integer not null, mod integer not null, usn integer not null, type integer not null, queue integer not null, due integer not null, ivl integer not null, factor integer not null, reps integer not null, lapses integer not null, \"left\" integer not null, odue integer not null, odid integer not null, flags integer not null, data text not null);")
                ankiDb.execSQL("CREATE TABLE graves (usn integer not null, oid integer not null, type integer not null);")
                ankiDb.execSQL("CREATE TABLE revlog (id integer primary key, cid integer not null, usn integer not null, ease integer not null, ivl integer not null, lastIvl integer not null, factor integer not null, time integer not null, type integer not null);")

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

                uniqueFrontNotes.remove("Front")
                uniqueFrontNotes.remove("Back")
                uniqueBackNotes.remove("Front")
                uniqueBackNotes.remove("Back")

                val finalFrontFields = uniqueFrontNotes.toList()
                val finalBackFields = uniqueBackNotes.map { name ->
                    if (uniqueFrontNotes.contains(name)) "$name (Back)" else name
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

                // Look up map to walk up the parent tree
                val deckMapForExport = sanitizedDecks.associateBy { it.deck.id }
                fun getFullAnkiName(deck: Deck): String {
                    var current = deck
                    val names = mutableListOf(current.name)
                    while (current.parentDeckId != null && deckMapForExport.containsKey(current.parentDeckId)) {
                        current = deckMapForExport[current.parentDeckId]!!.deck
                        names.add(current.name)
                    }
                    return names.reversed().joinToString("::")
                }

                sanitizedDecks.forEach { deckWithCards ->
                    currentAnkiDeckId++
                    val did = currentAnkiDeckId
                    deckIdMap[deckWithCards.deck.id] = did

                    val fullAnkiName = getFullAnkiName(deckWithCards.deck)
                    val safeName = fullAnkiName.replace("\"", "\\\"")

                    decksJsonObj.put(did.toString(), JSONObject("""{"id": $did, "mod": $currentTime, "name": "$safeName", "usn": -1, "lrnToday": [0, 0], "revToday": [0, 0], "newToday": [0, 0], "timeToday": [0, 0], "collapsed": false, "browserCollapsed": false, "desc": "", "dyn": 0, "conf": 1}"""))
                }

                val dconfJson = """{"1": {"id": 1, "mod": 0, "name": "Default", "usn": 0, "maxTaken": 60, "autoplay": true, "timer": 0, "replayq": true, "new": {"delays": [1, 10], "ints": [1, 4, 7], "initialFactor": 2500, "bury": true, "order": 1, "perDay": 20}, "rev": {"perDay": 200, "ease4": 1.3, "ivlFct": 1.0, "maxIvl": 36500, "bury": true, "minSpace": 1}, "lapse": {"delays": [10], "mult": 0.0, "minInt": 1, "leechFails": 8, "leechAction": 0}, "dyn": false}}"""

                ankiDb.execSQL("INSERT INTO col (id, crt, mod, scm, ver, dty, usn, ls, conf, models, decks, dconf, tags) VALUES (1, $currentTime, $currentTime, $currentTime, 11, 0, 0, 0, '{}', ?, ?, ?, '{}')", arrayOf(modelsJson, decksJsonObj.toString(), dconfJson))

                var noteIdCounter = System.currentTimeMillis()

                sanitizedDecks.forEach { deckWithCards ->
                    val did = deckIdMap[deckWithCards.deck.id] ?: 1L
                    deckWithCards.cards.forEach { card ->

                        val frontHtml = processHtmlForExport(card.frontRichText ?: card.front)
                        val backHtml = processHtmlForExport(card.backRichText ?: card.back)

                        val fieldValues = mutableListOf<String>()
                        fieldValues.add(frontHtml)
                        fieldValues.add(backHtml)

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

                        val flds = fieldValues.joinToString("\u001F")
                        val tags = card.tags.joinToString(" ")
                        val noteId = noteIdCounter++
                        val cardId = noteIdCounter++
                        val guid = UUID.randomUUID().toString().substring(0, 10)

                        ankiDb.execSQL("INSERT INTO notes (id, guid, mid, mod, usn, tags, flds, sfld, csum, flags, data) VALUES (?, ?, ?, ?, -1, ?, ?, ?, 0, 0, '')",
                            arrayOf(noteId, guid, defaultModelId, currentTime, tags, flds, frontHtml))

                        ankiDb.execSQL("INSERT INTO cards (id, nid, did, ord, mod, usn, type, queue, due, ivl, factor, reps, lapses, \"left\", odue, odid, flags, data) VALUES (?, ?, ?, 0, ?, -1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, '')",
                            arrayOf(cardId, noteId, did, currentTime))

                        // Inject Studiare History into Anki ---
                        card.reviewLogs.forEach { log ->
                            ankiDb.execSQL("INSERT INTO revlog (id, cid, usn, ease, ivl, lastIvl, factor, time, type) VALUES (?, ?, -1, ?, ?, ?, ?, ?, ?)",
                                arrayOf(log.id, cardId, log.ease, log.interval, log.lastInterval, (log.factor * 1000).toInt(), log.durationMs, log.type))
                        }
                    }
                }
                ankiDb.close()

                val mediaJsonObj = JSONObject()
                mediaMap.forEach { (numericKey, filename) -> mediaJsonObj.put(numericKey, filename) }
                File(stagingDir, "media").writeText(mediaJsonObj.toString())

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

    private fun extractAnkiFields(ankiDb: SQLiteDatabase): Map<Long, List<String>> {
        val modelFieldMap = mutableMapOf<Long, MutableList<String>>()

        try {
            ankiDb.rawQuery("SELECT ntid, name FROM fields ORDER BY ntid, ord", null).use { cursor ->
                while (cursor.moveToNext()) {
                    modelFieldMap.getOrPut(cursor.getLong(0)) { mutableListOf() }.add(cursor.getString(1))
                }
            }
            if (modelFieldMap.isNotEmpty()) return modelFieldMap
        } catch (e: Exception) { }

        try {
            ankiDb.rawQuery("SELECT models FROM col LIMIT 1", null).use { cursor ->
                if (cursor.moveToFirst()) {
                    val jsonStr = cursor.getString(0).trim()
                    if (jsonStr.startsWith("{")) {
                        val modelsObj = org.json.JSONObject(jsonStr)
                        modelsObj.keys().forEach { modelId ->
                            val flds = modelsObj.getJSONObject(modelId).getJSONArray("flds")
                            for (i in 0 until flds.length()) {
                                modelFieldMap.getOrPut(modelId.toLong()) { mutableListOf() }.add(flds.getJSONObject(i).getString("name"))
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) { }

        return modelFieldMap
    }
}