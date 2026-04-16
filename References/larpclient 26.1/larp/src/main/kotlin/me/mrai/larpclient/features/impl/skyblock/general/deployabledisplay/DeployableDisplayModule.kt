package me.mrai.larpclient.features.impl.skyblock.general.deployabledisplay

import com.mojang.authlib.properties.Property
import me.mrai.larpclient.module.ModeSetting
import me.mrai.larpclient.module.Module
import me.mrai.larpclient.module.ModuleCategory
import me.mrai.larpclient.util.TextSanitizer
import net.minecraft.client.Minecraft
import net.minecraft.core.component.DataComponents
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.ResolvableProfile
import net.minecraft.world.phys.AABB
import java.nio.charset.StandardCharsets
import java.util.Base64
import kotlin.math.max

object DeployableDisplayModule : Module(
    name = "Deployable Display",
    description = "Displays the strongest nearby deployable in a movable HUD.",
    category = ModuleCategory.SKYBLOCK_GENERAL
) {
    val style = ModeSetting("Style", listOf("Sweaty", "Compact"), "Sweaty")

    private val deployablePattern = Regex("""[A-Za-z '\-]* (?<seconds>\d+)s$""")
    private val totemPattern = Regex("""Remaining: (?:(?<minutes>\d{1,2})m )?(?<seconds>\d{1,2})s$""")
    private val texturePattern = Regex(""""url"\s*:\s*"http://textures\\.minecraft\\.net/texture/([a-f0-9]+)"""")

    @Volatile
    private var activeDeployable: ActiveDeployable? = null
    private var missedTicks = 0

    init {
        settings += listOf(style)
    }

    override fun onDisable() {
        activeDeployable = null
        missedTicks = 0
    }

    override fun onTick() {
        val client = Minecraft.getInstance()
        val player = client.player ?: run {
            clear()
            return
        }
        val level = client.level ?: run {
            clear()
            return
        }

        var best: ActiveDeployable? = null
        for (entity in level.entitiesForRendering()) {
            val stand = entity as? ArmorStand ?: continue
            val detected = detect(stand, player.gameProfile.name) ?: continue
            if (best == null || detected.type.ordinal > best.type.ordinal) {
                best = detected
            }
        }

        if (best != null) {
            activeDeployable = best
            missedTicks = 0
        } else {
            missedTicks++
            if (missedTicks > 3) {
                activeDeployable = null
            }
        }
    }

    fun active(): ActiveDeployable? = if (enabled) activeDeployable else null

    fun isSweaty(): Boolean = style.selected.equals("Sweaty", ignoreCase = true)

    private fun detect(stand: ArmorStand, playerName: String): ActiveDeployable? {
        val player = Minecraft.getInstance().player ?: return null
        val name = stand.customName?.string
        if (name != null) {
            val stripped = TextSanitizer.stripFormatting(name)
            val type = DeployableType.byDisplayName(stripped) ?: return null
            if (!type.inRange(stand.distanceToSqr(player))) return null

            if (type == DeployableType.TOTEM_OF_CORRUPTION) {
                return detectTotem(stand, playerName, type)
            }

            val seconds = deployablePattern.find(stripped)?.groups?.get("seconds")?.value?.toIntOrNull() ?: return null
            return ActiveDeployable(type, seconds)
        }

        if (!stand.isInvisible) return null
        val headItem = stand.getItemBySlot(EquipmentSlot.HEAD)
        if (headItem.isEmpty) return null

        val textureId = textureId(headItem) ?: return null
        val type = DeployableType.byTextureId(textureId) ?: return null
        if (!type.inRange(stand.distanceToSqr(player))) return null

        val seconds = max(0, 180 - stand.tickCount / 20)
        return ActiveDeployable(type, seconds)
    }

    private fun detectTotem(stand: ArmorStand, playerName: String, type: DeployableType): ActiveDeployable? {
        val level = Minecraft.getInstance().level ?: return null
        val nearby = level.getEntitiesOfClass(
            ArmorStand::class.java,
            AABB(stand.x - 0.1, stand.y - 1.0, stand.z - 0.1, stand.x + 0.1, stand.y, stand.z + 0.1)
        )
        val ownerLine = "Owner: $playerName"
        var owned = false
        var seconds: Int? = null

        for (entry in nearby) {
            val stripped = TextSanitizer.stripFormatting(entry.customName?.string ?: continue)
            if (stripped == ownerLine) {
                owned = true
                continue
            }
            val match = totemPattern.find(stripped) ?: continue
            val minutes = match.groups["minutes"]?.value?.toIntOrNull() ?: 0
            val secs = match.groups["seconds"]?.value?.toIntOrNull() ?: continue
            seconds = minutes * 60 + secs
        }

        return if (owned && seconds != null) ActiveDeployable(type, seconds) else null
    }

    private fun textureId(stack: ItemStack): String? {
        val profile: ResolvableProfile = stack.get(DataComponents.PROFILE) ?: return null
        val textureValue = profile.partialProfile().properties().get("textures")
            .firstOrNull()
            ?.let(Property::value)
            ?: return null

        val decoded = runCatching {
            String(Base64.getDecoder().decode(textureValue), StandardCharsets.UTF_8)
        }.getOrNull() ?: return null

        return texturePattern.find(decoded)?.groupValues?.getOrNull(1)
    }

    private fun clear() {
        activeDeployable = null
        missedTicks = 0
    }

    data class ActiveDeployable(
        val type: DeployableType,
        val seconds: Int
    )

    enum class DeployableType(
        val displayPrefix: String,
        val rangeSquared: Int,
        val textureId: String = "",
        val iconPath: String,
        val titleColor: Int,
        val stats: List<StatLine>
    ) {
        RADIANT(
            "Radiant ",
            18 * 18,
            iconPath = "radiant",
            titleColor = 0xFF6EE7A8.toInt(),
            stats = listOf(StatLine("HP", "+1%/s", 0xFF6EE7A8.toInt()))
        ),
        MANA_FLUX(
            "Mana Flux ",
            18 * 18,
            iconPath = "manaflux",
            titleColor = 0xFF7DD3FC.toInt(),
            stats = listOf(
                StatLine("HP", "+2%/s", 0xFF6EE7A8.toInt()),
                StatLine("MP", "+50%", 0xFF7DD3FC.toInt()),
                StatLine("STR", "+10", 0xFFFF8A70.toInt())
            )
        ),
        OVERFLUX(
            "Overflux ",
            18 * 18,
            iconPath = "overflux",
            titleColor = 0xFFFCA5A5.toInt(),
            stats = listOf(
                StatLine("HP", "+2.5%/s", 0xFF6EE7A8.toInt()),
                StatLine("MP", "+100%", 0xFF7DD3FC.toInt()),
                StatLine("STR", "+25", 0xFFFF8A70.toInt()),
                StatLine("VIT", "+5", 0xFFFCA5A5.toInt()),
                StatLine("MEND", "+5", 0xFF86EFAC.toInt())
            )
        ),
        PLASMAFLUX(
            "Plasmaflux ",
            20 * 20,
            iconPath = "plasmaflux",
            titleColor = 0xFFC4B5FD.toInt(),
            stats = listOf(
                StatLine("HP", "+3%/s", 0xFF6EE7A8.toInt()),
                StatLine("MP", "+125%", 0xFF7DD3FC.toInt()),
                StatLine("STR", "+35", 0xFFFF8A70.toInt()),
                StatLine("VIT", "+7.5", 0xFFFCA5A5.toInt()),
                StatLine("MEND", "+7.5", 0xFF86EFAC.toInt())
            )
        ),
        WARNING_FLARE(
            "Warning",
            40 * 40,
            textureId = "22e2bf6c1ec330247927ba63479e5872ac66b06903c86c82b52dac9f1c971458",
            iconPath = "warning",
            titleColor = 0xFFF59E0B.toInt(),
            stats = listOf(
                StatLine("VIT", "+10", 0xFFFCA5A5.toInt()),
                StatLine("TD", "+10", 0xFFE5E7EB.toInt())
            )
        ),
        ALERT_FLARE(
            "Alert",
            40 * 40,
            textureId = "9d2bf9864720d87fd06b84efa80b795c48ed539b16523c3b1f1990b40c003f6b",
            iconPath = "alert",
            titleColor = 0xFFFB7185.toInt(),
            stats = listOf(
                StatLine("MP", "+50%", 0xFF7DD3FC.toInt()),
                StatLine("VIT", "+20", 0xFFFCA5A5.toInt()),
                StatLine("TD", "+20", 0xFFE5E7EB.toInt()),
                StatLine("FERO", "+10", 0xFFF87171.toInt())
            )
        ),
        SOS_FLARE(
            "SOS",
            40 * 40,
            textureId = "c0062cc98ebda72a6a4b89783adcef2815b483a01d73ea87b3df76072a89d13b",
            iconPath = "sos",
            titleColor = 0xFF991B1B.toInt(),
            stats = listOf(
                StatLine("MP", "+125%", 0xFF7DD3FC.toInt()),
                StatLine("VIT", "+30", 0xFFFCA5A5.toInt()),
                StatLine("TD", "+25", 0xFFE5E7EB.toInt()),
                StatLine("FERO", "+10", 0xFFF87171.toInt()),
                StatLine("AS", "+5%", 0xFFFDE68A.toInt())
            )
        ),
        UMBERELLA(
            "Umberella ",
            30 * 30,
            iconPath = "umberella",
            titleColor = 0xFFFDE68A.toInt(),
            stats = listOf(StatLine("TROPHY", "+5", 0xFFFBBF24.toInt()))
        ),
        TOTEM_OF_CORRUPTION(
            "Totem of Corruption",
            30 * 30,
            iconPath = "totem_of_corruption",
            titleColor = 0xFFC084FC.toInt(),
            stats = listOf(StatLine("DEBUFF", "ACTIVE", 0xFFC084FC.toInt()))
        ),
        DWARVEN_LANTERN(
            "Dwarven Lantern ",
            30 * 30,
            iconPath = "dwarven",
            titleColor = 0xFFFBBF24.toInt(),
            stats = listOf(StatLine("MS", "+20", 0xFFFBBF24.toInt()))
        ),
        MITHRIL_LANTERN(
            "Mithril Lantern ",
            30 * 30,
            iconPath = "mithril",
            titleColor = 0xFFA7F3D0.toInt(),
            stats = listOf(
                StatLine("MS", "+40", 0xFFFBBF24.toInt()),
                StatLine("MF", "+10", 0xFFA7F3D0.toInt())
            )
        ),
        TITANIUM_LANTERN(
            "Titanium Lantern ",
            30 * 30,
            iconPath = "titanium",
            titleColor = 0xFFE5E7EB.toInt(),
            stats = listOf(
                StatLine("MS", "+60", 0xFFFBBF24.toInt()),
                StatLine("MF", "+15", 0xFFA7F3D0.toInt()),
                StatLine("HEAT", "+5", 0xFFFCA5A5.toInt())
            )
        ),
        GLACITE_LANTERN(
            "Glacite Lantern ",
            30 * 30,
            iconPath = "glacite",
            titleColor = 0xFF93C5FD.toInt(),
            stats = listOf(
                StatLine("MS", "+80", 0xFFFBBF24.toInt()),
                StatLine("MF", "+20", 0xFFA7F3D0.toInt()),
                StatLine("HEAT", "+10", 0xFFFCA5A5.toInt()),
                StatLine("COLD", "+5", 0xFF93C5FD.toInt())
            )
        ),
        WILL_O_WISP(
            "Will-o'-wisp ",
            30 * 30,
            iconPath = "will_o_wisp",
            titleColor = 0xFF67E8F9.toInt(),
            stats = listOf(
                StatLine("MS", "+100", 0xFFFBBF24.toInt()),
                StatLine("MF", "+25", 0xFFA7F3D0.toInt()),
                StatLine("HEAT", "+20", 0xFFFCA5A5.toInt()),
                StatLine("COLD", "+10", 0xFF93C5FD.toInt()),
                StatLine("GEM", "+2.5", 0xFFC4B5FD.toInt())
            )
        );

        val identifier: Identifier = Identifier.fromNamespaceAndPath("larpclient", "deployables/$iconPath.png")

        fun inRange(distanceSquared: Double): Boolean = distanceSquared <= rangeSquared

        fun label(): String = displayPrefix.trim()

        companion object {
            fun byDisplayName(display: String): DeployableType? {
                return entries.firstOrNull { display.startsWith(it.displayPrefix) }
            }

            fun byTextureId(textureId: String): DeployableType? {
                return entries.firstOrNull { it.textureId.isNotBlank() && it.textureId == textureId }
            }
        }
    }

    data class StatLine(
        val key: String,
        val value: String,
        val color: Int
    ) {
        fun renderText(): String = "$value $key"
    }
}
