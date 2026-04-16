package me.mrai.larpclient.util

import java.util.logging.Level
import java.util.logging.Logger

object LarpLog {
    private val logger: Logger = Logger.getLogger("LarpClient")
    private val debugEnabled: Boolean =
        System.getProperty("larpclient.debug")?.equals("true", ignoreCase = true) == true

    fun info(message: String) {
        logger.info(message)
    }

    fun warn(message: String) {
        logger.warning(message)
    }

    fun error(message: String, throwable: Throwable? = null) {
        if (throwable == null) {
            logger.severe(message)
            return
        }

        logger.log(Level.SEVERE, message, throwable)
    }

    fun debug(message: String) {
        if (debugEnabled) {
            logger.info("[debug] $message")
        }
    }
}
