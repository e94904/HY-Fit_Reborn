package com.example.hyfitlite

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hyfitlite.model.HealthMetrics
import com.example.hyfitlite.model.UserProfile
import com.example.hyfitlite.util.CsvManager
import com.example.hyfitlite.util.HealthCalculator
import com.example.hyfitlite.ble.BleScaleManager

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class MainActivity : ComponentActivity() {

    private lateinit var bleScaleManager: BleScaleManager
    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bleScaleManager = BleScaleManager(this)
        prefs = getSharedPreferences("hyfit_prefs", Context.MODE_PRIVATE)

        val profile = loadProfile(prefs)
        bleScaleManager.targetMacAddress = profile.macAddress

        setContent {
            HyFitApp(bleScaleManager, prefs)
        }
    }
}

// Dark Mode Palette
val DarkBg = Color(0xFF0F172A)
val SurfaceDark = Color(0xFF1E293B)
val CardBg = Color(0xFF334155)
val AccentCyan = Color(0xFF06B6D4)
val AccentGreen = Color(0xFF10B981)
val AccentPurple = Color(0xFF8B5CF6)
val AccentOrange = Color(0xFFF97316)
val TextPrimary = Color(0xFFF8FAFC)
val TextSecondary = Color(0xFF94A3B8)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HyFitApp(bleScaleManager: BleScaleManager, prefs: SharedPreferences) {
    val context = LocalContext.current

    // State
    var profile by remember { mutableStateOf(loadProfile(prefs)) }
    var history by remember { mutableStateOf(loadHistory(prefs)) }
    var currentMetrics by remember { mutableStateOf(history.firstOrNull() ?: HealthMetrics()) }
    var showProfileDialog by remember { mutableStateOf(prefs.getBoolean("first_launch", true)) }

    var lastWeightKg by remember { mutableStateOf(0.0) }
    var weightStableSince by remember { mutableStateOf(0L) }

    val scanState by bleScaleManager.scanState.collectAsState()
    val isScanning by bleScaleManager.isScanning.collectAsState()
    val discoveredScales by bleScaleManager.discoveredScales.collectAsState()

    // Activity Result Launchers
    val exportCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        uri?.let {
            val success = CsvManager.exportToCsv(context, it, history)
            Toast.makeText(context, if (success) "Exported successfully!" else "Export failed", Toast.LENGTH_SHORT).show()
        }
    }

    val importCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val imported = CsvManager.importFromCsv(context, it)
            if (imported.isNotEmpty()) {
                val updated = (imported + history).distinctBy { m -> m.timestamp }.sortedByDescending { m -> m.timestamp }
                history = updated
                saveHistory(prefs, updated)
                currentMetrics = updated.first()
                Toast.makeText(context, "Imported ${imported.size} records!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "No valid records found in CSV", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) {
            bleScaleManager.startScan()
        } else {
            Toast.makeText(context, "Bluetooth & Location permissions required", Toast.LENGTH_SHORT).show()
        }
    }

    // Auto-scan on startup if we have a paired scale and permissions
    LaunchedEffect(profile.macAddress) {
        if (profile.macAddress != null) {
            if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                bleScaleManager.startScan()
            }
        }
    }

    // Listen to BLE measurement
    LaunchedEffect(discoveredScales) {
        val newScale = discoveredScales.firstOrNull()
        if (newScale != null && profile.macAddress == null) {
            val updated = profile.copy(macAddress = newScale.macAddress)
            profile = updated
            saveProfile(prefs, updated)
            bleScaleManager.targetMacAddress = newScale.macAddress
            Toast.makeText(context, "Auto-Paired with ${newScale.deviceName}", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(scanState) {
        scanState?.let { state ->
            val calc = HealthCalculator.calculateMetrics(state.weightKg, state.impedance, profile)
            currentMetrics = calc

            if (state.weightKg != lastWeightKg) {
                lastWeightKg = state.weightKg
                weightStableSince = System.currentTimeMillis()
            }

            if (state.isFinalized) {
                val updated = (listOf(calc) + history).distinctBy { it.timestamp }.sortedByDescending { it.timestamp }
                history = updated
                saveHistory(prefs, updated)
                
                val weightStr = if (profile.isMetric) "${String.format("%.2f", calc.weightKg)} kg" else "${String.format("%.1f", calc.weightKg * 2.20462)} lbs"
                Toast.makeText(context, "Measurement Saved! ($weightStr)", Toast.LENGTH_LONG).show()
                
                lastWeightKg = 0.0
                weightStableSince = 0L
            }
        }
    }

    LaunchedEffect(lastWeightKg, weightStableSince) {
        if (lastWeightKg > 0.0) {
            kotlinx.coroutines.delay(8000)
            if (isScanning) {
                val calc = HealthCalculator.calculateMetrics(lastWeightKg, 0, profile)
                val updated = (listOf(calc) + history).distinctBy { it.timestamp }.sortedByDescending { it.timestamp }
                history = updated
                saveHistory(prefs, updated)
                
                val weightStr = if (profile.isMetric) "${String.format("%.2f", calc.weightKg)} kg" else "${String.format("%.1f", calc.weightKg * 2.20462)} lbs"
                Toast.makeText(context, "Measurement Saved! ($weightStr)", Toast.LENGTH_LONG).show()
                
                lastWeightKg = 0.0
                weightStableSince = 0L
            }
        }
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            background = DarkBg,
            surface = SurfaceDark,
            primary = AccentCyan,
            secondary = AccentGreen
        )
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.MonitorWeight, contentDescription = null, tint = AccentCyan)
                            Spacer(Modifier.width(8.dp))
                            Text("HY-Fit Reborn", fontWeight = FontWeight.Bold, color = TextPrimary)
                        }
                    },
                    actions = {
                        IconButton(onClick = { exportCsvLauncher.launch("hyfit_export.csv") }) {
                            Icon(Icons.Default.FileDownload, contentDescription = "Export CSV", tint = TextPrimary)
                        }
                        IconButton(onClick = { importCsvLauncher.launch("*/*") }) {
                            Icon(Icons.Default.FileUpload, contentDescription = "Import CSV", tint = TextPrimary)
                        }
                        IconButton(onClick = { showProfileDialog = true }) {
                            Icon(Icons.Default.Person, contentDescription = "Profile", tint = AccentCyan)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceDark)
                )
            },
            containerColor = DarkBg
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // BLE Scan Banner
                item {
                    ScanStatusCard(
                        isScanning = isScanning,
                        scanResult = scanState,
                        profile = profile,
                        onToggleScan = {
                            if (isScanning) {
                                bleScaleManager.stopScan()
                            } else {
                                checkPermissionsAndScan(context, permissionLauncher, bleScaleManager)
                            }
                        },
                        onUnpair = {
                            val updated = profile.copy(macAddress = null)
                            profile = updated
                            saveProfile(prefs, updated)
                            bleScaleManager.targetMacAddress = null
                            bleScaleManager.stopScan()
                        }
                    )
                }

                // Current Weight Card
                item {
                    MainWeightCard(metrics = currentMetrics, profile = profile)
                }

                // Health Metrics Grid
                item {
                    Text(
                        "Detailed Body Composition",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                item {
                    MetricsGrid(metrics = currentMetrics, profile = profile)
                }

                // Weigh-in History
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("History Log (${history.size})", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        if (history.isNotEmpty()) {
                            TextButton(onClick = {
                                history = emptyList()
                                saveHistory(prefs, emptyList())
                            }) {
                                Text("Clear", color = Color.Red)
                            }
                        }
                    }
                }
                
                if (history.isNotEmpty()) {
                    item {
                        WeightHistoryChart(history = history, profile = profile)
                    }
                }

                items(history) { record ->
                    HistoryItem(record = record, profile = profile, onDelete = {
                        val updated = history.filterNot { it.timestamp == record.timestamp }
                        history = updated
                        saveHistory(prefs, updated)
                    })
                }
            }
        }

        // Profile Setup Dialog
        if (showProfileDialog) {
            ProfileDialog(
                profile = profile,
                onDismiss = { 
                    showProfileDialog = false
                    prefs.edit().putBoolean("first_launch", false).apply()
                },
                onSave = { updated ->
                    profile = updated
                    saveProfile(prefs, updated)
                    showProfileDialog = false
                    prefs.edit().putBoolean("first_launch", false).apply()
                    Toast.makeText(context, "Profile Saved!", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
}

@Composable
fun ScanStatusCard(
    isScanning: Boolean,
    scanResult: com.example.hyfitlite.ble.ScaleScanResult?,
    profile: UserProfile,
    onToggleScan: () -> Unit,
    onUnpair: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when {
                        scanResult != null && scanResult.isFinalized -> "Scale Measured: ${if (profile.isMetric) "${String.format("%.2f", scanResult.weightKg)} kg" else "${String.format("%.1f", scanResult.weightKg * 2.20462)} lbs"}"
                        scanResult != null -> "Measuring: ${if (profile.isMetric) "${String.format("%.2f", scanResult.weightKg)} kg" else "${String.format("%.1f", scanResult.weightKg * 2.20462)} lbs"}..."
                        isScanning -> "Scanning for scale..."
                        else -> "Scale Offline"
                    },
                    fontWeight = FontWeight.Bold,
                    color = if (scanResult != null) AccentGreen else TextPrimary,
                    fontSize = 16.sp
                )
                Text(
                    text = scanResult?.deviceName ?: if (profile.macAddress != null) "Step on your scale to connect" else "No scale paired. Pair to start.",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
            Row {
                if (profile.macAddress != null) {
                    IconButton(onClick = onUnpair) {
                        Icon(Icons.Default.LinkOff, contentDescription = "Unpair", tint = Color.Red)
                    }
                }
                Button(
                    onClick = onToggleScan,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isScanning) Color.Red else AccentCyan
                    )
                ) {
                    Icon(if (isScanning) Icons.Default.Stop else Icons.Default.BluetoothSearching, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text(if (isScanning) "Stop" else if (profile.macAddress != null) "Connect" else "Pair Scale")
                }
            }
        }
    }
}

