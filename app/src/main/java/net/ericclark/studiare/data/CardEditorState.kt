package net.ericclark.studiare.data

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf

data class CardEditorState(
    val id: String,
    var front: MutableState<String>,
    var frontRichTextInfo: MutableState<String?>,
    var isFrontRichText: MutableState<Boolean>,
    var back: MutableState<String>,
    var backRichTextInfo: MutableState<String?>,
    var isBackRichText: MutableState<Boolean>,
    var frontNotes: MutableState<List<NoteField>>,
    var backNotes: MutableState<List<NoteField>>,
    var difficulty: MutableState<DifficultySetting>,
    var isKnown: MutableState<Boolean>,
    var reviewedCount: MutableState<Int>,
    var gradedAttempts: MutableState<List<Long>>,
    var incorrectAttempts: MutableState<List<Long>>,
    var reviewLogs: MutableState<List<ReviewLog>>,
    var absoluteDueDate: MutableState<Long?>,
    var tags: MutableState<List<String>>,
    // Added new fields to State to preserve them
    var isSuspended: MutableState<Boolean>,
    var flag: MutableState<CardFlag>,
    val createdAt: MutableState<Long>,
    var updatedAt: MutableState<Long>,
    // Read-only FSRS/history fields, carried through purely for display (e.g. the flip-to-info
    // card face) — never written back by any editor UI, so they're never part of CardDataForSave.
    val fsrsStability: MutableState<Double?> = mutableStateOf(null),
    val fsrsDifficulty: MutableState<Double?> = mutableStateOf(null),
    val fsrsElapsedDays: MutableState<Double?> = mutableStateOf(null),
    val fsrsScheduledDays: MutableState<Double?> = mutableStateOf(null),
    val fsrsState: MutableState<FsrsState?> = mutableStateOf(null),
    val fsrsLastReview: MutableState<Long?> = mutableStateOf(null),
    val fsrsLapses: MutableState<Int> = mutableStateOf(0),
    val lastReviewDurationMs: MutableState<Long> = mutableStateOf(0)
)
