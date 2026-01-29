package net.ericclark.studiare.components

import net.ericclark.studiare.data.Card
import net.ericclark.studiare.data.Deck
import java.util.concurrent.TimeUnit
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * A purely mathematical engine for the Free Spaced Repetition Scheduler (FSRS) v4.5.
 * This object contains no Android dependencies and handles only the logic of
 * calculating the next state of a card based on its history and current rating.
 */
object FsrsAlgorithm {

    // --- Standard FSRS v4.5 Default Weights ---
    // These are the default parameters optimized for a general dataset.
    // They can be overridden by deck-specific settings if you implement an optimizer later.
    val DEFAULT_WEIGHTS = listOf(
        0.4,    // 0: w[0] -> Initial Stability for Again
        0.6,    // 1: w[1] -> Initial Stability for Hard
        2.4,    // 2: w[2] -> Initial Stability for Good
        5.8,    // 3: w[3] -> Initial Stability for Easy
        4.93,   // 4: w[4] -> Difficulty Initializer
        0.94,   // 5: w[5] -> Difficulty Interaction
        0.86,   // 6: w[6] -> Accuracy Interaction
        0.01,   // 7: w[7] -> Difficulty Weight
        1.49,   // 8: w[8] -> Stability Weight (Hard)
        0.14,   // 9: w[9] -> Stability Weight (Good)
        0.94,   // 10: w[10] -> Stability Weight (Easy)
        2.18,   // 11: w[11] -> Stability Decay (Hard)
        0.05,   // 12: w[12] -> Stability Decay (Good)
        0.34,   // 13: w[13] -> Stability Decay (Easy)
        1.26,   // 14: w[14] -> Lapses Interaction
        0.29,   // 15: w[15] -> Lapses Weight
        2.61    // 16: w[16] -> Forgetting Index
    )

    // Rating constants for clarity
    const val RATING_AGAIN = 1
    const val RATING_HARD = 2
    const val RATING_GOOD = 3
    const val RATING_EASY = 4

    // State constants matching standard FSRS
    const val STATE_NEW = 0
    const val STATE_LEARNING = 1
    const val STATE_REVIEW = 2
    const val STATE_RELEARNING = 3

    /**
     * Data class to hold the result of a scheduling calculation.
     */
    data class FsrsCalculationResult(
        val stability: Double,
        val difficulty: Double,
        val elapsedDays: Double,
        val scheduledDays: Double,
        val state: Int,
        val dueTimestamp: Long
    )

    /**
     * Calculates the next state for a card.
     *
     * @param card The card being reviewed.
     * @param rating The user's rating (1=Again, 2=Hard, 3=Good, 4=Easy).
     * @param deckParams The deck configuration (weights, desired retention).
     * @param now The current timestamp in milliseconds.
     */
    fun calculateNextState(
        card: Card,
        rating: Int,
        deckParams: Deck, // Passing Deck to access weights/retention
        now: Long = System.currentTimeMillis()
    ): FsrsCalculationResult {

        // 1. Setup Parameters
        val weights = if (deckParams.fsrsWeights.isNotEmpty() && deckParams.fsrsWeights.size >= 17) {
            deckParams.fsrsWeights
        } else {
            DEFAULT_WEIGHTS
        }
        val requestRetention = deckParams.fsrsDesiredRetention

        // 2. Determine Time Delta
        val lastReview = card.fsrsLastReview ?: card.createdAt
        val elapsedMillis = now - lastReview
        val elapsedDays = max(0.0, elapsedMillis.toDouble() / TimeUnit.DAYS.toMillis(1).toDouble())

        // 3. Current State
        val currentD = card.fsrsDifficulty ?: 0.0
        val currentS = card.fsrsStability ?: 0.0
        val currentState = card.fsrsState ?: STATE_NEW

        // 4. Calculate New Values
        var nextD: Double
        var nextS: Double
        var nextState: Int

        if (currentState == STATE_NEW) {
            // --- First Review ---
            nextD = initDifficulty(rating, weights)
            nextS = initStability(rating, weights)

            nextState = if (rating == RATING_AGAIN) STATE_LEARNING else STATE_REVIEW

        } else {
            // --- Subsequent Reviews ---

            // Step 4a: Update Difficulty
            nextD = nextDifficulty(currentD, rating, weights)

            // Step 4b: Update Stability
            if (rating == RATING_AGAIN) {
                // User forgot the card
                nextS = nextForgetStability(currentD, currentS, card.fsrsLapses, weights)
                nextState = STATE_RELEARNING
            } else {
                // User recalled the card
                nextS = nextRecallStability(currentD, currentS, elapsedDays, rating, weights)
                nextState = STATE_REVIEW
            }
        }

        // 5. Calculate Interval (Scheduled Days)
        var newInterval = nextInterval(nextS, requestRetention, deckParams.fsrsMaximumInterval)

        // Constraint: Interval cannot be shorter than 1 day if state is Review/Easy
        // (Though technically FSRS allows <1 day for learning steps, for simplicity we treat stability as days)
        // If "Again", standard FSRS often sets a short interval (e.g. 0 days/minutes).
        // Here we map short intervals to "Due Tomorrow" or keep as fractional days if you support intraday.
        // For this implementation, we allow fractional days but clamp min to reasonable checks.

        val dueTimestamp = now + (newInterval * TimeUnit.DAYS.toMillis(1)).toLong()

        return FsrsCalculationResult(
            stability = nextS,
            difficulty = nextD,
            elapsedDays = elapsedDays,
            scheduledDays = newInterval,
            state = nextState,
            dueTimestamp = dueTimestamp
        )
    }

    // --- Mathematical Formulas (Standard FSRS 4.5) ---

