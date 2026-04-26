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
}