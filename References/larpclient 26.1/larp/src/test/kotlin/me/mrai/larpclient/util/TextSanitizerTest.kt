package me.mrai.larpclient.util

import kotlin.test.Test
import kotlin.test.assertEquals

class TextSanitizerTest {
    @Test
    fun `stripFormatting removes minecraft formatting codes`() {
        assertEquals("Hello World", TextSanitizer.stripFormatting("§aHello §lWorld"))
    }

    @Test
    fun `compactLower removes whitespace and lowercases`() {
        assertEquals("helloworld", TextSanitizer.compactLower(" §aHello   World "))
    }

    @Test
    fun `normalizedLower keeps single spaces between words`() {
        assertEquals("hello world", TextSanitizer.normalizedLower("  §bHello   World  "))
    }
}
