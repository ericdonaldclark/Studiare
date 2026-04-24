package net.ericclark.studiare.data

import java.util.UUID
import kotlin.text.lowercase

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

    override fun toString(): String {
        return when (this) {
            PLAIN_TEXT -> "Plain Text"
            RICH_TEXT -> "Rich Text"
            AUDIO -> "Audio"
            IMAGE -> "Image"
            VIDEO -> "Video"
            WEB_LINK -> "Web Link"
            HTML -> "HTML"
        }
    }
}

data class NoteField(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val content: String,
    val type: MediaType = MediaType.PLAIN_TEXT
)