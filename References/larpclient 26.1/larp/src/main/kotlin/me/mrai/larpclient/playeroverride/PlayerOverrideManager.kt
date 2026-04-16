package me.mrai.larpclient.playeroverride

import com.google.gson.Gson
import me.mrai.larpclient.util.LarpLog
import net.minecraft.network.chat.Component
import java.util.UUID

object PlayerOverrideManager {
    private val gson = Gson()

    @Volatile
    private var remoteSnapshot = Snapshot.EMPTY

    @Volatile
    private var localOverride = LocalOverrideState.EMPTY

    @Volatile
    private var selfCustomizationState = SelfCustomizationState.UNCHECKED

    data class PlayerOverrideEntry(
        val uuid: UUID,
        val minecraftName: String?,
        val customName: String?,
        val legacyCustomName: String?,
        val scaleX: Float,
        val scaleY: Float,
        val scaleZ: Float,
        val displayComponent: Component?
    )

    data class SelfCustomizationState(
        val checked: Boolean,
        val eligible: Boolean,
        val authenticated: Boolean,
        val minecraftName: String?,
        val customName: String?,
        val scaleX: Float,
        val scaleY: Float,
        val scaleZ: Float,
        val showToOthers: Boolean
    ) {
        companion object {
            val UNCHECKED = SelfCustomizationState(
                checked = false,
                eligible = false,
                authenticated = false,
                minecraftName = null,
                customName = null,
                scaleX = 1f,
                scaleY = 1f,
                scaleZ = 1f,
                showToOthers = false
            )
        }
    }

    private data class HeartbeatResponse(
        val ok: Boolean = false,
        val playerOverrides: Array<HeartbeatPlayerOverride>? = null,
        val selfCustomization: HeartbeatSelfCustomization? = null
    )

    data class HeartbeatPlayerOverride(
        val uuid: String = "",
        val minecraftName: String? = null,
        val customName: String? = null,
        val scaleX: Float = 1f,
        val scaleY: Float = 1f,
        val scaleZ: Float = 1f
    )

    private data class HeartbeatSelfCustomization(
        val eligible: Boolean = false,
        val authenticated: Boolean = false,
        val minecraftName: String? = null,
        val customName: String? = null,
        val scaleX: Float = 1f,
        val scaleY: Float = 1f,
        val scaleZ: Float = 1f,
        val showToOthers: Boolean = false
    )

    private data class Snapshot(
        val byUuid: Map<UUID, PlayerOverrideEntry>,
        val nameRules: List<PlayerOverrideTextRewriter.ReplacementRule>,
        val stringRules: List<PlayerOverrideStringRewriter.ReplacementRule>
    ) {
        companion object {
            val EMPTY = Snapshot(emptyMap(), emptyList(), emptyList())
        }
    }

    private data class LocalOverrideState(
        val entry: PlayerOverrideEntry?,
        val nameRules: List<PlayerOverrideTextRewriter.ReplacementRule>,
        val stringRules: List<PlayerOverrideStringRewriter.ReplacementRule>
    ) {
        companion object {
            val EMPTY = LocalOverrideState(null, emptyList(), emptyList())
        }
    }

    @JvmStatic
    fun updateFromHeartbeatResponse(body: String) {
        if (body.isBlank()) {
            return
        }

        val response = runCatching {
            gson.fromJson(body, HeartbeatResponse::class.java)
        }.getOrElse { throwable ->
            LarpLog.warn("Failed to parse player override heartbeat payload: ${throwable.message ?: throwable.javaClass.simpleName}")
            return
        }

        replaceOverrides(response.playerOverrides?.toList().orEmpty())
        replaceSelfCustomizationState(response.selfCustomization)
    }

    @JvmStatic
    fun rewriteComponent(component: Component): Component {
        val withLocal = PlayerOverrideTextRewriter.rewrite(component, localOverride.nameRules)
        return PlayerOverrideTextRewriter.rewrite(withLocal, remoteSnapshot.nameRules)
    }

    @JvmStatic
    fun rewriteLegacyText(text: String): String {
        val withLocal = PlayerOverrideStringRewriter.rewrite(text, localOverride.stringRules)
        return PlayerOverrideStringRewriter.rewrite(withLocal, remoteSnapshot.stringRules)
    }

    @JvmStatic
    fun rewritePlayerComponent(uuid: UUID, fallbackName: String?, component: Component): Component {
        val localEntry = localOverride.entry?.takeIf { it.uuid == uuid }
        if (localEntry != null) {
            val localRewrite = rewriteDirect(localEntry, fallbackName, component)
            if (localRewrite !== component) {
                return localRewrite
            }
        }

        val remoteEntry = remoteSnapshot.byUuid[uuid]
        if (remoteEntry != null) {
            val remoteRewrite = rewriteDirect(remoteEntry, fallbackName, component)
            if (remoteRewrite !== component) {
                return remoteRewrite
            }
        }

        return rewriteComponent(component)
    }

    @JvmStatic
    fun getOverride(uuid: UUID): PlayerOverrideEntry? {
        val localEntry = localOverride.entry
        return if (localEntry?.uuid == uuid) localEntry else remoteSnapshot.byUuid[uuid]
    }

