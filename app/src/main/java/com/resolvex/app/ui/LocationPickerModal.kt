package com.resolvex.app.ui

import android.content.Context
import android.graphics.Color as AndroidColor
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.resolvex.app.location.RealtimeLocationTracker
import com.resolvex.app.ui.theme.*
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import java.util.Locale

/**
 * In-app interactive map location selection modal.
 * Completely embedded inside ResolveX using OpenStreetMap (osmdroid).
 * Citizens can view their GPS location, tap/pan to adjust, use current GPS, and confirm coordinates.
 */
@Composable
fun LocationPickerModal(
    initialLatitude: Double?,
    initialLongitude: Double?,
    onConfirmLocation: (latitude: Double, longitude: Double) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val tracker = remember { RealtimeLocationTracker(context) }

    // Start with existing coordinates, or fall back to device GPS / default center
    val defaultIndiaCenter = GeoPoint(20.5937, 78.9629)
    var selectedGeoPoint by remember {
        mutableStateOf(
            if (initialLatitude != null && initialLongitude != null) {
                GeoPoint(initialLatitude, initialLongitude)
            } else {
                defaultIndiaCenter
            }
        )
    }

    var userLiveGpsPoint by remember { mutableStateOf<GeoPoint?>(null) }
    var gpsAccuracy by remember { mutableStateOf<Float?>(null) }
    var isLocatingGps by remember { mutableStateOf(false) }
    val mapViewRef = remember { mutableStateOf<MapView?>(null) }

    // Request GPS location fix when dialog opens
    fun fetchGpsFix(centerMap: Boolean = false) {
        if (!tracker.hasLocationPermission()) {
            Toast.makeText(context, "Location permission required to get live GPS fix", Toast.LENGTH_SHORT).show()
            return
        }

        isLocatingGps = true
        tracker.startContinuousTracking { state ->
            if (state.latitude != null && state.longitude != null) {
                val pt = GeoPoint(state.latitude, state.longitude)
                userLiveGpsPoint = pt
                gpsAccuracy = state.accuracyMeters
                isLocatingGps = false

                if (centerMap || (initialLatitude == null && initialLongitude == null)) {
                    selectedGeoPoint = pt
                    mapViewRef.value?.let { mv ->
                        mv.controller.animateTo(pt)
                        mv.controller.setZoom(16.5)
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        fetchGpsFix(centerMap = initialLatitude == null)
    }

    DisposableEffect(Unit) {
        onDispose {
            tracker.stopContinuousTracking()
            mapViewRef.value?.onDetach()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.75f))
                .padding(14.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.92f)
                    .clip(RoundedCornerShape(22.dp))
                    .background(NavyDark)
                    .border(1.dp, ElectricCyan.copy(alpha = 0.6f), RoundedCornerShape(22.dp))
                    .padding(16.dp)
            ) {
                // ── Header: Title & Close ─────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "📍 Select Incident Location",
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Tap anywhere on the map to place the incident pin",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(NavyCard)
                            .border(1.dp, Color(0xFF233554), CircleShape)
                            .clickable { onDismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("✕", color = Color(0xFFFF6B6B), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // ── Telemetry / Status Pill ───────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0C162E))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isLocatingGps) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            CircularProgressIndicator(modifier = Modifier.size(12.dp), color = ElectricCyan, strokeWidth = 1.5.dp)
                            Text("Acquiring real-time satellite GPS fix...", color = ElectricCyan, fontSize = 10.sp)
                        }
                    } else if (userLiveGpsPoint != null) {
                        val accStr = gpsAccuracy?.let { " (±${it.toInt()}m accuracy)" } ?: ""
                        Text(
                            text = "🛰️ Live GPS Active$accStr",
                            color = Color(0xFF34D399),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    } else {
                        Text(
                            text = "📍 Drag map or tap to select incident coordinates",
                            color = TextSecondary,
                            fontSize = 10.sp
                        )
                    }

                    Text(
                        text = "OpenStreetMap",
                        color = TextMuted,
                        fontSize = 9.sp
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // ── Interactive In-App MapView ─────────────────────────────
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.dp, Color(0xFF1E2E4E), RoundedCornerShape(14.dp))
                ) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            Configuration.getInstance().userAgentValue = "ResolveX/1.0 (com.resolvex.app)"
                            MapView(ctx).apply {
                                setTileSource(TileSourceFactory.MAPNIK)
                                setMultiTouchControls(true)
                                controller.setZoom(if (initialLatitude != null) 16.5 else 6.0)
                                controller.setCenter(selectedGeoPoint)
                                isHorizontalMapRepetitionEnabled = false
                                isVerticalMapRepetitionEnabled = false

                                // Tap on map overlay
                                val mapEventsReceiver = object : MapEventsReceiver {
                                    override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                                        if (p != null) {
                                            selectedGeoPoint = p
                                        }
                                        return true
                                    }

                                    override fun longPressHelper(p: GeoPoint?): Boolean {
                                        if (p != null) {
                                            selectedGeoPoint = p
                                        }
                                        return true
                                    }
                                }
                                overlays.add(MapEventsOverlay(mapEventsReceiver))
                                mapViewRef.value = this
                            }
                        },
                        update = { mapView ->
                            // Remove dynamic markers, keep the first MapEventsOverlay
                            while (mapView.overlays.size > 1) {
                                mapView.overlays.removeAt(mapView.overlays.size - 1)
                            }

                            // 1. Device's real live GPS marker (Blue pulse dot)
                            userLiveGpsPoint?.let { userPt ->
                                val userMarker = Marker(mapView).apply {
                                    position = userPt
                                    icon = createUserLocationDrawable()
                                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                    title = "Your Real-Time Location"
                                    infoWindow = null
                                }
                                mapView.overlays.add(userMarker)
                            }

                            // 2. Selected incident pin marker (Cyan pin)
                            val incidentMarker = Marker(mapView).apply {
                                position = selectedGeoPoint
                                icon = createMarkerDrawable(
                                    context,
                                    AndroidColor.parseColor("#00F0FF"),
                                    AndroidColor.parseColor("#007AFF")
                                )
                                setAnchor(Marker.ANCHOR_CENTER, 0.85f)
                                title = "Incident Location Pin"
                                infoWindow = null
                            }
                            mapView.overlays.add(incidentMarker)

                            mapView.invalidate()
                        }
                    )

                    // Zoom in / Zoom out buttons on map
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(NavyDark.copy(alpha = 0.9f))
                                .border(1.dp, Color(0xFF233554), RoundedCornerShape(8.dp))
                                .clickable {
                                    mapViewRef.value?.controller?.zoomIn()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("+", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        }

                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(NavyDark.copy(alpha = 0.9f))
                                .border(1.dp, Color(0xFF233554), RoundedCornerShape(8.dp))
                                .clickable {
                                    mapViewRef.value?.controller?.zoomOut()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("−", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // ── Selected Coordinates Display ──────────────────────────
                val latStr = String.format(Locale.US, "%.5f", selectedGeoPoint.latitude)
                val lonStr = String.format(Locale.US, "%.5f", selectedGeoPoint.longitude)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF0F1E38))
                        .border(0.5.dp, ElectricCyan.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Selected Pin:",
                            color = TextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "📍 $latStr° N, $lonStr° E",
                            color = ElectricCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // ── Action Buttons ────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // "Use Current Location" button
                    Button(
                        onClick = {
                            if (userLiveGpsPoint != null) {
                                selectedGeoPoint = userLiveGpsPoint!!
                                mapViewRef.value?.let { mv ->
                                    mv.controller.animateTo(userLiveGpsPoint!!)
                                    mv.controller.setZoom(16.5)
                                }
                                Toast.makeText(context, "Centered on current GPS location", Toast.LENGTH_SHORT).show()
                            } else {
                                fetchGpsFix(centerMap = true)
                            }
                        },
                        modifier = Modifier
                            .weight(1.1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF132349))
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("🎯", fontSize = 12.sp)
                            Text(
                                text = "Use Current Location",
                                color = SoftCyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }

                    // "Confirm Location" button
                    Button(
                        onClick = {
                            onConfirmLocation(selectedGeoPoint.latitude, selectedGeoPoint.longitude)
                            onDismiss()
                        },
                        modifier = Modifier
                            .weight(1.2f)
                            .height(44.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan)
                    ) {
                        Text(
                            text = "✓ Confirm Location",
                            color = NavyDeep,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