@Composable
fun MainWeightCard(metrics: HealthMetrics, profile: UserProfile) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(AccentCyan.copy(alpha = 0.2f), CardBg)
                    )
                )
                .padding(24.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text("CURRENT WEIGHT", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AccentCyan)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (profile.isMetric) "${String.format("%.2f", metrics.weightKg)} kg" else "${String.format("%.1f", metrics.weightKg * 2.20462)} lbs",
                    fontSize = 44.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextPrimary
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusBadge(text = "Score: ${metrics.physicalScore}", color = AccentGreen)
                    StatusBadge(text = metrics.figureType, color = AccentPurple)
                    StatusBadge(text = "BMI ${metrics.bmi}", color = AccentOrange)
                }
            }
        }
    }
}

@Composable
fun StatusBadge(text: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.2f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text = text,
            color = color,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun MetricsGrid(metrics: HealthMetrics, profile: UserProfile) {
    val weightSuffix = if (profile.isMetric) "kg" else "lbs"
    val muscle = if (profile.isMetric) String.format("%.2f", metrics.muscleMassKg) else String.format("%.1f", metrics.muscleMassKg * 2.20462)
    val bone = if (profile.isMetric) String.format("%.2f", metrics.boneMassKg) else String.format("%.1f", metrics.boneMassKg * 2.20462)
    val fatFree = if (profile.isMetric) String.format("%.2f", metrics.fatFreeWeightKg) else String.format("%.1f", metrics.fatFreeWeightKg * 2.20462)

    val items = listOf(
        MetricItemData("Body Fat", "${metrics.bodyFatPct}%", Icons.Default.Opacity, AccentOrange),
        MetricItemData("Body Water", "${metrics.waterPct}%", Icons.Default.WaterDrop, AccentCyan),
        MetricItemData("Muscle Mass", "$muscle $weightSuffix", Icons.Default.FitnessCenter, AccentGreen),
        MetricItemData("Skeletal Muscle", "${metrics.skeletalMusclePct}%", Icons.Default.DirectionsRun, AccentPurple),
        MetricItemData("Bone Mass", "$bone $weightSuffix", Icons.Default.Accessibility, AccentCyan),
        MetricItemData("Visceral Fat", "${metrics.visceralFat}", Icons.Default.Favorite, Color.Red),
        MetricItemData("BMR", "${metrics.bmrKcal} kcal", Icons.Default.LocalFireDepartment, AccentOrange),
        MetricItemData("Protein", "${metrics.proteinPct}%", Icons.Default.Restaurant, AccentGreen),
        MetricItemData("Physical Age", "${metrics.physicalAge} yrs", Icons.Default.Cake, AccentPurple),
        MetricItemData("Fat Free Wt", "$fatFree $weightSuffix", Icons.Default.MonitorWeight, AccentCyan)
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(2).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { item ->
                    Box(modifier = Modifier.weight(1f)) {
                        MetricCard(item)
                    }
                }
            }
        }
    }
}

