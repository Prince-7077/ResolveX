package com.resolvex.app.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Path
import android.location.LocationManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.resolvex.app.data.Complaint
import com.resolvex.app.data.ComplaintRepository
import com.resolvex.app.data.ComplaintStatus
import com.resolvex.app.location.GoogleMapsLauncher
import com.resolvex.app.location.LiveLocationState
import com.resolvex.app.location.RealtimeLocationTracker
import com.resolvex.app.ui.theme.*
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.infowindow.InfoWindow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.*

// ---------------------------------------------------------------------------
// Category → marker color mapping
// ---------------------------------------------------------------------------
fun categoryToMarkerColor(category: String): Int {
    return when {
        category.contains("Road", ignoreCase = true)       -> AndroidColor.parseColor("#FF6B35")
        category.contains("Water", ignoreCase = true)       -> AndroidColor.parseColor("#00B4D8")
        category.contains("Electric", ignoreCase = true)    -> AndroidColor.parseColor("#FFD60A")
        category.contains("Sanitation", ignoreCase = true) ||
        category.contains("Waste", ignoreCase = true)       -> AndroidColor.parseColor("#4ADE80")
        category.contains("Safety", ignoreCase = true) ||
        category.contains("Hazard", ignoreCase = true)      -> AndroidColor.parseColor("#FF5252")
        category.contains("Traffic", ignoreCase = true)     -> AndroidColor.parseColor("#8B5CF6")
        category.contains("Parks", ignoreCase = true) ||
        category.contains("Environment", ignoreCase = true) -> AndroidColor.parseColor("#34D399")
        category.contains("Health", ignoreCase = true)      -> AndroidColor.parseColor("#F472B6")
        else                                                 -> AndroidColor.parseColor("#94A3B8")
    }
}

fun categoryToEmoji(category: String): String {
    return when {
        category.contains("Road", ignoreCase = true)        -> "🛣️"
        category.contains("Water", ignoreCase = true)        -> "💧"
        category.contains("Electric", ignoreCase = true)     -> "⚡"
        category.contains("Sanitation", ignoreCase = true) ||
        category.contains("Waste", ignoreCase = true)        -> "🗑️"
        category.contains("Safety", ignoreCase = true) ||
        category.contains("Hazard", ignoreCase = true)       -> "⚠️"
        category.contains("Traffic", ignoreCase = true)      -> "🚦"
        category.contains("Parks", ignoreCase = true) ||
        category.contains("Environment", ignoreCase = true)  -> "🌳"
        category.contains("Health", ignoreCase = true)       -> "🏥"
        else                                                  -> "📍"
    }
}

fun statusToColor(status: ComplaintStatus): Color {
    return when (status) {
        ComplaintStatus.SUBMITTED          -> ElectricCyan
        ComplaintStatus.UNDER_REVIEW       -> SoftCyan
        ComplaintStatus.EVIDENCE_REQUESTED -> Color(0xFFF97316)
        ComplaintStatus.VERIFIED           -> ElectricBlue
        ComplaintStatus.ASSIGNED           -> NeonPurple
        ComplaintStatus.IN_PROGRESS        -> Color(0xFFFBBF24)
        ComplaintStatus.ACTION_TAKEN       -> Color(0xFF22D3EE)
        ComplaintStatus.RESOLVED           -> Color(0xFF4ADE80)
        ComplaintStatus.REJECTED           -> Color(0xFFFF5252)
    }
}

fun priorityToColor(priority: String): Color {
    return when (priority.lowercase(Locale.getDefault())) {
        "critical" -> Color(0xFFFF5252)
        "high"     -> Color(0xFFFF9800)
        "medium"   -> Color(0xFFFBBF24)
        else       -> Color(0xFF94A3B8)
    }
}

