package com.resolvex.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

object ImageStorageHelper {
    private const val TAG = "ImageStorageHelper"
    private const val MAX_DIMENSION = 2048

    /**
     * Safely copies an image from an external/content URI into the app's private files directory.
     * Downsamples if image exceeds 2048px to prevent OutOfMemory and ensure smooth performance.
     * Returns a persistent file:// Uri that will not expire across app restarts.
     */
    fun saveImageLocally(context: Context, sourceUri: Uri): Uri? {
        return try {
            val imagesDir = File(context.filesDir, "complaint_images").apply {
                if (!exists()) mkdirs()
            }
            val destinationFile = File(imagesDir, "complaint_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.jpg")

            // Step 1: Query dimensions
            var options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(sourceUri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }

            var sampleSize = 1
            val origWidth = options.outWidth
            val origHeight = options.outHeight

            if (origWidth > MAX_DIMENSION || origHeight > MAX_DIMENSION) {
                val halfWidth = origWidth / 2
                val halfHeight = origHeight / 2
                while ((halfWidth / sampleSize) >= MAX_DIMENSION || (halfHeight / sampleSize) >= MAX_DIMENSION) {
                    sampleSize *= 2
                }
            }

            // Step 2: Decode downsampled bitmap
            options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            val decodedBitmap: Bitmap? = context.contentResolver.openInputStream(sourceUri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }

            if (decodedBitmap != null) {
                FileOutputStream(destinationFile).use { out ->
                    decodedBitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                    out.flush()
                }
                decodedBitmap.recycle()
                Uri.fromFile(destinationFile)
            } else {
                // Direct byte stream copy fallback
                context.contentResolver.openInputStream(sourceUri)?.use { input ->
                    FileOutputStream(destinationFile).use { output ->
                        input.copyTo(output)
                    }
                }
                Uri.fromFile(destinationFile)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy image to internal storage: ${e.message}", e)
            null
        }
    }

    /**
     * Safely deletes a locally stored complaint image file if it exists.
     */
    fun deleteImageFile(uriString: String?) {
        if (uriString.isNullOrBlank()) return
        try {
            val uri = Uri.parse(uriString)
            if (uri.scheme == "file") {
                uri.path?.let { path ->
                    val file = File(path)
                    if (file.exists() && file.isFile) {
                        file.delete()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete file: ${e.message}")
        }
    }
}