data class MetricItemData(val title: String, val value: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val color: Color)

@Composable
fun MetricCard(data: MetricItemData) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(data.color.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(data.icon, contentDescription = null, tint = data.color, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(data.title, fontSize = 11.sp, color = TextSecondary)
                Text(data.value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            }
        }
    }
}

@Composable
fun HistoryItem(record: HealthMetrics, profile: UserProfile, onDelete: () -> Unit) {
    val sdf = java.text.SimpleDateFormat("MMM dd, yyyy h:mm a", java.util.Locale.getDefault())
    val dateStr = sdf.format(java.util.Date(record.timestamp))
    var expanded by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(if (profile.isMetric) "${String.format("%.2f", record.weightKg)} kg" else "${String.format("%.1f", record.weightKg * 2.20462)} lbs", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary)
                    Text(dateStr, fontSize = 12.sp, color = AccentCyan)
                    Text("Body Fat: ${record.bodyFatPct}% • BMI: ${record.bmi}", fontSize = 12.sp, color = TextSecondary)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = TextSecondary)
                }
            }
            
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(14.dp)) {
                    HorizontalDivider(color = CardBg, modifier = Modifier.padding(bottom = 8.dp))
                    MetricsGrid(metrics = record, profile = profile)
                }
            }
        }
    }
}

