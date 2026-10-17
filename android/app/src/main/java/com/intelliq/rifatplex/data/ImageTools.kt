package com.intelliq.rifatplex.data

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.ByteArrayOutputStream
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Decoded pictures, shared by the editor, the preview and the PDF (a data URL is decoded once). */
object PicCache {
    // Limited by memory, not by count: 24 pictures of 1400 px (ARGB_8888, ~8 MB each) could be ~190 MB.
    // An eldest picture that is evicted while still on screen is simply decoded again; nothing else changes.
    private val maxBytes = max(16 * 1024 * 1024L, Runtime.getRuntime().maxMemory() / 8L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    private val cache = object : LruCache<String, ImageBitmap>(maxBytes) {
        override fun sizeOf(key: String, value: ImageBitmap): Int {
            val bytes = try { value.asAndroidBitmap().allocationByteCount } catch (e: Throwable) { 1 }
            return max(1, bytes)
        }
    }

    fun get(url: String): ImageBitmap? {
        cache.get(url)?.let { return it }
        val bmp = ImageTools.decode(url) ?: return null
        val img = bmp.asImageBitmap()
        cache.put(url, img)
        return img
    }
}

/**
 * Picture processing, a port of the website's image tool (iq-img-tool-js): compress on insert, crop, and "clean"
 * (remove the paper background / shadows of a scanned diagram and keep the lines).
 */
object ImageTools {

