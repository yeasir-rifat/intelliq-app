package com.intelliq.rifatplex.data

import kotlin.random.Random

fun uid(prefix: String = "id"): String =
    "${prefix}_${System.currentTimeMillis().toString(36)}_${Random.nextInt(0, 2_000_000_000).toString(36)}"

data class Option(val id: String, val text: String = "", val correct: Boolean = false)

fun defaultOptions(): List<Option> = listOf(Option("a"), Option("b"), Option("c"), Option("d"))

data class Question(
    val id: String,
    val text: String = "",
    val layout: String = "vertical", // vertical | grid | horizontal
    val explanation: String = "",
    val options: List<Option> = defaultOptions(),
) {
    fun isEmpty(): Boolean = plainText(text).trim().isEmpty() && options.all { plainText(it.text).trim().isEmpty() }
    fun hasCorrect(): Boolean = options.any { it.correct }
}

fun newQuestion(): Question = Question(id = uid("q"))

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
