package com.resolvex.app.location

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import java.util.Locale

/**
 * Robust launcher for opening locations and navigation in Google Maps.
 * - Attempts to open the official Google Maps Android app first.
 * - Gracefully falls back to opening Google Maps in the default web browser.
 * - Properly handles permissions, missing apps, and errors.
 */
object GoogleMapsLauncher {

    /**
     * Open a specific geographic coordinate in Google Maps with an optional label.
     */
    fun openLocation(context: Context, latitude: Double, longitude: Double, label: String? = null) {
        val latStr = String.format(Locale.US, "%.6f", latitude)
        val lonStr = String.format(Locale.US, "%.6f", longitude)

        // Try Google Maps native application intent with geo: URI scheme
        try {
            val encodedLabel = if (!label.isNullOrBlank()) "(${Uri.encode(label.take(40))})" else ""
            val geoUri = Uri.parse("geo:$latStr,$lonStr?q=$latStr,$lonStr$encodedLabel")
            val appIntent = Intent(Intent.ACTION_VIEW, geoUri).apply {
                setPackage("com.google.android.apps.maps")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (appIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(appIntent)
                return
            }
        } catch (e: Exception) {
            // Fall back to web browser
        }

        // Standard Google Maps Web URL fallback
        try {
            val webUrl = "https://www.google.com/maps?q=$latStr,$lonStr"
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(webUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(webIntent)
        } catch (e: Exception) {
            Toast.makeText(context, "Unable to launch map viewer: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Open turn-by-turn navigation / directions in Google Maps to the destination coordinates.
     */
    fun openDirections(context: Context, destinationLat: Double, destinationLon: Double, destinationName: String? = null) {
        val latStr = String.format(Locale.US, "%.6f", destinationLat)
        val lonStr = String.format(Locale.US, "%.6f", destinationLon)

        // Try Google Navigation intent for direct turn-by-turn guidance
        try {
            val navUri = Uri.parse("google.navigation:q=$latStr,$lonStr")
            val navIntent = Intent(Intent.ACTION_VIEW, navUri).apply {
                setPackage("com.google.android.apps.maps")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (navIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(navIntent)
                return
            }
        } catch (e: Exception) {
            // Fall back to web directions
        }

        // Web directions fallback
        try {
            val webUrl = "https://www.google.com/maps/dir/?api=1&destination=$latStr,$lonStr"
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(webUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(webIntent)
        } catch (e: Exception) {
            Toast.makeText(context, "Unable to launch directions: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
