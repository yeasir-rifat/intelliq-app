package com.intelliq.rifatplex.data

/**
 * Prompts for the Gemini request. They follow the website's two prompts (file mode and topic mode) and add the
 * "formatting tools" section: Gemini writes with the same tools the editor has (subscript, superscript,
 * stacked fraction, symbols, chemistry formulas), so math and chemistry questions arrive fully formatted.
 */
object GeminiPrompts {

    private const val SCHEMA = """Respond with ONLY valid JSON (no markdown fences, no commentary) matching exactly this schema:
{
  "questions": [
    {
      "question": "question text (written with the formatting tools described below)",
      "options": ["option A text", "option B text", "option C text", "option D text"],
      "correctIndex": 0,
      "explanation": "brief explanation of the correct answer (same formatting tools)"
    }
  ]
}"""

    private const val FORMATTING = """FORMATTING TOOLS - this is important. The app has a rich-text editor with these tools. Every question, option and explanation must be written with exactly this markup. Never use Markdown, LaTeX, dollar signs, backslash commands, or the notations x^2, x_n, sqrt(x).
- Subscript: <sub>2</sub>   Superscript: <sup>2</sup>   Bold: <b>...</b>   Italic: <i>...</i>   Underline: <u>...</u>   Line break: <br>
- Stacked fraction: {{frac|numerator|denominator}}, for example {{frac|a+b|2}} or {{frac|dy|dx}}. The numerator and denominator may contain <sub>, <sup> and symbols, but never another fraction and never the characters | or }. Use it for algebraic and numeric fractions; simple common fractions may stay as one character (½ ¼ ¾).
- Symbols: type the real character: ² ³ ⁴ ½ ⅓ ¼ ⅔ ¾ √ ∛ ∑ ∫ ∞ ≈ ≠ ≤ ≥ ± × ÷ ∝ α β γ δ θ π λ μ Ω Δ ∂ ∇ → ⇌ ° ‰ · …
- Powers and indices: x<sup>2</sup>, 10<sup>-3</sup>, a<sub>n</sub>, e<sup>-x</sup>. Roots: √x, √(x+1), ∛x.
- Chemistry: atom counts are subscripts and charges / isotope numbers are superscripts: H<sub>2</sub>SO<sub>4</sub>, Ca(OH)<sub>2</sub>, Al<sub>2</sub>(SO<sub>4</sub>)<sub>3</sub>, CuSO<sub>4</sub>·5H<sub>2</sub>O, Fe<sup>3+</sup>, SO<sub>4</sub><sup>2-</sup>, <sup>14</sup>C. Write reactions like 2H<sub>2</sub> + O<sub>2</sub> → 2H<sub>2</sub>O (use ⇌ for reversible reactions; state symbols (s) (l) (g) (aq) as plain text). A coefficient is a plain digit in front of the formula, never a subscript.
- Inside formulas and expressions always use ASCII digits 0-9 and Latin element symbols, even when the sentence around them is Bangla.
- Plain text stays plain: do not add tags to ordinary words."""

    private const val QUALITY = """CALCULATION QUALITY:
- For every numerical, algebraic or chemical-calculation question, first solve it completely yourself and double-check the arithmetic, units, significant figures and the balancing of equations, then write the question. Exactly one option must be correct (unless the user asks otherwise); the other options must be plausible wrong answers (typical slips such as a sign error, a wrong formula or a unit mistake).
- The explanation of a calculation question must show the key steps briefly: formula, substitution, result with unit.
- Never write an unbalanced chemical equation; check atoms and charge on both sides.
- Keep numbers and units consistent (for example 2.5 mol, 10<sup>-3</sup> m)."""

    private const val COMMON_RULES = """- Always produce exactly 4 options per question unless the user's request specifies a different number of options.
- correctIndex is a zero-based index (0-3) pointing to the correct option.
- Return ONLY the JSON object, nothing else."""

    /** Topic mode (custom prompt switch on, no file). */
    fun topic(request: String): String = """You are an expert exam-question writer with broad, up-to-date knowledge across academic subjects and general topics. The user has NOT attached any file, image, or document. Instead they have named a subject/topic and given an instruction below. Use your own knowledge (as if researching the topic) to write accurate, well-sourced multiple-choice questions (MCQs) about it.

User's request: "$request"

$SCHEMA
Rules:
- Identify the subject/topic from the user's request and write factually accurate questions about it, as a knowledgeable expert or well-researched source would.
- Write the question and options in the same language the user wrote their request in (English or Bangla), unless the request explicitly asks for a different language.
$COMMON_RULES
- Vary question difficulty and phrasing so questions don't feel repetitive; cover different facets of the topic.
- If the user's request is too vague to identify any real subject/topic, still do your best to produce reasonable general-knowledge questions related to whatever terms were given.

$FORMATTING

$QUALITY"""

    /** File mode: images, PDFs and the extracted text of DOCX / PPTX files. */
    fun file(request: String, files: List<AiFile>): String {
        val kinds = LinkedHashSet<String>()
        files.forEach { f ->
            kinds.add(
                when {
                    f.kind == AiKind.Image -> "image"
                    f.kind == AiKind.Pdf -> "pdf"
                    f.source == "pptx" -> "pptx"
                    else -> "docx"
                },
            )
        }
        val labels = ArrayList<String>()
        if ("image" in kinds) labels.add("image(s) of a page")
        if ("pdf" in kinds) labels.add("PDF document(s)")
        if ("docx" in kinds) labels.add("Word document text")
        if ("pptx" in kinds) labels.add("PowerPoint slide text")
        val attached = if (files.size > 1) {
            "attached ${labels.joinToString(", ")} (${files.size} files total)"
        } else {
            "attached ${labels.firstOrNull() ?: "source material"}"
        }
        val texts = files.filter { it.kind == AiKind.Text }
        val extracted = if (texts.isEmpty()) "" else
            "\n\nExtracted document text follows (use this together with any attached images/PDFs above):\n" +
                texts.joinToString("\n\n") { "---${it.name}---\n${it.text}" }

        return """You are an expert exam-question writer. Look at the $attached (it may contain English text, Bangla (Bengali) text, or mathematical and chemical content/equations). Based on the content across ALL attached sources and the user's request below, generate multiple-choice questions (MCQs).

User's request: "$request"$extracted

$SCHEMA
Rules:
- Write the question and options in the same language as the source content (English or Bangla), unless the user's request explicitly asks for a different language.
$COMMON_RULES
- Base every question strictly on content visible in the source(s). If multiple files were attached, draw questions from across all of them.
- When the source shows equations, fractions or chemical formulas, reproduce them accurately with the formatting tools below.

$FORMATTING

$QUALITY"""
    }
}
