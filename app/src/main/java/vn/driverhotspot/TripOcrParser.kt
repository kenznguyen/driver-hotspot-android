package vn.driverhotspot

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date
import java.util.regex.Pattern

data class TripOcrResult(val revenue: Double? = null, val timestamp: Long? = null, val pickup: String = "", val dropoff: String = "")

object TripOcrParser {
    private val moneyPattern = Pattern.compile("(?i)(?:\\b(?:tổng tiền|doanh thu|tiền cuốc|giá cước|thành tiền|thu nhập)\\b\\D{0,24})?([0-9][0-9., ]{2,})(?:\\s*(?:đ|vnđ|vnd))?")
    private val datePatterns = listOf(
        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("vi", "VN")),
        SimpleDateFormat("dd-MM-yyyy HH:mm", Locale("vi", "VN")),
        SimpleDateFormat("dd/MM HH:mm", Locale("vi", "VN")),
        SimpleDateFormat("dd-MM HH:mm", Locale("vi", "VN"))
    )
    fun parse(rawText: String): TripOcrResult {
        val lines = rawText.lines().map { it.trim() }.filter { it.isNotBlank() }
        val lower = rawText.lowercase(Locale.ROOT)
        var revenue: Double? = null
        val labels = listOf("tổng tiền", "doanh thu", "tiền cuốc", "giá cước", "thành tiền", "thu nhập", "tổng thu nhập", "số tiền", "cước phí")
        for (label in labels) {
            val idx = lower.indexOf(label)
            if (idx >= 0) {
                val tail = rawText.substring((idx + label.length).coerceAtMost(rawText.length)).take(80)
                val m = moneyPattern.matcher(tail)
                if (m.find()) { revenue = parseMoney(m.group(1)); if (revenue != null) break }
            }
        }
        var timestamp: Long? = null
        val dateMatcher = Pattern.compile("\\b\\d{1,2}[/-]\\d{1,2}(?:[/-]\\d{4})?\\s+\\d{1,2}:\\d{2}\\b").matcher(rawText)
        if (dateMatcher.find()) {
            val dateText = dateMatcher.group()
            for (format in datePatterns) {
                runCatching { format.isLenient = false; format.parse(dateText)?.time }.getOrNull()?.let { timestamp = it; if (dateText.count { c -> c == '/' || c == '-' } == 1) {
                    val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
                    val calendar = java.util.Calendar.getInstance().apply { timeInMillis = it; set(java.util.Calendar.YEAR, currentYear) }
                    timestamp = calendar.timeInMillis
                }; return@let }
                if (timestamp != null) break
            }
        }
        var pickup = ""
        var dropoff = ""
        lines.forEachIndexed { index, line ->
            val l = line.lowercase(Locale.ROOT)
            if (pickup.isBlank() && (l.startsWith("điểm đón") || l.startsWith("đón") || l.startsWith("đón khách") || l.startsWith("địa chỉ đón"))) pickup = line.substringAfter(':', "").trim().ifBlank { lines.getOrNull(index + 1).orEmpty() }
            if (dropoff.isBlank() && (l.startsWith("điểm trả") || l.startsWith("trả") || l.startsWith("trả khách") || l.startsWith("địa chỉ trả"))) dropoff = line.substringAfter(':', "").trim().ifBlank { lines.getOrNull(index + 1).orEmpty() }
        }
        return TripOcrResult(revenue, timestamp, pickup, dropoff)
    }
    private fun parseMoney(raw: String?): Double? {
        if (raw == null) return null
        val digits = raw.filter(Char::isDigit)
        return digits.takeIf { it.length in 4..10 }?.toDoubleOrNull()
    }
}
