package me.mrai.blaze.platform

import net.minecraft.resources.Identifier

typealias BlazeIdentifier = Identifier

object BlazeIdentifiers {
    fun of(namespace: String, path: String): BlazeIdentifier {
        return BlazeIdentifier.fromNamespaceAndPath(namespace, path)
    }
}
