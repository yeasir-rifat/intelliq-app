package com.intelliq.rifatplex.data

import kotlin.random.Random

fun uid(prefix: String = "id"): String =
    "${prefix}_${System.currentTimeMillis().toString(36)}_${Random.nextInt(0, 2_000_000_000).toString(36)}"

data class Option(val id: String, val text: String = "", val correct: Boolean = false)

fun defaultOptions(): List<Option> = listOf(Option("a"), Option("b"), Option("c"), Option("d"))

/**
 * Question categories in the website's tab order (Parity Step 3).
 * mcq = MCQ, creative = CQ, tf = T/F, fill = Blanks, short = SQ, desc = BQ.
 */
val QUESTION_CATEGORIES = listOf("mcq", "creative", "tf", "fill", "short", "desc")
const val DEFAULT_CATEGORY = "mcq"

/** Allowed values for CategorySettings.columns and .section. */
val COLUMN_LAYOUTS = listOf("auto", "1", "2", "3")
val SECTION_LAYOUTS = listOf("left", "center")

/**
 * One sub-question of a CQ (creative) question (Parity Step 5). The stimulus is the question text.
 * marks is kept as typed text, as on the website ("2.5", "৫").
 */
data class SubQuestion(
    val id: String,
    val text: String = "",
    val marks: String = "",
    val answer: String = "",
)

data class Question(
    val id: String,
    val text: String = "",
    val layout: String = "vertical", // vertical | grid | horizontal (MCQ only)
    val explanation: String = "",
    val options: List<Option> = defaultOptions(),
    val category: String = DEFAULT_CATEGORY, // mcq | creative | tf | fill | short | desc
    val marks: String = "", // Optional per-question marks, typed as text to preserve decimal and Bangla digits
    // Parity Step 5: type-specific fields. Papers saved before Step 5 load with these defaults.
    val tfAnswer: Boolean? = null, // T/F: true, false, or null when no answer is chosen
    val answer: String = "", // Blanks, SQ and BQ: the answer (rich text HTML)
    val subQuestions: List<SubQuestion> = emptyList(), // CQ only
) {
    fun isEmpty(): Boolean = plainText(text).trim().isEmpty() &&
        options.all { plainText(it.text).trim().isEmpty() } &&
        plainText(answer).trim().isEmpty() &&
        subQuestions.all { plainText(it.text).trim().isEmpty() && plainText(it.answer).trim().isEmpty() }

    /** MCQ: an option is marked correct. T/F: an answer is chosen. */
    fun hasCorrect(): Boolean = when (category) {
        "tf" -> tfAnswer != null
        "fill", "short", "desc" -> plainText(answer).isNotBlank()
        "creative" -> subQuestions.any { plainText(it.answer).isNotBlank() }
        else -> options.any { it.correct }
    }
}

fun newQuestion(): Question = Question(id = uid("q"))

/** The website starts a new CQ with four sub-questions, marked 1 to 4. */
fun defaultSubQuestions(): List<SubQuestion> = (1..4).map { SubQuestion(uid("sq"), marks = it.toString()) }

/** Moves the question to [category]. A CQ without sub-questions gets the default four. */
fun Question.withCategory(category: String): Question =
    if (category == this.category) this
    else copy(
        category = category,
        subQuestions = if (category == "creative" && subQuestions.isEmpty()) defaultSubQuestions() else subQuestions,
    )

/** Sub-question label as on the website: ক, খ, গ in Bangla, a, b, c in English. */
fun subQuestionLabel(index: Int, bn: Boolean): String =
    if (bn) optionLabel(index, true) else optionLabel(index, false).lowercase()

/** Marks typed as text, in English or Bangla digits. Blank or invalid counts as 0. */
fun parseMarks(s: String): Double {
    val ascii = s.map { if (it in '০'..'৯') '0' + (it - '০') else it }.joinToString("")
    return ascii.trim().toDoubleOrNull() ?: 0.0
}

/** Turns ASCII digits into Bangla digits when [bn] is true. Other text is unchanged. */
fun localizeDigits(s: String, bn: Boolean): String =
    if (!bn) s else s.map { if (it in '0'..'9') '০' + (it - '0') else it }.joinToString("")

data class DocFields(
    val title: String = "",
    val subtitle: String = "",
    val marks: String = "",
    val time: String = "",
    val note: String = "",
)

data class DocSettings(
    val pageSize: String = "A4",
    val pageColor: String = "#FFFFFF",
    val fontFamily: String = "Times New Roman",
    val fontSize: Int = 10,
    val fontColor: String = "#111111",
    val chemAuto: Boolean = false, // auto-subscript digits in chemical formulas (H2O -> H₂O)
)

data class SetCodeSettings(
    val enabled: Boolean = false,
    val numberOfSets: Int = 1,
    val codeNames: String = "A, B, C, D, E, F, G, H, I, J",
    val randomization: String = "both", // none | questions | options | both
)

/**
 * Settings for one question category (Parity Step 3). Used from Parity Step 4 onward:
 * order = tab position, count = how many blank questions "Add Questions" adds,
 * columns = column layout (auto, 1, 2, 3), section = section title alignment (left, center),
 * sectionLine = draw a rule under the section title.
 */
data class CategorySettings(
    val key: String,
    val order: Int,
    val count: Int = 1,
    val columns: String = "auto",
    val section: String = "left",
    val sectionLine: Boolean = false,
)

fun defaultCategories(): List<CategorySettings> =
    QUESTION_CATEGORIES.mapIndexed { i, key -> CategorySettings(key, order = i) }

/** Settings of one category, with defaults if the key is missing. */
fun AppData.categorySettings(key: String): CategorySettings =
    categories.firstOrNull { it.key == key } ?: CategorySettings(key, order = QUESTION_CATEGORIES.indexOf(key).coerceAtLeast(0))

data class GeneratedSet(val code: String, val questions: List<Question>)

data class AiSettings(
    val apiKey: String = "",
    val model: String = "gemini-3-flash-preview",
    val insertMode: String = "append", // append | replace
)

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
    val zoom: Int = 100,
    val folderId: String? = null,
    // Parity Step 3: added at the end, so old saved papers load with the defaults below.
    val showMarks: Boolean = false,
    val categories: List<CategorySettings> = defaultCategories(),
)

/** Questions shown in the preview: the active generated set, or all non-empty questions. */
fun AppData.previewQuestions(): List<Question> =
    if (setCode.enabled && activeSetIndex in generatedSets.indices) generatedSets[activeSetIndex].questions
    else questions.filter { !it.isEmpty() }

fun AppData.hasAnswerKey(): Boolean = questions.any { it.hasCorrect() }

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
