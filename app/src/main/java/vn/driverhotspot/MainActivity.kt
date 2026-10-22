package vn.driverhotspot

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

data class Trip(val time: Long, val zone: String, val revenue: Double, val cost: Double, val startBattery: Int, val endBattery: Int, val note: String) {
    val net get() = revenue - cost
    val batteryUsed get() = max(0, startBattery - endBattery)
    val netPerPercent get() = if (batteryUsed > 0) net / batteryUsed else 0.0
}
class MainActivity : ComponentActivity() {
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val needed = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.POST_NOTIFICATIONS)
        permissionLauncher.launch(needed)
        setContent { MaterialTheme(colorScheme = darkColorScheme()) { Dashboard() } }
    }
    @Composable private fun Dashboard() {
        val prefs = getSharedPreferences("driver_hotspot", MODE_PRIVATE)
        var tracking by remember { mutableStateOf(prefs.getBoolean("tracking", false)) }
        var zone by remember { mutableStateOf("") }
        var revenue by remember { mutableStateOf("") }
        var cost by remember { mutableStateOf("0") }
        var startBattery by remember { mutableStateOf("80") }
        var endBattery by remember { mutableStateOf("70") }
        var note by remember { mutableStateOf("") }
        var refresh by remember { mutableIntStateOf(0) }
        val trips = remember(refresh) {
            runCatching { JSONArray(prefs.getString("trips", "[]")).let { a -> (0 until a.length()).map { i ->
                val o=a.getJSONObject(i); Trip(o.getLong("time"),o.optString("zone"),o.optDouble("revenue"),o.optDouble("cost"),o.optInt("startBattery"),o.optInt("endBattery"),o.optString("note"))
            }.reversed() } }.getOrDefault(emptyList())
        }
        val points = remember(refresh) { prefs.getString("points", "").orEmpty().lines().filter { it.count { c -> c == ',' } >= 3 } }
        val fmt = { v: Double -> String.format(Locale("vi","VN"), "%,.0f đ", v) }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("DRIVER HOTSPOT", style = MaterialTheme.typography.headlineMedium)
            Text("Nhật ký hành trình • Điểm nóng • Hiệu quả pin", color = Color.LightGray)
            Card { Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("THEO DÕI CA LÀM", style = MaterialTheme.typography.titleMedium)
                Text(if (tracking) "Đang ghi vị trí nền (chu kỳ khoảng 60 giây)" else "Đang tắt ghi vị trí")
                Switch(checked = tracking, onCheckedChange = { enabled ->
                    tracking = enabled; prefs.edit().putBoolean("tracking", enabled).apply()
                    val intent = Intent(this@MainActivity, LocationLoggingService::class.java)
                    if (enabled) {
                        if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                            ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                            ContextCompat.startForegroundService(this@MainActivity, intent)
                        } else { tracking = false; prefs.edit().putBoolean("tracking", false).apply(); permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }
                    } else stopService(intent)
                })
                Text("Vị trí chỉ được lưu trên thiết bị. Không đọc màn hình, không Accessibility, không giả lập GPS.", style = MaterialTheme.typography.bodySmall)
            } }
            Card { Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("NHẬP CUỐC THỦ CÔNG", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(zone, {zone=it}, label={Text("Khu vực / tên quán")}, modifier=Modifier.fillMaxWidth(), singleLine=true)
                OutlinedTextField(revenue, {revenue=it}, label={Text("Doanh thu (đ)")}, modifier=Modifier.fillMaxWidth(), singleLine=true)
                OutlinedTextField(cost, {cost=it}, label={Text("Chi phí cuốc (đ)")}, modifier=Modifier.fillMaxWidth(), singleLine=true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(startBattery, {startBattery=it.filter(Char::isDigit).take(3)}, label={Text("Pin đầu (%)")}, modifier=Modifier.weight(1f), singleLine=true)
                    OutlinedTextField(endBattery, {endBattery=it.filter(Char::isDigit).take(3)}, label={Text("Pin cuối (%)")}, modifier=Modifier.weight(1f), singleLine=true)
                }
                OutlinedTextField(note, {note=it}, label={Text("Ghi chú (tùy chọn)")}, modifier=Modifier.fillMaxWidth())
                Button(onClick = {
                    val r=revenue.replace(",","").replace(".","").toDoubleOrNull()
                    val c=cost.replace(",","").replace(".","").toDoubleOrNull()
                    val sb=startBattery.toIntOrNull(); val eb=endBattery.toIntOrNull()
                    if (zone.isNotBlank() && r != null && c != null && sb != null && eb != null && sb in 0..100 && eb in 0..100) {
                        val arr=runCatching { JSONArray(prefs.getString("trips","[]")) }.getOrDefault(JSONArray())
                        arr.put(JSONObject().put("time",System.currentTimeMillis()).put("zone",zone.trim()).put("revenue",r).put("cost",c).put("startBattery",sb).put("endBattery",eb).put("note",note.trim()))
                        prefs.edit().putString("trips",arr.toString()).apply()
                        zone=""; revenue=""; note=""; refresh++
                    }
                }, modifier=Modifier.fillMaxWidth()) { Text("Lưu cuốc") }
            } }
            val net = trips.sumOf { it.net }
            val used = trips.sumOf { it.batteryUsed }
            Card { Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("HIỆU QUẢ", style = MaterialTheme.typography.titleMedium)
                Text("Doanh thu ròng: ${fmt(net)}", style = MaterialTheme.typography.titleLarge)
                Text("Ròng trên 1% pin: ${if (used>0) fmt(net/used) else "Chưa đủ dữ liệu"}")
                Text("Số cuốc: ${trips.size} • Điểm GPS: ${points.size}")
            } }
            Card { Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("BẢN ĐỒ NHIỆT CÁ NHÂN", style = MaterialTheme.typography.titleMedium)
                Text("Màu thể hiện mật độ điểm ghi nhận theo cụm GPS (không phải số cuốc). Nhập tên khu vực khi lưu cuốc để so sánh doanh thu.")
                val buckets = points.mapNotNull { line -> line.split(",").takeIf { it.size >= 4 }?.let { it[1].toDoubleOrNull() to it[2].toDoubleOrNull() } }.filter { it.first != null && it.second != null }
                Canvas(Modifier.fillMaxWidth().height(180.dp)) {
                    drawRect(Color(0xFF14202A))
                    if (buckets.isNotEmpty()) {
                        val minLat=buckets.minOf { it.first!! }; val maxLat=buckets.maxOf { it.first!! }
                        val minLon=buckets.minOf { it.second!! }; val maxLon=buckets.maxOf { it.second!! }
                        buckets.forEach { pair ->
                            val x=if(maxLon-minLon<1e-6) size.width/2 else ((pair.second!!-minLon)/(maxLon-minLon)*size.width).toFloat()
                            val y=if(maxLat-minLat<1e-6) size.height/2 else (size.height-(pair.first!!-minLat)/(maxLat-minLat)*size.height).toFloat()
                            drawCircle(if(buckets.size>50) Color(0xFFE64A45) else Color(0xFFFFC857), radius=7f, center=androidx.compose.ui.geometry.Offset(x,y), alpha=0.65f)
                        }
                    }
                }
                Text("Hiện có ${points.size} điểm. Hãy bật theo dõi khi bắt đầu ca để tích lũy dữ liệu.")
            } }
            Text("NHẬT KÝ CUỐC", style = MaterialTheme.typography.titleMedium)
            if (trips.isEmpty()) Text("Chưa có cuốc. Dữ liệu mẫu không được tạo tự động.")
            trips.forEach { trip ->
                Card { Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(trip.zone, style = MaterialTheme.typography.titleMedium)
                    Text(SimpleDateFormat("dd/MM HH:mm", Locale("vi","VN")).format(Date(trip.time)))
                    Text("Ròng: ${fmt(trip.net)} • Pin: ${trip.batteryUsed}% • ${fmt(trip.netPerPercent)}/1% pin")
                    if(trip.note.isNotBlank()) Text(trip.note)
                } }
            }
            OutlinedButton(onClick = {
                val data = buildString {
                    append("timestamp,zone,revenue,cost,start_battery,end_battery,net\n")
                    trips.reversed().forEach { append("${it.time},\" ${it.zone.replace("\"","")}\",${it.revenue},${it.cost},${it.startBattery},${it.endBattery},${it.net}\n") }
                }
                val name="driver-hotspot-trips.csv"
                getExternalFilesDir(null)?.resolve(name)?.writeText(data)
                android.widget.Toast.makeText(this@MainActivity, "Đã xuất CSV vào thư mục Android/data/vn.driverhotspot/files", android.widget.Toast.LENGTH_LONG).show()
            }, modifier=Modifier.fillMaxWidth()) { Text("Xuất nhật ký CSV") }
        }
    }
}
