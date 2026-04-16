package me.mrai.larpclient.auth

import com.google.gson.JsonParser
import me.mrai.larpclient.presence.HeartbeatClientType
import me.mrai.larpclient.presence.HeartbeatManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AuthPayloadTest {
    @Test
    fun `auth payload uses expected request fields`() {
        val payload = JsonParser.parseString(
            AuthManager.buildAuthRequestBody(
                uuid = "uuid-123",
                licenseKey = "license",
                minecraftName = "mrai",
                modVersion = "1.0.0"
            )
        ).asJsonObject

        assertEquals("uuid-123", payload["uuid"].asString)
        assertEquals("license", payload["key"].asString)
        assertEquals("mrai", payload["minecraftName"].asString)
        assertEquals("1.0.0", payload["modVersion"].asString)
    }

    @Test
    fun `heartbeat payload keeps uuid field lowercase`() {
        val payload = JsonParser.parseString(
            HeartbeatManager.buildHeartbeatRequestBody(
                uuid = "uuid-123",
                licenseKey = "license",
                minecraftName = "mrai",
                modVersion = "1.0.0",
                clientType = HeartbeatClientType.ADDON
            )
        ).asJsonObject

        assertEquals("uuid-123", payload["uuid"].asString)
        assertFalse(payload.has("UUID"))
        assertEquals("mrai", payload["minecraftName"].asString)
        assertTrue(payload.has("modVersion"))
        assertEquals("larp-addon", payload["clientType"].asString)
    }
}
