package net.ericclark.studiare.data

import androidx.compose.runtime.MutableState

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
    var tags: MutableState<List<String>>,
    // Added new fields to State to preserve them
    var isSuspended: MutableState<Boolean>,
    var flag: MutableState<CardFlag>,
    val createdAt: MutableState<Long>,
    var updatedAt: MutableState<Long>
)
