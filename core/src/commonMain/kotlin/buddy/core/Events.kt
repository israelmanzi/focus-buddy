package buddy.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One immutable fact. The whole synced game state is a fold over these (see [GameState.fold]). */
@Serializable
data class Event(
    val id: String,
    val device: String,
    /** Epoch millis on the creating device. Primary sort key. */
    val ts: Long,
    val payload: Payload,
)

@Serializable
sealed interface Payload {

    /** A focus session ended, either completed or abandoned. Rewards are derived, not stored. */
    @Serializable
    @SerialName("session")
    data class Session(
        val startedAt: Long,
        val endedAt: Long,
        /** Planned minutes for count-down sessions; 0 for count-up. */
        val plannedMin: Int,
        val focusedSec: Long,
        val tag: String,
        val completed: Boolean,
        val deepFocus: Boolean = false,
    ) : Payload

    @Serializable
    @SerialName("buy")
    data class Buy(val item: String) : Payload

    /** Put an owned decor item in a slot, or clear it with null. */
    @Serializable
    @SerialName("equip")
    data class Equip(val slot: String, val item: String?) : Payload

    @Serializable
    @SerialName("style")
    data class Style(
        val name: String,
        val skin: String,
        val hat: String? = null,
        val neck: String? = null,
        val face: String? = null,
    ) : Payload

    @Serializable
    @SerialName("tag")
    data class Tag(val tagId: String, val name: String, val color: Long, val archived: Boolean = false) : Payload
}

@Serializable
data class SyncRequest(val since: Long, val events: List<Event>)

@Serializable
data class SyncResponse(
    /** Server cursor to pass as `since` next time. */
    val cursor: Long,
    val events: List<Event>,
)

val BuddyJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    classDiscriminator = "type"
}