@Composable
fun WeightHistoryChart(history: List<HealthMetrics>, profile: UserProfile) {
    if (history.size < 2) return
    
    val sortedHistory = history.sortedBy { it.timestamp }
    val weights = sortedHistory.map { if (profile.isMetric) it.weightKg else it.weightKg * 2.20462 }
    
    val maxWeight = weights.maxOrNull() ?: return
    val minWeight = weights.minOrNull() ?: return
    
    // Add padding to max and min
    val range = maxOf(maxWeight - minWeight, 5.0)
    val upper = maxWeight + range * 0.1
    val lower = minWeight - range * 0.1
    val yRange = upper - lower

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        modifier = Modifier.fillMaxWidth().height(200.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Weight Trend", color = TextPrimary, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height
                val stepX = width / (weights.size - 1).coerceAtLeast(1)

                val points = weights.mapIndexed { index, weight ->
                    val x = index * stepX
                    val y = height - ((weight - lower) / yRange * height).toFloat()
                    androidx.compose.ui.geometry.Offset(x, y)
                }

                // Draw path
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(points.first().x, points.first().y)
                    points.forEach { point ->
                        lineTo(point.x, point.y)
                    }
                }

                drawPath(
                    path = path,
                    color = AccentCyan,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4.dp.toPx())
                )

                // Draw points
                points.forEach { point ->
                    drawCircle(
                        color = AccentCyan,
                        radius = 6.dp.toPx(),
                        center = point
                    )
                    drawCircle(
                        color = SurfaceDark,
                        radius = 4.dp.toPx(),
                        center = point
                    )
                }
            }
        }
    }
}

