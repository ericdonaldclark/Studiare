package net.ericclark.studiare.data

import java.util.UUID

enum class MediaType(val value: Int) {
    PLAIN_TEXT(0),
    RICH_TEXT(1),
    AUDIO(2),
    IMAGE(3),
    VIDEO(4),
    WEB_LINK(5),
    HTML(6);

    companion object {
        fun fromInt(value: Int?) = entries.find { it.value == value } ?: PLAIN_TEXT
    }
}

data class NoteField(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val content: String,
    val type: MediaType = MediaType.PLAIN_TEXT
)