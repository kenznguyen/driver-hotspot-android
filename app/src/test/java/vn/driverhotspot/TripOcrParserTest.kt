package vn.driverhotspot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TripOcrParserTest {
    @Test fun extractsLabeledRevenueWithoutTreatingDateAsMoney() {
        val parsed = TripOcrParser.parse("Ngày giờ: 21/10/2026 14:35\nTổng tiền: 85.000 đ")
        assertEquals(85000.0, parsed.revenue!!, 0.001)
        assertEquals("21/10/2026 14:35", java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale("vi", "VN")).format(java.util.Date(parsed.timestamp!!)))
    }
    @Test fun leavesRevenueEmptyWhenNoRevenueLabelExists() {
        val parsed = TripOcrParser.parse("Ngày giờ: 21/10/2026 14:35\nĐiểm đón: Công viên\nĐiểm trả: Bến xe")
        assertNull(parsed.revenue)
        assertEquals("Công viên", parsed.pickup)
        assertEquals("Bến xe", parsed.dropoff)
    }
}
