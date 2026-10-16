package com.intelliq.rifatplex.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * The Gemini prompt of AI Mode: one prompt for file mode and topic mode (website `buildPrompt`). It asks for exactly the
 * enabled categories in the configured order. Text fields are written with the app's formatting tools
 * (<sub> <sup> <b> <i> <u> <br> and {{frac|n|d}}), which [aiMarkupToHtml] turns into editor markup.
 */
object GeminiPrompts {
    private val TYPE_LABELS = mapOf(
        QType.MCQ to "MCQ",
        QType.TF to "True/False",
        QType.FILL to "Gap Filling",
        QType.SHORT to "Short Answer",
        QType.DESC to "Descriptive/Essay",
        QType.CREATIVE to "Creative Question Sets",
    )

    private const val FORMATTING = """FORMATTING TOOLS - this is important. The app has a rich-text editor with these tools. Every question, option and explanation must be written with exactly this markup. Never use Markdown, LaTeX, dollar signs, backslash commands, or the notations x^2, x_n, sqrt(x).
- Subscript: <sub>2</sub>   Superscript: <sup>2</sup>   Bold: <b>...</b>   Italic: <i>...</i>   Underline: <u>...</u>   Line break: <br>
- Stacked fraction: {{frac|numerator|denominator}}, for example {{frac|a+b|2}} or {{frac|dy|dx}}. The numerator and denominator may contain <sub>, <sup> and symbols, but never another fraction and never the characters | or }. Use it for algebraic and numeric fractions; simple common fractions may stay as one character (½ ¼ ¾).
- Symbols: type the real character: ² ³ ⁴ ½ ⅓ ¼ ⅔ ¾ √ ∛ ∑ ∫ ∞ ≈ ≠ ≤ ≥ ± × ÷ ∝ α β γ δ θ π λ μ Ω Δ ∂ ∇ → ⇌ ° ‰ · …
- Powers and indices: x<sup>2</sup>, 10<sup>-3</sup>, a<sub>n</sub>, e<sup>-x</sup>. Roots: √x, √(x+1), ∛x.
- Chemistry: atom counts are subscripts and charges / isotope numbers are superscripts: H<sub>2</sub>SO<sub>4</sub>, Ca(OH)<sub>2</sub>, Al<sub>2</sub>(SO<sub>4</sub>)<sub>3</sub>, CuSO<sub>4</sub>·5H<sub>2</sub>O, Fe<sup>3+</sup>, SO<sub>4</sub><sup>2-</sup>, <sup>14</sup>C. Write reactions like 2H<sub>2</sub> + O<sub>2</sub> → 2H<sub>2</sub>O (use ⇌ for reversible reactions; state symbols (s) (l) (g) (aq) as plain text). A coefficient is a plain digit in front of the formula, never a subscript.
- Inside formulas and expressions always use ASCII digits 0-9 and Latin element symbols, even when the sentence around them is Bangla.
- Plain text stays plain: do not add tags to ordinary words.
- Lists and steps: never use <ul>, <ol> or <li>. When an answer, explanation or marking guide has several points, causes, features or examples, write each point on its own line (lines are separated with <br>) and start each line with "• ". Never squeeze them into one paragraph.
- Mathematical explanations and solutions must NEVER be one running paragraph: put every step on its own line (<br>), one equation per line: formula first, then substitution, then the final result with its unit. Example: v = u + at<br>v = 0 + 2 × 5<br>v = 10 m/s.
- A chemical reaction must NEVER sit inside a sentence: put it on its own line (<br> before and after when other text is present), balanced, with → or ⇌, state symbols where useful and conditions in brackets after the equation.
- In Bangla content keep ordinary numbers and units in Bangla, but keep chemical formulas, variables and mathematical symbols in English letters.
- Keep the JSON valid: no raw line breaks inside strings (use <br> instead) and escape any double quotes inside text."""

    private const val QUALITY = """CALCULATION QUALITY:
- For every numerical, algebraic or chemical-calculation question, first solve it completely yourself and double-check the arithmetic, units, significant figures and the balancing of equations, then write the question. Exactly one option must be correct (unless the user asks otherwise); the other options must be plausible wrong answers (typical slips such as a sign error, a wrong formula or a unit mistake).
- The explanation of a calculation question must show the key steps briefly: formula, substitution, result with unit.
- Never write an unbalanced chemical equation; check atoms and charge on both sides.
- Keep numbers and units consistent (for example 2.5 mol, 10<sup>-3</sup> m)."""

