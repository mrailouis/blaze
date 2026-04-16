package me.mrai.larpclient.render.gui

import com.mojang.blaze3d.pipeline.BlendFunction
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.shaders.UniformType
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.resources.Identifier

object LarpRenderPipelines {
    val ROUND_RECT: RenderPipeline = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.GUI_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("larpclient", "pipeline/round_rect"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("larpclient", "core/round_rect"))
            .withVertexShader(Identifier.fromNamespaceAndPath("larpclient", "core/round_rect"))
            .withVertexFormat(DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS)
            .withUniform("u", UniformType.UNIFORM_BUFFER)
            .withBlend(BlendFunction.TRANSLUCENT)
            .build()
    )
}
