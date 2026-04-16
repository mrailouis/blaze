package me.mrai.larpclient.features.impl.misc.other.headsscale

import org.joml.Matrix3x2fStack
import net.minecraft.world.item.ItemStack

object HeadScalingFeature {
    @JvmStatic
    fun applyGuiScale(stack: ItemStack, pose: Matrix3x2fStack, x: Int, y: Int) {
        if (!HeadsScaleModule.enabled || !HeadItemUtil.isHeadItem(stack)) return

        val scale = HeadsScaleModule.scaleMultiplier()
        pose.pushMatrix()
        pose.translate(x + 8f, y + 8f)
        pose.scale(scale, scale)
        pose.translate(-(x + 8f), -(y + 8f))
    }

    @JvmStatic
    fun popGuiScale(stack: ItemStack, pose: Matrix3x2fStack) {
        if (!HeadsScaleModule.enabled || !HeadItemUtil.isHeadItem(stack)) return
        pose.popMatrix()
    }
}
