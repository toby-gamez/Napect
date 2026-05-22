package com.tkolymp.napect.ui.recipes

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import timber.log.Timber

private const val MAX_DIMENSION = 1024

fun sampledBitmap(bytes: ByteArray): Bitmap? {
    return try {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        opts.inSampleSize = computeSampleSize(opts.outWidth, opts.outHeight)
        opts.inJustDecodeBounds = false
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    } catch (e: Exception) {
        Timber.w(e, "Failed to sample bitmap")
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }
}

private fun computeSampleSize(width: Int, height: Int): Int {
    var sampleSize = 1
    while (width / sampleSize > MAX_DIMENSION || height / sampleSize > MAX_DIMENSION) {
        sampleSize *= 2
    }
    return sampleSize
}
