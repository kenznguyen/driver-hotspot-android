package vn.driverhotspot

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
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

data class Trip(val time: Long, val zone: String, val revenue: Double, val cost: Double, val startBattery: Int, val endBattery: Int, val note: String, val pickup: String = "", val dropoff: String = "") {
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
        var note by remember { mutableStateOf("") }
        var pickup by remember { mutableStateOf("") }
        var dropoff by remember { mutableStateOf("") }
        var tripTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
        var ocrStatus by remember { mutableStateOf("OCR xử lý trên thiết bị; hãy kiểm tra trước khi lưu.") }
        var refresh by remember { mutableIntStateOf(0) }
        val trips = remember(refresh) {
            runCatching { JSONArray(prefs.getString("trips", "[]")).let { a -> (0 until a.length()).map { i ->
                val o=a.getJSONObject(i); Trip(o.getLong("time"),o.optString("zone"),o.optDouble("revenue"),o.optDouble("cost"),o.optInt("startBattery"),o.optInt("endBattery"),o.optString("note"),o.optString("pickup"),o.optString("dropoff"))
            }.reversed() } }.getOrDefault(emptyList())
        }
        val points = remember(refresh) { prefs.getString("points", "").orEmpty().lines().filter { it.count { c -> c == ',' } >= 3 } }
        val fmt = { v: Double -> String.format(Locale("vi","VN"), "%,.0f đ", v) }
        val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri != null) {
                ocrStatus = "Đang đọc ảnh trên thiết bị…"
                runCatching { InputImage.fromFilePath(this@MainActivity, uri) }
                    .onSuccess { image ->
                        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(image)
                            .addOnSuccessListener { result ->
                                val parsed = TripOcrParser.parse(result.text)
                                if (parsed.timestamp != null) tripTime = parsed.timestamp
                                if (parsed.pickup.isNotBlank()) pickup = parsed.pickup
                                if (parsed.dropoff.isNotBlank()) dropoff = parsed.dropoff
                                if (zone.isBlank()) zone = parsed.pickup.ifBlank { parsed.dropoff }
                                ocrStatus = "Đã đọc ảnh. Kiểm tra điểm đón và thời gian trước khi lưu."
                            }
                            .addOnFailureListener { ocrStatus = "Không đọc được ảnh. Hãy chọn ảnh khác hoặc nhập thủ công." }
                    }
                    .onFailure { ocrStatus = "Không mở được ảnh. Hãy chọn ảnh khác." }
            }
        }
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
                Text("NHẬP CUỐC XE", style = MaterialTheme.typography.titleMedium)
                OutlinedButton(onClick = { imagePicker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) { Text("Chọn ảnh chi tiết cuốc Xanh SM (OCR)") }
                Text(ocrStatus, style = MaterialTheme.typography.bodySmall, color = Color.LightGray)
                Text("Thời gian cuốc: " + SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("vi","VN")).format(Date(tripTime)), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(pickup, {pickup=it}, label={Text("Điểm đón (OCR, có thể sửa)")}, modifier=Modifier.fillMaxWidth(), singleLine=true)
                OutlinedTextField(dropoff, {dropoff=it}, label={Text("Điểm trả (OCR, có thể sửa)")}, modifier=Modifier.fillMaxWidth(), singleLine=true)
                Button(onClick = {
                    val address = pickup.trim()
                    if (address.isNotBlank()) {
                        val arr = runCatching { JSONArray(prefs.getString("trips", "[]")) }.getOrDefault(JSONArray())
                        val duplicate = (0 until arr.length()).any { i ->
                            val old = arr.getJSONObject(i)
                            old.optString("pickup").trim().equals(address, ignoreCase = true) && kotlin.math.abs(old.optLong("time") - tripTime) < 120000L
                        }
                        if (duplicate) { ocrStatus = "Có vẻ cuốc này đã lưu trong vòng 2 phút; không lưu trùng." }
                        else {
                            arr.put(JSONObject().put("time", tripTime).put("zone", address).put("revenue", 0).put("cost", 0).put("startBattery", 0).put("endBattery", 0).put("note", "").put("pickup", address).put("dropoff", dropoff.trim()))
                            prefs.edit().putString("trips", arr.toString()).apply()
                            pickup = ""; dropoff = ""; tripTime = System.currentTimeMillis(); ocrStatus = "Đã lưu quan sát điểm đón."; refresh++
                        }
                    } else ocrStatus = "Cần có địa chỉ điểm đón để đưa vào bản đồ nhiệt."
                }, modifier=Modifier.fillMaxWidth()) { Text("Lưu điểm nhu cầu") }
            } }
            var days by remember { mutableIntStateOf(7) }
            val cutoff = System.currentTimeMillis() - days.toLong() * 24L * 60L * 60L * 1000L
            val observations = trips.filter { it.time >= cutoff && it.pickup.isNotBlank() }
            val hotspots = observations.groupBy { it.pickup.trim().lowercase(Locale("vi", "VN")) }.entries.sortedByDescending { it.value.size }
            Card { Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("BẢN ĐỒ NHIỆT ĐIỂM ĐÓN", style = MaterialTheme.typography.titleMedium)
                Text("Chỉ phản ánh các cuốc bạn đã tự ghi nhận, không đại diện toàn bộ nhu cầu Xanh SM.", style = MaterialTheme.typography.bodySmall)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    listOf(1, 3, 7).forEachIndexed { i, d -> SegmentedButton(selected = days == d, onClick = { days = d }, shape = SegmentedButtonDefaults.itemShape(index = i, count = 3)) { Text("${d} ngày") } }
                }
                Text("${observations.size} cuốc đã ghi nhận • ${hotspots.size} địa chỉ điểm đón")
                val hours = observations.groupBy { SimpleDateFormat("HH", Locale.US).format(Date(it.time)) }.toSortedMap()
                Text("Số cuốc theo giờ", style = MaterialTheme.typography.titleSmall)
                hours.forEach { (hour, items) -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${hour}:00", modifier = Modifier.width(54.dp))
                    Box(Modifier.weight(1f).height(18.dp)) { Canvas(Modifier.fillMaxSize()) { drawRect(Color(0xFF263746)); drawRect(Color(0xFFE6A23C), size = androidx.compose.ui.geometry.Size(size.width * items.size.toFloat() / observations.size.coerceAtLeast(1), size.height)) } }
                    Text(items.size.toString())
                } }
                Text("Khu vực đông nhất", style = MaterialTheme.typography.titleSmall)
                if (hotspots.isEmpty()) Text("Chưa có dữ liệu trong ${days} ngày gần đây.")
                hotspots.take(10).forEachIndexed { i, entry -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${i + 1}.", modifier = Modifier.width(24.dp)); Text(entry.value.first().pickup, modifier = Modifier.weight(1f)); Text("${entry.value.size} cuốc")
                } }
                Text("GPS là dữ liệu di chuyển riêng, không được tính là cuốc. Bản đồ địa lý cần bổ sung geocoding và lớp bản đồ.", style = MaterialTheme.typography.bodySmall, color = Color.LightGray)
            } }
            Text("LỊCH SỬ ĐIỂM ĐÓN", style = MaterialTheme.typography.titleMedium)
            if (trips.isEmpty()) Text("Chưa có cuốc. Dữ liệu mẫu không được tạo tự động.")
            trips.forEach { trip ->
                Card { Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(trip.zone, style = MaterialTheme.typography.titleMedium)
                    Text(SimpleDateFormat("dd/MM HH:mm", Locale("vi","VN")).format(Date(trip.time)))

                    if(trip.pickup.isNotBlank()) Text("Đón: " + trip.pickup)
                    if(trip.dropoff.isNotBlank()) Text("Trả: " + trip.dropoff)
                } }
            }
            OutlinedButton(onClick = {
                val data = buildString {
                    append("timestamp,pickup,dropoff\n")
                    trips.reversed().forEach { append("${it.time},\"${it.pickup.replace("\"", "")}\",\"${it.dropoff.replace("\"", "")}\"\n") }
                }
                val name="driver-hotspot-trips.csv"
                getExternalFilesDir(null)?.resolve(name)?.writeText(data)
                android.widget.Toast.makeText(this@MainActivity, "Đã xuất CSV vào thư mục Android/data/vn.driverhotspot/files", android.widget.Toast.LENGTH_LONG).show()
            }, modifier=Modifier.fillMaxWidth()) { Text("Xuất nhật ký CSV") }
        }
    }
}
