package io.github.jvcsampaio.sonar.ui

import java.util.Locale

fun formatDuration(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0) / 1000)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    else String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
}

/** "2 h 14 min" for collection headers. */
fun formatTotalDuration(ms: Long): String {
    val minutes = ms / 60_000
    return if (minutes >= 60) "${minutes / 60} h ${minutes % 60} min" else "$minutes min"
}

fun formatBytes(bytes: Long): String = when {
    bytes >= 1L shl 30 -> String.format(Locale.ROOT, "%.1f GB", bytes / (1L shl 30).toDouble())
    bytes >= 1L shl 20 -> String.format(Locale.ROOT, "%.0f MB", bytes / (1L shl 20).toDouble())
    else -> String.format(Locale.ROOT, "%.0f KB", bytes / 1024.0)
}

fun formatAgo(time: Long, now: Long = System.currentTimeMillis()): String {
    if (time <= 0) return "nunca"
    val minutes = (now - time) / 60_000
    return when {
        minutes < 1 -> "agora mesmo"
        minutes < 60 -> "há $minutes min"
        minutes < 24 * 60 -> "há ${minutes / 60} h"
        else -> "há ${minutes / (24 * 60)} dias"
    }
}

fun songCount(count: Int) = if (count == 1) "1 música" else "$count músicas"

/** "Artist · 3:45", leaving out what is not known yet (shared links before download). */
fun artistAndDuration(artist: String, durationMs: Long): String =
    listOfNotNull(artist.takeIf { it.isNotBlank() }, durationMs.takeIf { it > 0 }?.let(::formatDuration))
        .joinToString(" · ")
        .ifEmpty { "Detalhes chegam com o download" }

/** ISO 8601 date, as everything in MERIDIANO: "2026-10-24". */
fun formatData(time: Long): String =
    java.time.Instant.ofEpochMilli(time).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString()

/** Log time stamp: the hour for today ("14:02"), the ISO month and day before that ("10-23"). */
fun formatCarimbo(time: Long, now: Long = System.currentTimeMillis()): String {
    val zone = java.time.ZoneId.systemDefault()
    val moment = java.time.Instant.ofEpochMilli(time).atZone(zone)
    val today = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    return if (moment.toLocalDate() == today) String.format(Locale.ROOT, "%02d:%02d", moment.hour, moment.minute)
    else String.format(Locale.ROOT, "%02d-%02d", moment.monthValue, moment.dayOfMonth)
}
