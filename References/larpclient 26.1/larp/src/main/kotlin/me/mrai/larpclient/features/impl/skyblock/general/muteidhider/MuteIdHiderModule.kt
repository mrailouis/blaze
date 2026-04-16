package me.mrai.larpclient.features.impl.skyblock.general.muteidhider

import me.mrai.larpclient.module.Module
import me.mrai.larpclient.module.ModuleCategory

object MuteIdHiderModule : Module(
    name = "Mute ID Hider",
    description = "Hides Hypixel mute blocks and replaces them with a clean message.",
    category = ModuleCategory.SKYBLOCK_GENERAL
)
