package me.mrai.larpclient.util

import net.fabricmc.loader.api.FabricLoader

object VersionInfo {
    val version: String by lazy {
        FabricLoader.getInstance()
            .getModContainer("larpclient")
            .orElseThrow()
            .metadata
            .version
            .friendlyString
    }
}