// ---------------------------------------------------------------------------
// Custom circular marker drawable drawn with Canvas
// ---------------------------------------------------------------------------
fun createMarkerDrawable(context: Context, color: Int, statusColor: Int): android.graphics.drawable.Drawable {
    return object : android.graphics.drawable.Drawable() {
        private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.FILL
        }
        private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = statusColor
            style = Paint.Style.STROKE
            strokeWidth = 5f
        }
        private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = AndroidColor.argb(80, 0, 0, 0)
            style = Paint.Style.FILL
        }
        private val tailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.FILL
        }

        override fun draw(canvas: Canvas) {
            val b = bounds
            val cx = b.exactCenterX()
            val cy = b.exactCenterY() - 8f
            val r = minOf(b.width(), b.height()) / 2f - 6f

            // Shadow
            canvas.drawCircle(cx + 2f, cy + 2f, r, shadowPaint)
            // Body
            canvas.drawCircle(cx, cy, r, bodyPaint)
            // Ring from status
            canvas.drawCircle(cx, cy, r, ringPaint)

            // Tail pin
            val path = Path().apply {
                moveTo(cx - 6f, cy + r - 2f)
                lineTo(cx + 6f, cy + r - 2f)
                lineTo(cx, cy + r + 14f)
                close()
            }
            canvas.drawPath(path, tailPaint)
        }

        override fun setAlpha(alpha: Int) { bodyPaint.alpha = alpha }
        override fun setColorFilter(cf: android.graphics.ColorFilter?) { bodyPaint.colorFilter = cf }
        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
        override fun getIntrinsicWidth() = 64
        override fun getIntrinsicHeight() = 80
    }
}

fun createUserLocationDrawable(): android.graphics.drawable.Drawable {
    return object : android.graphics.drawable.Drawable() {
        private val outerPulsePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.argb(80, 0, 240, 255)
            style = Paint.Style.FILL
        }
        private val whiteRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        private val innerBluePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.parseColor("#007AFF")
            style = Paint.Style.FILL
        }

        override fun draw(canvas: Canvas) {
            val b = bounds
            val cx = b.exactCenterX()
            val cy = b.exactCenterY()
            val r = minOf(b.width(), b.height()) / 2f
            canvas.drawCircle(cx, cy, r, outerPulsePaint)
            canvas.drawCircle(cx, cy, r * 0.5f, innerBluePaint)
            canvas.drawCircle(cx, cy, r * 0.5f, whiteRingPaint)
        }

        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(cf: android.graphics.ColorFilter?) {}
        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
        override fun getIntrinsicWidth() = 56
        override fun getIntrinsicHeight() = 56
    }
}

