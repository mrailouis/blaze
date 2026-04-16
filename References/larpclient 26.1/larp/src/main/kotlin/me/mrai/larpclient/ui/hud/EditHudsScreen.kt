package me.mrai.larpclient.ui.hud

import com.mojang.blaze3d.platform.InputConstants
import me.mrai.larpclient.features.impl.dungeons.general.truesplits.TrueSplitsRenderer
import me.mrai.larpclient.features.impl.misc.ui.cleanscoreboard.CleanScoreboardModule
import me.mrai.larpclient.features.impl.misc.ui.cleanscoreboard.CleanScoreboardRenderer
import me.mrai.larpclient.render.blur.BlurManager
import me.mrai.larpclient.util.LarpBranding
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW

class EditHudsScreen : Screen(Component.literal("Edit HUDs")) {
    private enum class DragTarget {
        MODULE_LIST,
        CLEAN_SCOREBOARD,
        KUUDRA_GO_TO,
        BUILD_PROGRESS,
        DEPLOYABLE_DISPLAY,
        GOLEM_TIMER,
        PERFORMANCE_HUD,
        KUUDRA_DIRECTION,
        KUUDRA_PRIORITY,
        KUUDRA_SPAWNED_CRATES,
        TRUE_SPLITS,
        TRUE_SPLITS_BREAKDOWN,
        ARCHER_UTILS_TITLE,
        LAST_BREATH_UTILS_TITLE,
        MAXOR_HP_HUD
    }

    private data class AABB(
        val x: Float,
        val y: Float,
        val w: Float,
        val h: Float
    ) {
        fun contains(mx: Float, my: Float): Boolean {
            return mx >= x && mx <= x + w && my >= y && my <= y + h
        }
    }

    private data class HoverInfo(
        val label: String,
        val detail: String
    )

    private var dragging: DragTarget? = null
    private var dragOffsetX = 0f
    private var dragOffsetY = 0f
    private var openProgress = 0f

    override fun init() {
        super.init()
        HudPositionManager.load()
        BlurManager.captureScreen()
        openProgress = 0f
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        super.extractRenderState(graphics, mouseX, mouseY, delta)
        openProgress += (1f - openProgress) * 0.08f

        renderBackdrop(graphics)
        val anim = easeOutCubic(openProgress)
        val pose = graphics.pose()
        pose.pushMatrix()
        pose.translate(0f, (1f - anim) * 24f)
        pose.scale(0.90f + 0.10f * anim, 0.90f + 0.10f * anim)
        renderPreviews(graphics)
        drawStatus(graphics, hoveredInfo(mouseX.toFloat(), mouseY.toFloat()))
        pose.popMatrix()
    }

    private fun renderBackdrop(graphics: GuiGraphicsExtractor) {
        if (BlurManager.debugReady()) {
            BlurManager.drawBlurredRegion(graphics, 0, 0, width, height)
        } else {
            graphics.fill(0, 0, width, height, (((0x18 * easeOutCubic(openProgress)).toInt().coerceIn(0, 255)) shl 24))
        }
    }

    private fun renderPreviews(graphics: GuiGraphicsExtractor) {
        ModuleListRenderer.renderPreview(graphics)
        CleanScoreboardRenderer.renderPreview(graphics)
        TrueSplitsRenderer.renderMainPreview(graphics)
        TrueSplitsRenderer.renderBreakdownPreview(graphics)
        KuudraGoToHudRenderer.renderPreview(graphics)
        BuildProgressHudRenderer.renderPreview(graphics)
        DeployableDisplayHudRenderer.renderPreview(graphics)
        GolemTimerHudRenderer.renderPreview(graphics)
        PerformanceHudRenderer.renderPreview(graphics)
        KuudraDirectionHudRenderer.renderPreview(graphics)
        KuudraPriorityHudRenderer.renderPreview(graphics)
        KuudraSpawnedCratesHudRenderer.renderPreview(graphics)
        ArcherUtilsHudRenderer.renderPreview(graphics)
        LastBreathUtilsHudRenderer.renderPreview(graphics)
        MaxorHpHudRenderer.renderPreview(graphics)
    }