    @JvmStatic
    fun setLocalOverride(
        uuid: UUID?,
        minecraftName: String?,
        customName: String?,
        scaleX: Float,
        scaleY: Float,
        scaleZ: Float
    ) {
        if (uuid == null) {
            clearLocalOverride()
            return
        }

        val entry = buildEntry(
            uuid = uuid,
            minecraftName = minecraftName,
            customName = customName,
            scaleX = scaleX,
            scaleY = scaleY,
            scaleZ = scaleZ
        )

        if (entry == null || (entry.customName == null && isDefaultScale(entry.scaleX, entry.scaleY, entry.scaleZ))) {
            clearLocalOverride()
            return
        }

        val entries = listOf(entry)
        localOverride = LocalOverrideState(
            entry = entry,
            nameRules = buildTextRules(entries),
            stringRules = buildStringRules(entries)
        )
    }

    @JvmStatic
    fun clearLocalOverride() {
        localOverride = LocalOverrideState.EMPTY
    }

    @JvmStatic
    fun getSelfCustomizationState(): SelfCustomizationState = selfCustomizationState

    internal fun replaceOverrides(overrides: List<HeartbeatPlayerOverride>) {
        val entries = overrides.mapNotNull { wire ->
            val uuid = runCatching { UUID.fromString(wire.uuid.trim()) }.getOrNull() ?: return@mapNotNull null
            buildEntry(
                uuid = uuid,
                minecraftName = wire.minecraftName,
                customName = wire.customName,
                scaleX = wire.scaleX,
                scaleY = wire.scaleY,
                scaleZ = wire.scaleZ
            )
        }

        remoteSnapshot = Snapshot(
            byUuid = entries.associateBy(PlayerOverrideEntry::uuid),
            nameRules = buildTextRules(entries),
            stringRules = buildStringRules(entries)
        )
    }

    private fun replaceSelfCustomizationState(payload: HeartbeatSelfCustomization?) {
        selfCustomizationState = if (payload == null) {
            SelfCustomizationState(
                checked = true,
                eligible = false,
                authenticated = false,
                minecraftName = null,
                customName = null,
                scaleX = 1f,
                scaleY = 1f,
                scaleZ = 1f,
                showToOthers = false
            )
        } else {
            SelfCustomizationState(
                checked = true,
                eligible = payload.eligible,
                authenticated = payload.authenticated,
                minecraftName = payload.minecraftName?.trim()?.takeIf(String::isNotEmpty),
                customName = payload.customName?.trim()?.takeIf(String::isNotEmpty),
                scaleX = payload.scaleX,
                scaleY = payload.scaleY,
                scaleZ = payload.scaleZ,
                showToOthers = payload.showToOthers
            )
        }
    }

    private fun rewriteDirect(entry: PlayerOverrideEntry, fallbackName: String?, component: Component): Component {
        val replacement = entry.displayComponent ?: return component
        val names = replacementNames(entry.minecraftName, fallbackName)
        if (names.isEmpty()) {
            return component
        }

        return PlayerOverrideTextRewriter.rewrite(
            component,
            listOf(PlayerOverrideTextRewriter.ReplacementRule(names, replacement))
        )
    }

    private fun buildEntry(
        uuid: UUID,
        minecraftName: String?,
        customName: String?,
        scaleX: Float,
        scaleY: Float,
        scaleZ: Float
    ): PlayerOverrideEntry? {
        val cleanedName = minecraftName?.trim()?.takeIf(String::isNotEmpty)
        val cleanedCustomName = customName?.trim()?.takeIf(String::isNotEmpty)

        if (cleanedName == null && cleanedCustomName == null && isDefaultScale(scaleX, scaleY, scaleZ)) {
            return null
        }

        return PlayerOverrideEntry(
            uuid = uuid,
            minecraftName = cleanedName,
            customName = cleanedCustomName,
            legacyCustomName = cleanedCustomName?.replace('&', '\u00A7'),
            scaleX = scaleX,
            scaleY = scaleY,
            scaleZ = scaleZ,
            displayComponent = cleanedCustomName?.let(LegacyFormattedNameParser::parse)
        )
    }

    private fun buildTextRules(entries: List<PlayerOverrideEntry>): List<PlayerOverrideTextRewriter.ReplacementRule> {
        return entries
            .mapNotNull { entry ->
                val replacement = entry.displayComponent ?: return@mapNotNull null
                val names = replacementNames(entry.minecraftName)
                if (names.isEmpty()) {
                    null
                } else {
                    PlayerOverrideTextRewriter.ReplacementRule(names, replacement)
                }
            }
            .sortedByDescending(PlayerOverrideTextRewriter.ReplacementRule::longestNameLength)
    }

    private fun buildStringRules(entries: List<PlayerOverrideEntry>): List<PlayerOverrideStringRewriter.ReplacementRule> {
        return entries
            .mapNotNull { entry ->
                val replacement = entry.legacyCustomName ?: return@mapNotNull null
                val names = replacementNames(entry.minecraftName)
                if (names.isEmpty()) {
                    null
                } else {
                    PlayerOverrideStringRewriter.ReplacementRule(names, replacement)
                }
            }
            .sortedByDescending(PlayerOverrideStringRewriter.ReplacementRule::longestNameLength)
    }

    private fun replacementNames(primaryName: String?, fallbackName: String? = null): List<String> {
        return listOfNotNull(primaryName, fallbackName)
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinctBy { it.lowercase() }
            .sortedByDescending { it.length }
    }

    private fun isDefaultScale(scaleX: Float, scaleY: Float, scaleZ: Float): Boolean {
        return scaleX == 1.0f && scaleY == 1.0f && scaleZ == 1.0f
    }
}
