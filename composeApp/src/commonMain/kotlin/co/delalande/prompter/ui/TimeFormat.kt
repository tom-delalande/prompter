package co.delalande.prompter.ui

import kotlin.time.Clock

fun formatLastUpdated(
    timestamp: Long,
    nowMillis: Long = Clock.System.now().toEpochMilliseconds()
): String? {
    if (timestamp <= 0) return null
    val diff = nowMillis - timestamp
    if (diff < 60_000L) return "just now"
    val minutes = diff / 60_000L
    if (minutes < 60L) return "$minutes minute${if (minutes == 1L) "" else "s"} ago"
    val hours = minutes / 60L
    if (hours < 24L) return "$hours hour${if (hours == 1L) "" else "s"} ago"
    val days = hours / 24L
    if (days < 30L) return "$days day${if (days == 1L) "" else "s"} ago"
    val months = days / 30L
    if (months < 12L) return "$months month${if (months == 1L) "" else "s"} ago"
    val years = months / 12L
    return "$years year${if (years == 1L) "" else "s"} ago"
}
