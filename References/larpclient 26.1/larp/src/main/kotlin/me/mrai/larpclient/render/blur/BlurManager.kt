package me.mrai.larpclient.render.blur

import com.mojang.blaze3d.pipeline.TextureTarget
import me.mrai.larpclient.util.LarpLog
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import org.lwjgl.opengl.GL11

object BlurManager {
    private const val DOWNSCALE = 8

    private var smallFramebuffer: TextureTarget? = null
    private var lastMainWidth = -1
    private var lastMainHeight = -1

    private fun ensureFramebuffer(mainWidth: Int, mainHeight: Int) {
        if (mainWidth <= 0 || mainHeight <= 0) return

        if (
            smallFramebuffer != null &&
            mainWidth == lastMainWidth &&
            mainHeight == lastMainHeight
        ) {
            return
        }

        smallFramebuffer = null

        val guiScaledWidth = (mainWidth / DOWNSCALE).coerceAtLeast(1)
        val guiScaledHeight = (mainHeight / DOWNSCALE).coerceAtLeast(1)

        val created = createFramebufferReflective(
            width = guiScaledWidth,
            height = guiScaledHeight
        ) ?: return

        smallFramebuffer = created
        setLinearFilterReflective(created)

        lastMainWidth = mainWidth
        lastMainHeight = mainHeight
    }

    fun captureScreen() {
        val client = Minecraft.getInstance()
        val mainFramebuffer = readMainFramebuffer(client) ?: return

        val mainWidth = readInt(mainFramebuffer, "textureWidth", "viewportWidth", "texWidth", "width") ?: return
        val mainHeight = readInt(mainFramebuffer, "textureHeight", "viewportHeight", "texHeight", "height") ?: return

        ensureFramebuffer(mainWidth, mainHeight)

        val small = smallFramebuffer ?: return

        val mainView = callNoArg(mainFramebuffer, "getColorAttachmentView")
            ?: callNoArg(mainFramebuffer, "getColorAttachment")
            ?: return

        callOneArg(small, "drawBlit", mainView)
        setLinearFilterReflective(small)
    }

    fun drawBlurredRegion(
        context: GuiGraphicsExtractor,
        x: Int,
        y: Int,
        width: Int,
        height: Int
    ) {
        if (width <= 0 || height <= 0) return

        val client = Minecraft.getInstance()
        val mainFramebuffer = readMainFramebuffer(client) ?: return
        val small = smallFramebuffer ?: return

        val smallView = callNoArg(small, "getColorAttachmentView")
            ?: callNoArg(small, "getColorAttachment")
            ?: return

        context.enableScissor(x, y, x + width, y + height)
        callOneArg(mainFramebuffer, "drawBlit", smallView)
        context.disableScissor()
    }

    fun debugReady(): Boolean = smallFramebuffer != null

    private fun readMainFramebuffer(client: Minecraft): Any? {
        return try {
            client.mainRenderTarget
        } catch (throwable: Throwable) {
            LarpLog.debug("BlurManager failed to read mainRenderTarget directly: ${throwable.message ?: throwable.javaClass.simpleName}")
            try {
                val field = client.javaClass.methods.firstOrNull {
                    it.name == "getMainRenderTarget" && it.parameterCount == 0
                }
                field?.invoke(client)
            } catch (fallbackThrowable: Throwable) {
                LarpLog.warn("BlurManager could not resolve the main render target: ${fallbackThrowable.message ?: fallbackThrowable.javaClass.simpleName}")
                null
            }
        }
    }

    private fun createFramebufferReflective(
        width: Int,
        height: Int
    ): TextureTarget? {
        return try {
            val clazz = TextureTarget::class.java

            clazz.constructors.firstOrNull { ctor ->
                val p = ctor.parameterTypes
                p.size == 4 &&
                        p[0] == String::class.java &&
                        p[1] == Int::class.javaPrimitiveType &&
                        p[2] == Int::class.javaPrimitiveType &&
                        p[3] == Boolean::class.javaPrimitiveType
            }?.newInstance("larpclient_blur_small", width, height, false) as? TextureTarget
                ?: clazz.constructors.firstOrNull { ctor ->
                    val p = ctor.parameterTypes
                    p.size == 3 &&
                            p[0] == Int::class.javaPrimitiveType &&
                            p[1] == Int::class.javaPrimitiveType &&
                            p[2] == Boolean::class.javaPrimitiveType
                }?.newInstance(width, height, false) as? TextureTarget
        } catch (throwable: Throwable) {
            LarpLog.warn("BlurManager could not create the downscaled framebuffer: ${throwable.message ?: throwable.javaClass.simpleName}")
            null
        }
    }

