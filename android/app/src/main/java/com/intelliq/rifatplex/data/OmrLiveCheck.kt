package com.intelliq.rifatplex.data

/**
 * Live guidance while the camera is open (pure Kotlin, no Android). A preview frame (red channel, like a real photo) is run
 * through the same aligner as the final photo, but with lenient limits: the frame is far smaller than the final photo, so
 * this only says "the sheet is in view and roughly right". The strict check on the final photo is still done after the shot.
 */
internal enum class LiveHint { NO_SHEET, TOO_FAR, TOO_DARK, UNSTABLE, FLAT, OK }

internal class LiveResult(val hint: LiveHint, val corners: List<PointD>?)

internal object OmrLiveCheck {
    /** Lower limits than the default: a 1280x960 frame has ~3 px/mm at best, the final photo ~13 px/mm. */
    val LIVE = OmrAlignParams(workMaxSide = 960, minScalePxPerMm = 2.5, minDashes = 30, maxFitRmsMm = 1.0, dashOutlierMm = 1.2)

    /** Paper level (90th percentile of grey) below this = too dark to read marks. */
    const val MIN_PAPER_LEVEL = 100

    fun check(img: GrayImage): LiveResult {
        if (paperLevel(img) < MIN_PAPER_LEVEL) return LiveResult(LiveHint.TOO_DARK, null)
        return when (val r = OmrAlign.align(img, LIVE)) {
            is AlignResult.Ok -> LiveResult(LiveHint.OK, r.corners)
            is AlignResult.Failed -> LiveResult(hintFor(r.reason), r.corners)
        }
    }

    fun hintFor(reason: AlignFail): LiveHint = when (reason) {
        AlignFail.CORNERS_NOT_FOUND -> LiveHint.NO_SHEET
        AlignFail.TOO_FAR, AlignFail.IMAGE_TOO_SMALL -> LiveHint.TOO_FAR
        AlignFail.MARKS_UNREADABLE -> LiveHint.UNSTABLE
        AlignFail.BAD_FIT -> LiveHint.FLAT
        else -> LiveHint.UNSTABLE
    }

    /** Grey level that 90 % of the (sampled) pixels do not exceed: the brightness of the paper. */
    fun paperLevel(img: GrayImage): Int {
        val hist = IntArray(256)
        var n = 0
        var i = 0
        while (i < img.px.size) {
            hist[img.px[i].toInt() and 0xFF]++
            n++
            i += 5
        }
        val target = n * 9 / 10
        var acc = 0
        for (v in 0..255) {
            acc += hist[v]
            if (acc >= target) return v
        }
        return 255
    }

    fun key(h: LiveHint): String = when (h) {
        LiveHint.OK -> "live.ok"
        LiveHint.NO_SHEET -> "live.noSheet"
        LiveHint.TOO_FAR -> "live.tooFar"
        LiveHint.TOO_DARK -> "live.dark"
        LiveHint.UNSTABLE -> "live.unstable"
        LiveHint.FLAT -> "live.flat"
    }
}
