package net.ericclark.studiare.data

data class LinkageSettings(
    val syncCardAdditions: Boolean = true,
    val syncCardDeletions: Boolean = false,
    val linkCardData: Boolean = true,
    // Card order (sorting) is deliberately NOT linkable — a set's sort order never touches the
    // shared Card row, so it has no shared-state conflict to protect against, the same reasoning
    // that already keeps Default Prompt Side unlinked. Every deck/set always has its own.
    val linkFieldConfig: Boolean = true,
    val linkMetadata: Boolean = true,
    val linkScoring: Boolean = true,
    // Normalization DOES need this, unlike Card Order/Prompt Side: it rewrites the shared Card
    // row's actual text on save, so a set left free to diverge from its parent here would fight
    // the parent over that row's formatting every time either deck's editor saves it.
    val linkNormalization: Boolean = true
)