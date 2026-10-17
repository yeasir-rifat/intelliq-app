package com.intelliq.rifatplex.ui

/**
 * The four documents the app can preview and download.
 *  - [QUESTION] question paper
 *  - [ANSWER]   answer paper (questions with correct answers and explanations)
 *  - [OMR]      bubble sheet for the MCQs
 *  - [KEY]      "উত্তরমালা": the correct MCQ answers in a box (for the examiner)
 */
enum class DocKind {
    QUESTION, ANSWER, OMR, KEY;

    /** Drawn by [PaperPages] (the others are drawn by [SheetPages]). */
    val isPaper: Boolean get() = this == QUESTION || this == ANSWER

    /** The paper is drawn with the answers on. */
    val showsAnswers: Boolean get() = this == ANSWER

    /** Key in [com.intelliq.rifatplex.data.PAGE_SIZES_MM]: the OMR sheet and the answer sheet are always A4 (fixed look, see SheetStyle). */
    fun pageSizeKey(settings: com.intelliq.rifatplex.data.DocSettings): String = if (this == OMR || this == KEY) "A4" else settings.pageSize
}
