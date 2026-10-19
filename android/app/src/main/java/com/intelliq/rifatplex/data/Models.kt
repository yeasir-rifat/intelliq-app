package com.intelliq.rifatplex.data

import kotlin.random.Random

fun uid(prefix: String = "id"): String =
    "${prefix}_${System.currentTimeMillis().toString(36)}_${Random.nextInt(0, 2_000_000_000).toString(36)}"

data class Option(val id: String, val text: String = "", val correct: Boolean = false)

fun defaultOptions(): List<Option> = listOf(Option("a"), Option("b"), Option("c"), Option("d"))

/** Question categories, same ids and order as the website (QTypes). A question without a type is an MCQ. */
object QType {
    const val MCQ = "mcq"
    const val CREATIVE = "creative"
    const val TF = "tf"
    const val FILL = "fill"
    const val SHORT = "short"
    const val DESC = "desc"

    /** Order of the section / type tabs (Mix is the UI-only "all types" tab, not a type). */
    val ALL = listOf(MCQ, CREATIVE, TF, FILL, SHORT, DESC)

    fun normalize(t: String?): String = if (t != null && t in ALL) t else MCQ
}

/** One sub-question of a Creative question (ক / খ / গ / ঘ). */
data class SubQuestion(
    val id: String,
    val text: String = "",
    val marks: String = "",
    val answer: String = "",
)

/** Creative questions start with four sub-questions worth 1, 2, 3 and 4 marks, like the website. */
fun defaultSubs(): List<SubQuestion> =
    (1..4).map { SubQuestion(uid("sq"), marks = it.toString()) }

/**
 * Backward compatible with the old MCQ-only model: every new field has a default, and switching the type never
 * deletes the data of another type (switching back restores it).
 *
 * - [text]        the question / statement / stimulus (rich HTML-like markup)
 * - [explanation] MCQ and True/False explanation, Short question marking guide
 * - [marks]       optional marks of any type (string, so "৫" and "2.5" both work)
 * - [tfAnswer]    True/False answer (null = not chosen)
 * - [answer]      expected answer of Blanks / Short / Broad questions
 * - [subs]        sub-questions of a Creative question
 * - [cols]        per-question column override (1..3) used by the Mix tab; null = follow the category default
 */
data class Question(
    val id: String,
    val text: String = "",
    val layout: String = "auto", // auto | vertical | grid | horizontal
    val explanation: String = "",
    val options: List<Option> = defaultOptions(),
    val type: String = QType.MCQ,
    val marks: String = "",
    val tfAnswer: Boolean? = null,
    val answer: String = "",
    val subs: List<SubQuestion> = emptyList(),
    val cols: Int? = null,
) {
    fun kind(): String = QType.normalize(type)

    fun isEmpty(): Boolean = when (kind()) {
        QType.TF, QType.FILL, QType.SHORT, QType.DESC -> plainText(text).trim().isEmpty()
        QType.CREATIVE -> plainText(text).trim().isEmpty() && subs.all { plainText(it.text).trim().isEmpty() }
        else -> plainText(text).trim().isEmpty() && options.all { plainText(it.text).trim().isEmpty() }
    }

    /** MCQ only: is at least one option marked correct. */
    fun hasCorrect(): Boolean = options.any { it.correct }

    /** Does this question carry an answer for the answer key (any type). */
    fun hasAnswer(): Boolean = when (kind()) {
        QType.TF -> tfAnswer != null
        QType.FILL, QType.SHORT, QType.DESC -> plainText(answer).trim().isNotEmpty()
        QType.CREATIVE -> subs.any { plainText(it.answer).trim().isNotEmpty() }
        else -> hasCorrect()
    }

    /** Fills in the containers the current type needs, never removes anything (website `QTypes.ensure`). */
    fun ensured(): Question = when {
        kind() == QType.CREATIVE && subs.isEmpty() -> copy(subs = defaultSubs())
        kind() == QType.MCQ && options.isEmpty() -> copy(options = defaultOptions())
        else -> this
    }

    /** Marks of this question: its own value, or for a Creative question the sum of its sub-question marks. */
    fun totalMarks(): Double {
        val own = parseMarks(marks)
        if (own > 0.0) return own
        return if (kind() == QType.CREATIVE) subs.sumOf { parseMarks(it.marks) } else 0.0
    }
}