// ---------------------------------------------------------------------------
// Main Composable
// ---------------------------------------------------------------------------
@Composable
fun CivicMapScreen(navController: NavController) {
    val context = LocalContext.current
    val repo = remember { ComplaintRepository.getInstance(context) }
    val tracker = remember { RealtimeLocationTracker(context) }

    var allComplaints by remember { mutableStateOf<List<Complaint>>(emptyList()) }
    var mappableComplaints by remember { mutableStateOf<List<Complaint>>(emptyList()) }
    var selectedComplaint by remember { mutableStateOf<Complaint?>(null) }

    // Filters
    var filterCategory by remember { mutableStateOf<String?>(null) }
    var filterStatus by remember { mutableStateOf<ComplaintStatus?>(null) }
    var filterPriority by remember { mutableStateOf<String?>(null) }
    var showFiltersPanel by remember { mutableStateOf(false) }

    // Real-time Live Location state
    var liveLocationState by remember { mutableStateOf(tracker.currentState) }
    var showNearby by remember { mutableStateOf(false) }

    // MapView ref
    val mapViewRef = remember { mutableStateOf<MapView?>(null) }

    fun reload() {
        allComplaints = repo.getPublicComplaints()
        mappableComplaints = allComplaints.filter { it.hasValidCoordinates() }
    }

    LaunchedEffect(Unit) { reload() }

    // Ensure continuous tracking stops when screen leaves
    DisposableEffect(Unit) {
        onDispose {
            tracker.stopContinuousTracking()
        }
    }

    // Filtered and nearby-sorted list
    val displayComplaints = remember(
        mappableComplaints,
        filterCategory,
        filterStatus,
        filterPriority,
        showNearby,
        liveLocationState.latitude,
        liveLocationState.longitude
    ) {
        var list = mappableComplaints
        filterCategory?.let { cat -> list = list.filter { it.category.contains(cat, ignoreCase = true) } }
        filterStatus?.let { st -> list = list.filter { it.status == st } }
        filterPriority?.let { pri -> list = list.filter { it.priority.equals(pri, ignoreCase = true) } }

        val uLat = liveLocationState.latitude
        val uLon = liveLocationState.longitude

        if (showNearby && uLat != null && uLon != null) {
            list = list.filter { c ->
                val dlat = Math.toRadians(c.latitude!! - uLat)
                val dlon = Math.toRadians(c.longitude!! - uLon)
                val sinDlat = sin(dlat / 2)
                val sinDlon = sin(dlon / 2)
                val a = sinDlat * sinDlat + cos(Math.toRadians(uLat)) *
                        cos(Math.toRadians(c.latitude!!)) * sinDlon * sinDlon
                val dist = 6371000.0 * 2 * atan2(sqrt(a), sqrt(1 - a))
                dist <= 15000.0 // 15 km radius for nearby
            }.sortedBy { c ->
                val dlat = Math.toRadians(c.latitude!! - uLat)
                val dlon = Math.toRadians(c.longitude!! - uLon)
                val sinDlat = sin(dlat / 2)
                val sinDlon = sin(dlon / 2)
                val a = sinDlat * sinDlat + cos(Math.toRadians(uLat)) *
                        cos(Math.toRadians(c.latitude!!)) * sinDlon * sinDlon
                6371000.0 * 2 * atan2(sqrt(a), sqrt(1 - a))
            }
        }
        list
    }

    val locationPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val granted = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                      perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            tracker.startContinuousTracking { updated ->
                liveLocationState = updated
                if (updated.latitude != null && updated.longitude != null) {
                    mapViewRef.value?.controller?.animateTo(GeoPoint(updated.latitude, updated.longitude))
                    mapViewRef.value?.controller?.setZoom(14.5)
                }
            }
        } else {
            Toast.makeText(context, "Location permission required for live map tracking", Toast.LENGTH_SHORT).show()
        }
    }

    val gradient = Brush.verticalGradient(listOf(NavyDeep, NavyDark, Color(0xFF040817)))

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradient)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Top Navigation Bar ──────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(
                        onClick = {
                            if (!navController.popBackStack()) {
                                navController.navigate("citizen_dashboard") {
                                    popUpTo("civic_map") { inclusive = true }
                                }
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("← Back", color = SoftCyan, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    Column {
                        Text(
                            text = "Live Civic Issue Map",
                            color = TextPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${displayComplaints.size} of ${mappableComplaints.size} reports visible",
                            color = ElectricCyan,
                            fontSize = 11.sp
                        )
                    }
                }

                // Quick Refresh button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(NavyCard)
                        .border(1.dp, Color(0xFF233554), RoundedCornerShape(8.dp))
                        .clickable { reload() }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text("↺ Reload", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            // ── Controls Row (Responsive, no text clipping) ────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Live GPS Toggle button
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (liveLocationState.isTracking) Color(0xFF064E3B) else NavyCard)
                        .border(1.dp, if (liveLocationState.isTracking) Color(0xFF10B981) else Color(0xFF233554), RoundedCornerShape(10.dp))
                        .clickable {
                            if (liveLocationState.isTracking) {
                                tracker.stopContinuousTracking()
                                liveLocationState = tracker.currentState
                                showNearby = false
                            } else {
                                if (tracker.hasLocationPermission()) {
                                    tracker.startContinuousTracking { updated ->
                                        liveLocationState = updated
                                        if (updated.latitude != null && updated.longitude != null) {
                                            mapViewRef.value?.controller?.animateTo(GeoPoint(updated.latitude, updated.longitude))
                                            mapViewRef.value?.controller?.setZoom(14.5)
                                        }
                                    }
                                    showNearby = true
                                } else {
                                    locationPermLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                            Manifest.permission.ACCESS_COARSE_LOCATION
                                        )
                                    )
                                }
                            }
                        }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(if (liveLocationState.isTracking) "🟢" else "📍", fontSize = 11.sp)
                        Text(
                            text = if (liveLocationState.isTracking) "GPS Active" else "Track My Location",
                            color = if (liveLocationState.isTracking) Color(0xFF34D399) else TextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }

                // Near Me button (15km radius)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (showNearby) ElectricCyan.copy(alpha = 0.2f) else NavyCard)
                        .border(1.dp, if (showNearby) ElectricCyan else Color(0xFF233554), RoundedCornerShape(10.dp))
                        .clickable {
                            if (!tracker.hasLocationPermission()) {
                                locationPermLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            } else {
                                if (!liveLocationState.isTracking) {
                                    tracker.startContinuousTracking { updated ->
                                        liveLocationState = updated
                                    }
                                }
                                showNearby = !showNearby
                            }
                        }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("🎯", fontSize = 11.sp)
                        Text(
                            text = if (showNearby) "Nearby (15km) ✓" else "Nearby Issues",
                            color = if (showNearby) ElectricCyan else TextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }

                // Filter toggle
                Box(
                    modifier = Modifier
                        .weight(0.8f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (showFiltersPanel) NeonPurple.copy(alpha = 0.25f) else NavyCard)
                        .border(1.dp, if (showFiltersPanel) NeonPurple else Color(0xFF233554), RoundedCornerShape(10.dp))
                        .clickable { showFiltersPanel = !showFiltersPanel }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("⚡", fontSize = 11.sp)
                        Text(
                            text = "Filters",
                            color = if (showFiltersPanel) NeonPurple else TextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // ── Live Location Telemetry Banner (Real coordinates, accuracy & time) ──
            if (liveLocationState.isTracking && liveLocationState.latitude != null) {
                val latStr = String.format(Locale.US, "%.5f", liveLocationState.latitude)
                val lonStr = String.format(Locale.US, "%.5f", liveLocationState.longitude)
                val accStr = liveLocationState.accuracyMeters?.let { " • ±${it.roundToInt()}m accuracy" } ?: ""
                val provStr = " • via ${liveLocationState.provider ?: "GPS"}"

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 2.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF042F2E))
                        .border(0.5.dp, Color(0xFF14B8A6), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🛰️ $latStr° N, $lonStr° E$accStr$provStr",
                            color = Color(0xFF5EEAD4),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Open in Maps",
                                color = ElectricCyan,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clickable {
                                        liveLocationState.latitude?.let { lat ->
                                            liveLocationState.longitude?.let { lon ->
                                                GoogleMapsLauncher.openLocation(context, lat, lon, "My Live Location")
                                            }
                                        }
                                    }
                                    .padding(horizontal = 4.dp)
                            )
                            Text(
                                text = "Stop",
                                color = Color(0xFFF87171),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clickable {
                                        tracker.stopContinuousTracking()
                                        liveLocationState = tracker.currentState
                                    }
                                    .padding(horizontal = 4.dp)
                            )
                        }
                    }
                }
            } else if (!liveLocationState.errorMessage.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 2.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF2E1010))
                        .border(0.5.dp, Color(0xFFEF4444), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "⚠️ ${liveLocationState.errorMessage}",
                        color = Color(0xFFFCA5A5),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // ── Filter panel ─────────────────────────────────────────────
            AnimatedVisibility(visible = showFiltersPanel, enter = fadeIn(), exit = fadeOut()) {
                FilterPanel(
                    filterCategory = filterCategory,
                    filterStatus = filterStatus,
                    filterPriority = filterPriority,
                    onCategoryChange = { filterCategory = it },
                    onStatusChange = { filterStatus = it },
                    onPriorityChange = { filterPriority = it },
                    onClearAll = {
                        filterCategory = null; filterStatus = null; filterPriority = null
                        showNearby = false
                    }
                )
            }

            // ── Map ───────────────────────────────────────────────────────
            Box(modifier = Modifier.weight(1f)) {
                if (mappableComplaints.isEmpty() && allComplaints.isNotEmpty()) {
                    // Complaints exist but none have GPS
                    MapEmptyState(
                        message = "No GPS coordinates on file",
                        subMessage = "Tap 'Detect GPS' when raising a complaint to plot it on this map."
                    )
                } else if (allComplaints.isEmpty()) {
                    MapEmptyState(
                        message = "No Civic Complaints Yet",
                        subMessage = "Submit your first civic complaint with GPS coordinates to see it appear here."
                    )
                } else {
                    OsmMapView(
                        complaints = displayComplaints,
                        userLocationState = liveLocationState,
                        mapViewRef = mapViewRef,
                        onMarkerTapped = { selectedComplaint = it }
                    )
                    // Count badge
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(10.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(NavyDark.copy(alpha = 0.92f))
                            .border(1.dp, Color(0xFF233554), RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "${displayComplaints.size} of ${mappableComplaints.size} mapped",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        // ── Complaint Detail Bottom Sheet ─────────────────────────────────
        selectedComplaint?.let { complaint ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable { selectedComplaint = null },
                contentAlignment = Alignment.BottomCenter
            ) {
                MapComplaintDetailCard(
                    complaint = complaint,
                    onDismiss = { selectedComplaint = null },
                    onTrack = {
                        selectedComplaint = null
                        navController.navigate("track_complaint/${complaint.id}")
                    }
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// OSMDroid Map View
// ---------------------------------------------------------------------------
@Composable
fun OsmMapView(
    complaints: List<Complaint>,
    userLocationState: LiveLocationState,
    mapViewRef: MutableState<MapView?>,
    onMarkerTapped: (Complaint) -> Unit
) {
    val context = LocalContext.current

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            Configuration.getInstance().userAgentValue = "ResolveX/1.0 (com.resolvex.app)"
            MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                controller.setZoom(5.5)
                // Default center: India
                controller.setCenter(GeoPoint(20.5937, 78.9629))
                isHorizontalMapRepetitionEnabled = false
                isVerticalMapRepetitionEnabled = false
                mapViewRef.value = this
            }
        },
        update = { mapView ->
            mapView.overlays.clear()
            // Close any open info windows by removing all overlays
            InfoWindow.closeAllInfoWindowsOn(mapView)

            // Plot real-time user location marker if GPS fix is active
            val uLat = userLocationState.latitude
            val uLon = userLocationState.longitude
            if (uLat != null && uLon != null) {
                val userMarker = Marker(mapView).apply {
                    position = GeoPoint(uLat, uLon)
                    icon = createUserLocationDrawable()
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    title = "Your Real-Time Location"
                    snippet = userLocationState.accuracyMeters?.let { "Accuracy: ±${it.roundToInt()}m" } ?: "Live GPS Fix"
                    infoWindow = null
                }
                mapView.overlays.add(userMarker)
            }

            complaints.forEach { complaint ->
                val lat = complaint.latitude ?: return@forEach
                val lon = complaint.longitude ?: return@forEach
                val geoPoint = GeoPoint(lat, lon)

                val markerColor = categoryToMarkerColor(complaint.category)
                val statusInt = when (complaint.status) {
                    ComplaintStatus.SUBMITTED          -> AndroidColor.parseColor("#00F0FF")
                    ComplaintStatus.UNDER_REVIEW       -> AndroidColor.parseColor("#38BDF8")
                    ComplaintStatus.EVIDENCE_REQUESTED -> AndroidColor.parseColor("#F97316")
                    ComplaintStatus.VERIFIED           -> AndroidColor.parseColor("#007AFF")
                    ComplaintStatus.ASSIGNED           -> AndroidColor.parseColor("#8B5CF6")
                    ComplaintStatus.IN_PROGRESS        -> AndroidColor.parseColor("#FBBF24")
                    ComplaintStatus.ACTION_TAKEN       -> AndroidColor.parseColor("#22D3EE")
                    ComplaintStatus.RESOLVED           -> AndroidColor.parseColor("#4ADE80")
                    ComplaintStatus.REJECTED           -> AndroidColor.parseColor("#FF5252")
                }

                val marker = Marker(mapView).apply {
                    position = geoPoint
                    icon = createMarkerDrawable(context, markerColor, statusInt)
                    setAnchor(Marker.ANCHOR_CENTER, 0.85f)
                    title = complaint.title
                    snippet = complaint.id
                    infoWindow = null
                    setOnMarkerClickListener { _, _ ->
                        onMarkerTapped(complaint)
                        true
                    }
                }
                mapView.overlays.add(marker)
            }

            mapView.invalidate()
        }
    )

    DisposableEffect(Unit) {
        onDispose {
            mapViewRef.value?.onDetach()
        }
    }
}

// ---------------------------------------------------------------------------
// Filter Panel
// ---------------------------------------------------------------------------
@Composable
fun FilterPanel(
    filterCategory: String?,
    filterStatus: ComplaintStatus?,
    filterPriority: String?,
    onCategoryChange: (String?) -> Unit,
    onStatusChange: (ComplaintStatus?) -> Unit,
    onPriorityChange: (String?) -> Unit,
    onClearAll: () -> Unit
) {
    val categories = listOf(
        "Roads", "Water", "Electricity", "Sanitation", "Safety", "Traffic", "Parks", "Health"
    )
    val statuses = ComplaintStatus.values().toList()
    val priorities = listOf("Low", "Medium", "High", "Critical")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(NavyDark)
            .padding(bottom = 1.dp)
            .background(NavyCard)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Filter Complaints", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            TextButton(onClick = onClearAll) {
                Text("Clear All", color = Color(0xFFFF6B6B), fontSize = 12.sp)
            }
        }
        // Category row
        Text("Category", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(categories) { cat ->
                val sel = filterCategory == cat
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (sel) ElectricCyan.copy(alpha = 0.2f) else NavyCard)
                        .border(1.dp, if (sel) ElectricCyan else Color(0xFF233554), RoundedCornerShape(6.dp))
                        .clickable { onCategoryChange(if (sel) null else cat) }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(cat, color = if (sel) ElectricCyan else TextSecondary, fontSize = 11.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
        // Status row
        Text("Status", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(statuses) { st ->
                val sel = filterStatus == st
                val stColor = statusToColor(st)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (sel) stColor.copy(alpha = 0.2f) else NavyCard)
                        .border(1.dp, if (sel) stColor else Color(0xFF233554), RoundedCornerShape(6.dp))
                        .clickable { onStatusChange(if (sel) null else st) }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(st.displayName, color = if (sel) stColor else TextSecondary, fontSize = 11.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
        // Priority row
        Text("Priority", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(priorities) { pri ->
                val sel = filterPriority == pri
                val priColor = priorityToColor(pri)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (sel) priColor.copy(alpha = 0.2f) else NavyCard)
                        .border(1.dp, if (sel) priColor else Color(0xFF233554), RoundedCornerShape(6.dp))
                        .clickable { onPriorityChange(if (sel) null else pri) }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(pri, color = if (sel) priColor else TextSecondary, fontSize = 11.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Map Empty State
// ---------------------------------------------------------------------------
@Composable
fun MapEmptyState(message: String, subMessage: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .padding(32.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(NavyCard)
                .border(1.dp, Color(0xFF1E2E4E), RoundedCornerShape(20.dp))
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("🗺️", fontSize = 44.sp)
            Text(message, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(subMessage, color = TextMuted, fontSize = 12.sp, textAlign = TextAlign.Center, lineHeight = 17.sp)
        }
    }
}

// ---------------------------------------------------------------------------
// Complaint Detail Card (bottom sheet)
// ---------------------------------------------------------------------------
@Composable
fun MapComplaintDetailCard(
    complaint: Complaint,
    onDismiss: () -> Unit,
    onTrack: () -> Unit
) {
    val context = LocalContext.current
    val dateStr = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(complaint.createdAt))
    val statusColor = statusToColor(complaint.status)
    val priorityColor = priorityToColor(complaint.priority)
    val emoji = categoryToEmoji(complaint.category)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(NavyDark)
            .border(1.dp, Color(0xFF1E3054), RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .clickable { /* consume clicks so they don't dismiss */ }
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        // Handle
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color(0xFF2E4070))
                .align(Alignment.CenterHorizontally)
        )

        Spacer(Modifier.height(14.dp))

        // Category emoji + title row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF131D33)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(emoji, fontSize = 18.sp)
                }
                Column {
                    Text(
                        text = complaint.title,
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 220.dp)
                    )
                    Text(complaint.category, color = SoftCyan, fontSize = 11.sp)
                }
            }
            TextButton(onClick = onDismiss) {
                Text("✕", color = TextMuted, fontSize = 16.sp)
            }
        }

        Spacer(Modifier.height(14.dp))

        // Status & Priority chips
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(statusColor.copy(alpha = 0.15f))
                    .border(1.dp, statusColor.copy(alpha = 0.6f), RoundedCornerShape(50))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(complaint.status.displayName, color = statusColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(priorityColor.copy(alpha = 0.15f))
                    .border(1.dp, priorityColor.copy(alpha = 0.6f), RoundedCornerShape(50))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text("${complaint.priority} Priority", color = priorityColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(12.dp))

        // Details grid
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(NavyCard)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            DetailRow(label = "ID", value = complaint.id, valueColor = ElectricCyan)
            DetailRow(label = "Reported", value = dateStr)
            DetailRow(label = "Location", value = complaint.locationAddress.take(60))
            if (complaint.latitude != null && complaint.longitude != null) {
                val coordStr = "${String.format(Locale.US, "%.5f", complaint.latitude)}° N, ${String.format(Locale.US, "%.5f", complaint.longitude)}° E"
                DetailRow(label = "GPS Fix", value = coordStr, valueColor = Color(0xFF34D399))
            }
            if (!complaint.assignedDepartment.contains("Pending", ignoreCase = true)) {
                DetailRow(label = "Department", value = complaint.assignedDepartment)
            }
            // AI urgency if available
            complaint.aiSuggestedUrgency?.let {
                DetailRow(label = "AI Urgency", value = it, valueColor = when(it) {
                    "Critical" -> Color(0xFFFF5252)
                    "High"     -> Color(0xFFFF9800)
                    "Medium"   -> Color(0xFFFBBF24)
                    else       -> Color(0xFF4ADE80)
                })
            }
        }

        Spacer(Modifier.height(14.dp))

        // Open in Google Maps and Navigation Buttons (Requirement 3)
        if (complaint.latitude != null && complaint.longitude != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        GoogleMapsLauncher.openLocation(
                            context = context,
                            latitude = complaint.latitude,
                            longitude = complaint.longitude,
                            label = complaint.title
                        )
                    },
                    modifier = Modifier.weight(1f).height(42.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E3A8A))
                ) {
                    Text("🗺️ Google Maps", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1)
                }

                Button(
                    onClick = {
                        GoogleMapsLauncher.openDirections(
                            context = context,
                            destinationLat = complaint.latitude,
                            destinationLon = complaint.longitude,
                            destinationName = complaint.title
                        )
                    },
                    modifier = Modifier.weight(1f).height(42.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF065F46))
                ) {
                    Text("🧭 Directions", color = Color(0xFF6EE7B7), fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1)
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        Button(
            onClick = onTrack,
            modifier = Modifier.fillMaxWidth().height(46.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan)
        ) {
            Text("🔍  Track Full Complaint Timeline", color = NavyDeep, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }

        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun DetailRow(label: String, value: String, valueColor: Color = TextSecondary) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = TextMuted, fontSize = 11.sp, modifier = Modifier.padding(end = 8.dp))
        Text(
            value,
            color = valueColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End
        )
    }
}
