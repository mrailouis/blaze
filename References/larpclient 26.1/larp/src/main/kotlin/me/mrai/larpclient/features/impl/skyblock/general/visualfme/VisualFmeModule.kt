package me.mrai.larpclient.features.impl.skyblock.general.visualfme

import me.mrai.larpclient.module.ActionSetting
import me.mrai.larpclient.module.BoolSetting
import me.mrai.larpclient.module.ComponentSetting
import me.mrai.larpclient.module.InfoSetting
import me.mrai.larpclient.module.Module
import me.mrai.larpclient.module.ModuleCategory
import me.mrai.larpclient.module.SliderSetting
import me.mrai.larpclient.module.shownWhen

object VisualFmeModule : Module(
    name = "Visual FME",
    description = "Client-side floor visual replacements with in-world edit controls.",
    category = ModuleCategory.SKYBLOCK_GENERAL
) {
    private val editMode = BoolSetting("Edit Mode", false)
    private val schitzoMode = BoolSetting("Schitzo Mode", false)
    private val floorId = ComponentSetting("Floor Id", "7")
    private val renderDistance = SliderSetting("Render Distance", 128.0, 16.0, 512.0, 16.0)
    private val selectedBlock = ComponentSetting("Selected Block", "minecraft:glass")
    private val schitzoRandomize = ActionSetting("Schitzo Randomizer", { "Randomize" }) {
        VisualFmeState.randomizeSchitzoMappings()
    }.shownWhen { schitzoMode.value }
    private val schitzoNiceRandomize = ActionSetting("Nice Randomizer", { "Nice Randomize" }) {
        VisualFmeState.niceRandomizeSchitzoMappings()
    }.shownWhen { schitzoMode.value }
    private val status = InfoSetting("Status") { VisualFmeState.statusLine() }
    private val schitzoStatus = InfoSetting("Schitzo") { VisualFmeState.schitzoStatusLine() }.shownWhen { schitzoMode.value }
    private val controls = InfoSetting("Controls") { VisualFmeState.controlsLine() }

    private var lastEditMode = false
    private var lastSchitzoMode = false
    private var lastFloorId = "7"
    private var lastRenderDistance = 128.0
    private var lastSelectedBlock = "minecraft:glass"

    init {
        VisualFmeState.initialize()
        editMode.value = VisualFmeState.isEditMode()
        schitzoMode.value = VisualFmeState.isSchitzoMode()
        floorId.text = VisualFmeState.floor()
        renderDistance.value = VisualFmeState.maxRenderDistance().toDouble()
        selectedBlock.text = VisualFmeState.selectedBlockId()
        snapshotSettings()

        settings += editMode
        settings += schitzoMode
        settings += floorId
        settings += renderDistance
        settings += selectedBlock
        settings += schitzoRandomize
        settings += schitzoNiceRandomize
        settings += status
        settings += schitzoStatus
        settings += controls
    }

    override fun onEnable() {
        VisualFmeState.setEnabled(true)
        syncSettings(force = true)
    }

    override fun onDisable() {
        VisualFmeState.setEnabled(false)
        VisualFmeState.resetInputTracking()
    }

    override fun onTick() {
        syncSettings(force = false)
        if (enabled) {
            VisualFmeState.onClientTick()
        }
    }

    private fun syncSettings(force: Boolean) {
        if (!enabled && !force) return

        if (force || editMode.value != lastEditMode) {
            VisualFmeState.setEditMode(editMode.value)
            lastEditMode = editMode.value
        }

        if (force || schitzoMode.value != lastSchitzoMode) {
            VisualFmeState.setSchitzoMode(schitzoMode.value)
            lastSchitzoMode = schitzoMode.value
        }

        if (force || floorId.text != lastFloorId) {
            VisualFmeState.setFloor(floorId.text)
            lastFloorId = floorId.text
        }

        if (force || renderDistance.value != lastRenderDistance) {
            VisualFmeState.setMaxRenderDistance(renderDistance.value.toInt())
            lastRenderDistance = renderDistance.value
        }

        if (force || selectedBlock.text != lastSelectedBlock) {
            if (VisualFmeState.setSelectedBlock(selectedBlock.text)) {
                lastSelectedBlock = selectedBlock.text
            }
        }
    }

    private fun snapshotSettings() {
        lastEditMode = editMode.value
        lastSchitzoMode = schitzoMode.value
        lastFloorId = floorId.text
        lastRenderDistance = renderDistance.value
        lastSelectedBlock = selectedBlock.text
    }
}
