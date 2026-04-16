package me.mrai.larpclient.util

object TextSanitizer {
    private val formattingRegex = Regex("§.")
    private val whitespaceRegex = Regex("\\s+")

    fun stripFormatting(text: String): String = formattingRegex.replace(text, "")

    fun compactLower(text: String): String {
        return whitespaceRegex.replace(stripFormatting(text), "").lowercase()
    }

    fun normalizedLower(text: String): String {
        return whitespaceRegex.replace(stripFormatting(text), " ").trim().lowercase()
    }
}
