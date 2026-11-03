package buddy.core

import kotlin.math.ceil
import kotlin.math.floor

object Rules {
    const val MIN_MINUTES = 5
    const val MAX_MINUTES = 360
    const val STEP_MINUTES = 5
    const val FREE_CANCEL_SEC = 10
    const val COMPLETION_BONUS = 0.2
    const val DEFAULT_TAG = "focus"

    /**
     * Acorns for a session: 1 per focused minute; a completed session also gets the 20 % bonus.
     * An abandoned session keeps the minutes already focused (no bonus), so giving up after an
     * hour does not throw the hour away. Below the minimum it earns nothing.
     */
    fun reward(s: Payload.Session): Int {
        // Clamp before the Long→Int conversion: crafted or corrupted events must not overflow into
        // a negative reward (which would take acorns away).
        val focus = s.focusedSec.coerceAtLeast(0)
        val away = s.awaySec.coerceAtLeast(0)
        val minutes = (focus / 60).coerceIn(0, MAX_MINUTES.toLong()).toInt()
        if (minutes < MIN_MINUTES) return 0
        val base = if (s.completed) minutes + ceil(minutes * COMPLETION_BONUS).toInt() else minutes
        if (away <= 0) return base
        // Pauses are not free: the payout is scaled by the share of the session spent focusing.
        val share = focus.toDouble() / (focus + away).toDouble()
        return floor(base * share).toInt().coerceAtLeast(0)
    }

    /**
     * Whether a session event is physically possible for this app. Events can arrive from backups
     * or other devices with arbitrary numbers; dropping impossible ones keeps rewards and stats
     * bounded and keeps every device's fold identical.
     */
    fun validSession(s: Payload.Session): Boolean {
        if (s.focusedSec !in 0..(MAX_MINUTES * 60L)) return false
        if (s.endedAt < s.startedAt) return false
        if (s.awaySec !in 0..(MAX_MINUTES * 60L)) return false
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

    fun apply(e: Event): GameState = Builder.of(this).also { it.apply(e) }.build()

    companion object {
        /**
         * Events stamped in the same millisecond still need a causal order: money before spending,
         * ownership before wearing. Payload type is a stable tie-break; the id settles the rest.
         */
        fun rank(e: Event): Int = when (e.payload) {
            is Payload.Session -> 0
            is Payload.Tag -> 1
            is Payload.Buy -> 2
            is Payload.Style -> 3
            is Payload.Equip -> 4
            is Payload.Revoke -> 5
            Payload.Unknown -> 6
        }

        val eventOrder = compareBy<Event>({ it.ts }, { rank(it) }, { it.id })

        /**
         * Deterministic: every device holding the same events reaches the same state.
         *
         * Earnings are applied before anything else, so a purchase only depends on the total
         * earned rather than on which device's clock was ahead when the events were stamped.
         * Two events claiming one id are resolved by their canonical JSON, not by arrival order.
         */
        fun fold(events: Collection<Event>): GameState {
            val ordered = events.sortedWith(eventOrder)
            val unique = if (ordered.size < 2) ordered else dedupe(ordered)
            // One linear pass per phase: nothing is copied per event, so a large log folds in O(n).
            // Revocations are global, not ordered: a session removed on one device must vanish on all.
            val revoked = unique.filter { it.payload is Payload.Revoke }.mapTo(HashSet()) {
                (it.payload as Payload.Revoke).target
            }
            val b = Builder()
            for (e in unique) if (e.payload is Payload.Session && e.id !in revoked) b.apply(e)
            for (e in unique) if (e.payload !is Payload.Session) b.apply(e)
            return b.build()
        }

        /** Collapses events that claim the same id, keeping the lexicographically smaller JSON. */
        private fun dedupe(ordered: List<Event>): List<Event> {
            val byId = HashMap<String, Event>(ordered.size)
            var duplicates = false
            for (e in ordered) {
                val prev = byId[e.id]
                if (prev == null) byId[e.id] = e
                else {
                    duplicates = true
                    if (e.canonical() < prev.canonical()) byId[e.id] = e
                }
            }
            return if (duplicates) byId.values.sortedWith(eventOrder) else ordered
        }

        /** Mutable fold target: the public [GameState] is immutable, but rebuilding it per event is O(n²). */
        private class Builder {
            var acorns = 0
            var earnedTotal = 0
            val owned = Catalog.starterOwned.toMutableSet()
            val equipped = Catalog.starterDecor.toMutableMap()
            var style = CompanionStyle()
            val tags = hashMapOf(Rules.DEFAULT_TAG to TagInfo(Rules.DEFAULT_TAG, "Focus", 0xFF7FB069, false))
            val sessions = ArrayList<SessionRecord>()

            fun apply(e: Event) {
                when (val p = e.payload) {
                    is Payload.Session -> if (Rules.validSession(p)) {
                        val r = Rules.reward(p)
                        acorns += r
                        earnedTotal += r
                        sessions += SessionRecord(e.id, p, r)
                    }
                    is Payload.Buy -> {
                        val price = Catalog.price(p.item)
                        if (price != null && p.item !in owned && price <= acorns) {
                            acorns -= price
                            owned += p.item
                        }
                    }
                    is Payload.Equip -> {
                        val room = Catalog.roomOfSlot(p.slot)
                        val slot = Catalog.slot(p.slot)
                        when {
                            room == null || slot == null || "room:${room.id}" !in owned -> Unit
                            p.item == null -> equipped.remove(p.slot)
                            p.item in owned && Catalog.decor(p.item)?.kind == slot.kind -> equipped[p.slot] = p.item
                        }
                    }
                    is Payload.Style -> {
                        fun wearable(id: String?, slot: Catalog.OutfitSlot) =
                            id == null || (Catalog.outfit(id)?.slot == slot && "outfit:$id" in owned)
                        if ("skin:${p.skin}" in owned && wearable(p.hat, Catalog.OutfitSlot.HAT) &&
                            wearable(p.neck, Catalog.OutfitSlot.NECK) && wearable(p.face, Catalog.OutfitSlot.FACE)
                        ) {
                            style = CompanionStyle(p.name.take(24).ifBlank { "Pip" }, p.skin, p.hat, p.neck, p.face)
                        }
                    }
                    is Payload.Tag -> tags[p.tagId] = TagInfo(p.tagId, p.name.take(24), p.color, p.archived)
                    is Payload.Revoke -> Unit
                    Payload.Unknown -> Unit
                }
            }

            fun build() = GameState(
                acorns = acorns,
                earnedTotal = earnedTotal,
                owned = owned.toSet(),
                equipped = equipped.toMap(),
                style = style,
                tags = tags.toMap(),
                sessions = sessions.toList(),
            )

            companion object {
                fun of(s: GameState) = Builder().also { b ->
                    b.acorns = s.acorns
                    b.earnedTotal = s.earnedTotal
                    b.owned += s.owned
                    b.equipped += s.equipped
                    b.style = s.style
                    b.tags += s.tags
                    b.sessions += s.sessions
                }
            }
        }

}
}
