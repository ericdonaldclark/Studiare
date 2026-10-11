package net.ericclark.studiare.components

/**
 * Largest whole unit since [timestamp]: m, h, D, M (30 days) or Y (365 days) — anything under a
 * minute reads as "<1m" instead of a live seconds count, since a per-second value forced this to
 * keep re-rendering while it was still under a minute old.
 */
fun formatTimeAgo(timestamp: Long, now: Long = System.currentTimeMillis()): String {
    val seconds = ((now - timestamp) / 1000).coerceAtLeast(0)
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24
    return when {
        days >= 365 -> "${days / 365}Y"
        days >= 30 -> "${days / 30}M"
        days >= 1 -> "${days}D"
        hours >= 1 -> "${hours}h"
        minutes >= 1 -> "${minutes}m"
        else -> "<1m"
    }
}