fun newQuestion(type: String = QType.MCQ): Question = Question(id = uid("q"), type = type).ensured()

/** Accepts "5", "৫", "2.5", "২.৫"; anything else is 0. */
fun parseMarks(v: String?): Double {
    val s = (v ?: "").trim().map { if (it in '\u09E6'..'\u09EF') '0' + (it - '\u09E6') else it }.joinToString("")
    return s.toDoubleOrNull()?.takeIf { it.isFinite() } ?: 0.0
}

/** "10" instead of "10.0", at most two decimals, digits in Bangla when [bn]. */
fun formatMarks(n: Double, bn: Boolean): String {
    val r = Math.round(n * 100.0) / 100.0
    val t = if (r == Math.floor(r)) r.toLong().toString() else r.toString()
    return if (bn) t.map { if (it in '0'..'9') '০' + (it - '0') else it }.joinToString("") else t
}

/** Sub-question label: ক খ গ ঘ in Bangla, a b c d in English. */
fun subLabel(index: Int, bn: Boolean): String {
    val l = optionLabel(index, bn)
    return if (bn) l else l.lowercase()
}

data class DocFields(
    val title: String = "",
    val subtitle: String = "",
    val marks: String = "",
    val time: String = "",
    val note: String = "",
    val chapter: String = "", // chapter / topic; shown after the subject ([subtitle]) as "subject • chapter"
) {
    /** "বিষয় • অধ্যায়": the subject and the chapter that are filled in, joined by a bullet. */
    fun subjectLine(): String = listOf(subtitle.trim(), chapter.trim()).filter { it.isNotEmpty() }.joinToString(" • ")
}

/** Optional custom heading / instruction of one section (type) in the arranged paper. */
data class SectionMeta(val title: String = "", val instruction: String = "")

data class DocSettings(
    val pageSize: String = "A4",
    val pageColor: String = "#FFFFFF",
    val fontEn: String = "Times New Roman",        // English letters / digits: Times New Roman | Georgia | Cambria
    val fontBn: String = "Kalpurush",              // Bangla: Noto Serif Bengali | Nikosh | Kalpurush | SolaimanLipi | AdorshoLipi
    val fontSize: Int = 10,
    val fontColor: String = "#111111",
    val chemAuto: Boolean = false, // auto-subscript digits in chemical formulas (H2O -> H₂O)
    // ----- website parity: columns + section headings -----
    val typeCols: Map<String, Int> = emptyMap(), // columns (1..3) per question type; missing = DEFAULT_COLS
    val sectionMode: Boolean = true, // "Arrange by section" (only matters when 2+ types are present)
    val sectionOrder: List<String> = QType.ALL,
    val sectionMeta: Map<String, SectionMeta> = emptyMap(),
    val sectionLayout: String = "center", // left | center
    val sectionDividerOff: Set<String> = emptySet(), // types whose 2/3-column divider line is switched off
    val sectionLineOff: Set<String> = setOf("center"), // "left" / "center" layouts whose line below the heading is off (default: centre has no line)
    val showMarks: Boolean = true, // show the [marks] badge on the right of each question / sub-question
    val boldQuestions: Boolean = true, // question text of the six types is bold by default (can be turned back to normal per character)
    val showPageNumber: Boolean = true, // page number at the bottom of every page ("পৃষ্ঠা ১/৩" / "Page 01/03")
    val autoFix: Boolean = false, // "Document AutoFix": fill every page as fully as possible (short questions move up into gaps)
    // ----- answer-key formatting -----
    val showAnswer: Boolean = true, // keep the correct answer in the answer key
    val showExplanation: Boolean = true, // keep the explanation of the correct answer
    val answerLayout: String = ANS_LAYOUT_EXPL, // fill | expl | corner (MCQ only)
    val answerColor: String = ANS_COLOR_GREEN, // colour of the correct answer in the answer key: green | black
    val omr: OmrSettings = OmrSettings(), // OMR sheet: automatic or custom (settings panel -> "OMR Sheet")
)

const val OMR_AUTO = "auto" // built from the document fields and the institution branding
const val OMR_CUSTOM = "custom" // every text, block and size chosen by the user

/**
 * OMR sheet settings. In [OMR_AUTO] mode only [mode] matters: the sheet is built from the document header fields
 * (exam name, subject), the institution branding and the built-in texts. In [OMR_CUSTOM] mode the values below are
 * used; a text left EMPTY falls back to the automatic text, so the user only types what they want to change.
 */
