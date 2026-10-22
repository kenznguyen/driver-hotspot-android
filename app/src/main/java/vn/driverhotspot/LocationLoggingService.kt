package vn.driverhotspot

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*

class LocationLoggingService : Service() {
    private lateinit var client: FusedLocationProviderClient
    private lateinit var callback: LocationCallback
    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(NotificationChannel("driver_hotspot_tracking", "Nhật ký hành trình", NotificationManager.IMPORTANCE_LOW))
        val notification: Notification = NotificationCompat.Builder(this, "driver_hotspot_tracking")
            .setContentTitle("Driver Hotspot đang ghi hành trình")
            .setContentText("Chỉ lưu vị trí khi bạn bật theo dõi ca làm.")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation).setOngoing(true).build()
        startForeground(21, notification)
        client = LocationServices.getFusedLocationProviderClient(this)
        callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { savePoint(it) }
            }
        }
        val granted = ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 60_000L)
                .setMinUpdateIntervalMillis(30_000L).build()
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        } else stopSelf()
    }
    private fun savePoint(location: Location) {
        val prefs = getSharedPreferences("driver_hotspot", Context.MODE_PRIVATE)
        val old = prefs.getString("points", "").orEmpty()
        val battery = (getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager)
            .getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)
        val row = "${System.currentTimeMillis()},${location.latitude},${location.longitude},$battery"
        val rows = (old.split("\n").filter { it.isNotBlank() } + row).takeLast(5000)
        prefs.edit().putString("points", rows.joinToString("\n")).apply()
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY
    override fun onDestroy() { if (::client.isInitialized && ::callback.isInitialized) client.removeLocationUpdates(callback); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null
}
