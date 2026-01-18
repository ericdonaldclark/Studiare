package net.ericclark.studiare.data

data class CrosswordCell(
    val x: Int,
    val y: Int,
    val correctChar: Char,
    val associatedWordIds: List<String> // IDs of words crossing this cell
)