data class OmrSettings(
    val mode: String = OMR_AUTO,
    // ----- header texts
    val boardName: String = "", // big first line (auto: institution name, else the exam title)
    val examLine: String = "", // second line, before the year boxes (auto: exam title / subject)
    val sheetTitle: String = "", // "নৈর্ব্যক্তিক অভীক্ষার উত্তরপত্র"
    val banner1: String = "", // pink banner
    val banner2: String = "", // boxed banner
    val showYear: Boolean = true, // year boxes after the exam line
    val yearPrefix: String = "20", // digits printed in the first year boxes; the rest stay empty
    val showBanner1: Boolean = true,
    val showBanner2: Boolean = true,
    // ----- answer blocks
    val qHeader: String = "", // "প্র. নং"
    val ansHeader: String = "", // "উত্তর"
    val shading: Boolean = true, // every second answer row tinted
    // ----- blocks on the right (label, switch, digits)
    val showRoll: Boolean = true, val rollLabel: String = "", val rollDigits: Int = 6,
    val showSet: Boolean = true, val setLabel: String = "",
    val showReg: Boolean = true, val regLabel: String = "", val regDigits: Int = 10,
    val showSubject: Boolean = true, val subjectLabel: String = "", val subjectDigits: Int = 3,
    val showMarks: Boolean = true, val marksLabel: String = "",
    val showInvigilator: Boolean = true, val invigilatorLabel: String = "",
    // ----- bottom
    val showNote: Boolean = true, val note: String = "",
    val showRules: Boolean = true, val rulesTitle: String = "", val rules: String = "", // one rule per line
    val showSignature: Boolean = false, val signatureLabel: String = "",
) {
    val isCustom: Boolean get() = mode == OMR_CUSTOM

    /** What the sheet really uses: the automatic sheet ignores every custom value. */
    fun effective(): OmrSettings = if (isCustom) this else OmrSettings()
}

const val ANS_COLOR_GREEN = "green"
const val ANS_COLOR_BLACK = "black"

const val ANS_LAYOUT_FILL = "fill" // correct option filled
const val ANS_LAYOUT_EXPL = "expl" // "(খ) ব্যাখ্যা: ..." line
const val ANS_LAYOUT_CORNER = "corner" // round badge at the bottom-right corner of the MCQ

const val DEFAULT_COLS = 2

data class SetCodeSettings(
    val enabled: Boolean = false,
    val numberOfSets: Int = 1,
    val codeNames: String = "A, B, C, D, E, F, G, H, I, J",
    val randomization: String = "both", // none | questions | options | both
)

data class GeneratedSet(val code: String, val questions: List<Question>)

/** One row of the AI dialog's "Question types" list (same ids as [QType]). */
data class AiTypeCfg(val type: String, val enabled: Boolean, val count: Int)

/** Website defaults: MCQ 20, T/F 10, Gap 10, Short 10, Essay 5, Creative 3 (MCQ, Gap and Essay switched on). */
fun defaultAiTypes(): List<AiTypeCfg> = listOf(
    AiTypeCfg(QType.MCQ, true, 20),
    AiTypeCfg(QType.TF, false, 10),
    AiTypeCfg(QType.FILL, true, 10),
    AiTypeCfg(QType.SHORT, false, 10),
    AiTypeCfg(QType.DESC, true, 5),
    AiTypeCfg(QType.CREATIVE, false, 3),
)

data class AiSettings(
    val apiKey: String = "",
    val model: String = "gemini-3.8-flash",
    val insertMode: String = "append", // append | replace
    val types: List<AiTypeCfg> = defaultAiTypes(), // order = order of the generated sections
    val difficulty: String = "mixed", // mixed | easy | medium | hard
    val answers: Boolean = true,
    val explanations: Boolean = true,
)

/** What one Gemini request asks for: only the enabled categories, in order. */
data class AiGenConfig(val types: List<AiTypeCfg>, val difficulty: String, val answers: Boolean, val explanations: Boolean)

const val BRAND_CLASSIC = "classic" // logo / set code row on top, then the centred texts
const val BRAND_LETTER = "letterhead" // logo beside the institution name, exam details centred below
const val BRAND_BOARD = "board" // logo | centred texts | set code, in three columns
const val DIV_NONE = "none"
const val DIV_THIN = "thin"
const val DIV_THICK = "thick"
const val DIV_DOUBLE = "double"
const val POS_START = "start"
const val POS_CENTER = "center"
const val POS_END = "end"

