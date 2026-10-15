package com.intelliq.rifatplex.data

/** Website-aligned generation prompts for all supported exam question types. */
object GeminiPrompts {
    private const val FORMAT = """FORMATTING RULES:
- Return valid JSON only. Never include Markdown fences or commentary. Escape quotes and use <br> for line breaks inside text.
- Use <sub> and <sup> for indices and powers, for example H<sub>2</sub>O and x<sup>2</sup>. Use real symbols such as × ÷ ± ≤ ≥ ≠ ≈ π θ α β Δ ∑ ∫ ° → ⇌.
- Fractions use {{frac|numerator|denominator}}. Roots use √. Keep chemical equations balanced and put reactions on their own line.
- Use correct IUPAC/common chemical names, SI units, symbols and significant figures. Check every calculation and equation before returning it.
- Follow Bangladesh education standards. For school levels follow NCTB content, terminology, units and chapter scope. Infer level from the request or source and do not mix levels in one set. Use standard Bangla for Bangla output.
- Never invent facts, dates, statistics, citations or textbook references. Prefer established facts and accurate answers.
- Use the same language as the source unless the user's instruction requests another language."""

    private fun typeName(type: String) = when (type) {
        "tf" -> "True/False"
        "fill" -> "Gap Fill"
        "short" -> "Short Answer"
        "desc" -> "Essay / Descriptive"
        "creative" -> "Creative (stimulus and sub-questions)"
        else -> "MCQ"
    }

    private fun schema(type: String, answers: Boolean, explanations: Boolean): String {
        val fields = when (type) {
            "tf" -> "\"question\":\"...\"" + if (answers) ", \"answer\":true" else ""
            "fill", "short", "desc" -> "\"question\":\"...\"" + if (answers) ", \"answer\":\"model answer\"" else ""
            "creative" -> "\"stimulus\":\"...\", \"subQuestions\":[{" + "\"question\":\"...\"" + (if (answers) ", \"answer\":\"...\"" else "") + ", \"marks\":\"1\"}]"
            else -> "\"question\":\"...\", \"options\":[\"A\",\"B\",\"C\",\"D\"]" + if (answers) ", \"correctIndex\":0" else ""
        }
        val explanation = if (explanations && type in listOf("mcq", "tf")) ", \"explanation\":\"brief reasoning\"" else ""
        return "{\"questions\":[{$fields$explanation}]}"
    }

    fun topic(request: String, type: String = "mcq", difficulty: String = "mixed", answers: Boolean = true, explanations: Boolean = true): String = buildPrompt(
        "You are an expert exam-question writer. Generate accurate ${typeName(type)} questions using your subject knowledge.",
        "User's request: $request", type, difficulty, answers, explanations, "Use your knowledge to create questions on the requested topic."
    )

    fun file(request: String, files: List<AiFile>, type: String = "mcq", difficulty: String = "mixed", answers: Boolean = true, explanations: Boolean = true): String {
        val labels = files.joinToString(", ") { it.name }
        val extracted = files.filter { it.kind == AiKind.Text }.joinToString("\n\n") { "---${it.name}---\n${it.text}" }
        return buildPrompt(
            "You are an expert exam-question writer. Generate ${typeName(type)} questions from the attached source material.",
            "User's request: ${request.ifBlank { "Generate questions based on the provided source material." }}\nAttached files: $labels\n$extracted",
            type, difficulty, answers, explanations,
            "Base questions strictly on the supplied files. Across multiple files, sample all relevant sources."
        )
    }

    private fun buildPrompt(role: String, request: String, type: String, difficulty: String, answers: Boolean, explanations: Boolean, sourceRule: String): String {
        val difficultyRule = when (difficulty) {
            "easy" -> "Keep all questions easy and foundational."
            "medium" -> "Use medium difficulty, testing understanding and application."
            "hard" -> "Use challenging, higher-order questions appropriate to the inferred academic level."
            else -> "Mix easy, medium and hard questions while keeping one consistent academic level."
        }
        val typeRules = when (type) {
            "tf" -> "Each question is one unambiguous statement. answer must be Boolean true or false."
            "fill" -> "Use _____ at each blank. answer must contain only the exact missing word or phrase, or comma-separated answers for multiple blanks."
            "short" -> "Each question expects a concise answer, usually 2 to 4 sentences or key points."
            "desc" -> "Each question expects a structured model answer with a brief introduction, logical headings or lists, and a conclusion suited to the marks/level."
            "creative" -> "Each item has a short stimulus and subQuestions ordered knowledge, comprehension, application and higher-order thinking. Include marks as strings."
            else -> "Each MCQ has exactly four options and exactly one clearly correct option. correctIndex is zero-based from 0 to 3. Distractors must be plausible but wrong."
        }
        val answerRule = if (answers) "Include the answer key in the appropriate answer field." else "Do not provide answers or answer keys. Omit answer fields and do not mark options as correct."
        val explanationRule = if (explanations) "Include concise, accurate explanations where the type supports them. Numerical explanations show formula, substitution, result and SI unit. For false statements, give the corrected form." else "Do not include explanations; omit explanation fields."
        return """$role

$request

OUTPUT JSON SCHEMA (shape example, fill with actual questions): ${schema(type, answers, explanations)}

QUESTION TYPE: ${typeName(type)}
DIFFICULTY: $difficultyRule
TYPE RULES: $typeRules
ANSWER SETTING: $answerRule
EXPLANATION SETTING: $explanationRule
SOURCE RULE: $sourceRule
ACADEMIC STANDARD FOR BANGLADESH:
- For primary, junior, SSC, HSC, admission, university and job-exam questions, follow the appropriate Bangladesh curriculum and expected depth. Follow NCTB textbook scope and terminology for school levels.
- Use correct IUPAC and common chemical names, balanced reactions, SI units and symbols. Keep formulas in ASCII digits and Latin element symbols.
- Verify arithmetic, facts, dates, formulas, units and answer keys. Never invent facts or references. Keep the set's academic level consistent.

$FORMAT

Return only a JSON object with a non-empty "questions" array. Each question must match the selected type's schema. Do not include text outside JSON."""
    }
}
