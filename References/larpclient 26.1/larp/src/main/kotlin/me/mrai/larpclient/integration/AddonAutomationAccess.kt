package me.mrai.larpclient.integration

object AddonAutomationAccess {
    var hasAddonFeatures: () -> Boolean = { false }
    var isAutomationEnabled: () -> Boolean = { false }
}