    private const val ACADEMIC = """ACADEMIC STANDARD FOR QUESTIONS, ANSWERS AND EXPLANATIONS (Bangladesh):
- Everything must be consistent with the Bangladesh education system. Infer the level from the user instruction or source: Class 1-5 (primary), Class 6-8 (junior), Class 9-10 (SSC), Class 11-12 (HSC), university admission tests (Medical, Engineering/BUET, Public University, GST, Agriculture), university course exams, or job exams (BCS preliminary/written, Bank, Primary teacher, NTRCA, other recruitment tests). If the level is not stated, choose the standard that best fits the topic and keep it uniform. Never mix levels inside one set.
- Follow NCTB textbook content, definitions, terminology, units (SI), symbols, chapter scope and difficulty for school levels; follow the typical past-paper pattern and depth for admission and recruitment exams. Use Standard Bangla (প্রমিত বাংলা) with the technical terms used in NCTB books and give the English term in brackets once when it helps, e.g. সালোকসংশ্লেষণ (Photosynthesis).
- Accuracy first: every fact, formula, date, name and number must be correct and verifiable. Never invent statistics, years, book references or board-exam citations. If you are not sure about a fact, do not build a question on it. Each MCQ must have exactly one clearly correct option; distractors must be plausible but definitely wrong.
- Explanation style by level: Class 1-5: very short, simple words, one or two lines. Class 6-10: textbook-style reasoning, key definition or rule first, then the reason. Class 11-12 and university: precise concept or law, derivation or mechanism in short steps. Admission and BCS/Bank: crisp concept, the shortcut or trick where one exists, and a one-line note on the common trap.
- MCQ explanation (2 to 5 short lines): first state the correct answer with the core reason; then, only when it adds value, a short list of lines starting with "• " explaining why the main distractors are wrong or giving a related fact worth remembering. Never repeat the question and never write a long essay.
- Math and numerical problems: (1) data given and what to find, (2) formula or law, (3) substitution, (4) calculation, (5) final answer with unit, each on its own line as described above. For admission and job exams add a one-line shortcut method after the standard solution when one exists. Keep to the notation and symbols of NCTB books.
- Physics and Chemistry numericals: write given data, formula, substitution, result with correct SI unit and sensible significant figures. Chemistry: use correct IUPAC and common names as in NCTB, balanced equations, and state conditions. Biology: use correct scientific terms, keep classification and processes in ordered steps or lists. Accounting, Economics, ICT: use the standard terms, formulas and formats taught in the curriculum.
- English: for grammar or vocabulary items state the rule in one line and give the correct form; for synonyms and antonyms give the meaning of the answer. Bangla language and literature: be precise about grammar rules, authors, works and literary periods.
- General Knowledge, Bangladesh Affairs, International Affairs, Mental Ability, Everyday Science (BCS and bank style): state the fact with the key context (who, when, where, why) in one to three short lines; use only well-established facts and avoid anything that changes frequently unless the source gives it.
- True/False: explain in one or two lines why the statement is true or false; for a false statement also give the correct form. Gap Filling: put only the exact missing word or phrase in answer. Short Answer: 2 to 4 sentences or 2 to 4 list points covering the key points the examiner expects. Descriptive/Essay: a structured model answer with short bold sub-headings (<b>) and lists, with an introduction, main points in logical order, and a conclusion; length suited to the level and marks.
- Creative questions (সৃজনশীল প্রশ্ন, as in SSC/HSC): the stimulus is a short original passage, scenario, data or figure description. For Class 6-12 Bangladeshi curriculum use exactly 4 subQuestions in this order with marks 1, 2, 3, 4: knowledge (জ্ঞানমূলক: what/define/name), comprehension (অনুধাবনমূলক: explain why/how), application (প্রয়োগমূলক: apply to the stimulus, calculate or solve), higher order (উচ্চতর দক্ষতামূলক: analyse, evaluate or justify with a clear conclusion). Do not write the labels ক, খ, গ, ঘ inside the text. Each model answer must match the cognitive level and its marks: 1 mark one line, 2 marks a short explanation, 3 marks application with steps, 4 marks reasoned analysis ending with a judgement.
- Smart formatting of answers: lead with the answer, then the reason. Put key terms, laws and final results in <b>. Use separate lines starting with "• " for multiple points, separate lines for steps, equations and reactions. Keep every answer compact, clean and exam-ready; do not add greetings, filler, disclaimers or sentences like Here is the explanation.

EXAM HEADER AND CONSISTENCY RULES:
- Always return a meta object written in the same language as the questions. meta.title: a proper examination title built from the subject/class/topic (for example a model test title); meta.subtitle: a short line such as class, subject or chapter; meta.marks: the total of all question marks as a number; meta.time: a sensible duration for the paper (for example 60 Minutes or ৬০ মিনিট); meta.note: one short instruction line for examinees.
- Every question must have marks: MCQ, True/False and Gap Filling 1 each, Short Answer 2 each, Descriptive/Essay 5 each (adjust to level), Creative sets 10 in total with subQuestion marks 1, 2, 3, 4.
- Use one consistent technical term for the same concept in every question, option, answer and explanation of the paper, following the NCTB textbook term; never alternate between two names for the same thing.
- A fraction inside a superscript or subscript must be written with a slash (for example 2<sup>1/2</sup>), never with a stacked fraction inside <sup> or <sub>.
- A chemical equation, including any heat term such as + তাপ, must stay together on its own line with <br> before and after it; never put an equation in the middle of a sentence."""