    private fun drawStatus(graphics: GuiGraphicsExtractor, hoveredInfo: HoverInfo?) {
        val text = hoveredInfo?.let { "${it.label}  •  ${it.detail}" }
            ?: "Drag to move  •  Scroll to resize  •  Scoreboard: Shift+Scroll width, Ctrl+Scroll height"
        val component = LarpBranding.prefixed(text)
        val font = minecraft.font
        val barHeight = font.lineHeight + 10
        val barY = height - barHeight - 10

        graphics.fill(10, barY, width - 10, barY + barHeight, 0x99070B14.toInt())
        graphics.text(font, component, 16, barY + 5, 0xFFFFFFFF.toInt(), false)
    }

    private fun easeOutCubic(t: Float): Float {
        val c = t.coerceIn(0f, 1f)
        val inv = 1f - c
        return 1f - inv * inv * inv
    }

    private fun getModuleListBounds(): AABB {
        val layout = ModuleListRenderer.measurePreviewLayout(Minecraft.getInstance())
        return AABB(
            x = layout.renderX.toFloat(),
            y = layout.renderY.toFloat(),
            w = layout.guiScaledWidth.toFloat(),
            h = layout.guiScaledHeight.toFloat()
        )
    }

    private fun getScoreboardBounds(): AABB? {
        val client = Minecraft.getInstance()
        val layout = CleanScoreboardRenderer.measurePreviewLayout(client) ?: return null
        return AABB(
            x = layout.renderX.toFloat(),
            y = layout.renderY.toFloat(),
            w = layout.guiScaledWidth.toFloat(),
            h = layout.guiScaledHeight.toFloat()
        )
    }

    private fun getTrueSplitsBounds(): AABB {
        val layout = TrueSplitsRenderer.measureMainLayout(preview = true)
        return AABB(layout.x, layout.y, layout.width, layout.height)
    }

    private fun getTrueSplitsBreakdownBounds(): AABB {
        val layout = TrueSplitsRenderer.measureBreakdownLayout(preview = true)
        return AABB(layout.x, layout.y, layout.width, layout.height)
    }

    private fun getArcherUtilsBounds(): AABB {
        return AABB(
            x = HudPositionManager.config.archerUtilsTitle.x,
            y = HudPositionManager.config.archerUtilsTitle.y,
            w = ArcherUtilsHudRenderer.getWidth(),
            h = ArcherUtilsHudRenderer.getHeight()
        )
    }

    private fun getKuudraPriorityBounds(): AABB {
        return AABB(
            x = HudPositionManager.config.kuudraPriority.x,
            y = HudPositionManager.config.kuudraPriority.y,
            w = KuudraPriorityHudRenderer.getWidth(preview = true),
            h = KuudraPriorityHudRenderer.getHeight()
        )
    }

    private fun getKuudraSpawnedCratesBounds(): AABB {
        return AABB(
            x = HudPositionManager.config.kuudraSpawnedCrates.x,
            y = HudPositionManager.config.kuudraSpawnedCrates.y,
            w = KuudraSpawnedCratesHudRenderer.getWidth(preview = true),
            h = KuudraSpawnedCratesHudRenderer.getHeight(preview = true)
        )
    }

    private fun getKuudraGoToBounds(): AABB {
        return AABB(
            x = HudPositionManager.config.kuudraGoTo.x,
            y = HudPositionManager.config.kuudraGoTo.y,
            w = KuudraGoToHudRenderer.getWidth(preview = true),
            h = KuudraGoToHudRenderer.getHeight()
        )
    }

    private fun getBuildProgressBounds(): AABB {
        return AABB(
            x = HudPositionManager.config.buildProgressHud.x,
            y = HudPositionManager.config.buildProgressHud.y,
            w = BuildProgressHudRenderer.getWidth(),
            h = BuildProgressHudRenderer.getHeight()
        )
    }

    private fun getDeployableDisplayBounds(): AABB {
        return AABB(
            x = HudPositionManager.config.deployableDisplay.x,
            y = HudPositionManager.config.deployableDisplay.y,
            w = DeployableDisplayHudRenderer.getWidth(preview = true),
            h = DeployableDisplayHudRenderer.getHeight(preview = true)
        )
    }

