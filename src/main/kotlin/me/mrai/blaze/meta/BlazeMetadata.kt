package me.mrai.blaze.meta

import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.Properties

object BlazeMetadata {
    private val properties: Properties by lazy {
        Properties().apply {
            BlazeMetadata::class.java.getResourceAsStream("/blaze.properties")?.use { input ->
                InputStreamReader(input, StandardCharsets.UTF_8).use(::load)
            }
        }
    }

    val version: String
        get() = properties.getProperty("version", "dev")

    val prefixSymbol: String
        get() = properties.getProperty("prefixSymbol", "\u00BB")
}
