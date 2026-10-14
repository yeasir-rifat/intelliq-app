package com.intelliq.rifatplex.data

/** Port of the website's Randomizer. */
object Randomizer {
    fun parseSetNames(csv: String, count: Int): List<String> {
        val names = csv.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        return (0 until count).map { names.getOrNull(it) ?: ('A' + it).toString() }
    }

    private fun shuffleOptions(q: Question): Question = q.copy(options = q.options.shuffled())

    private fun build(source: List<Question>, mode: String): List<Question> {
        var list = source
        if (mode == "questions" || mode == "both") list = list.shuffled()
        if (mode == "options" || mode == "both") list = list.map { shuffleOptions(it) }
        return list
    }

    fun generate(d: AppData): List<GeneratedSet> {
        val n = d.setCode.numberOfSets.coerceIn(1, 26)
        val names = parseSetNames(d.setCode.codeNames, n)
        val source = d.questions.filter { !it.isEmpty() }
        return names.map { GeneratedSet(it, build(source, d.setCode.randomization)) }
    }
}
