package net.ericclark.studiare.data

import androidx.compose.runtime.MutableState

data class CardEditorState(
    val id: String,
    var front: MutableState<String>,
    var back: MutableState<String>,
    var frontNotes: MutableState<String?>,
    var backNotes: MutableState<String?>,
    var difficulty: MutableState<DifficultySetting>,
    var isKnown: MutableState<Boolean>,
    var reviewedCount: MutableState<Int>,
    var gradedAttempts: MutableState<List<Long>>,
    var incorrectAttempts: MutableState<List<Long>>,
    var tags: MutableState<List<String>>,
    // Added new fields to State to preserve them
    var isSuspended: MutableState<Boolean>,
    var flag: MutableState<CardFlag>,
    val createdAt: MutableState<Long>,
    var updatedAt: MutableState<Long>
)
