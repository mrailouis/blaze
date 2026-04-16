package me.mrai.larpclient.playeroverride

object PlayerOverrideStringRewriter {
    data class ReplacementRule(
        val names: List<String>,
        val replacement: String
    ) {
        val longestNameLength: Int = names.maxOfOrNull(String::length) ?: 0
    }

    fun rewrite(text: String, rules: List<ReplacementRule>): String {
        if (text.isEmpty() || rules.isEmpty()) {
            return text
        }

        val matches = findMatches(text, rules)
        if (matches.isEmpty()) {
            return text
        }

        val output = StringBuilder(text.length + matches.sumOf { it.rule.replacement.length })
        var cursor = 0

        matches.forEach { match ->
            if (match.start > cursor) {
                output.append(text, cursor, match.start)
            }

            output.append(match.rule.replacement)
            cursor = match.endExclusive
        }

        if (cursor < text.length) {
            output.append(text, cursor, text.length)
        }

        return output.toString()
    }

    private data class Match(
        val start: Int,
        val endExclusive: Int,
        val rule: ReplacementRule,
        val matchedNameLength: Int
    )

    private fun findMatches(text: String, rules: List<ReplacementRule>): List<Match> {
        val matches = mutableListOf<Match>()
        var cursor = 0

        while (cursor < text.length) {
            val match = findNextMatch(text, cursor, rules) ?: break
            matches += match
            cursor = match.endExclusive
        }

        return matches
    }

    private fun findNextMatch(text: String, startIndex: Int, rules: List<ReplacementRule>): Match? {
        var best: Match? = null

        rules.forEach { rule ->
            rule.names.forEach { candidate ->
                var searchIndex = startIndex
                while (searchIndex < text.length) {
                    val foundIndex = text.indexOf(candidate, searchIndex, ignoreCase = true)
                    if (foundIndex < 0) {
                        break
                    }

                    val endIndex = foundIndex + candidate.length
                    if (hasNameBoundaries(text, foundIndex, endIndex)) {
                        val nextMatch = Match(foundIndex, endIndex, rule, candidate.length)
                        best = when {
                            best == null -> nextMatch
                            nextMatch.start < best!!.start -> nextMatch
                            nextMatch.start == best!!.start && nextMatch.matchedNameLength > best!!.matchedNameLength -> nextMatch
                            else -> best
                        }
                        break
                    }

                    searchIndex = foundIndex + 1
                }
            }
        }

        return best
    }

    private fun hasNameBoundaries(text: String, start: Int, endExclusive: Int): Boolean {
        val previous = text.getOrNull(start - 1)
        val next = text.getOrNull(endExclusive)
        return !isUsernameChar(previous) && !isUsernameChar(next)
    }

    private fun isUsernameChar(value: Char?): Boolean {
        return value?.let { it.isLetterOrDigit() || it == '_' } == true
    }
}
