package org.javerland.homecenter.tv.ui.common

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DATE_TIME = DateTimeFormatter.ofPattern("d.M.yyyy HH:mm")
    .withLocale(Locale.forLanguageTag("sk"))

/** "1:23:45" or "4:07"—the hour part disappears when there is none. */
fun formatDuration(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = listOf("B", "kB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    return if (unit == 0) "%.0f %s".format(value, units[unit]) else "%.1f %s".format(value, units[unit])
}

/**
 * The server sends ISO-8601 instants as strings—the OpenAPI client keeps them that way
 * rather than pulling in a date library for two display-only fields. An unparseable value
 * is shown as nothing at all instead of an error.
 */
fun formatInstant(isoInstant: String?): String? {
    if (isoInstant.isNullOrBlank()) return null
    return runCatching {
        DATE_TIME.format(Instant.parse(isoInstant).atZone(ZoneId.systemDefault()))
    }.getOrNull()
}
