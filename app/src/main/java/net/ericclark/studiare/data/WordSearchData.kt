package net.ericclark.studiare.data

data class WordSearchWord(
    val id: String,
    val word: String,
    val clue: String,
    val startX: Int,
    val startY: Int,
    val endX: Int,
    val endY: Int
) {
    val positions: List<Pair<Int, Int>>
        get() {
            val length = word.length
            val dx = if (startX == endX) 0 else if (startX < endX) 1 else -1
            val dy = if (startY == endY) 0 else if (startY < endY) 1 else -1
            return (0 until length).map { i -> (startX + i * dx) to (startY + i * dy) }
        }
}