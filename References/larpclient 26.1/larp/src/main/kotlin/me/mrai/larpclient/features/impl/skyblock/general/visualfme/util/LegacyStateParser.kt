package me.mrai.larpclient.features.impl.skyblock.general.visualfme.util

import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.Property

object LegacyStateParser {

    fun parse(raw: String): BlockState? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null

        val match = STATE_REGEX.matchEntire(trimmed) ?: return null
        val legacyId = match.groupValues[1]
        val props = parseProperties(match.groupValues.getOrElse(2) { "" }).toMutableMap()

        val (modernId, modernProps) = convertLegacyState(legacyId, props)

        val identifier = Identifier.tryParse(modernId) ?: return null
        if (!BuiltInRegistries.BLOCK.containsKey(identifier)) return null

        var state = BuiltInRegistries.BLOCK.getValue(identifier)?.defaultBlockState() ?: return null

        for ((name, value) in modernProps) {
            state = applyProperty(state, name, value)
        }

        return state
    }

    private fun parseProperties(raw: String): Map<String, String> {
        if (raw.isBlank()) return emptyMap()

        return raw.split(",")
            .mapNotNull { token ->
                val idx = token.indexOf('=')
                if (idx == -1) return@mapNotNull null
                val key = token.substring(0, idx).trim()
                val value = token.substring(idx + 1).trim()
                if (key.isEmpty()) null else key to value
            }
            .toMap()
    }

    private fun convertLegacyState(
        legacyId: String,
        originalProps: MutableMap<String, String>
    ): Pair<String, MutableMap<String, String>> {
        val props = originalProps.toMutableMap()

        fun slabHalfToType() {
            val half = props.remove("half") ?: return
            props["type"] = if (half == "top") "top" else "bottom"
        }

        fun colorBlock(prefix: String, suffix: String): Pair<String, MutableMap<String, String>> {
            val color = props.remove("color") ?: return "minecraft:${prefix}_${suffix}" to props
            return "minecraft:${color}_${suffix}" to props
        }

        return when (legacyId) {
            "minecraft:wool" -> {
                val color = props.remove("color") ?: "white"
                "minecraft:${color}_wool" to props
            }

            "minecraft:stained_glass" -> {
                val color = props.remove("color") ?: "white"
                "minecraft:${color}_stained_glass" to props
            }

            "minecraft:stained_glass_pane" -> {
                val color = props.remove("color") ?: "white"
                "minecraft:${color}_stained_glass_pane" to props
            }

            "minecraft:stained_hardened_clay" -> {
                val color = props.remove("color") ?: "white"
                "minecraft:${color}_terracotta" to props
            }

            "minecraft:lit_redstone_lamp" -> {
                props["lit"] = "true"
                "minecraft:redstone_lamp" to props
            }

            "minecraft:redstone_lamp" -> {
                if (!props.containsKey("lit")) {
                    props["lit"] = "false"
                }
                "minecraft:redstone_lamp" to props
            }

            "minecraft:monster_egg" -> {
                val variant = props.remove("variant") ?: "stone"
                val mapped = when (variant) {
                    "stone" -> "minecraft:infested_stone"
                    "cobblestone" -> "minecraft:infested_cobblestone"
                    "stone_brick", "stonebrick" -> "minecraft:infested_stone_bricks"
                    "mossy_brick", "mossy_stone_brick" -> "minecraft:infested_mossy_stone_bricks"
                    "cracked_brick", "cracked_stone_brick" -> "minecraft:infested_cracked_stone_bricks"
                    "chiseled_brick", "chiseled_stone_brick" -> "minecraft:infested_chiseled_stone_bricks"
                    else -> "minecraft:infested_stone_bricks"
                }
                mapped to props
            }

            "minecraft:double_plant" -> {
                props.remove("facing")
                val variant = props.remove("variant") ?: "double_grass"
                val mapped = when (variant) {
                    "sunflower" -> "minecraft:sunflower"
                    "syringa" -> "minecraft:lilac"
                    "double_grass" -> "minecraft:tall_grass"
                    "double_fern" -> "minecraft:large_fern"
                    "rose" -> "minecraft:rose_bush"
                    "paeonia" -> "minecraft:peony"
                    else -> "minecraft:tall_grass"
                }
                mapped to props
            }

            "minecraft:log" -> {
                val variant = props.remove("variant") ?: "oak"
                val mapped = when (variant) {
                    "oak" -> "minecraft:oak_log"
                    "spruce" -> "minecraft:spruce_log"
                    "birch" -> "minecraft:birch_log"
                    "jungle" -> "minecraft:jungle_log"
                    else -> "minecraft:oak_log"
                }
                mapped to props
            }

            "minecraft:log2" -> {
                val variant = props.remove("variant") ?: "acacia"
                val mapped = when (variant) {
                    "acacia" -> "minecraft:acacia_log"
                    "dark_oak" -> "minecraft:dark_oak_log"
                    else -> "minecraft:acacia_log"
                }
                mapped to props
            }

            "minecraft:planks" -> {
                val variant = props.remove("variant") ?: "oak"
                "minecraft:${variant}_planks" to props
            }

            "minecraft:fence_gate" -> {
                "minecraft:oak_fence_gate" to props
            }

            "minecraft:stone" -> {
                val variant = props.remove("variant") ?: "stone"
                val mapped = when (variant) {
                    "stone" -> "minecraft:stone"
                    "granite" -> "minecraft:granite"
                    "smooth_granite" -> "minecraft:polished_granite"
                    "diorite" -> "minecraft:diorite"
                    "smooth_diorite" -> "minecraft:polished_diorite"
                    "andesite" -> "minecraft:andesite"
                    "smooth_andesite" -> "minecraft:polished_andesite"
                    else -> "minecraft:stone"
                }
                mapped to props
            }

            "minecraft:stonebrick" -> {
                val variant = props.remove("variant") ?: "default"
                val mapped = when (variant) {
                    "default", "stonebrick" -> "minecraft:stone_bricks"
                    "mossy_stonebrick", "mossy_brick" -> "minecraft:mossy_stone_bricks"
                    "cracked_stonebrick", "cracked_brick" -> "minecraft:cracked_stone_bricks"
                    "chiseled_stonebrick", "chiseled_brick" -> "minecraft:chiseled_stone_bricks"
                    else -> "minecraft:stone_bricks"
                }
                mapped to props
            }

            "minecraft:red_sandstone" -> {
                val type = props.remove("type") ?: "red_sandstone"
                val mapped = when (type) {
                    "red_sandstone" -> "minecraft:red_sandstone"
                    "chiseled_red_sandstone" -> "minecraft:chiseled_red_sandstone"
                    "smooth_red_sandstone" -> "minecraft:smooth_red_sandstone"
                    else -> "minecraft:red_sandstone"
                }
                mapped to props
            }

            "minecraft:sandstone" -> {
                val type = props.remove("type") ?: "sandstone"
                val mapped = when (type) {
                    "sandstone" -> "minecraft:sandstone"
                    "chiseled_sandstone" -> "minecraft:chiseled_sandstone"
                    "smooth_sandstone" -> "minecraft:smooth_sandstone"
                    else -> "minecraft:sandstone"
                }
                mapped to props
            }

            "minecraft:quartz_block" -> {
                val variant = props.remove("variant") ?: "default"
                val mapped = when (variant) {
                    "default" -> "minecraft:quartz_block"
                    "chiseled" -> "minecraft:chiseled_quartz_block"
                    "lines_y" -> "minecraft:quartz_pillar"
                    "lines_x" -> {
                        props["axis"] = "x"
                        "minecraft:quartz_pillar"
                    }
                    "lines_z" -> {
                        props["axis"] = "z"
                        "minecraft:quartz_pillar"
                    }
                    else -> "minecraft:quartz_block"
                }
                mapped to props
            }

            "minecraft:prismarine" -> {
                val variant = props.remove("variant") ?: "rough"
                val mapped = when (variant) {
                    "rough", "prismarine" -> "minecraft:prismarine"
                    "bricks", "prismarine_bricks" -> "minecraft:prismarine_bricks"
                    "dark" -> "minecraft:dark_prismarine"
                    else -> "minecraft:prismarine"
                }
                mapped to props
            }

            "minecraft:brick_block" -> {
                "minecraft:bricks" to props
            }

            "minecraft:stone_slab" -> {
                val variant = props.remove("variant") ?: "stone"
                slabHalfToType()
                val mapped = when (variant) {
                    "stone" -> "minecraft:smooth_stone_slab"
                    "sand" -> "minecraft:sandstone_slab"
                    "wood_old", "wood" -> "minecraft:oak_slab"
                    "cobblestone", "cobble" -> "minecraft:cobblestone_slab"
                    "brick" -> "minecraft:brick_slab"
                    "smooth_brick", "stone_brick" -> "minecraft:stone_brick_slab"
                    "nether_brick" -> "minecraft:nether_brick_slab"
                    "quartz" -> "minecraft:quartz_slab"
                    else -> "minecraft:smooth_stone_slab"
                }
                mapped to props
            }

            "minecraft:stone_slab2" -> {
                val variant = props.remove("variant") ?: "red_sandstone"
                slabHalfToType()
                val mapped = when (variant) {
                    "red_sandstone" -> "minecraft:red_sandstone_slab"
                    else -> "minecraft:red_sandstone_slab"
                }
                mapped to props
            }

            "minecraft:wooden_slab" -> {
                val variant = props.remove("variant") ?: "oak"
                slabHalfToType()
                "minecraft:${variant}_slab" to props
            }

            else -> legacyId to props
        }
    }

    private fun applyProperty(state: BlockState, name: String, value: String): BlockState {
        val property = state.properties.firstOrNull { it.name == name } ?: return state
        val parsed = property.getValue(value)
        if (parsed.isEmpty) return state

        @Suppress("UNCHECKED_CAST")
        return state.setValue(property as Property<Comparable<Any>>, parsed.get() as Comparable<Any>)
    }

    private val STATE_REGEX = Regex("""^([^\[]+)(?:\[(.*)])?$""")
}
