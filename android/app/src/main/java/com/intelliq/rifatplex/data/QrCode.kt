package com.intelliq.rifatplex.data

import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.Encoder

/**
 * Makes the QR code with ZXing, error correction level M (about 15 % of the code may be damaged).
 *
 * It uses ZXing's own [Encoder], which gives exactly one cell per module: no scaling and no quiet zone. (The quiet zone
 * is kept free on the sheet, see OmrGeometry.QR_QUIET_MM.)
 */
object QrCode {
    fun encode(text: String): QrMatrix {
        val qr = Encoder.encode(text, ErrorCorrectionLevel.M, mapOf(EncodeHintType.CHARACTER_SET to "UTF-8"))
        val m = qr.matrix
        val n = m.width
        val bits = BooleanArray(n * n)
        for (y in 0 until n) for (x in 0 until n) bits[y * n + x] = m.get(x, y).toInt() == 1
        return QrMatrix(n, bits)
    }
}
