package vn.driverhotspot

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class TripOcrResult(
    val revenue: Double? = null,
    val timestamp: Long? = null,
    val pickup: String = "",
    val dropoff: String = "",
    val placeName: String = ""
)

object TripOcrParser {
    private val moneyRegex = Regex("""(?<!\d)-?\d(?:[\d., ]{2,}\d)?\s*(?:đ|vnđ|vnd)?""", RegexOption.IGNORE_CASE)
    private val dateRegex = Regex("""\b\d{1,2}[/-]\d{1,2}(?:[/-]\d{2,4})?\s+\d{1,2}:\d{2}\b""")
    private val dateFormats = listOf(
        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("vi", "VN")),
        SimpleDateFormat("dd-MM-yyyy HH:mm", Locale("vi", "VN")),
        SimpleDateFormat("dd/MM/yy HH:mm", Locale("vi", "VN")),
        SimpleDateFormat("dd-MM-yy HH:mm", Locale("vi", "VN")),
        SimpleDateFormat("dd/MM HH:mm", Locale("vi", "VN")),
        SimpleDateFormat("dd-MM HH:mm", Locale("vi", "VN"))
    )

    fun parse(rawText: String): TripOcrResult {
        val lines = rawText.lines().map { it.trim().replace(Regex("""\s+"""), " ") }.filter { it.isNotBlank() }
        val normalized = lines.map { it.lowercase(Locale("vi", "VN")).replace(Regex("""^\s*\d+\s*[.)-]?\s*"""), "").trim() }
        var revenue: Double? = null
        val revenueLabels = listOf("khách thanh toán", "thành tiền", "tổng tiền", "doanh thu",
            "tiền cuốc", "giá cước", "cước phí", "thu nhập", "tổng thu nhập")
        for (label in revenueLabels) {
            val index = normalized.indexOfFirst { it.contains(label) }
            if (index < 0) continue
            val sameLine = lines[index].substringAfter(':', "").ifBlank { lines[index].takeIf { it.contains(':') }.orEmpty() }
            val candidates = listOf(sameLine) + listOfNotNull(lines.getOrNull(index + 1))
            for (candidate in candidates) {
                val match = moneyRegex.findAll(candidate).lastOrNull() ?: continue
                val parsed = parseMoney(match.value)
                if (parsed != null) { revenue = parsed; break }
            }
            if (revenue != null) break
        }

        var timestamp: Long? = null
        val dateMatch = dateRegex.find(rawText)?.value
        if (dateMatch != null) {
            for (format in dateFormats) {
                val parsed = runCatching { format.isLenient = false; format.parse(dateMatch)?.time }.getOrNull()
                if (parsed != null) {
                    timestamp = parsed
                    if (!Regex("""[/-]\d{2,4}\s""").containsMatchIn(dateMatch)) {
                        timestamp = Calendar.getInstance().apply {
                            timeInMillis = parsed
                            set(Calendar.YEAR, Calendar.getInstance().get(Calendar.YEAR))
                        }.timeInMillis
                    }
                    break
                }
            }
        }
        // When no trip date is printed, use the Android status-bar clock and today's local date.
        if (timestamp == null) {
            val clock = lines.firstNotNullOfOrNull { Regex("""(\d{1,2}):(\d{2})""").find(it) }
            if (clock != null) {
                val hour = clock.groupValues[1].toIntOrNull()
                val minute = clock.groupValues[2].toIntOrNull()
                if (hour != null && minute != null && hour in 0..23 && minute in 0..59) {
                    timestamp = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, hour)
                        set(Calendar.MINUTE, minute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                }
            }
        }

        var pickup = ""
        var dropoff = ""
        var placeName = ""
        var section = ""
        val pickupLabelIndex = normalized.indexOfFirst { it.startsWith("điểm đón khách") || it == "điểm đón" || it == "đón khách" || it == "địa chỉ đón" }
        val orderCardIndex = normalized.indexOfFirst { it.startsWith("điểm lấy hàng") || it.startsWith("điểm lấy đồ") || it.startsWith("địa điểm lấy hàng") }
        if (pickupLabelIndex < 0 && orderCardIndex >= 0) {
            val cardLines = lines.drop(orderCardIndex + 1).take(8)
            placeName = cardLines.firstOrNull { line ->
                val n = line.lowercase(Locale("vi", "VN"))
                isLikelyLocation(line) && !isNonLocationLine(n) && !n.startsWith("mã đơn") && !n.startsWith("danh sách món") &&
                    !n.startsWith("gọi quán") && !n.startsWith("điều hướng") &&
                    !n.startsWith("đã nhận đơn") && !n.startsWith("địa đi") &&
                    !Regex("""^\d+\s*\.\s*""").containsMatchIn(n)
            }.orEmpty()
            pickup = cardLines.firstOrNull { line ->
                val n = line.lowercase(Locale("vi", "VN"))
                isLikelyLocation(line) && (n.contains("đường") || n.contains("phường") || n.contains("thành phố") ||
                    n.contains("quận") || n.contains("huyện") || Regex("""\b\d{1,4}[a-z]?\s+[^,]+""").containsMatchIn(n)) &&
                    !n.startsWith("mã đơn")
            }.orEmpty()
            if (pickup.isBlank() && placeName.isNotBlank() && Regex("""\b\d+[a-z]?\s+.+""", RegexOption.IGNORE_CASE).containsMatchIn(placeName)) {
                pickup = placeName
            }
        }
        for (i in lines.indices) {
            val line = lines[i]
            val n = normalized[i]
            if (n.startsWith("tên quán") || n.startsWith("tên địa điểm") || n.startsWith("nhà hàng") || n.startsWith("cửa hàng")) {
                placeName = inlineValue(line)
                section = ""
                continue
            }
            if (n.startsWith("điểm đón khách") || n == "điểm đón" || n == "đón khách" || n == "địa chỉ đón") {
                section = "pickup"; pickup = inlineValue(line); continue
            }
            if (n.startsWith("điểm trả khách") || n == "điểm trả" || n == "trả khách" || n == "địa chỉ trả") {
                section = "dropoff"; dropoff = inlineValue(line); continue
            }
            if (section == "pickup") {
                if (pickup.isBlank() && isLikelyLocation(line) && !isNonLocationLine(n)) pickup = line
                else if (pickup.isNotBlank() && isAddressContinuation(n)) pickup = appendAddress(pickup, line)
            } else if (section == "dropoff") {
                if (dropoff.isBlank() && isLikelyLocation(line) && !isNonLocationLine(n)) dropoff = line
                else if (dropoff.isNotBlank() && isAddressContinuation(n)) dropoff = appendAddress(dropoff, line)
            }
            if (n.startsWith("cước phí chuyến đi") || n == "cước phí" || n == "giảm giá" ||
                n.startsWith("khách thanh toán") || n == "nhắn tin" || n == "gọi điện") section = ""
        }
        return TripOcrResult(revenue, timestamp, pickup.trim(), dropoff.trim(), placeName.trim())
    }

    private fun isLikelyLocation(line: String): Boolean {
        val value = line.trim()
        if (value.length < 3) return false
        val letters = value.count { it.isLetter() }
        val digits = value.count { it.isDigit() }
        val symbols = value.count { !it.isLetterOrDigit() && !it.isWhitespace() }
        if (letters < 3 || symbols > letters + digits) return false
        if (Regex("""^[\\d\\s.,|<>/\\-]+$""").matches(value)) return false
        return true
    }
    private fun appendAddress(existing: String, next: String) = existing.trimEnd().trimEnd(',') + ", " + next.trimStart()
    private fun inlineValue(line: String) = line.substringAfter(':', "").trim()
    private fun isAddressContinuation(n: String) = listOf("đường", "phường", "thành phố", "quận", "huyện", "việt nam", "tỉnh").any(n::contains)
    private fun isNonLocationLine(n: String) = n.startsWith("cước phí") || n.startsWith("giảm giá") ||
        n.startsWith("khách thanh toán") || n.startsWith("nhận thanh toán") || n.startsWith("nhắn tin") ||
        n.startsWith("gọi điện") || n == "tiền mặt" || n.contains("km") || n.contains("phút") ||
        n.startsWith("thu nhập chuyến đi") || n.startsWith("khoảng cách") || n.startsWith("thời gian dự kiến")
    private fun parseMoney(raw: String): Double? {
        val digits = raw.filter(Char::isDigit)
        return digits.takeIf { it.length in 4..10 }?.toDoubleOrNull()
    }
}
