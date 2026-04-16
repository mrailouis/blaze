package me.mrai.larpclient.features.impl.dungeons.general.velocitybuffer

import com.mojang.blaze3d.platform.InputConstants
import me.mrai.larpclient.module.KeybindSetting
import me.mrai.larpclient.module.Module
import me.mrai.larpclient.module.ModuleCategory
import me.mrai.larpclient.ui.toast.ToastManager
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientPacketListener
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.common.ClientboundPingPacket
import net.minecraft.network.protocol.game.ClientboundBundlePacket
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket
import java.util.concurrent.ConcurrentLinkedQueue

object VelocityBufferModule : Module(
    name = "Velocity Buffer",
    description = "Buffers incoming velocity packets until you pop or flush them.",
    category = ModuleCategory.DUNGEONS_GENERAL
) {
    private val popKey = KeybindSetting("Pop Key")
    private val packetSet: Set<Class<out Packet<*>>> = setOf(
        ClientboundPingPacket::class.java,
        ClientboundBundlePacket::class.java
    )

    private val queue = ConcurrentLinkedQueue<Packet<*>>()
    private var bufferedCount = 0
    private var popWasPressed = false

    init {
        settings += popKey
    }

    override fun onEnable() {
        flushInternal(showToast = false)
    }

    override fun onDisable() {
        flushInternal(showToast = false)
    }

    override fun onTick() {
        val key = popKey.key
        if (key == InputConstants.UNKNOWN.getValue()) return

        val client = Minecraft.getInstance()
        val pressed = InputConstants.isKeyDown(client.window, key)
        if (pressed && !popWasPressed) {
            popQueue(showToast = true)
        }
        popWasPressed = pressed
    }

    fun onWorldChange() {
        synchronized(queue) {
            queue.clear()
            bufferedCount = 0
            popWasPressed = false
            if (enabled) {
                enabled = false
            }
        }
    }

    fun enableWithToast() {
        if (!enabled) {
            toggle()
        } else {
            ToastManager.show(name, "Enabled")
        }
    }

    fun disableWithToast() {
        if (enabled) {
            toggle()
        } else {
            ToastManager.show(name, "Disabled")
        }
    }

    fun flushWithToast() {
        flushInternal(showToast = true)
    }

    fun popWithToast() {
        popQueue(showToast = true)
    }

    fun onReceivePacketPre(packet: Packet<*>): Boolean {
        val client = Minecraft.getInstance()
        val player = client.player ?: return false
        synchronized(queue) {
            if (!enabled) return false

            if (packet is ClientboundPlayerPositionPacket) {
                client.execute {
                    if (enabled) {
                        enabled = false
                    }
                }
                return false
            }

            if (isMotionPacket(packet, player.id)) {
                queue.add(packet)
                bufferedCount++
                return true
            }

            if (packetSet.contains(packet.javaClass) && queue.isNotEmpty()) {
                queue.add(packet)
                return true
            }
        }

        return false
    }

    private fun popQueue(showToast: Boolean) {
        val listener = Minecraft.getInstance().connection ?: return
        synchronized(queue) {
            if (queue.isEmpty()) {
                if (showToast) {
                    ToastManager.show(name, "No packets buffered")
                }
                return
            }

            while (queue.isNotEmpty()) {
                val packet = queue.poll() ?: break
                receivePacket(packet, listener)
                if (isMotionPacket(packet, Minecraft.getInstance().player?.id ?: -1)) {
                    bufferedCount = (bufferedCount - 1).coerceAtLeast(0)
                    if (!queue.any { isMotionPacket(it, Minecraft.getInstance().player?.id ?: -1) }) {
                        flushInternal(showToast = false)
                        if (enabled) {
                            enabled = false
                        }
                    }
                    break
                }
            }
        }

        if (showToast) {
            ToastManager.show(name, "Popped 1 velocity")
        }
    }

    private fun flushInternal(showToast: Boolean) {
        val listener = Minecraft.getInstance().connection
        synchronized(queue) {
            if (listener != null) {
                while (queue.isNotEmpty()) {
                    receivePacket(queue.poll() ?: break, listener)
                }
            } else {
                queue.clear()
            }
            bufferedCount = 0
        }
        if (showToast) {
            ToastManager.show(name, "Flushed packets")
        }
    }

    private fun receivePacket(packet: Packet<*>, listener: ClientPacketListener) {
        @Suppress("UNCHECKED_CAST")
        (packet as Packet<ClientPacketListener>).handle(listener)
    }

    private fun isMotionPacket(packet: Packet<*>, playerId: Int): Boolean {
        return packet is ClientboundSetEntityMotionPacket && packet.id() == playerId
    }
}