    private fun setLinearFilterReflective(framebuffer: Any) {
        try {
            framebuffer.javaClass.methods.firstOrNull {
                it.name == "setFilter" &&
                        it.parameterCount == 1 &&
                        it.parameterTypes[0] == Int::class.javaPrimitiveType
            }?.let {
                it.isAccessible = true
                it.invoke(framebuffer, GL11.GL_LINEAR)
                return
            }

            framebuffer.javaClass.methods.firstOrNull {
                it.name == "setTexFilter" &&
                        it.parameterCount == 1 &&
                        it.parameterTypes[0] == Int::class.javaPrimitiveType
            }?.let {
                it.isAccessible = true
                it.invoke(framebuffer, GL11.GL_LINEAR)
                return
            }

            framebuffer.javaClass.methods.firstOrNull {
                it.name == "setFilter" && it.parameterCount == 1 && it.parameterTypes[0].isEnum
            }?.let { method ->
                val enumType = method.parameterTypes[0]
                val linear = enumType.enumConstants.firstOrNull {
                    it.toString().equals("LINEAR", ignoreCase = true)
                } ?: enumType.enumConstants.firstOrNull() ?: return
                method.isAccessible = true
                method.invoke(framebuffer, linear)
                return
            }
        } catch (throwable: Throwable) {
            LarpLog.warn("BlurManager could not set linear filtering on the framebuffer: ${throwable.message ?: throwable.javaClass.simpleName}")
        }
    }

    private fun callNoArg(instance: Any, methodName: String): Any? {
        return try {
            val method = instance.javaClass.methods.firstOrNull {
                it.name == methodName && it.parameterCount == 0
            } ?: return null
            method.isAccessible = true
            method.invoke(instance)
        } catch (throwable: Throwable) {
            LarpLog.warn("BlurManager failed to call $methodName reflectively: ${throwable.message ?: throwable.javaClass.simpleName}")
            null
        }
    }

    private fun callOneArg(instance: Any, methodName: String, arg: Any): Any? {
        return try {
            val method = instance.javaClass.methods.firstOrNull {
                it.name == methodName &&
                        it.parameterCount == 1 &&
                        it.parameterTypes[0].isAssignableFrom(arg.javaClass)
            } ?: instance.javaClass.methods.firstOrNull {
                it.name == methodName && it.parameterCount == 1
            } ?: return null

            method.isAccessible = true
            method.invoke(instance, arg)
        } catch (throwable: Throwable) {
            LarpLog.warn("BlurManager failed to call $methodName reflectively: ${throwable.message ?: throwable.javaClass.simpleName}")
            null
        }
    }

    private fun readInt(instance: Any, vararg fieldNames: String): Int? {
        for (name in fieldNames) {
            try {
                val field = instance.javaClass.getDeclaredField(name)
                field.isAccessible = true
                val value = field.get(instance)
                if (value is Int) return value
            } catch (throwable: Throwable) {
                LarpLog.debug("BlurManager could not read framebuffer field '$name': ${throwable.message ?: throwable.javaClass.simpleName}")
            }
        }

        for (name in fieldNames) {
            try {
                val getterName = "get" + name.replaceFirstChar { it.uppercase() }
                val method = instance.javaClass.methods.firstOrNull {
                    it.name == getterName && it.parameterCount == 0
                } ?: continue

                val value = method.invoke(instance)
                if (value is Int) return value
            } catch (throwable: Throwable) {
                LarpLog.debug("BlurManager could not read framebuffer getter '$name': ${throwable.message ?: throwable.javaClass.simpleName}")
            }
        }

        return null
    }
}
