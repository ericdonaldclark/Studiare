package net.ericclark.studiare.data

data class LinkageSettings(
    val syncCardAdditions: Boolean = true,
    val syncCardDeletions: Boolean = false,
    val linkCardData: Boolean = true,
    val linkCardOrder: Boolean = true,
    val linkFieldConfig: Boolean = true,
    val linkMetadata: Boolean = true,
    val linkScoring: Boolean = true
)