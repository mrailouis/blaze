package me.mrai.larpclient.playeroverride

import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.contents.PlainTextContents
import net.minecraft.network.chat.contents.TranslatableContents

object PlayerOverrideTextRewriter {
    data class ReplacementRule(
        val names: List<String>,
        val replacement: Component
    ) {
        val longestNameLength: Int = names.maxOfOrNull(String::length) ?: 0
    }

    fun rewrite(component: Component, rules: List<ReplacementRule>): Component {
        if (rules.isEmpty()) {
            return component
        }

        val plain = component.string
        if (plain.isEmpty() || !containsAnyCandidate(plain, rules)) {
            return component
        }

        return rewriteInternal(component, rules)
    }

    private fun rewriteInternal(component: Component, rules: List<ReplacementRule>): Component {
        val rewrittenBase = rewriteBase(component, rules)
        var siblingsChanged = false
        val rewrittenSiblings = component.siblings.map { sibling ->
            val rewritten = rewriteInternal(sibling, rules)
            if (rewritten !== sibling) {
                siblingsChanged = true
            }
            rewritten
        }

        if (rewrittenBase == null && !siblingsChanged) {
            return component
        }

        val result = rewrittenBase ?: component.plainCopy().setStyle(component.style)
        rewrittenSiblings.forEach(result::append)
        return result
    }

    private fun rewriteBase(component: Component, rules: List<ReplacementRule>): MutableComponent? {
        return when (val contents = component.contents) {
            is PlainTextContents.LiteralContents -> rewriteLiteral(contents.text(), component.style, rules)
            is TranslatableContents -> rewriteTranslatable(contents, component.style, rules)
            else -> null
        }
    }

    private fun rewriteTranslatable(
        contents: TranslatableContents,
        style: Style,
        rules: List<ReplacementRule>
    ): MutableComponent? {
        val originalArgs = contents.args
        val rewrittenArgs = ArrayList<Any>(originalArgs.size)
        var changed = false

        for (index in originalArgs.indices) {
            val arg = originalArgs[index]
            val rewritten = when (arg) {
                is Component -> rewrite(arg, rules)
                is String -> rewriteLiteral(arg, Style.EMPTY, rules) ?: arg
                else -> arg
            }

            if (rewritten !== arg) {
                changed = true
            }

            rewrittenArgs += rewritten
        }

        if (!changed) {
            return null
        }

        val fallback = contents.fallback
        val translated = if (fallback != null) {
            Component.translatableWithFallback(contents.key, fallback, *rewrittenArgs.toTypedArray())
        } else {
            Component.translatable(contents.key, *rewrittenArgs.toTypedArray())
        }

        return translated.setStyle(style)
    }

    private fun rewriteLiteral(
        text: String,
        baseStyle: Style,
        rules: List<ReplacementRule>
    ): MutableComponent? {
        val matches = findMatches(text, rules)
        if (matches.isEmpty()) {
            return null
        }

        val root = Component.empty().setStyle(baseStyle)
        var cursor = 0

        matches.forEach { match ->
            if (match.start > cursor) {
                root.append(Component.literal(text.substring(cursor, match.start)).setStyle(baseStyle))
            }

            root.append(LegacyFormattedNameParser.copyWithFallbackStyle(match.rule.replacement, baseStyle))
            cursor = match.endExclusive
        }

        if (cursor < text.length) {
            root.append(Component.literal(text.substring(cursor)).setStyle(baseStyle))
        }

        return root
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

    private fun containsAnyCandidate(text: String, rules: List<ReplacementRule>): Boolean {
        return rules.any { rule ->
            rule.names.any { candidate ->
                text.contains(candidate, ignoreCase = true)
            }
        }
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
