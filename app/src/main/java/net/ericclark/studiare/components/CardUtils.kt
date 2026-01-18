package net.ericclark.studiare.components

import net.ericclark.studiare.data.AutoSetConfig
import net.ericclark.studiare.data.Card
import net.ericclark.studiare.data.DeckWithCards
import java.util.Locale

class CardUtils {

    private val usePerceivedRandomness = true

    fun getFilteredAndSortedCards(parentDeck: net.ericclark.studiare.data.DeckWithCards, config: net.ericclark.studiare.data.AutoSetConfig): List<net.ericclark.studiare.data.Card> {
        var pool = parentDeck.cards
        if (config.excludeKnown) pool = pool.filter { !it.isKnown }

        val timeMultiplier = when (config.timeUnit) {
            "Days" -> 24 * 60 * 60 * 1000L
            "Weeks" -> 7 * 24 * 60 * 60 * 1000L
            "Months" -> 30 * 24 * 60 * 60 * 1000L
            "Years" -> 365 * 24 * 60 * 60 * 1000L
            else -> 0L
        }
        val cutoffTime = System.currentTimeMillis() - (config.timeValue * timeMultiplier)

        pool = when (config.selectionMode) {
            "Difficulty" -> pool.filter { it.difficulty in config.selectedDifficulties }
            "Tags" -> pool.filter { card -> card.tags.any { it in config.selectedTags } }
            "Alphabet" -> {
                val start = config.alphabetStart.uppercase()
                val end = config.alphabetEnd.uppercase()
                pool.filter { card ->
                    val text = if (config.filterSide == "Front") card.front else card.back
                    val firstChar = text.trim().uppercase(Locale.getDefault()).firstOrNull()?.toString()
                    firstChar != null && firstChar >= start && firstChar <= end
                }
            }
            "Card Order" -> {
                val s = (config.cardOrderStart - 1).coerceAtLeast(0)
                val e = (config.cardOrderEnd - 1).coerceAtMost(parentDeck.cards.size - 1)
                if (s <= e && parentDeck.cards.isNotEmpty()) {
                    val allowedIds = parentDeck.cards.slice(s..e).map { it.id }.toSet()
                    pool.filter { it.id in allowedIds }
                } else emptyList()
            }
            "Review Date" -> {
                if (config.filterType == "Include") pool.filter { it.reviewedAt != null && it.reviewedAt >= cutoffTime }
                else pool.filter { it.reviewedAt == null || it.reviewedAt < cutoffTime }
            }
            "Incorrect Date" -> {
                if (config.filterType == "Include") pool.filter { card -> card.incorrectAttempts.maxOrNull()?.let { last -> last >= cutoffTime } == true }
                else pool.filter { card -> card.incorrectAttempts.isEmpty() || card.incorrectAttempts.maxOrNull()!! < cutoffTime }
            }
            "Review Count" -> {
                if (config.reviewCountDirection == "Maximum") pool.filter { it.reviewedCount <= config.reviewCountThreshold }
                else pool.filter { it.reviewedCount >= config.reviewCountThreshold }
            }
            "Score" -> {
                val getScore: (net.ericclark.studiare.data.Card) -> Float = { card ->
                    val total = card.gradedAttempts.size
                    if (total == 0) 0f else (total - card.incorrectAttempts.size).toFloat() / total
                }
                val threshold = config.scoreThreshold.toFloat() / 100f
                if (config.scoreDirection == "Maximum") pool.filter { getScore(it) <= threshold }
                else pool.filter { getScore(it) >= threshold }
            }
            else -> pool
        }

        val getScore: (net.ericclark.studiare.data.Card) -> Float = { card ->
            val total = card.gradedAttempts.size
            if (total == 0) 0f else (total - card.incorrectAttempts.size).toFloat() / total
        }
        val isAsc = config.sortDirection == "ASC"

        return when (config.sortMode) {
            "Alphabetical" -> {
                val selector: (net.ericclark.studiare.data.Card) -> String = { if (config.sortSide == "Front") it.front.lowercase() else it.back.lowercase() }
                if (isAsc) pool.sortedBy(selector) else pool.sortedByDescending(selector)
            }
            "Review Date" -> {
                val selector: (net.ericclark.studiare.data.Card) -> Long? = { it.reviewedAt }
                if (isAsc) pool.sortedWith(compareBy(nullsLast(), selector)) else pool.sortedWith(compareByDescending(nullsLast(), selector))
            }
            "Incorrect Date" -> {
                val selector: (net.ericclark.studiare.data.Card) -> Long? = { it.incorrectAttempts.maxOrNull() }
                if (isAsc) pool.sortedWith(compareBy(nullsLast(), selector)) else pool.sortedWith(compareByDescending(nullsLast(), selector))
            }
            "Review Count" -> {
                if (isAsc) pool.sortedBy { it.reviewedCount } else pool.sortedByDescending { it.reviewedCount }
            }
            "Score" -> {
                if (isAsc) pool.sortedBy(getScore) else pool.sortedByDescending(getScore)
            }
            "Card Order" -> {
                val indexMap = parentDeck.cards.mapIndexed { index, card -> card.id to index }.toMap()
                val selector: (net.ericclark.studiare.data.Card) -> Int = { indexMap[it.id] ?: Int.MAX_VALUE }
                if (isAsc) pool.sortedBy(selector) else pool.sortedByDescending(selector)
            }
            "Random" -> {
                if (usePerceivedRandomness) createPerceivedRandomList(pool) else pool.shuffled()
            }
            else -> pool
        }
    }

    fun createPerceivedRandomList(cards: List<net.ericclark.studiare.data.Card>): List<net.ericclark.studiare.data.Card> {
        if (cards.isEmpty()) return emptyList()
        val sourceList = cards.toMutableList(); val finalList = mutableListOf<net.ericclark.studiare.data.Card>()
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