    private fun getKuudraDirectionBounds(): AABB {
        return AABB(
            x = HudPositionManager.config.kuudraDirectionHud.x,
            y = HudPositionManager.config.kuudraDirectionHud.y,
            w = KuudraDirectionHudRenderer.getWidth(),
            h = KuudraDirectionHudRenderer.getHeight()
        )
    }

    private fun getPerformanceHudBounds(): AABB {
        return AABB(
            x = HudPositionManager.config.performanceHud.x,
            y = HudPositionManager.config.performanceHud.y,
            w = PerformanceHudRenderer.getWidth(preview = true),
            h = PerformanceHudRenderer.getHeight(preview = true)
        )
    }

    private fun getGolemTimerBounds(): AABB {
        return AABB(
            x = HudPositionManager.config.golemTimerHud.x,
            y = HudPositionManager.config.golemTimerHud.y,
            w = GolemTimerHudRenderer.getWidth(),
            h = GolemTimerHudRenderer.getHeight()
        )
    }

    private fun getLastBreathUtilsBounds(): AABB {
        return AABB(
            x = HudPositionManager.config.lastBreathUtilsTitle.x,
            y = HudPositionManager.config.lastBreathUtilsTitle.y,
            w = LastBreathUtilsHudRenderer.getWidth(),
            h = LastBreathUtilsHudRenderer.getHeight()
        )
    }

    private fun getMaxorHpBounds(): AABB {
        return AABB(
            x = HudPositionManager.config.maxorHpHud.x,
            y = HudPositionManager.config.maxorHpHud.y,
            w = MaxorHpHudRenderer.getWidth(),
            h = MaxorHpHudRenderer.getHeight()
        )
    }

    private fun hoveredInfo(mx: Float, my: Float): HoverInfo? {
        return when {
            getTrueSplitsBounds().contains(mx, my) -> HoverInfo("True Splits", "Drag to move  •  Scroll to scale")
            getTrueSplitsBreakdownBounds().contains(mx, my) -> HoverInfo("True Splits Breakdown", "Drag to move  •  Scroll to scale")
            getModuleListBounds().contains(mx, my) -> HoverInfo("Module List", "Drag to move  •  Scroll to scale")
            getScoreboardBounds()?.contains(mx, my) == true -> HoverInfo(
                "Clean Scoreboard",
                "Drag to move  •  Scroll scale  •  Shift width  •  Ctrl height"
            )
            getKuudraGoToBounds().contains(mx, my) -> HoverInfo("Kuudra Go To", "Drag to move  •  Scroll to scale")
            getBuildProgressBounds().contains(mx, my) -> HoverInfo("Build Progress", "Drag to move  •  Scroll to scale")
            getDeployableDisplayBounds().contains(mx, my) -> HoverInfo("Deployable Display", "Drag to move  •  Scroll to scale")
            getGolemTimerBounds().contains(mx, my) -> HoverInfo("Golem Timer", "Drag to move  •  Scroll to scale")
            getPerformanceHudBounds().contains(mx, my) -> HoverInfo("Performance HUD", "Drag to move  •  Scroll to scale")
            getKuudraDirectionBounds().contains(mx, my) -> HoverInfo("Kuudra Direction", "Drag to move  •  Scroll to scale")
            getKuudraPriorityBounds().contains(mx, my) -> HoverInfo("Kuudra Priority", "Drag to move  •  Scroll to scale")
            getKuudraSpawnedCratesBounds().contains(mx, my) -> HoverInfo("Spawned Crates", "Drag to move  •  Scroll to scale")
            getArcherUtilsBounds().contains(mx, my) -> HoverInfo("Archer Utils", "Drag to move  •  Scroll to scale")
            getLastBreathUtilsBounds().contains(mx, my) -> HoverInfo("LB Utils", "Drag to move  •  Scroll to scale")
            getMaxorHpBounds().contains(mx, my) -> HoverInfo("Maxor HP", "Drag to move  •  Scroll to scale")
            else -> null
        }
    }

