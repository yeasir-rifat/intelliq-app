package com.intelliq.rifatplex.data

import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.NotFoundException
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/** The QR code of the sheet: built with ZXing, drawn exactly as OmrSheet does (box, quiet zone, module size), read back with ZXing. */
class QrCodeTest {
    private val sample = QrPayload("K7M2QX9A", 2, 1, 0, 1)

    /**
     * Photographs the QR box of the sheet: [pxPerMm] pixels per mm, the page turned by [angleDeg] degrees about the
     * middle of the box, 3 x 3 sub-samples per pixel (soft edges like a real picture), [pad] mm of white page around the box.
     */
    private fun photo(m: QrMatrix, pxPerMm: Double, angleDeg: Double, pad: Double = 8.0): BinaryBitmap {
        val box = OmrGeometry.QR_BOX_MM.toDouble()
        val quiet = OmrGeometry.QR_QUIET_MM.toDouble()
        val code = OmrGeometry.QR_CODE_MM.toDouble()
        val mod = code / m.size
        val side = ((box + 2 * pad) * pxPerMm).toInt()
        val c = box / 2
        val a = Math.toRadians(angleDeg)
        val pixels = IntArray(side * side)
        for (py in 0 until side) for (px in 0 until side) {
            var sum = 0
            for (sy in 0 until 3) for (sx in 0 until 3) {
                val x = (px + (sx + 0.5) / 3) / pxPerMm - pad - c
                val y = (py + (sy + 0.5) / 3) / pxPerMm - pad - c
                // back to the flat sheet
                val fx = x * cos(a) + y * sin(a) + c
                val fy = -x * sin(a) + y * cos(a) + c
                val mx = floor((fx - quiet) / mod).toInt()
                val my = floor((fy - quiet) / mod).toInt()
                val black = fx >= quiet && fy >= quiet && mx in 0 until m.size && my in 0 until m.size && m[mx, my]
                sum += if (black) 0x23 else 0xFF // the printed black is #231F20
            }
            val g = sum / 9
            pixels[py * side + px] = (0xFF shl 24) or (g shl 16) or (g shl 8) or g
        }
        return BinaryBitmap(HybridBinarizer(RGBLuminanceSource(side, side, pixels)))
    }

    private fun read(bmp: BinaryBitmap): String? = try {
        QRCodeReader().decode(bmp, mapOf(DecodeHintType.TRY_HARDER to true)).text
    } catch (e: NotFoundException) {
        null
    } catch (e: com.google.zxing.ChecksumException) {
        null
    } catch (e: com.google.zxing.FormatException) {
        null
    }

    @Test
    fun codeIsVersion2WithoutMargin() {
        val m = QrCode.encode(sample.encode())
        assertEquals(25, m.size) // version 2 = 25 x 25 modules
        // finder pattern of the top-left corner: a 7 x 7 ring with a 3 x 3 centre
        for (i in 0 until 7) {
            assertTrue(m[i, 0]); assertTrue(m[i, 6]); assertTrue(m[0, i]); assertTrue(m[6, i])
        }
        assertTrue(m[3, 3])
    }

    @Test
    fun moduleSizeMatchesThePlan() {
        val m = QrCode.encode(sample.encode())
        assertEquals(0.56f, OmrGeometry.QR_CODE_MM / m.size, 0.001f) // 14 mm / 25 modules
    }

    @Test
    fun runsCoverEveryBlackModuleExactlyOnce() {
        val m = QrCode.encode(sample.encode())
        val seen = BooleanArray(m.size * m.size)
        for ((row, col, len) in m.runs()) {
            assertTrue(len >= 1)
            for (x in col until col + len) {
                assertTrue("module ($x,$row) is in two runs", !seen[row * m.size + x])
                seen[row * m.size + x] = true
            }
        }
        for (y in 0 until m.size) for (x in 0 until m.size) assertEquals("module ($x,$y)", m[x, y], seen[y * m.size + x])
        assertTrue("about 300 runs, not one per module", m.runs().size in 100..400)
    }

    @Test
    fun readsBackAtTheResolutionOfA1600PixelPhoto() {
        // 1600 px for the 210 mm of the page = 7.6 px/mm: about 4.3 px per module
        val got = read(photo(QrCode.encode(sample.encode()), 7.6, 0.0))
        assertEquals(sample.encode(), got)
        assertEquals(sample, QrPayload.decode(got!!))
    }

    @Test
    fun readsBackAtTheLowestAcceptedResolution() {
        // the scanner asks for 6 px/mm at least (about 3.4 px per module)
        assertEquals(sample.encode(), read(photo(QrCode.encode(sample.encode()), 6.0, 0.0)))
    }

    @Test
    fun readsBackWhenTheSheetIsTurned() {
        for (deg in listOf(-15.0, -7.0, 7.0, 15.0, 90.0, 180.0)) {
            assertEquals("angle $deg", sample.encode(), read(photo(QrCode.encode(sample.encode()), 7.6, deg)))
        }
    }

    @Test
    fun readsBackWithPartOfTheCodeCovered() {
        // level M survives about 15 % damage: paint a 4 x 4 module block (2.5 % of the area) white
        val m = QrCode.encode(sample.encode())
        val bits = BooleanArray(m.size * m.size) { m[it % m.size, it / m.size] }
        for (y in 14 until 18) for (x in 9 until 13) bits[y * m.size + x] = false // data area, clear of the finder, timing and alignment patterns
        assertEquals(sample.encode(), read(photo(QrMatrix(m.size, bits), 7.6, 0.0)))
    }

    @Test
    fun everySetAndPageGivesADifferentCodeThatReadsBack() {
        val seen = HashSet<String>()
        for (set in listOf<Int?>(null, 0, 1, 9)) for (page in 0..2) {
            val p = QrPayload("K7M2QX9A", set, 1, page, 3)
            val m = QrCode.encode(p.encode())
            assertTrue(seen.add(m.runs().joinToString { it.joinToString(",") })) // another picture each time
            assertEquals(p.encode(), read(photo(m, 7.6, 3.0)))
        }
    }

    @Test
    fun boxInTheSheetIsFoundInAFullPageImage() {
        // the whole A4 page at 8 px/mm with only the code on it, read from the full image (scanner fallback)
        val m = QrCode.encode(sample.encode())
        val ppm = 8.0
        val w = (210 * ppm).toInt()
        val h = (297 * ppm).toInt()
        val pixels = IntArray(w * h) { -1 }
        val box = OmrGeometry.qrBox()
        val mod = OmrGeometry.QR_CODE_MM.toDouble() / m.size
        for ((row, col, len) in m.runs()) {
            val x0 = ((box.x + OmrGeometry.QR_QUIET_MM + col * mod) * ppm).toInt()
            val x1 = ((box.x + OmrGeometry.QR_QUIET_MM + (col + len) * mod) * ppm).toInt()
            val y0 = ((box.y + OmrGeometry.QR_QUIET_MM + row * mod) * ppm).toInt()
            val y1 = ((box.y + OmrGeometry.QR_QUIET_MM + (row + 1) * mod) * ppm).toInt()
            for (y in y0 until y1) for (x in x0 until x1) pixels[y * w + x] = 0xFF231F20.toInt()
        }
        val text = read(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(w, h, pixels))))
        assertNotNull(text)
        assertEquals(sample.encode(), text)
    }
}
