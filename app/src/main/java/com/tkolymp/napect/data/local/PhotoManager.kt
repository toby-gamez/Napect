package com.tkolymp.napect.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream

object PhotoManager {

    private const val MAX_DIMENSION = 1024

    fun savePhoto(context: Context, recipeId: Long, bytes: ByteArray): String {
        val dir = File(context.filesDir, "photos").also { it.mkdirs() }
        val file = File(dir, "${recipeId}.jpg")
        FileOutputStream(file).use { fos -> fos.write(bytes) }
        Timber.d("Saved photo %dx%d for recipe %d -> %s", bytes.size, 0, recipeId, file.absolutePath)
        return file.absolutePath
    }

    fun loadBitmap(path: String): Bitmap? {
        return try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, opts)
            opts.inSampleSize = computeSampleSize(opts.outWidth, opts.outHeight)
            opts.inJustDecodeBounds = false
            BitmapFactory.decodeFile(path, opts)
        } catch (e: Exception) {
            Timber.w(e, "Failed to load sampled bitmap from %s", path)
            try {
                BitmapFactory.decodeFile(path)
            } catch (e2: Exception) {
                Timber.w(e2, "Fallback decode also failed for %s", path)
                null
            }
        }
    }

    fun deletePhoto(path: String?): Boolean {
        if (path == null) return false
        val deleted = File(path).delete()
        if (deleted) Timber.d("Deleted photo %s", path)
        return deleted
    }

    fun getPhotoPath(context: Context, recipeId: Long): String {
        return File(File(context.filesDir, "photos"), "${recipeId}.jpg").absolutePath
    }

    private fun computeSampleSize(width: Int, height: Int): Int {
        var sampleSize = 1
        while (width / sampleSize > MAX_DIMENSION || height / sampleSize > MAX_DIMENSION) {
            sampleSize *= 2
        }
        return sampleSize
    }
}