/** Institution branding of the paper header. Off by default; everything is chosen in the settings panel. */
data class BrandSettings(
    val enabled: Boolean = false,
    val layout: String = BRAND_CLASSIC,
    val name: String = "",
    val slogan: String = "",
    val logo: String = "", // data URL (png), "" = no logo yet
    val logoW: Int = 0,
    val logoH: Int = 0,
    val showLogo: Boolean = true,
    val showName: Boolean = true,
    val showSlogan: Boolean = true,
    val showExam: Boolean = true,
    val showSubject: Boolean = true,
    val showChapter: Boolean = true,
    val showTime: Boolean = true,
    val showMarks: Boolean = true,
    val showSetCode: Boolean = true,
    val logoPos: String = POS_CENTER, // start | center | end (letterhead and board have no centre)
    val codePos: String = POS_END, // start | end
    // style
    val logoScale: Int = 100, // logo height in percent of the default (60..200)
    val nameScale: Int = 100, // institution name size in percent of the default (70..150)
    val color: String = "", // "#RRGGBB" for the institution name and the divider; "" = the paper's font colour
    val divider: String = "", // none | thin | thick | double; "" = the layout's default (letterhead: thin, others: none)
    // extra institution lines
    val address: String = "",
    val contact: String = "", // phone / e-mail / website
    val eiin: String = "", // EIIN or institution code (shown as "EIIN: ...")
    val showAddress: Boolean = true,
    val showContact: Boolean = true,
    val showEiin: Boolean = true,
) {
    /** Layouts without a centre slot treat "center" as "start". */
    fun effLogoPos(): String = if (layout != BRAND_CLASSIC && logoPos == POS_CENTER) POS_START else logoPos
    fun hasLogo(): Boolean = showLogo && logo.isNotEmpty()
    fun effDivider(): String = divider.ifEmpty { if (layout == BRAND_LETTER) DIV_THIN else DIV_NONE }
    /** The small grey lines under the institution name: address, then "EIIN: .. • contact". */
    fun infoLines(): List<String> {
        val a = if (showAddress) address.trim() else ""
        val e = if (showEiin && eiin.isNotBlank()) "EIIN: " + eiin.trim() else ""
        val c = if (showContact) contact.trim() else ""
        return listOf(a, listOf(e, c).filter { it.isNotEmpty() }.joinToString(" • ")).filter { it.isNotEmpty() }
    }
}

data class AppData(
    val language: String = "en",
    val dark: Boolean = false,
    val doc: DocFields = DocFields(),
    val questions: List<Question> = listOf(newQuestion()),
    val settings: DocSettings = DocSettings(),
    val setCode: SetCodeSettings = SetCodeSettings(),
    val generatedSets: List<GeneratedSet> = emptyList(),
    val activeSetIndex: Int = -1,
    val ai: AiSettings = AiSettings(),
    val brand: BrandSettings = BrandSettings(),
    val zoom: Int = 100,
    val folderId: String? = null,
    /** Permanent id of this exam, printed in the QR code of the OMR sheet (step 4.2). A new document gets a new one. */
    val examId: String = ExamId.generate(),
    /** Marks per right answer and negative marking of the OMR check (step 4.3). Saved with the exam, so each exam keeps its own. */
    val grading: GradingRules = GradingRules(),
)

/** Questions shown in the preview: the active generated set, or all non-empty questions. */
fun AppData.previewQuestions(): List<Question> =
    if (setCode.enabled && activeSetIndex in generatedSets.indices) generatedSets[activeSetIndex].questions
    else questions.filter { !it.isEmpty() }

fun AppData.hasAnswerKey(): Boolean = questions.any { !it.isEmpty() && it.hasAnswer() }

/** At least one MCQ: needed for the OMR sheet. */
fun AppData.hasMcq(): Boolean = questions.any { !it.isEmpty() && it.kind() == QType.MCQ }

/** At least one MCQ with its correct option marked: needed for the answer grid ("উত্তরমালা"). */
fun AppData.hasMcqKey(): Boolean = questions.any { !it.isEmpty() && it.kind() == QType.MCQ && it.hasCorrect() }

fun AppData.previewSetCode(): String? =
    if (setCode.enabled && activeSetIndex in generatedSets.indices) generatedSets[activeSetIndex].code else null

