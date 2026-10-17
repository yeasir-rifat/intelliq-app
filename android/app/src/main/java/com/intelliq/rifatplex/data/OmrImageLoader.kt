package com.intelliq.rifatplex.data

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlin.math.max

/**
 * Step 4.4 - a photo (camera or gallery) -> the RED channel as a [GrayImage], upright as the viewer shows it.
 *
 * Why red: the sheet is printed in magenta, which is almost white in the red channel, while black and blue ink stay dark
 * (README 4.2, section 2). The photo is decoded at most once, row by row into a byte array, and the bitmap is recycled
 * straight away, so a 12 MP photo costs about 12 MB of grey plus the short-lived decode.
 */
internal object OmrImageLoader {
    /** Longest side kept. A4 filling the long side gives about 10 px/mm, plenty (the scanner needs 6). */
    const val MAX_SIDE = 3200

    /** null when the file cannot be read or decoded. Slow (decoding): call from a background thread. */
    fun load(cr: ContentResolver, uri: Uri): GrayImage? {
        for (extraShift in 0..2) { // an out-of-memory decode is retried at half, then quarter size
            try {
                return decode(cr, uri, extraShift)
            } catch (e: OutOfMemoryError) {
                // try smaller
            } catch (e: Exception) {
                return null
            }
        }
        return null
    }

    private fun decode(cr: ContentResolver, uri: Uri, extraShift: Int): GrayImage? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        val big = max(bounds.outWidth, bounds.outHeight)
        while (big / (sample * 2) >= MAX_SIDE) sample *= 2
        sample = sample shl extraShift
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bmp = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
        val w = bmp.width
        val h = bmp.height
        val gray = ByteArray(w * h)
        val row = IntArray(w)
        for (y in 0 until h) {
            bmp.getPixels(row, 0, w, 0, y, w, 1)
            val o = y * w
            for (x in 0 until w) gray[o + x] = ((row[x] shr 16) and 0xFF).toByte()
        }
        bmp.recycle()
        val exif = try {
            cr.openInputStream(uri)?.use {
                android.media.ExifInterface(it).getAttributeInt(
                    android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_NORMAL,
                )
            } ?: android.media.ExifInterface.ORIENTATION_NORMAL
        } catch (e: Exception) {
            android.media.ExifInterface.ORIENTATION_NORMAL
        }
        return GrayImage(w, h, gray).oriented(exif).downscaled(MAX_SIDE)
    }

    /** A grey bitmap of [img], at most [maxSide] px on the long side (for showing the photo on screen). */
    fun toBitmap(img: GrayImage, maxSide: Int): Bitmap {
        val s = img.downscaled(maxSide)
        val pixels = IntArray(s.w * s.h)
        for (i in pixels.indices) {
            val g = s.px[i].toInt() and 0xFF
            pixels[i] = (0xFF shl 24) or (g shl 16) or (g shl 8) or g
        }
        return Bitmap.createBitmap(pixels, s.w, s.h, Bitmap.Config.ARGB_8888)
    }
}
