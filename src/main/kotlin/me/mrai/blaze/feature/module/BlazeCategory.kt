package me.mrai.blaze.feature.module

object BlazeModuleIds {
    const val AUTOCLICKER = "general.autoclicker"
    const val BEACON_REMINDER = "general.beacon_reminder"
    const val DARK_MODE_ISLE = "general.dark_mode_isle"
    const val ACTIVE_SLAYER_QUEST_CHECK = "blaze.active_slayer_quest_check"
    const val BLAZE_BEACON_REMINDER = "blaze.beacon_reminder"
    const val POWER_REMINDER = "blaze.power_reminder"
    const val GUMMY_REMINDER = "blaze.gummy_reminder"
    const val BLAZE_ESP = "blaze.blaze_esp"
    const val MINIBOSSES = "blaze.minibosses"
    const val FLARE = "blaze.flare"
    const val AUTO_DAGGER_SWAP = "blaze.auto_dagger_swap"
    const val BOSS_ESP = "blaze.boss_esp"
    const val TUNING_DISPLAY = "blaze.tuning_display"
    const val TWILIGHT_POISON = "blaze.twilight_poison"
    const val RAGNAROCK = "blaze.ragnarock"
    const val DEMON_ESP = "blaze.demon_esp"
    const val DEMON_PRIORITY = "blaze.demon_priority"
    const val PROFIT_TRACKER = "blaze.profit_tracker"
    const val SPLITS = "blaze.splits"
    const val CHAT_NOTIFICATIONS = "blaze.chat_notifications"
    const val BLAZE_PATHFINDER = "blaze.pathfinder"
}

enum class BlazeCategory(
    val displayName: String,
    val description: String,
    val modules: List<BlazeModule>
) {
    GENERAL(
        displayName = "General",
        description = "",
        modules = listOf(
            BlazeModule(BlazeModuleIds.AUTOCLICKER, "Autoclicker", "Clicks for you.", configurable = true),
            BlazeModule(BlazeModuleIds.BEACON_REMINDER, "Beacon Reminder", "Tells you if your beacon is not on magic find."),
            BlazeModule(BlazeModuleIds.DARK_MODE_ISLE, "Dark Mode Isle", "Overlays the smoldering tomb with a dark overlay.")
        )
    ),
    BLAZE(
        displayName = "Blaze",
        description = "Blaze slayer helpers, overlays, alerts, and run utilities.",
        modules = listOf(
            BlazeModule(BlazeModuleIds.ACTIVE_SLAYER_QUEST_CHECK, "Active slayer quest check", "Verifies that a Blaze slayer quest is currently active."),
            BlazeModule(BlazeModuleIds.BLAZE_BEACON_REMINDER, "Beacon Reminder", "Blaze-specific beacon notifications and timing cues."),
            BlazeModule(BlazeModuleIds.POWER_REMINDER, "Power Reminder", "Tracks when your power orb or power phase needs attention."),
            BlazeModule(BlazeModuleIds.GUMMY_REMINDER, "Gummy Reminder", "Reminds you when gummy buffs or swaps are due."),
            BlazeModule(BlazeModuleIds.BLAZE_ESP, "Blaze ESP", "Highlights Blaze targets and important entities in the arena."),
            BlazeModule(BlazeModuleIds.MINIBOSSES, "Minibosses", "Alerts and warning controls for miniboss encounters.", children = listOf("Alert", "Warning")),
            BlazeModule(BlazeModuleIds.FLARE, "Flare", "Flare reminder and automation tools for Blaze runs.", children = listOf("Reminder", "Auto Flare")),
            BlazeModule(BlazeModuleIds.AUTO_DAGGER_SWAP, "Auto Dagger Swap", "Automatically swaps daggers when your phase calls for it."),
            BlazeModule(BlazeModuleIds.BOSS_ESP, "Boss ESP", "Tracks the main boss with a clearer on-screen highlight."),
            BlazeModule(BlazeModuleIds.TUNING_DISPLAY, "Tuning Display", "Shows your current tuning setup in a compact display."),
            BlazeModule(BlazeModuleIds.TWILIGHT_POISON, "Twilight Poison", "Reminder and automation support for Twilight Poison.", children = listOf("Reminder", "Auto Poison")),
            BlazeModule(BlazeModuleIds.RAGNAROCK, "Ragnarock", "Reminder, auto-cast, and cast-state notifications for Ragnarock.", children = listOf("Reminder", "Auto Ragnarock", "Casted / Cancelled notification")),
            BlazeModule(BlazeModuleIds.DEMON_ESP, "Demon ESP", "Highlights spawned demons for faster target pickup."),
            BlazeModule(BlazeModuleIds.DEMON_PRIORITY, "Demon Priority", "Helps prioritize demon targets during Blaze phases."),
            BlazeModule(BlazeModuleIds.BLAZE_PATHFINDER, "Blaze Pathfinder", "Configure pathfinding rotation, look bias, and route follower behavior.", configurable = true),
            BlazeModule(BlazeModuleIds.PROFIT_TRACKER, "Profit Tracker", "Tracks run profit and session performance."),
            BlazeModule(
                BlazeModuleIds.SPLITS,
                "Splits",
                "Phase-by-phase split timing for Blaze boss progression.",
                children = listOf(
                    "Time to spawn",
                    "1/3 boss",
                    "Demon 1",
                    "Demon 2",
                    "2/3 boss",
                    "Demon 1",
                    "Demon 2",
                    "3/3 boss",
                    "Total kill time",
                    "Overall time"
                )
            ),
            BlazeModule(BlazeModuleIds.CHAT_NOTIFICATIONS, "Chat Notifications", "Sends selected split updates to chat during each phase.", children = listOf("Choose from splits to send in chat at each phase."))
        )
    );
}

data class BlazeModule(
    val id: String,
    val name: String,
    val description: String,
    val children: List<String> = emptyList(),
    val configurable: Boolean = false
) {
    val expandable: Boolean
        get() = configurable || children.isNotEmpty()
}
