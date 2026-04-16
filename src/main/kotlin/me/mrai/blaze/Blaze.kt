package me.mrai.blaze

import net.fabricmc.api.ModInitializer
import org.slf4j.LoggerFactory

object Blaze : ModInitializer {
    const val MOD_ID = "blaze"

    val logger = LoggerFactory.getLogger(MOD_ID)

    override fun onInitialize() {
        logger.info("Initializing BLAZE core")
    }
}
