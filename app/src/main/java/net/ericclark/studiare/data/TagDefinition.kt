package net.ericclark.studiare.data

import java.util.UUID

data class TagDefinition(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    // Store color as an ARGB Int. Default to a generic color (e.g., specific Blue or Grey).
    val color: String = "#0D47A1",
    val createdAt: Long = System.currentTimeMillis()
) {
    constructor() : this(UUID.randomUUID().toString())
}
