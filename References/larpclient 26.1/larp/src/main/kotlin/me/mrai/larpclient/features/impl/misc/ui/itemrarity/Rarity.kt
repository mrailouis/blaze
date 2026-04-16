package me.mrai.larpclient.features.impl.misc.ui.itemrarity

enum class Rarity(val color: Int) {
    COMMON(0xAAAAAA),
    UNCOMMON(0x55FF55),
    RARE(0x5555FF),
    EPIC(0xAA00AA),
    LEGENDARY(0xFFAA00),
    MYTHIC(0xFF55FF),
    SPECIAL(0xFF5555),
    VERY_SPECIAL(0xFF0000);

    companion object {
        fun fromLore(lines: List<String>): Rarity? {
            for (line in lines) {
                val normalized = line.uppercase()
                when {
                    "VERY SPECIAL" in normalized -> return VERY_SPECIAL
                    "SPECIAL" in normalized -> return SPECIAL
                    "MYTHIC" in normalized -> return MYTHIC
                    "LEGENDARY" in normalized -> return LEGENDARY
                    "EPIC" in normalized -> return EPIC
                    "RARE" in normalized -> return RARE
                    "UNCOMMON" in normalized -> return UNCOMMON
                    "COMMON" in normalized -> return COMMON
                }
            }
            return null
        }
    }
}
