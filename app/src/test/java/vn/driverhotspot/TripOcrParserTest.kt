package vn.driverhotspot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TripOcrParserTest {
    @Test fun extractsLabeledRevenueAndDate() {
        val parsed = TripOcrParser.parse("Ngày giờ: 21/10/2026 14:35\nTổng tiền: 85.000 đ")
        assertEquals(85000.0, parsed.revenue!!, 0.001)
        assertEquals("21/10/2026 14:35", SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("vi", "VN")).format(Date(parsed.timestamp!!)))
    }

    @Test fun parsesActualXanhSmTripIncomeScreenshotLayout() {
        val parsed = TripOcrParser.parse("""
            Thu nhập chuyến đi
            Khoảng cách
            2.9 km
            Thời gian dự kiến
            6,6 phút
            ĐIỂM ĐÓN KHÁCH
            Nướng Tự Chọn Ý Phát
            151/22A Đường Trần Hoàng Na, Phường Tân An,
            Thành phố Cần Thơ, Việt Nam
            ĐIỂM TRẢ KHÁCH
            Cổng B Đhct
            Phường Ninh Kiều, Thành phố Cần Thơ, Việt Nam
            CƯỚC PHÍ CHUYẾN ĐI
            Nhận thanh toán
            Tiền mặt
            Cước phí
            20.000đ
            Giảm giá
            -3.000đ
            Khách thanh toán
            17.000đ
        """.trimIndent())
        assertEquals(17000.0, parsed.revenue!!, 0.001)
        assertEquals("Nướng Tự Chọn Ý Phát, 151/22A Đường Trần Hoàng Na, Phường Tân An, Thành phố Cần Thơ, Việt Nam", parsed.pickup)
        assertEquals("Cổng B Đhct, Phường Ninh Kiều, Thành phố Cần Thơ, Việt Nam", parsed.dropoff)
        assertNull(parsed.timestamp)
    }

    @Test fun doesNotTreatDateOrDistanceAsRevenue() {
        val parsed = TripOcrParser.parse("Ngày giờ: 21/10/2026 14:35\nKhoảng cách: 2.9 km\nĐiểm đón khách\nCông viên\nĐiểm trả khách\nBến xe")
        assertNull(parsed.revenue)
        assertEquals("Công viên", parsed.pickup)
        assertEquals("Bến xe", parsed.dropoff)
    }
}
