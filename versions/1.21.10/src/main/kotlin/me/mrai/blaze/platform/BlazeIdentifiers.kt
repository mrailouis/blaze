package me.mrai.blaze.platform

import net.minecraft.resources.ResourceLocation

typealias BlazeIdentifier = ResourceLocation

object BlazeIdentifiers {
    fun of(namespace: String, path: String): BlazeIdentifier {
        return BlazeIdentifier.fromNamespaceAndPath(namespace, path)
    }
}
