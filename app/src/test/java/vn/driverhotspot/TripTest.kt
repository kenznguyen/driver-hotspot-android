package vn.driverhotspot

import org.junit.Assert.assertEquals
import org.junit.Test

class TripTest {
    @Test fun netRevenueSubtractsCosts() {
        val trip = Trip(0, "A", 180000.0, 20000.0, 80, 60, "")
        assertEquals(160000.0, trip.net, 0.001)
    }
    @Test fun netPerBatteryPercentUsesConsumedBattery() {
        val trip = Trip(0, "A", 180000.0, 0.0, 80, 60, "")
        assertEquals(9000.0, trip.netPerPercent, 0.001)
    }
    @Test fun noBatteryConsumptionDoesNotDivideByZero() {
        val trip = Trip(0, "A", 1000.0, 0.0, 70, 70, "")
        assertEquals(0.0, trip.netPerPercent, 0.001)
    }
}