    private fun isShiftHeld(): Boolean {
        val window = Minecraft.getInstance().window
        return InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_SHIFT) ||
            InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_SHIFT)
    }

    private fun isControlHeld(): Boolean {
        val window = Minecraft.getInstance().window
        return InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_CONTROL) ||
            InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_CONTROL)
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        val mx = event.x().toFloat()
        val my = event.y().toFloat()

        val trueSplitsBounds = getTrueSplitsBounds()
        if (trueSplitsBounds.contains(mx, my)) {
            dragging = DragTarget.TRUE_SPLITS
            dragOffsetX = mx - trueSplitsBounds.x
            dragOffsetY = my - trueSplitsBounds.y
            return true
        }

        val breakdownBounds = getTrueSplitsBreakdownBounds()
        if (breakdownBounds.contains(mx, my)) {
            dragging = DragTarget.TRUE_SPLITS_BREAKDOWN
            dragOffsetX = mx - breakdownBounds.x
            dragOffsetY = my - breakdownBounds.y
            return true
        }

        val moduleListBounds = getModuleListBounds()
        if (moduleListBounds.contains(mx, my)) {
            dragging = DragTarget.MODULE_LIST
            dragOffsetX = moduleListBounds.x + moduleListBounds.w - mx
            dragOffsetY = my - moduleListBounds.y
            return true
        }

        val scoreboardBounds = getScoreboardBounds()
        if (scoreboardBounds != null && scoreboardBounds.contains(mx, my)) {
            dragging = DragTarget.CLEAN_SCOREBOARD
            dragOffsetX = mx - scoreboardBounds.x
            dragOffsetY = my - scoreboardBounds.y
            return true
        }

        val kuudraGoToBounds = getKuudraGoToBounds()
        if (kuudraGoToBounds.contains(mx, my)) {
            dragging = DragTarget.KUUDRA_GO_TO
            dragOffsetX = mx - kuudraGoToBounds.x
            dragOffsetY = my - kuudraGoToBounds.y
            return true
        }

        val buildProgressBounds = getBuildProgressBounds()
        if (buildProgressBounds.contains(mx, my)) {
            dragging = DragTarget.BUILD_PROGRESS
            dragOffsetX = mx - buildProgressBounds.x
            dragOffsetY = my - buildProgressBounds.y
            return true
        }

        val deployableDisplayBounds = getDeployableDisplayBounds()
        if (deployableDisplayBounds.contains(mx, my)) {
            dragging = DragTarget.DEPLOYABLE_DISPLAY
            dragOffsetX = mx - deployableDisplayBounds.x
            dragOffsetY = my - deployableDisplayBounds.y
            return true
        }

        val golemTimerBounds = getGolemTimerBounds()
        if (golemTimerBounds.contains(mx, my)) {
            dragging = DragTarget.GOLEM_TIMER
            dragOffsetX = mx - golemTimerBounds.x
            dragOffsetY = my - golemTimerBounds.y
            return true
        }

        val performanceHudBounds = getPerformanceHudBounds()
        if (performanceHudBounds.contains(mx, my)) {
            dragging = DragTarget.PERFORMANCE_HUD
            dragOffsetX = mx - performanceHudBounds.x
            dragOffsetY = my - performanceHudBounds.y
            return true
        }

        val kuudraDirectionBounds = getKuudraDirectionBounds()
        if (kuudraDirectionBounds.contains(mx, my)) {
            dragging = DragTarget.KUUDRA_DIRECTION
            dragOffsetX = mx - kuudraDirectionBounds.x
            dragOffsetY = my - kuudraDirectionBounds.y
            return true
        }

        val kuudraBounds = getKuudraPriorityBounds()
        if (kuudraBounds.contains(mx, my)) {
            dragging = DragTarget.KUUDRA_PRIORITY
            dragOffsetX = mx - kuudraBounds.x
            dragOffsetY = my - kuudraBounds.y
            return true
        }

        val kuudraSpawnedCratesBounds = getKuudraSpawnedCratesBounds()
        if (kuudraSpawnedCratesBounds.contains(mx, my)) {
            dragging = DragTarget.KUUDRA_SPAWNED_CRATES
            dragOffsetX = mx - kuudraSpawnedCratesBounds.x
            dragOffsetY = my - kuudraSpawnedCratesBounds.y
            return true
        }

        val archerBounds = getArcherUtilsBounds()
        if (archerBounds.contains(mx, my)) {
            dragging = DragTarget.ARCHER_UTILS_TITLE
            dragOffsetX = mx - archerBounds.x
            dragOffsetY = my - archerBounds.y
            return true
        }

        val lbBounds = getLastBreathUtilsBounds()
        if (lbBounds.contains(mx, my)) {
            dragging = DragTarget.LAST_BREATH_UTILS_TITLE
            dragOffsetX = mx - lbBounds.x
            dragOffsetY = my - lbBounds.y
            return true
        }

        val maxorBounds = getMaxorHpBounds()
        if (maxorBounds.contains(mx, my)) {
            dragging = DragTarget.MAXOR_HP_HUD
            dragOffsetX = mx - maxorBounds.x
            dragOffsetY = my - maxorBounds.y
            return true
        }

        return super.mouseClicked(event, doubleClick)
    }

    override fun mouseDragged(event: MouseButtonEvent, dx: Double, dy: Double): Boolean {
        val mx = event.x().toFloat()
        val my = event.y().toFloat()
        val client = Minecraft.getInstance()
        val screenWidth = client.window.guiScaledWidth.toFloat()
        val screenHeight = client.window.guiScaledHeight.toFloat()

        when (dragging) {
            DragTarget.TRUE_SPLITS -> {
                val layout = TrueSplitsRenderer.measureMainLayout(preview = true)
                HudPositionManager.config.trueSplits.x =
                    (mx - dragOffsetX).coerceIn(0f, (screenWidth - layout.width).coerceAtLeast(0f))
                HudPositionManager.config.trueSplits.y =
                    (my - dragOffsetY).coerceIn(0f, (screenHeight - layout.height).coerceAtLeast(0f))
                return true
            }

            DragTarget.TRUE_SPLITS_BREAKDOWN -> {
                val layout = TrueSplitsRenderer.measureBreakdownLayout(preview = true)
                HudPositionManager.config.trueSplitsBreakdown.x =
                    (mx - dragOffsetX).coerceIn(0f, (screenWidth - layout.width).coerceAtLeast(0f))
                HudPositionManager.config.trueSplitsBreakdown.y =
                    (my - dragOffsetY).coerceIn(0f, (screenHeight - layout.height).coerceAtLeast(0f))
                return true
            }

            DragTarget.MODULE_LIST -> {
                val layout = ModuleListRenderer.measurePreviewLayout(client)
                val width = layout.guiScaledWidth.toFloat()
                HudPositionManager.config.moduleList.x =
                    (mx + dragOffsetX).coerceIn(width, screenWidth)
                HudPositionManager.config.moduleList.y =
                    (my - dragOffsetY).coerceIn(0f, (screenHeight - layout.guiScaledHeight).coerceAtLeast(0f))
                return true
            }

            DragTarget.CLEAN_SCOREBOARD -> {
                val layout = CleanScoreboardRenderer.measurePreviewLayout(client)
                if (layout != null) {
                    val newLeft = mx - dragOffsetX
                    val newTop = my - dragOffsetY
                    HudPositionManager.config.scoreboard.x =
                        (newLeft + layout.guiScaledWidth).coerceIn(layout.guiScaledWidth.toFloat(), screenWidth)
                    HudPositionManager.config.scoreboard.y =
                        newTop.coerceIn(0f, (screenHeight - layout.guiScaledHeight).coerceAtLeast(0f))
                }
                return true
            }

            DragTarget.KUUDRA_GO_TO -> {
                val w = KuudraGoToHudRenderer.getWidth(preview = true)
                val h = KuudraGoToHudRenderer.getHeight()
                HudPositionManager.config.kuudraGoTo.x =
                    (mx - dragOffsetX).coerceIn(0f, (screenWidth - w).coerceAtLeast(0f))
                HudPositionManager.config.kuudraGoTo.y =
                    (my - dragOffsetY).coerceIn(0f, (screenHeight - h).coerceAtLeast(0f))
                return true
            }

            DragTarget.BUILD_PROGRESS -> {
                val w = BuildProgressHudRenderer.getWidth()
                val h = BuildProgressHudRenderer.getHeight()
                HudPositionManager.config.buildProgressHud.x =
                    (mx - dragOffsetX).coerceIn(0f, (screenWidth - w).coerceAtLeast(0f))
                HudPositionManager.config.buildProgressHud.y =
                    (my - dragOffsetY).coerceIn(0f, (screenHeight - h).coerceAtLeast(0f))
                return true
            }

            DragTarget.DEPLOYABLE_DISPLAY -> {
                val w = DeployableDisplayHudRenderer.getWidth(preview = true)
                val h = DeployableDisplayHudRenderer.getHeight(preview = true)
                HudPositionManager.config.deployableDisplay.x =
                    (mx - dragOffsetX).coerceIn(0f, (screenWidth - w).coerceAtLeast(0f))
                HudPositionManager.config.deployableDisplay.y =
                    (my - dragOffsetY).coerceIn(0f, (screenHeight - h).coerceAtLeast(0f))
                return true
            }

            DragTarget.GOLEM_TIMER -> {
                val w = GolemTimerHudRenderer.getWidth()
                val h = GolemTimerHudRenderer.getHeight()
                HudPositionManager.config.golemTimerHud.x =
                    (mx - dragOffsetX).coerceIn(0f, (screenWidth - w).coerceAtLeast(0f))
                HudPositionManager.config.golemTimerHud.y =
                    (my - dragOffsetY).coerceIn(0f, (screenHeight - h).coerceAtLeast(0f))
                return true
            }

            DragTarget.PERFORMANCE_HUD -> {
                val w = PerformanceHudRenderer.getWidth(preview = true)
                val h = PerformanceHudRenderer.getHeight(preview = true)
                HudPositionManager.config.performanceHud.x =
                    (mx - dragOffsetX).coerceIn(0f, (screenWidth - w).coerceAtLeast(0f))
                HudPositionManager.config.performanceHud.y =
                    (my - dragOffsetY).coerceIn(0f, (screenHeight - h).coerceAtLeast(0f))
                return true
            }

            DragTarget.KUUDRA_DIRECTION -> {
                val w = KuudraDirectionHudRenderer.getWidth()
                val h = KuudraDirectionHudRenderer.getHeight()
                HudPositionManager.config.kuudraDirectionHud.x =
                    (mx - dragOffsetX).coerceIn(0f, (screenWidth - w).coerceAtLeast(0f))
                HudPositionManager.config.kuudraDirectionHud.y =
                    (my - dragOffsetY).coerceIn(0f, (screenHeight - h).coerceAtLeast(0f))
                return true
            }

            DragTarget.KUUDRA_PRIORITY -> {
                val w = KuudraPriorityHudRenderer.getWidth(preview = true)
                val h = KuudraPriorityHudRenderer.getHeight()
                HudPositionManager.config.kuudraPriority.x =
                    (mx - dragOffsetX).coerceIn(0f, (screenWidth - w).coerceAtLeast(0f))
                HudPositionManager.config.kuudraPriority.y =
                    (my - dragOffsetY).coerceIn(0f, (screenHeight - h).coerceAtLeast(0f))
                return true
            }

            DragTarget.KUUDRA_SPAWNED_CRATES -> {
                val w = KuudraSpawnedCratesHudRenderer.getWidth(preview = true)
                val h = KuudraSpawnedCratesHudRenderer.getHeight(preview = true)
                HudPositionManager.config.kuudraSpawnedCrates.x =
                    (mx - dragOffsetX).coerceIn(0f, (screenWidth - w).coerceAtLeast(0f))
                HudPositionManager.config.kuudraSpawnedCrates.y =
                    (my - dragOffsetY).coerceIn(0f, (screenHeight - h).coerceAtLeast(0f))
                return true
            }

            DragTarget.ARCHER_UTILS_TITLE -> {
                val w = ArcherUtilsHudRenderer.getWidth()
                val h = ArcherUtilsHudRenderer.getHeight()
                HudPositionManager.config.archerUtilsTitle.x =
                    (mx - dragOffsetX).coerceIn(0f, (screenWidth - w).coerceAtLeast(0f))
                HudPositionManager.config.archerUtilsTitle.y =
                    (my - dragOffsetY).coerceIn(0f, (screenHeight - h).coerceAtLeast(0f))
                return true
            }

            DragTarget.LAST_BREATH_UTILS_TITLE -> {
                val w = LastBreathUtilsHudRenderer.getWidth()
                val h = LastBreathUtilsHudRenderer.getHeight()
                HudPositionManager.config.lastBreathUtilsTitle.x =
                    (mx - dragOffsetX).coerceIn(0f, (screenWidth - w).coerceAtLeast(0f))
                HudPositionManager.config.lastBreathUtilsTitle.y =
                    (my - dragOffsetY).coerceIn(0f, (screenHeight - h).coerceAtLeast(0f))
                return true
            }

            DragTarget.MAXOR_HP_HUD -> {
                val w = MaxorHpHudRenderer.getWidth()
                val h = MaxorHpHudRenderer.getHeight()
                HudPositionManager.config.maxorHpHud.x =
                    (mx - dragOffsetX).coerceIn(0f, (screenWidth - w).coerceAtLeast(0f))
                HudPositionManager.config.maxorHpHud.y =
                    (my - dragOffsetY).coerceIn(0f, (screenHeight - h).coerceAtLeast(0f))
                return true
            }

            null -> Unit
        }

        return super.mouseDragged(event, dx, dy)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        val mx = mouseX.toFloat()
        val my = mouseY.toFloat()

        if (getModuleListBounds().contains(mx, my)) {
            HudPositionManager.config.moduleListScale =
                (HudPositionManager.config.moduleListScale + verticalAmount.toFloat() * 0.05f)
                    .coerceIn(0.5f, 4.0f)
            return true
        }

        val trueSplitsBounds = getTrueSplitsBounds()
        if (trueSplitsBounds.contains(mx, my)) {
            HudPositionManager.config.trueSplitsScale =
                (HudPositionManager.config.trueSplitsScale + verticalAmount.toFloat() * 0.05f)
                    .coerceIn(0.5f, 4.0f)
            return true
        }

        val trueSplitsBreakdownBounds = getTrueSplitsBreakdownBounds()
        if (trueSplitsBreakdownBounds.contains(mx, my)) {
            HudPositionManager.config.trueSplitsBreakdownScale =
                (HudPositionManager.config.trueSplitsBreakdownScale + verticalAmount.toFloat() * 0.05f)
                    .coerceIn(0.5f, 4.0f)
            return true
        }

        val scoreboardBounds = getScoreboardBounds()
        if (scoreboardBounds != null && scoreboardBounds.contains(mx, my)) {
            when {
                isShiftHeld() -> {
                    CleanScoreboardModule.extraWidth.value =
                        (CleanScoreboardModule.extraWidth.value + verticalAmount * 6.0)
                            .coerceIn(CleanScoreboardModule.extraWidth.min, CleanScoreboardModule.extraWidth.max)
                }

                isControlHeld() -> {
                    CleanScoreboardModule.extraHeight.value =
                        (CleanScoreboardModule.extraHeight.value + verticalAmount * 6.0)
                            .coerceIn(CleanScoreboardModule.extraHeight.min, CleanScoreboardModule.extraHeight.max)
                }

                else -> {
                    CleanScoreboardModule.hudScale.value =
                        (CleanScoreboardModule.hudScale.value + verticalAmount * 0.05)
                            .coerceIn(CleanScoreboardModule.hudScale.min, CleanScoreboardModule.hudScale.max)
                }
            }
            return true
        }

        val kuudraGoToBounds = getKuudraGoToBounds()
        if (kuudraGoToBounds.contains(mx, my)) {
            HudPositionManager.config.kuudraGoToScale =
                (HudPositionManager.config.kuudraGoToScale + verticalAmount.toFloat() * 0.05f)
                    .coerceIn(0.5f, 4.0f)
            return true
        }

        val buildProgressBounds = getBuildProgressBounds()
        if (buildProgressBounds.contains(mx, my)) {
            HudPositionManager.config.buildProgressHudScale =
                (HudPositionManager.config.buildProgressHudScale + verticalAmount.toFloat() * 0.05f)
                    .coerceIn(0.5f, 4.0f)
            return true
        }

        val deployableDisplayBounds = getDeployableDisplayBounds()
        if (deployableDisplayBounds.contains(mx, my)) {
            HudPositionManager.config.deployableDisplayScale =
                (HudPositionManager.config.deployableDisplayScale + verticalAmount.toFloat() * 0.05f)
                    .coerceIn(0.5f, 4.0f)
            return true
        }

        val golemTimerBounds = getGolemTimerBounds()
        if (golemTimerBounds.contains(mx, my)) {
            HudPositionManager.config.golemTimerHudScale =
                (HudPositionManager.config.golemTimerHudScale + verticalAmount.toFloat() * 0.05f)
                    .coerceIn(0.5f, 4.0f)
            return true
        }

        val performanceHudBounds = getPerformanceHudBounds()
        if (performanceHudBounds.contains(mx, my)) {
            HudPositionManager.config.performanceHudScale =
                (HudPositionManager.config.performanceHudScale + verticalAmount.toFloat() * 0.05f)
                    .coerceIn(0.5f, 4.0f)
            return true
        }

        val kuudraDirectionBounds = getKuudraDirectionBounds()
        if (kuudraDirectionBounds.contains(mx, my)) {
            HudPositionManager.config.kuudraDirectionHudScale =
                (HudPositionManager.config.kuudraDirectionHudScale + verticalAmount.toFloat() * 0.05f)
                    .coerceIn(0.5f, 4.0f)
            return true
        }

        val kuudraPriorityBounds = getKuudraPriorityBounds()
        if (kuudraPriorityBounds.contains(mx, my)) {
            HudPositionManager.config.kuudraPriorityScale =
                (HudPositionManager.config.kuudraPriorityScale + verticalAmount.toFloat() * 0.05f)
                    .coerceIn(0.5f, 4.0f)
            return true
        }

        val kuudraSpawnedCratesBounds = getKuudraSpawnedCratesBounds()
        if (kuudraSpawnedCratesBounds.contains(mx, my)) {
            HudPositionManager.config.kuudraSpawnedCratesScale =
                (HudPositionManager.config.kuudraSpawnedCratesScale + verticalAmount.toFloat() * 0.05f)
                    .coerceIn(0.5f, 4.0f)
            return true
        }

        val archerBounds = getArcherUtilsBounds()
        if (archerBounds.contains(mx, my)) {
            HudPositionManager.config.archerUtilsTitleScale =
                (HudPositionManager.config.archerUtilsTitleScale + verticalAmount.toFloat() * 0.05f)
                    .coerceIn(0.5f, 4.0f)
            return true
        }

        val lbBounds = getLastBreathUtilsBounds()
        if (lbBounds.contains(mx, my)) {
            HudPositionManager.config.lastBreathUtilsTitleScale =
                (HudPositionManager.config.lastBreathUtilsTitleScale + verticalAmount.toFloat() * 0.05f)
                    .coerceIn(0.5f, 4.0f)
            return true
        }

        val maxorBounds = getMaxorHpBounds()
        if (maxorBounds.contains(mx, my)) {
            HudPositionManager.config.maxorHpHudScale =
                (HudPositionManager.config.maxorHpHudScale + verticalAmount.toFloat() * 0.05f)
                    .coerceIn(0.5f, 4.0f)
            return true
        }

        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        dragging = null
        HudPositionManager.save()
        return super.mouseReleased(event)
    }

    override fun removed() {
        HudPositionManager.save()
        super.removed()
    }

    override fun onClose() {
        HudPositionManager.save()
        super.onClose()
    }

    override fun isPauseScreen(): Boolean = false
}
