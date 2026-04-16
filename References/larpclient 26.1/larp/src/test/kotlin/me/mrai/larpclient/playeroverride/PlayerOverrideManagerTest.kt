package me.mrai.larpclient.playeroverride

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class PlayerOverrideManagerTest {
    @AfterTest
    fun tearDown() {
        PlayerOverrideManager.replaceOverrides(emptyList())
        PlayerOverrideManager.clearLocalOverride()
    }

    @Test
    fun `generic rewrite replaces ign with formatted custom component`() {
        PlayerOverrideManager.replaceOverrides(
            listOf(
                PlayerOverrideManager.HeartbeatPlayerOverride(
                    uuid = UUID.randomUUID().toString(),
                    minecraftName = "TargetUser",
                    customName = "&bCreator"
                )
            )
        )

        val rewritten = PlayerOverrideManager.rewriteComponent(Component.literal("Hello TargetUser!"))
        val coloredPart = rewritten.toFlatList().firstOrNull { it.string == "Creator" }

        assertEquals("Hello Creator!", rewritten.string)
        assertNotNull(coloredPart)
        assertEquals(ChatFormatting.AQUA.color, coloredPart.style.color?.value)
    }

    @Test
    fun `direct player rewrite can use fallback name when ign is missing`() {
        val uuid = UUID.randomUUID()
        PlayerOverrideManager.replaceOverrides(
            listOf(
                PlayerOverrideManager.HeartbeatPlayerOverride(
                    uuid = uuid.toString(),
                    customName = "&cWide Boy"
                )
            )
        )

        val rewritten = PlayerOverrideManager.rewritePlayerComponent(
            uuid,
            "TargetUser",
            Component.literal("[VIP] TargetUser")
        )

        assertEquals("[VIP] Wide Boy", rewritten.string)
    }

    @Test
    fun `plain string rewrite replaces exact ign with legacy formatted text`() {
        PlayerOverrideManager.replaceOverrides(
            listOf(
                PlayerOverrideManager.HeartbeatPlayerOverride(
                    uuid = UUID.randomUUID().toString(),
                    minecraftName = "TargetUser",
                    customName = "&dCreator"
                )
            )
        )

        assertEquals("Hello §dCreator!", PlayerOverrideManager.rewriteLegacyText("Hello TargetUser!"))
        assertEquals("TargetUser123", PlayerOverrideManager.rewriteLegacyText("TargetUser123"))
    }

    @Test
    fun `heartbeat response updates stored scale override`() {
        val uuid = UUID.randomUUID()

        PlayerOverrideManager.updateFromHeartbeatResponse(
            """
            {
              "ok": true,
              "playerOverrides": [
                {
                  "uuid": "${uuid}",
                  "minecraftName": "TargetUser",
                  "customName": "&aScaled",
                  "scaleX": 1.5,
                  "scaleY": 0.75,
                  "scaleZ": 2.0
                }
              ]
            }
            """.trimIndent()
        )

        val override = PlayerOverrideManager.getOverride(uuid)

        assertNotNull(override)
        assertEquals(1.5f, override.scaleX)
        assertEquals(0.75f, override.scaleY)
        assertEquals(2.0f, override.scaleZ)
    }

    @Test
    fun `local override takes precedence for the current player`() {
        val uuid = UUID.randomUUID()
        PlayerOverrideManager.replaceOverrides(
            listOf(
                PlayerOverrideManager.HeartbeatPlayerOverride(
                    uuid = uuid.toString(),
                    minecraftName = "TargetUser",
                    customName = "&aShared"
                )
            )
        )

        PlayerOverrideManager.setLocalOverride(
            uuid = uuid,
            minecraftName = "TargetUser",
            customName = "&cLocal",
            scaleX = 1f,
            scaleY = 1f,
            scaleZ = 1f
        )

        val rewritten = PlayerOverrideManager.rewritePlayerComponent(
            uuid,
            "TargetUser",
            Component.literal("TargetUser")
        )

        assertEquals("Local", rewritten.string)
    }

    @Test
    fun `heartbeat response exposes self customization state`() {
        PlayerOverrideManager.updateFromHeartbeatResponse(
            """
            {
              "ok": true,
              "playerOverrides": [],
              "selfCustomization": {
                "eligible": true,
                "authenticated": true,
                "minecraftName": "TargetUser",
                "customName": "&bCreator",
                "scaleX": 1.25,
                "scaleY": 0.8,
                "scaleZ": 1.1,
                "showToOthers": true
              }
            }
            """.trimIndent()
        )

        val state = PlayerOverrideManager.getSelfCustomizationState()

        assertEquals(true, state.checked)
        assertEquals(true, state.eligible)
        assertEquals(true, state.authenticated)
        assertEquals("TargetUser", state.minecraftName)
        assertEquals("&bCreator", state.customName)
        assertEquals(1.25f, state.scaleX)
        assertEquals(0.8f, state.scaleY)
        assertEquals(1.1f, state.scaleZ)
        assertEquals(true, state.showToOthers)
    }
}