    fun decode(url: String): Bitmap? = try {
        val bytes = Base64.decode(url.substringAfter(','), Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } catch (e: Throwable) {
        null
    }

    /** Draws [src] on a white background at the given size (JPEG has no transparency). */
    private fun onWhite(src: Bitmap, w: Int, h: Int): Bitmap {
        val out = Bitmap.createBitmap(max(1, w), max(1, h), Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(Color.WHITE)
        val dst = android.graphics.Rect(0, 0, out.width, out.height)
        c.drawBitmap(src, null, dst, android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
        return out
    }

    private fun toUrl(bmp: Bitmap, png: Boolean, quality: Int): String {
        val bos = ByteArrayOutputStream()
        bmp.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, quality, bos)
        return (if (png) "data:image/png;base64," else "data:image/jpeg;base64,") + Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
    }

    /**
     * JPEG data URL below [limit] characters: the longest side is reduced to [maxSide] first, then, while the result is
     * still too big, the size shrinks by [shrink] and the quality drops (5 tries, like the website).
     */
    fun compressJpeg(src: Bitmap, maxSide: Int, q0: Float, limit: Int, shrink: Float): Pic {
        val w0 = src.width
        val h0 = src.height
        var k = min(1f, maxSide.toFloat() / max(w0, h0))
        var q = q0
        var url = ""
        var w = 1
        var h = 1
        for (t in 0 until 5) {
            w = max(1, (w0 * k).roundToInt())
            h = max(1, (h0 * k).roundToInt())
            val flat = onWhite(src, w, h)
            url = toUrl(flat, false, (q * 100).roundToInt())
            flat.recycle()
            if (url.length < limit) break
            k *= shrink
            q = max(0.6f, q - 0.06f)
        }
        return Pic(url, w, h)
    }

    /** A picked photo / image file -> Pic (EXIF rotation applied, longest side 1000 px, under about 195 KB). */
    fun fromUri(cr: ContentResolver, uri: Uri): Pic? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        val big = max(bounds.outWidth, bounds.outHeight)
        while (big / (sample * 2) >= 1400) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        var bmp = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        if (bmp != null) {
            val orient = try {
                cr.openInputStream(uri)?.use {
                    android.media.ExifInterface(it).getAttributeInt(
                        android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_NORMAL,
                    )
                } ?: android.media.ExifInterface.ORIENTATION_NORMAL
            } catch (e: Exception) {
                android.media.ExifInterface.ORIENTATION_NORMAL
            }
            val m = Matrix()
            when (orient) {
                android.media.ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
                android.media.ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
                android.media.ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
                android.media.ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
                android.media.ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
                android.media.ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f) }
                android.media.ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(270f); m.postScale(-1f, 1f) }
            }
            if (!m.isIdentity) bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            compressJpeg(bmp, 1000, 0.86f, 260000, 0.82f)
        } else null
    } catch (e: Throwable) {
        null
    }

    /** A picked logo -> PNG Pic (longest side 400 px, transparency kept, EXIF rotation ignored). */
    fun logoFromUri(cr: ContentResolver, uri: Uri): Pic? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        val big = max(bounds.outWidth, bounds.outHeight)
        while (big / (sample * 2) >= 800) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val src = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        if (src == null) null else {
            val k = min(1f, 400f / max(src.width, src.height))
            val w = max(1, (src.width * k).roundToInt())
            val h = max(1, (src.height * k).roundToInt())
            val scaled = if (k < 1f) Bitmap.createScaledBitmap(src, w, h, true) else src
            Pic(toUrl(scaled, true, 100), scaled.width, scaled.height)
        }
    } catch (e: Throwable) {
        null
    }

    /** A region of a page / photo (AI figure extraction) -> Pic. */
    fun fromBitmap(bmp: Bitmap): Pic = compressJpeg(bmp, 1000, 0.86f, 260000, 0.82f)

    /** Crop. [fx] [fy] [fw] [fh] are fractions (0..1) of the picture. The result is a JPEG, longest side 1400 px. */
    fun crop(pic: Pic, fx: Float, fy: Float, fw: Float, fh: Float): Pic? = try {
        cropUnsafe(pic, fx, fy, fw, fh)
    } catch (e: Throwable) {
        null // out of memory / bad pixels: the dialog just closes, the app must not die
    }

    private fun cropUnsafe(pic: Pic, fx: Float, fy: Float, fw: Float, fh: Float): Pic? {
        val bmp = decode(pic.url) ?: return null
        val bw = bmp.width
        val bh = bmp.height
        val sx = (fx * bw).roundToInt().coerceIn(0, bw - 1)
        val sy = (fy * bh).roundToInt().coerceIn(0, bh - 1)
        val sw = max(1, (fw * bw).roundToInt()).coerceAtMost(bw - sx)
        val sh = max(1, (fh * bh).roundToInt()).coerceAtMost(bh - sy)
        val part = Bitmap.createBitmap(bmp, sx, sy, sw, sh)
        return compressJpeg(part, 1400, 0.9f, 300000, 0.85f)
    }

    /** Crop for the institution logo: stays a PNG, so a transparent logo is not put on a white box. */
    fun cropLogo(pic: Pic, fx: Float, fy: Float, fw: Float, fh: Float): Pic? = try {
        cropLogoUnsafe(pic, fx, fy, fw, fh)
    } catch (e: Throwable) {
        null
    }

    private fun cropLogoUnsafe(pic: Pic, fx: Float, fy: Float, fw: Float, fh: Float): Pic? {
        val bmp = decode(pic.url) ?: return null
        val bw = bmp.width
        val bh = bmp.height
        val sx = (fx * bw).roundToInt().coerceIn(0, bw - 1)
        val sy = (fy * bh).roundToInt().coerceIn(0, bh - 1)
        val sw = max(1, (fw * bw).roundToInt()).coerceAtMost(bw - sx)
        val sh = max(1, (fh * bh).roundToInt()).coerceAtMost(bh - sy)
        val part = Bitmap.createBitmap(bmp, sx, sy, sw, sh)
        return Pic(toUrl(part, true, 100), part.width, part.height)
    }

    /* ───────────── clean: remove the paper background, keep the lines ───────────── */

    /** Result of the (slow) analysis; [render] is then cheap, so the slider can be live. */
    class CleanData(val w: Int, val h: Int, val nr: FloatArray, val ng: FloatArray, val nb: FloatArray, val a: FloatArray)

    fun cleanPrepare(src: Bitmap): CleanData {
        val cap = 1100
        var w = src.width
        var h = src.height
        val k = min(1f, cap.toFloat() / max(w, h))
        w = max(1, (w * k).roundToInt())
        h = max(1, (h * k).roundToInt())
        val flat = onWhite(src, w, h)
        val px = IntArray(w * h)
        flat.getPixels(px, 0, w, 0, 0, w, h)
        flat.recycle()
        val n = w * h
        val b = max(6, (max(w, h) / 50f).roundToInt())
        val gw = ceil(w.toDouble() / b).toInt()
        val gh = ceil(h.toDouble() / b).toInt()
        val grid = Array(3) { FloatArray(gw * gh) }
        for (ch in 0 until 3) {
            val shift = when (ch) { 0 -> 16; 1 -> 8; else -> 0 }
            val g = grid[ch]
            for (by in 0 until gh) for (bx in 0 until gw) {
                var m = 0
                val y1 = min(h, (by + 1) * b)
                val x1 = min(w, (bx + 1) * b)
                for (yy in by * b until y1) {
                    val row = yy * w
                    for (xx in bx * b until x1) {
                        val v = (px[row + xx] shr shift) and 0xFF
                        if (v > m) m = v
                    }
                }
                g[by * gw + bx] = m.toFloat()
            }
        }
        val gl = FloatArray(3)
        for (q in 0 until 3) {
            val sorted = grid[q].sortedArray()
            val v = if (sorted.isEmpty()) 255f else sorted[floor(sorted.size * 0.6).toInt().coerceAtMost(sorted.size - 1)]
            gl[q] = if (v == 0f) 255f else v
        }
        for (q in 0 until 3) {
            val gg = grid[q]
            val lo = gl[q] * 0.6f
            for (i in gg.indices) if (gg[i] < lo) gg[i] = lo
            for (pass in 0 until 2) {
                val tmp = FloatArray(gg.size)
                for (by in 0 until gh) for (bx in 0 until gw) {
                    var sum = 0f
                    var cn = 0
                    for (oy in -1..1) for (ox in -1..1) {
                        val ny = by + oy
                        val nx = bx + ox
                        if (ny < 0 || nx < 0 || ny >= gh || nx >= gw) continue
                        sum += gg[ny * gw + nx]
                        cn++
                    }
                    tmp[by * gw + bx] = sum / cn
                }
                System.arraycopy(tmp, 0, gg, 0, gg.size)
            }
        }
        val nr = FloatArray(n)
        val ng = FloatArray(n)
        val nb = FloatArray(n)
        val av = FloatArray(n)
        val vals = FloatArray(3)
        for (y in 0 until h) {
            val fy = min((gh - 1).toFloat(), max(0f, y.toFloat() / b - 0.5f))
            val y0 = floor(fy).toInt()
            val y1b = min(gh - 1, y0 + 1)
            val wy = fy - y0
            for (x in 0 until w) {
                val fx = min((gw - 1).toFloat(), max(0f, x.toFloat() / b - 0.5f))
                val x0 = floor(fx).toInt()
                val x1b = min(gw - 1, x0 + 1)
                val wx = fx - x0
                val idx = y * w + x
                val p = px[idx]
                for (cc in 0 until 3) {
                    val gr = grid[cc]
                    val bg = (gr[y0 * gw + x0] * (1 - wx) + gr[y0 * gw + x1b] * wx) * (1 - wy) +
                        (gr[y1b * gw + x0] * (1 - wx) + gr[y1b * gw + x1b] * wx) * wy
                    val shift = when (cc) { 0 -> 16; 1 -> 8; else -> 0 }
                    val nv = ((p shr shift) and 0xFF) / max(1f, bg)
                    vals[cc] = if (nv > 1f) 1f else nv
                }
                nr[idx] = vals[0]
                ng[idx] = vals[1]
                nb[idx] = vals[2]
                av[idx] = 1f - (0.299f * vals[0] + 0.587f * vals[1] + 0.114f * vals[2])
            }
        }
        return CleanData(w, h, nr, ng, nb, av)
    }

    private fun smooth(t0: Float, t1: Float, v: Float): Float {
        if (v <= t0) return 0f
        if (v >= t1) return 1f
        val t = (v - t0) / (t1 - t0)
        return t * t * (3 - 2 * t)
    }

    /** [level] 0..100, [transparent] = keep the background see-through (PNG) instead of white. */
    fun cleanRender(d: CleanData, level: Int, transparent: Boolean): Bitmap {
        val sl = level / 100f
        val t0 = 0.05f + 0.25f * sl
        val t1 = t0 + 0.25f
        val out = IntArray(d.w * d.h)
        for (i in out.indices) {
            val al = smooth(t0, t1, d.a[i])
            val r = d.nr[i] * d.nr[i]
            val g = d.ng[i] * d.ng[i]
            val b = d.nb[i] * d.nb[i]
            out[i] = if (transparent) {
                Color.argb((al * 255).toInt().coerceIn(0, 255), (r * 255).toInt().coerceIn(0, 255), (g * 255).toInt().coerceIn(0, 255), (b * 255).toInt().coerceIn(0, 255))
            } else {
                Color.rgb(
                    (255 * ((1 - al) + al * r)).toInt().coerceIn(0, 255),
                    (255 * ((1 - al) + al * g)).toInt().coerceIn(0, 255),
                    (255 * ((1 - al) + al * b)).toInt().coerceIn(0, 255),
                )
            }
        }
        val bmp = Bitmap.createBitmap(d.w, d.h, Bitmap.Config.ARGB_8888)
        bmp.setPixels(out, 0, d.w, 0, 0, d.w, d.h)
        return bmp
    }

    /** Final cleaned picture: PNG when transparent, JPEG otherwise; shrunk (5 tries) when above about 330 KB. */
    fun cleanFinish(d: CleanData, level: Int, transparent: Boolean): Pic? = try {
        cleanFinishUnsafe(d, level, transparent)
    } catch (e: Throwable) {
        null
    }

    private fun cleanFinishUnsafe(d: CleanData, level: Int, transparent: Boolean): Pic {
        var bmp = cleanRender(d, level, transparent)
        var url = toUrl(bmp, transparent, 90)
        var t = 0
        while (t++ < 5 && url.length > 450000) {
            val w = max(1, (bmp.width * 0.8f).roundToInt())
            val h = max(1, (bmp.height * 0.8f).roundToInt())
            val small = if (transparent) Bitmap.createScaledBitmap(bmp, w, h, true) else onWhite(bmp, w, h)
            bmp = small
            url = toUrl(bmp, transparent, 85)
        }
        return Pic(url, bmp.width, bmp.height)
    }
}