val PAGE_SIZES_MM: Map<String, Pair<Float, Float>> = mapOf(
    "A4" to (210f to 297f),
    "Letter" to (215.9f to 279.4f),
    "Legal" to (215.9f to 355.6f),
)

val BN_OPTION_LABELS = listOf("ক", "খ", "গ", "ঘ", "ঙ", "চ", "ছ", "জ")
val EN_OPTION_LABELS = listOf("A", "B", "C", "D", "E", "F", "G", "H")

fun optionLabel(index: Int, bn: Boolean): String =
    (if (bn) BN_OPTION_LABELS else EN_OPTION_LABELS).getOrNull(index) ?: (index + 1).toString()

fun toBanglaNumber(n: Int): String =
    n.toString().map { if (it in '0'..'9') '০' + (it - '0') else it }.joinToString("")

fun formatNumber(n: Int, bn: Boolean): String = if (bn) toBanglaNumber(n) else n.toString()


// ---------------------------------------------------------------------------------------------------------------
// Sections + columns (port of the website's "Column layout" and "Section headings" modules)
// ---------------------------------------------------------------------------------------------------------------

/** Section order with every type present exactly once (unknown / duplicate ids dropped, missing ones appended). */
fun DocSettings.orderedTypes(): List<String> {
    val out = sectionOrder.filter { it in QType.ALL }.distinct().toMutableList()
    QType.ALL.forEach { if (it !in out) out.add(it) }
    return out
}

/** Columns of a question: its own override, else the category setting, else 2. Always 1..3. */
fun DocSettings.colsOf(q: Question): Int =
    q.cols?.takeIf { it in 1..3 } ?: typeCols[q.kind()]?.takeIf { it in 1..3 } ?: DEFAULT_COLS

fun DocSettings.dividerOn(type: String): Boolean = type !in sectionDividerOff
fun DocSettings.lineOn(layout: String): Boolean = layout !in sectionLineOff

/** Section title: the custom one when set, else the default for [bn] language. */
fun DocSettings.sectionTitle(type: String, bn: Boolean): String =
    sectionMeta[type]?.title?.trim()?.takeIf { it.isNotEmpty() } ?: defaultSectionTitle(type, bn)

fun DocSettings.sectionInstruction(type: String): String = sectionMeta[type]?.instruction?.trim().orEmpty()

fun defaultSectionTitle(type: String, bn: Boolean): String = when (type) {
    QType.CREATIVE -> if (bn) "সৃজনশীল প্রশ্ন" else "Creative Questions"
    QType.TF -> if (bn) "সত্য / মিথ্যা" else "True / False"
    QType.FILL -> if (bn) "শূন্যস্থান পূরণ" else "Fill in the Blanks"
    QType.SHORT -> if (bn) "সংক্ষিপ্ত প্রশ্ন" else "Short Questions"
    QType.DESC -> if (bn) "রচনামূলক প্রশ্ন" else "Broad / Essay Questions"
    else -> if (bn) "বহুনির্বাচনি প্রশ্ন" else "Multiple Choice Questions"
}

/** A question placed in the paper: [n] is its number inside its section, [firstOfSection] shows the heading. */
data class Placed(val q: Question, val n: Int, val firstOfSection: Boolean, val type: String)

fun List<Question>.distinctTypes(): List<String> = filter { !it.isEmpty() }.map { it.kind() }.distinct()

/**
 * Groups questions by section in [DocSettings.orderedTypes] order when "arrange by section" is on and the paper
 * has at least two types. Otherwise the list keeps its order and numbering is simply 1..n (no headings).
 */
fun arrangeSections(list: List<Question>, st: DocSettings): List<Placed> {
    val arranged = st.sectionMode && list.distinctTypes().size >= 2
    if (!arranged) return list.mapIndexed { i, q -> Placed(q, i + 1, false, q.kind()) }
    val out = ArrayList<Placed>(list.size)
    st.orderedTypes().forEach { t ->
        list.filter { it.kind() == t }.forEachIndexed { i, q -> out.add(Placed(q, i + 1, i == 0, t)) }
    }
    return out
}

/** Total marks of the paper: every question's own marks (Creative: sum of sub-question marks). */
fun List<Question>.totalMarks(): Double = filter { !it.isEmpty() }.sumOf { it.totalMarks() }
