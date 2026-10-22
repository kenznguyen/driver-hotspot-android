package vn.driverhotspot

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.regex.Pattern

data class TripOcrResult(val revenue: Double? = null, val timestamp: Long? = null, val pickup: String = "", val dropoff: String = "")

object TripOcrParser {
    private val moneyRegex = Regex("(?<!\\\\d)-?\\\\d(?:[\\\\d., ]{2,}\\\\d)?\\\\s*(?:đ|vnđ|vnd)?", RegexOption.IGNORE_CASE)
    private val dateRegex = Regex("\\\\b\\\\d{1,2}[/-]\\\\d{1,2}(?:[/-]\\\\d{2,4})?\\\\s+\\\\d{1,2}:\\\\d{2}\\\\b")
    private val dateFormats = listOf(
        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("vi", "VN")),
        SimpleDateFormat("dd-MM-yyyy HH:mm", Locale("vi", "VN")),
        SimpleDateFormat("dd/MM/yy HH:mm", Locale("vi", "VN")),
        SimpleDateFormat("dd-MM-yy HH:mm", Locale("vi", "VN")),
        SimpleDateFormat("dd/MM HH:mm", Locale("vi", "VN")),
        SimpleDateFormat("dd-MM HH:mm", Locale("vi", "VN"))
    )

    fun parse(rawText: String): TripOcrResult {
        val lines = rawText.lines().map { it.trim().replace(Regex("\\\\s+"), " ") }.filter { it.isNotBlank() }
        val normalized = lines.map { it.lowercase(Locale("vi", "VN")).replace('đ', 'd') }
        var revenue: Double? = null

        // On the Xanh SM "Thu nhập chuyến đi" screen, "Khách thanh toán"
        // is the final amount paid after discount; prefer it over the pre-discount fare.
        val revenueLabels = listOf(
            "khách thanh toán", "thành tiền", "tổng tiền", "doanh thu",
            "tiền cuốc", "giá cước", "cước phí", "thu nhập", "tổng thu nhập"
        )
        for (label in revenueLabels) {
            val index = normalized.indexOfFirst { it.contains(label) }
            if (index < 0) continue
            val candidates = listOf(lines[index]) + listOfNotNull(lines.getOrNull(index + 1))
            for (candidate in candidates) {
                val tail = candidate.substringAfter(':', candidate)
                val match = moneyRegex.findAll(tail).lastOrNull() ?: continue
                val parsed = parseMoney(match.value)
                if (parsed != null) { revenue = parsed; break }
            }
            if (revenue != null) break
        }

        var timestamp: Long? = null
        val dateMatch = dateRegex.find(rawText)?.value
        if (dateMatch != null) {
            for (format in dateFormats) {
                val parsed = runCatching {
                    format.isLenient = false
                    format.parse(dateMatch)?.time
                }.getOrNull()
                if (parsed != null) {
                    timestamp = parsed
                    if (!dateMatch.contains(Regex("[/-]\\\\d{2,4}\\\\s").find(dateMatch)?.value.orEmpty())) {
                        val calendar = Calendar.getInstance().apply {
                            timeInMillis = parsed
                            set(Calendar.YEAR, Calendar.getInstance().get(Calendar.YEAR))
                        }
                        timestamp = calendar.timeInMillis
                    }
                    break
                }
            }
        }

        var pickup = ""
        var dropoff = ""
        var section = ""
        val pickupHeaders = setOf("điểm đón khách", "điểm đón", "đón khách", "địa chỉ đón")
        val dropoffHeaders = setOf("điểm trả khách", "điểm trả", "trả khách", "địa chỉ trả")
        for (i in lines.indices) {
            val line = lines[i]
            val n = normalized[i]
            if (n in pickupHeaders || n.startsWith("điểm đón khách")) {
                section = "pickup"
                pickup = inlineValue(line)
                continue
            }
            if (n in dropoffHeaders || n.startsWith("điểm trả khách")) {
                section = "dropoff"
                dropoff = inlineValue(line)
                continue
            }
            // In the Xanh SM layout, the location heading is followed by a venue
            // name and then a street/ward address on the next line(s).
            if (section == "pickup" && pickup.isBlank() && !isNonLocationLine(n)) pickup = line
            else if (section == "pickup" && pickup.isNotBlank() && !isNonLocationLine(n) &&
                (n.contains("đường") || n.contains("phường") || n.contains("thành phố") || n.contains("quận") || n.contains("việt nam"))) {
                pickup = "$pickup, $line"
            }
            if (section == "dropoff" && dropoff.isBlank() && !isNonLocationLine(n)) dropoff = line
            else if (section == "dropoff" && dropoff.isNotBlank() && !isNonLocationLine(n) &&
                (n.contains("đường") || n.contains("phường") || n.contains("thành phố") || n.contains("quận") || n.contains("việt nam"))) {
                dropoff = "$dropoff, $line"
            }
            if (n in setOf("cước phí", "giảm giá", "khách thanh toán", "nhắn tin", "gọi điện") ||
                n.startsWith("cước phí") || n.startsWith("giảm giá") || n.startsWith("khách thanh toán")) section = ""
        }
        return TripOcrResult(revenue, timestamp, pickup.trim(), dropoff.trim())
    }

    private fun inlineValue(line: String): String =
        line.substringAfter(':', "").trim().takeIf { it.isNotBlank() }.orEmpty()

    private fun isNonLocationLine(line: String): Boolean =
        line.startsWith("cước phí") || line.startsWith("giảm giá") ||
        line.startsWith("khách thanh toán") || line.startsWith("nhận thanh toán") ||
        line.startsWith("nhắn tin") || line.startsWith("gọi điện") ||
        line == "tiền mặt" || line.contains("km") || line.contains("phút")

    private fun parseMoney(raw: String): Double? {
        val digits = raw.filter(Char::isDigit)
        return digits.takeIf { it.length in 4..10 }?.toDoubleOrNull()
    }
}
