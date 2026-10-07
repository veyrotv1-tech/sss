package com.example.video

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream

object GallerySaver {

    /**
     * Copies the completed video to the public MediaStore Movies/Video2Photo folder
     * so it shows up in Google Photos and Gallery apps without requiring storage permissions.
     */
    suspend fun saveToGallery(context: Context, videoFile: File): Uri? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, videoFile.name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Video2Photo")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        val itemUri = resolver.insert(collection, contentValues) ?: return@withContext null

        try {
            resolver.openOutputStream(itemUri)?.use { out ->
                FileInputStream(videoFile).use { input ->
                    input.copyTo(out)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(itemUri, contentValues, null, null)
            }
            itemUri
        } catch (e: Exception) {
            try { resolver.delete(itemUri, null, null) } catch (_: Exception) {}
            null
        }
    }

    /**
     * Opens the native Android share sheet to share the video file via any app.
     */
    fun shareVideo(context: Context, videoFile: File) {
        try {
            val authority = "${context.packageName}.fileprovider"
            val uri: Uri = FileProvider.getUriForFile(context, authority, videoFile)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "video/mp4"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Video2Photo Slideshow Video")
                putExtra(Intent.EXTRA_TEXT, "Created with Video2Photo - 3s screenshots slideshow with original synchronized audio!")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(shareIntent, "Share Video2Photo")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(context, "Error sharing video: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
