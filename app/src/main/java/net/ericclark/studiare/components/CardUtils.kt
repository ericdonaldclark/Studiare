package net.ericclark.studiare.components

import net.ericclark.studiare.data.AutoSetConfig
import net.ericclark.studiare.data.Card
import net.ericclark.studiare.data.CardSide
import net.ericclark.studiare.data.DeckWithCards
import net.ericclark.studiare.data.Direction
import net.ericclark.studiare.data.FilterType
import net.ericclark.studiare.data.SelectionMode
import net.ericclark.studiare.data.SortMode
import net.ericclark.studiare.data.TimeUnit
import java.util.Locale

class CardUtils {

    private val usePerceivedRandomness = true

    fun getFilteredAndSortedCards(parentDeck: DeckWithCards, config: AutoSetConfig): List<Card> {
        var pool = parentDeck.cards

        // 1. Exclude Known (Existing Logic)
        if (config.excludeKnown) {
            pool = pool.filter { !it.isKnown }
        }

        // 2. Exclude Suspended (New Logic)
        // Unless explicitly requested, we filter out suspended cards.
        if (!config.includeSuspended) {
            pool = pool.filter { !it.isSuspended }
        }

        // 3. Filter by Flag (New Logic)
        // If selectedFlags is not empty, only include cards with those flags.
        if (config.selectedFlags.isNotEmpty()) {
            pool = pool.filter { it.flag.value in config.selectedFlags }
        }

        val timeMultiplier = when (config.timeUnit) {
            TimeUnit.DAYS -> 24 * 60 * 60 * 1000L
            TimeUnit.WEEKS -> 7 * 24 * 60 * 60 * 1000L
            TimeUnit.MONTHS -> 30 * 24 * 60 * 60 * 1000L
            TimeUnit.YEARS -> 365 * 24 * 60 * 60 * 1000L
        }
        val cutoffTime = System.currentTimeMillis() - (config.timeValue * timeMultiplier)

        pool = when (config.selectionMode) {
            SelectionMode.DIFFICULTY -> pool.filter { it.difficulty.value in config.selectedDifficulties }
            SelectionMode.TAGS -> pool.filter { card -> card.tags.any { it in config.selectedTags } }
            SelectionMode.ALPHABET -> {
                val start = config.alphabetStart.uppercase()
                val end = config.alphabetEnd.uppercase()
                pool.filter { card ->
                    val text = if (config.filterSide == CardSide.FRONT) card.front else card.back
                    val firstChar = text.trim().uppercase(Locale.getDefault()).firstOrNull()?.toString()
                    firstChar != null && firstChar >= start && firstChar <= end
                }
            }
            SelectionMode.CARD_ORDER -> {
                val s = (config.cardOrderStart - 1).coerceAtLeast(0)
                val e = (config.cardOrderEnd - 1).coerceAtMost(parentDeck.cards.size - 1)
                if (s <= e && parentDeck.cards.isNotEmpty()) {
                    val allowedIds = parentDeck.cards.slice(s..e).map { it.id }.toSet()
                    pool.filter { it.id in allowedIds }
                } else emptyList()
            }
            SelectionMode.REVIEW_DATE -> {
                if (config.filterType == FilterType.INCLUDE) pool.filter { it.reviewedAt != null && it.reviewedAt >= cutoffTime }
                else pool.filter { it.reviewedAt == null || it.reviewedAt < cutoffTime }
            }
            SelectionMode.INCORRECT_DATE -> {
                if (config.filterType == FilterType.INCLUDE) pool.filter { card -> card.incorrectAttempts.maxOrNull()?.let { last -> last >= cutoffTime } == true }
                else pool.filter { card -> card.incorrectAttempts.isEmpty() || card.incorrectAttempts.maxOrNull()!! < cutoffTime }
            }
            SelectionMode.REVIEW_COUNT -> {
                if (config.reviewCountDirection == Direction.ASC) pool.filter { it.reviewedCount <= config.reviewCountThreshold }
                else pool.filter { it.reviewedCount >= config.reviewCountThreshold }
            }
            SelectionMode.SCORE -> {
                val getScore: (Card) -> Float = { card ->
                    val total = card.gradedAttempts.size
                    if (total == 0) 0f else (total - card.incorrectAttempts.size).toFloat() / total
                }
                val threshold = config.scoreThreshold.toFloat() / 100f
                if (config.scoreDirection == Direction.DESC) pool.filter { getScore(it) <= threshold }
                else pool.filter { getScore(it) >= threshold }
            }
            else -> pool
        }

        val getScore: (Card) -> Float = { card ->
            val total = card.gradedAttempts.size
            if (total == 0) 0f else (total - card.incorrectAttempts.size).toFloat() / total
        }
        val isAsc = config.sortDirection == Direction.ASC

        return when (config.sortMode) {
            SortMode.ALPHABETICAL -> {
                val selector: (Card) -> String = { if (config.sortSide == CardSide.FRONT) it.front.lowercase() else it.back.lowercase() }
                if (isAsc) pool.sortedBy(selector) else pool.sortedByDescending(selector)
            }
            SortMode.REVIEW_DATE -> {
                val selector: (Card) -> Long? = { it.reviewedAt }
                if (isAsc) pool.sortedWith(compareBy(nullsLast(), selector)) else pool.sortedWith(compareByDescending(nullsLast(), selector))
            }
            SortMode.INCORRECT_DATE -> {
                val selector: (Card) -> Long? = { it.incorrectAttempts.maxOrNull() }
                if (isAsc) pool.sortedWith(compareBy(nullsLast(), selector)) else pool.sortedWith(compareByDescending(nullsLast(), selector))
            }
            SortMode.REVIEW_COUNT -> {
                if (isAsc) pool.sortedBy { it.reviewedCount } else pool.sortedByDescending { it.reviewedCount }
            }
            SortMode.SCORE -> {
                if (isAsc) pool.sortedBy(getScore) else pool.sortedByDescending(getScore)
            }
            SortMode.CARD_ORDER -> {
                // If "Card Order" filter is not used, we can default to defaultSortOrder logic
                // But generally users rely on the list index from the Deck object (which is what we mapped in indexMap).
                // We will stick to the existing indexMap logic as it represents the current deck state.
                val indexMap = parentDeck.cards.mapIndexed { index, card -> card.id to index }.toMap()
                val selector: (Card) -> Int = { indexMap[it.id] ?: Int.MAX_VALUE }
                if (isAsc) pool.sortedBy(selector) else pool.sortedByDescending(selector)
            }
            SortMode.RANDOM -> {
                if (usePerceivedRandomness) createPerceivedRandomList(pool) else pool.shuffled()
            }
            else -> pool
        }
    }

    fun createPerceivedRandomList(cards: List<Card>): List<Card> {
        if (cards.isEmpty()) return emptyList()
        val sourceList = cards.toMutableList(); val finalList = mutableListOf<Card>()
        val distance = (cards.size * 0.1).toInt().coerceAtLeast(1)
        while (sourceList.isNotEmpty()) {
            val recent = finalList.takeLast(distance).map { it.id }.toSet()
            val pickable = sourceList.filter { it.id !in recent }
            val card = if (pickable.isNotEmpty()) pickable.random() else sourceList.random()
            finalList.add(card); sourceList.remove(card)
        }
        return finalList
    }
}