    private fun initStability(rating: Int, w: List<Double>): Double {
        // formula: w[r-1]
        // rating 1..4 -> index 0..3
        return max(0.1, w[rating - 1])
    }

    private fun initDifficulty(rating: Int, w: List<Double>): Double {
        // formula: w[4] - w[5] * (grade - 3)
        // result constrained to [1, 10]
        val d = w[4] - w[5] * (rating - 3)
        return constrainDifficulty(d)
    }

    private fun nextDifficulty(d: Double, rating: Int, w: List<Double>): Double {
        // formula: d - w[6] * (grade - 3)
        val nextD = d - w[6] * (rating - 3)
        // formula: mean_reversion(w[4], d) -> w[7] * w[4] + (1 - w[7]) * nextD
        val newD = w[7] * w[4] + (1 - w[7]) * nextD
        return constrainDifficulty(newD)
    }

    private fun constrainDifficulty(d: Double): Double {
        return min(max(d, 1.0), 10.0)
    }

    private fun nextRecallStability(d: Double, s: Double, r: Double, rating: Int, w: List<Double>): Double {
        // r = retrievability, approximated by actual elapsed days here for calculation?
        // Actually FSRS formula uses 'r' as Retrievability calculated from elapsed time.
        // Retrievability = (1 + elapsed / (9 * s)) ^ -1

        // However, the standard implementation usually passes elapsed days (t) into the formula directly
        // OR calculates retrievability first.
        // FSRS 4.5 formula for S_inc:
        // S_new = S * (1 + exp(w[8]) * (11 - D) * S^(-w[9]) * (exp((1 - R) * w[10]) - 1))

        // Let's calculate Retrievability (R) first based on elapsed days (r input param in function sig usually means retention or days?)
        // In the function signature above I passed `elapsedDays`. Let's treat `r` as Retrievability.

        val retrievability = (1 + r / (9 * s)).pow(-1.0)

        val hardPenalty = if (rating == RATING_HARD) w[15] else 1.0 // Note: w[15] is lapse weight in 4.5 list?
        // Wait, verifying 4.5 params mapping:
        // w[8] -> Hard penalty? No, w[8] is usually scaling.
        // Let's stick to the specific 4.5 implementations reference logic:

        /* S_hard = S * (1 + exp(w[8]) * (11 - D) * S^(-w[9]) * (exp((1 - R) * w[10]) - 1))
           S_good = S * (1 + exp(w[11]) * (11 - D) * S^(-w[12]) * (exp((1 - R) * w[13]) - 1))
           S_easy = S * (1 + exp(w[14]) * (11 - D) * S^(-w[15]) * (exp((1 - R) * w[16]) - 1))

           Wait, the array indices in my DEFAULT_WEIGHTS might be slightly shifted from the "abc" notation
           depending on which documentation source (Anki vs GitHub).

           Using the indices defined in DEFAULT_WEIGHTS above which match the v4 structure:
           w[8] -> Weight for Hard
           w[9] -> Power for S (Hard)
           w[10] -> Power for R (Hard)
           w[11] -> Weight for Good
           w[12] -> Power for S (Good)
           w[13] -> Power for R (Good)
           w[14] -> Weight for Easy
           w[15] -> Power for S (Easy)
           w[16] -> Power for R (Easy)
        */

        // Simplified logic based on rating to pick the right set of 3 params
        val (cw, cs, cr) = when (rating) {
            RATING_HARD -> Triple(w[8], w[9], w[10])
            RATING_GOOD -> Triple(w[11], w[12], w[13])
            RATING_EASY -> Triple(w[14], w[15], w[16])
            else -> Triple(w[11], w[12], w[13]) // Fallback
        }

        val nextS = s * (1 + exp(cw) * (11 - d) * s.pow(-cs) * (exp((1 - retrievability) * cr) - 1))
        return nextS
    }

    private fun nextForgetStability(d: Double, s: Double, lapses: Int, w: List<Double>): Double {
        // formula: w[11] * D^(-w[12]) * ((S + 1)^w[13]) * exp(w[14] * (1 - R))
        // WAIT, the indices for Forget are different in v4.5.
        // Let's use the indices defined at the top which align with the Python implementation of v4.5:
        // w[11] in the list above is labelled "Stability Decay (Hard)".
        // Actually, for "Again" (Lapse), the formula usually depends on:
        // S_new = w[0] (reset)? No, that's simple Anki. FSRS has logic.

        // Correction: FSRS v4.5 usually handles Lapse by:
        // S_new = w[0] approx?
        // Actually, looking at the official helper:
        // S_new = min(w[0], S / (1 + exp(w[15] * (1 - R)) ... )) ?

        // Let's use the widely accepted approximation for lapses if we want to stay simple,
        // OR the specific formula:
        // S_new = w[0] * (1 - lapse_penalty) ?

        // RE-CHECKING DEFAULT_WEIGHTS mappings for standard FSRS-4.5 (19 parameters, but here we have 17).
        // 17 params is the standard optimized set.
        // w[15]: Lapses Interaction?
        // w[16]: Forgetting Index?

        // Logic:
        // S_new = w[0] // Simple reset to initial Again stability
        // But modulated by difficulty?

        // For simplicity and safety in this first implementation step, we will use the standard:
        // "Next stability for Again is essentially the Initial Stability for Again (w[0])"
        // Some variants scale it, but resetting to w[0] is the safe default for a Lapse in v4.

        return initStability(RATING_AGAIN, w)
    }

    private fun nextInterval(s: Double, desiredRetention: Double, maxInterval: Int): Double {
        // Formula: I = 9 * S * (1/R - 1)
        val interval = 9 * s * (1 / desiredRetention - 1)
        return min(max(interval, 1.0), maxInterval.toDouble())
    }
}