@Composable
fun ProfileDialog(profile: UserProfile, onDismiss: () -> Unit, onSave: (UserProfile) -> Unit) {
    var height by remember { mutableStateOf(profile.heightCm.toString()) }
    var heightFt by remember { mutableStateOf((profile.heightCm / 30.48).toInt().toString()) }
    var heightIn by remember { mutableStateOf(Math.round((profile.heightCm / 2.54) % 12.0).toInt().toString()) }
    var age by remember { mutableStateOf(profile.age.toString()) }
    var gender by remember { mutableStateOf(profile.gender) }
    var isMetric by remember { mutableStateOf(profile.isMetric) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("User Profile", color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    FilterChip(
                        selected = isMetric,
                        onClick = { isMetric = true },
                        label = { Text("Metric (kg, cm)") }
                    )
                    FilterChip(
                        selected = !isMetric,
                        onClick = { isMetric = false },
                        label = { Text("Imperial (lbs, ft/in)") }
                    )
                }

                if (isMetric) {
                    OutlinedTextField(
                        value = height,
                        onValueChange = { height = it },
                        label = { Text("Height (cm)") }
                    )
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = heightFt,
                            onValueChange = { heightFt = it },
                            label = { Text("ft") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = heightIn,
                            onValueChange = { heightIn = it },
                            label = { Text("in") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                OutlinedTextField(
                    value = age,
                    onValueChange = { age = it },
                    label = { Text("Age (years)") }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    FilterChip(
                        selected = gender == 1,
                        onClick = { gender = 1 },
                        label = { Text("Male") }
                    )
                    FilterChip(
                        selected = gender == 0,
                        onClick = { gender = 0 },
                        label = { Text("Female") }
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val computedHeight = if (isMetric) {
                    height.toIntOrNull() ?: 175
                } else {
                    val ft = heightFt.toIntOrNull() ?: 5
                    val inch = heightIn.toIntOrNull() ?: 9
                    Math.round((ft * 12 + inch) * 2.54).toInt()
                }

                onSave(
                    profile.copy(
                        heightCm = computedHeight,
                        age = age.toIntOrNull() ?: 25,
                        gender = gender,
                        isMetric = isMetric
                    )
                )
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

fun checkPermissionsAndScan(
    context: Context,
    launcher: androidx.activity.result.ActivityResultLauncher<Array<String>>,
    bleScaleManager: BleScaleManager
) {
    val perms = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        perms.add(Manifest.permission.BLUETOOTH_SCAN)
        perms.add(Manifest.permission.BLUETOOTH_CONNECT)
    }
    launcher.launch(perms.toTypedArray())
}

fun loadProfile(prefs: SharedPreferences): UserProfile {
    return UserProfile(
        heightCm = prefs.getInt("height", 175),
        age = prefs.getInt("age", 25),
        gender = prefs.getInt("gender", 1),
        isMetric = prefs.getBoolean("isMetric", true),
        macAddress = prefs.getString("macAddress", null)
    )
}

fun saveProfile(prefs: SharedPreferences, profile: UserProfile) {
    prefs.edit()
        .putInt("height", profile.heightCm)
        .putInt("age", profile.age)
        .putInt("gender", profile.gender)
        .putBoolean("isMetric", profile.isMetric)
        .putString("macAddress", profile.macAddress)
        .apply()
}

fun loadHistory(prefs: SharedPreferences): List<HealthMetrics> {
    val json = prefs.getString("history", null) ?: return emptyList()
    return try {
        val type = object : TypeToken<List<HealthMetrics>>() {}.type
        Gson().fromJson(json, type)
    } catch (e: Exception) {
        emptyList()
    }
}

fun saveHistory(prefs: SharedPreferences, history: List<HealthMetrics>) {
    prefs.edit().putString("history", Gson().toJson(history)).apply()
}
