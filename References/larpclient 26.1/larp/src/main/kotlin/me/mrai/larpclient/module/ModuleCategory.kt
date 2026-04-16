package me.mrai.larpclient.module

enum class ModuleCategory(
    val display: String,
    val group: String
) {
    SKYBLOCK_GENERAL("General", "Skyblock"),
    SKYBLOCK_GOLEMS("Golems", "Skyblock"),

    DUNGEONS_GENERAL("General", "Dungeons"),

    DUNGEONS_F7_GENERAL("General", "Floor 7"),
    DUNGEONS_F7_PREDEV("Predev", "Floor 7"),
    DUNGEONS_F7_P1("Phase 1", "Floor 7"),
    DUNGEONS_F7_P2("Phase 2", "Floor 7"),
    DUNGEONS_F7_P3("Phase 3", "Floor 7"),
    DUNGEONS_F7_P4("Phase 4", "Floor 7"),
    DUNGEONS_F7_P5("Phase 5", "Floor 7"),

    KUUDRA_GENERAL("General", "Kuudra"),
    KUUDRA_P1("Phase 1", "Kuudra"),
    KUUDRA_P2("Phase 2", "Kuudra"),
    KUUDRA_P3("Phase 3", "Kuudra"),
    KUUDRA_P4("Phase 4", "Kuudra"),

    MISC_UI("UI", "Misc"),
    MISC_OTHER("Other", "Misc")
}
