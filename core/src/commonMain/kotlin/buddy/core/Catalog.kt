package buddy.core

/** Everything that can be owned. Ids are namespaced: `decor:rug.cozy.2`, `skin:mint`, `outfit:hat.beret`, `room:study`. */
object Catalog {

    enum class DecorKind(val label: String) {
        WALL_ART("Wall art"), WINDOW("Window"), RUG("Rug"), PLANT("Plant"), LAMP("Lamp"),
        SEAT("Seat"), SHELF("Shelf"), TABLE("Table"), BED("Bed"), DESK("Desk"), CLOCK("Clock"),
    }

    enum class Theme(val label: String, val price: Int) {
        BASIC("Basic", 30), COZY("Cozy", 60), GARDEN("Garden", 80), RETRO("Retro", 100), NIGHT("Night", 120),
    }

    const val VARIANTS = 4

    data class Slot(val id: String, val kind: DecorKind)

    data class Room(val id: String, val name: String, val price: Int, val slots: List<Slot>)

    val rooms: List<Room> = listOf(
        Room(
            "living", "Living room", 0, slots(
                "living", DecorKind.WINDOW, DecorKind.WALL_ART, DecorKind.CLOCK, DecorKind.RUG,
                DecorKind.SEAT, DecorKind.PLANT, DecorKind.LAMP, DecorKind.SHELF,
            ),
        ),
        Room(
            "study", "Study", 250, slots(
                "study", DecorKind.WINDOW, DecorKind.SHELF, DecorKind.WALL_ART, DecorKind.DESK,
                DecorKind.RUG, DecorKind.LAMP, DecorKind.PLANT, DecorKind.CLOCK,
            ),
        ),
        Room(
            "bedroom", "Bedroom", 500, slots(
                "bedroom", DecorKind.WINDOW, DecorKind.WALL_ART, DecorKind.BED, DecorKind.RUG,
                DecorKind.LAMP, DecorKind.PLANT, DecorKind.SHELF, DecorKind.TABLE,
            ),
        ),
        Room(
            "porch", "Garden porch", 900, slots(
                "porch", DecorKind.WALL_ART, DecorKind.SEAT, DecorKind.TABLE, DecorKind.RUG,
                DecorKind.PLANT, DecorKind.LAMP, DecorKind.CLOCK, DecorKind.SHELF,
            ),
        ),
    )

    private fun slots(room: String, vararg kinds: DecorKind): List<Slot> {
        val seen = mutableMapOf<DecorKind, Int>()
        return kinds.map { k ->
            val n = seen.getOrElse(k) { 0 }.also { seen[k] = it + 1 }
            Slot(if (n == 0) "$room.${k.name.lowercase()}" else "$room.${k.name.lowercase()}$n", k)
        }
    }

    fun room(id: String): Room? = rooms.firstOrNull { it.id == id }
    fun slot(id: String): Slot? = rooms.firstNotNullOfOrNull { r -> r.slots.firstOrNull { it.id == id } }
    fun roomOfSlot(slotId: String): Room? = rooms.firstOrNull { r -> r.slots.any { it.id == slotId } }

    data class DecorItem(val kind: DecorKind, val theme: Theme, val variant: Int) {
        val id get() = "decor:${kind.name.lowercase()}.${theme.name.lowercase()}.$variant"
        val price get() = theme.price + variant * 5
    }

    fun decor(id: String): DecorItem? {
        val parts = id.removePrefix("decor:").split('.')
        if (!id.startsWith("decor:") || parts.size != 3) return null
        val kind = DecorKind.entries.firstOrNull { it.name.lowercase() == parts[0] } ?: return null
        val theme = Theme.entries.firstOrNull { it.name.lowercase() == parts[1] } ?: return null
        val v = parts[2].toIntOrNull()?.takeIf { it in 0 until VARIANTS } ?: return null
        return DecorItem(kind, theme, v)
    }

    fun decorFor(kind: DecorKind): List<DecorItem> =
        Theme.entries.flatMap { t -> (0 until VARIANTS).map { DecorItem(kind, t, it) } }

    data class Skin(val id: String, val name: String, val price: Int)

    val skins = listOf(
        Skin("classic", "Oat", 0), Skin("mint", "Mint", 150), Skin("peach", "Peach", 150),
        Skin("lavender", "Lavender", 200), Skin("sky", "Sky", 200), Skin("cocoa", "Cocoa", 250),
        Skin("midnight", "Midnight", 300), Skin("honey", "Honey", 400), Skin("ghost", "Ghost", 500),
    )

    enum class OutfitSlot { HAT, NECK, FACE }

    data class Outfit(val id: String, val slot: OutfitSlot, val name: String, val price: Int)

    val outfits = listOf(
        Outfit("hat.beanie", OutfitSlot.HAT, "Beanie", 80),
        Outfit("hat.beret", OutfitSlot.HAT, "Beret", 100),
        Outfit("hat.flower", OutfitSlot.HAT, "Flower", 90),
        Outfit("hat.cap", OutfitSlot.HAT, "Cap", 100),
        Outfit("hat.wizard", OutfitSlot.HAT, "Wizard hat", 200),
        Outfit("hat.crown", OutfitSlot.HAT, "Crown", 300),
        Outfit("hat.mushroom", OutfitSlot.HAT, "Mushroom cap", 180),
        Outfit("neck.scarf", OutfitSlot.NECK, "Scarf", 90),
        Outfit("neck.bow", OutfitSlot.NECK, "Bow tie", 80),
        Outfit("neck.bandana", OutfitSlot.NECK, "Bandana", 90),
        Outfit("neck.bell", OutfitSlot.NECK, "Bell collar", 120),
        Outfit("face.round", OutfitSlot.FACE, "Round glasses", 100),
        Outfit("face.shades", OutfitSlot.FACE, "Shades", 150),
        Outfit("face.blush", OutfitSlot.FACE, "Extra blush", 60),
    )

    fun outfit(id: String): Outfit? = outfits.firstOrNull { it.id == id }

    /** Price of any purchasable id, or null if the id is unknown. */
    fun price(itemId: String): Int? = when {
        itemId.startsWith("decor:") -> decor(itemId)?.price
        itemId.startsWith("skin:") -> skins.firstOrNull { "skin:${it.id}" == itemId }?.price
        itemId.startsWith("outfit:") -> outfit(itemId.removePrefix("outfit:"))?.price
        itemId.startsWith("room:") -> room(itemId.removePrefix("room:"))?.price
        else -> null
    }

    /** Owned from the start, and equipped in the living room. */
    val starterDecor: Map<String, String> = mapOf(
        "living.window" to "decor:window.basic.0",
        "living.rug" to "decor:rug.basic.0",
        "living.seat" to "decor:seat.basic.0",
        "living.plant" to "decor:plant.basic.0",
    )

    val starterOwned: Set<String> = starterDecor.values.toSet() + "skin:classic" + "room:living"
}
