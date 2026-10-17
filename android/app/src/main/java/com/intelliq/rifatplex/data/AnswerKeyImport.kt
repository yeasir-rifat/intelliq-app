package com.intelliq.rifatplex.data

/**
 * Step 4.9 - importing an answer sheet ("উত্তরমালা") by scanning the QR code of each page (photos or a PDF).
 * Pure Kotlin: picture -> QR text -> [KeyScan] -> [KeyImport] (the pages collected so far) -> [KeyQrAssembly].
 */

/** What one picture / PDF page turned out to be. */
internal sealed class KeyScan {
    /** A page of an IntelliQ answer sheet this app version understands. */
    class Page(val payload: KeyQrPayload) : KeyScan()

    /** "IQK..." but not readable by this version (another format version). */
    object OtherVersion : KeyScan()

    /** A QR code that is not an answer-sheet page (a student OMR sheet, another app, ...). */
    object NotKey : KeyScan()

    /** No QR code found. */
    object None : KeyScan()
}

/** The answer-sheet pages collected so far (any order; the same page twice is kept once). Immutable. */
internal data class KeyImport(val parts: List<KeyQrPayload> = emptyList()) {
    val isEmpty: Boolean get() = parts.isEmpty()

    fun with(p: KeyQrPayload): KeyImport = if (p in parts) this else KeyImport(parts + p)

    /** Takes back the page added last (to get out of a [KeyQrAssembly.Mismatch]). */
    fun dropLast(): KeyImport = if (parts.isEmpty()) this else KeyImport(parts.dropLast(1))

    val assembly: KeyQrAssembly get() = KeyQrPayload.assemble(parts)

    /** Total number of pages the sheet says it has (of the first page scanned), 0 when nothing is scanned. */
    val pagesTotal: Int get() = parts.firstOrNull()?.pages ?: 0

    /** Distinct pages scanned so far. */
    val pagesHave: Int get() = parts.map { it.page }.toSet().size
}

/** How a finished import relates to what the phone already keeps for the same exam + set. */
internal enum class KeyExisting { NONE, SAME, DIFFERENT }

internal object AnswerKeyImport {
    fun classify(text: String?): KeyScan {
        if (text == null) return KeyScan.None
        if (!KeyQrPayload.isKeyPage(text)) return KeyScan.NotKey
        val k = KeyQrPayload.decode(text) ?: return KeyScan.OtherVersion
        return KeyScan.Page(k)
    }

    /** Finds the QR code anywhere in the picture (no sheet corners needed). */
    fun readImage(img: GrayImage): KeyScan = classify(OmrReader.decodeQr(img))

    fun existing(old: AnswerKeySnapshot?, new: AnswerKeySnapshot): KeyExisting = when {
        old == null -> KeyExisting.NONE
        old.sameContent(new) -> KeyExisting.SAME
        else -> KeyExisting.DIFFERENT
    }
}
