package buddy.core

import kotlin.math.ceil

object Rules {
    const val MIN_MINUTES = 5
    const val MAX_MINUTES = 360
    const val STEP_MINUTES = 5
    const val FREE_CANCEL_SEC = 10
    const val COMPLETION_BONUS = 0.2
    const val DEFAULT_TAG = "focus"

    /** Acorns for a session: 1 per focused minute, +20 % for finishing. Nothing below the minimum or if abandoned. */
    fun reward(s: Payload.Session): Int {
        // Clamp before the Long→Int conversion: crafted or corrupted events must not overflow into
        // a negative reward (which would take acorns away).
        val minutes = (s.focusedSec / 60).coerceIn(0, MAX_MINUTES.toLong()).toInt()
        if (!s.completed || minutes < MIN_MINUTES) return 0
        return minutes + ceil(minutes * COMPLETION_BONUS).toInt()
    }

    /**
     * Whether a session event is physically possible for this app. Events can arrive from backups
     * or other devices with arbitrary numbers; dropping impossible ones keeps rewards and stats
     * bounded and keeps every device's fold identical.
     */
    fun validSession(s: Payload.Session): Boolean {
        if (s.focusedSec !in 0..(MAX_MINUTES * 60L)) return false
        if (s.endedAt < s.startedAt) return false
        if (s.plannedMin == 0) return true
        if (s.plannedMin !in MIN_MINUTES..MAX_MINUTES) return false
        return s.focusedSec <= s.plannedMin * 60L
    }
}

data class TagInfo(val id: String, val name: String, val color: Long, val archived: Boolean)

data class CompanionStyle(
    val name: String = "Pip",
    val skin: String = "classic",
    val hat: String? = null,
    val neck: String? = null,
    val face: String? = null,
)

data class SessionRecord(val eventId: String, val session: Payload.Session, val reward: Int)

data class GameState(
    val acorns: Int = 0,
    val earnedTotal: Int = 0,
    val owned: Set<String> = Catalog.starterOwned,
    val equipped: Map<String, String> = Catalog.starterDecor,
    val style: CompanionStyle = CompanionStyle(),
    val tags: Map<String, TagInfo> = mapOf(Rules.DEFAULT_TAG to TagInfo(Rules.DEFAULT_TAG, "Focus", 0xFF7FB069, false)),
    val sessions: List<SessionRecord> = emptyList(),
) {
    fun owns(item: String) = item in owned
    fun canAfford(item: String) = (Catalog.price(item) ?: Int.MAX_VALUE) <= acorns
    fun roomUnlocked(roomId: String) = "room:$roomId" in owned

    fun apply(e: Event): GameState = when (val p = e.payload) {
        is Payload.Session -> if (!Rules.validSession(p)) this else {
            val r = Rules.reward(p)
            copy(acorns = acorns + r, earnedTotal = earnedTotal + r, sessions = sessions + SessionRecord(e.id, p, r))
        }
        is Payload.Buy -> {
            val price = Catalog.price(p.item)
            if (price == null || p.item in owned || price > acorns) this
            else copy(acorns = acorns - price, owned = owned + p.item)
        }
        is Payload.Equip -> {
            val room = Catalog.roomOfSlot(p.slot)
            val slot = Catalog.slot(p.slot)
            when {
                room == null || slot == null || !roomUnlocked(room.id) -> this
                p.item == null -> copy(equipped = equipped - p.slot)
                p.item !in owned || Catalog.decor(p.item)?.kind != slot.kind -> this
                else -> copy(equipped = equipped + (p.slot to p.item))
            }
        }
        is Payload.Style -> {
            fun wearable(id: String?, slot: Catalog.OutfitSlot) =
                id == null || (Catalog.outfit(id)?.slot == slot && "outfit:$id" in owned)
            if ("skin:${p.skin}" !in owned || !wearable(p.hat, Catalog.OutfitSlot.HAT) ||
                !wearable(p.neck, Catalog.OutfitSlot.NECK) || !wearable(p.face, Catalog.OutfitSlot.FACE)
            ) this
            else copy(style = CompanionStyle(p.name.take(24).ifBlank { "Pip" }, p.skin, p.hat, p.neck, p.face))
        }
        is Payload.Tag -> copy(tags = tags + (p.tagId to TagInfo(p.tagId, p.name.take(24), p.color, p.archived)))
    }

    companion object {
        val eventOrder = compareBy<Event>({ it.ts }, { it.id })

        /** Deterministic: every device holding the same events reaches the same state. */
        fun fold(events: Collection<Event>): GameState =
            events.sortedWith(eventOrder).distinctBy { it.id }.fold(GameState()) { s, e -> s.apply(e) }
    }
}