    private const val SCHEMA_AND_RULES = """Return ONLY valid JSON in this schema: {"meta":{"title":"","subtitle":"","marks":"","time":"","note":""},"questions":[{"type":"mcq|tf|fill|short|desc|creative","question":"question or stimulus text","options":["A","B","C","D"],"correctIndex":0,"tfAnswer":true,"answer":"expected/model answer","explanation":"brief explanation or marking guide","marks":"1","subQuestions":[{"question":"subquestion text","marks":"1","answer":"expected answer"}]}]}

Rules:
- Use type values exactly: mcq, tf, fill, short, desc, creative.
- MCQ: exactly four options and correctIndex 0-3 (unless the user's request asks for a different number of options).
- True/False: tfAnswer must be boolean true or false.
- Gap Filling: write a clear sentence with a blank such as ______; put the missing text in answer.
- Short Answer: put a concise expected answer in answer.
- Descriptive/Essay: put the question in question and the model answer or key points in answer.
- Creative: question is the stimulus/context; subQuestions is an array of 2-4 objects with question, marks, and answer (exactly 4 objects with marks 1, 2, 3, 4 for Bangladeshi Class 6-12 creative questions, as described in the academic standard). Keep each set grouped as one question record.
- Include answer and explanation fields only when requested; otherwise use empty strings or omit them.
- Follow the source language or the user's explicit language request. Preserve accurate Bangla/English text and Unicode.
- Avoid duplicates, unsupported claims, and repeated wording. Follow the exact requested quantity for every category.
{{FIGURE_LINE}}
- Return no markdown, comments, or text outside the JSON object."""

    private const val FIGURE_RULE = """- Figures taken from the source (optional field "figure"): when the attached image/PDF itself contains the figure, graph, circuit, map, table-picture or drawing that a question, a creative stimulus or a sub-question depends on, do NOT redraw it. Instead add "figure":{"file":N,"page":P,"box":[ymin,xmin,ymax,xmax]} where N is the 1-based number of the attached image/PDF file listed above, P is the 1-based page (1 for an image), and box is the tight bounding box of ONLY that figure (not the question text around it) in coordinates normalized to 0-1000 (0,0 is the top-left of the page, 1000,1000 the bottom-right). Put it on the question record (or inside the subQuestion object when only that part needs the figure). Never add a "diagram" field and never draw a figure as SVG. If the source has no figure that a question needs, do not write a question that depends on a picture you cannot describe completely in words."""

    private const val NO_FIGURE_RULE = """- Do NOT add "figure" or "diagram" fields, and do not write a question that depends on a picture you cannot describe completely in words."""

    fun build(request: String, files: List<AiFile>, topic: Boolean, cfg: AiGenConfig): String {
        val names = files.map { it.name }.filter { it.isNotBlank() }
        val media = files.filter { it.kind == AiKind.Image || it.kind == AiKind.Pdf }
        val mediaList = media.mapIndexed { i, f -> (i + 1).toString() + ". " + f.name + " (" + (if (f.kind == AiKind.Pdf) "pdf" else "image") + ")" }
        val selected = JSONArray()
        cfg.types.forEach { t ->
            selected.put(JSONObject().put("type", t.type).put("label", TYPE_LABELS[t.type] ?: t.type).put("count", t.count))
        }
        val texts = files.filter { it.kind == AiKind.Text }
        val extracted = if (texts.isEmpty()) "" else
            "Extracted document text:\n" + texts.joinToString("\n\n") { "--- " + it.name + " ---\n" + it.text } + "\n\n"

        val sb = StringBuilder()
        sb.append("You are an expert exam-question writer. Generate a mixed-type question paper based on the user's subject/topic or attached source material.\n\n")
        sb.append("User instruction / subject: ").append(request).append('\n')
        sb.append("Mode: ").append(if (topic) "topic-based generation using your knowledge (no file is attached)" else "source-based generation; prioritize attached source material").append('\n')
        sb.append("Attached source files: ").append(if (names.isEmpty()) "none" else names.joinToString(", ")).append('\n')
        sb.append("Attached image/PDF files in the order they were sent: ").append(if (mediaList.isEmpty()) "none" else mediaList.joinToString("; ")).append('\n')
        sb.append("Difficulty: ").append(cfg.difficulty).append('\n')
        sb.append("Include answers: ").append(cfg.answers).append('\n')
        sb.append("Include explanations: ").append(cfg.explanations).append("\n\n")
        sb.append("Generate only these enabled categories, in exactly this order and quantity: ").append(selected.toString())
        sb.append(". The count for creative means complete creative sets, not subquestions. Never add a disabled category.\n\n")
        sb.append(extracted)
        sb.append(FORMATTING).append("\n\n").append(QUALITY).append("\n\n").append(ACADEMIC).append("\n\n").append(SCHEMA_AND_RULES.replace("{{FIGURE_LINE}}", if (media.isEmpty()) NO_FIGURE_RULE else FIGURE_RULE))
        return sb.toString()
    }
}
