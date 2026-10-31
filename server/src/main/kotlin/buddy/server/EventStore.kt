package buddy.server

import java.sql.Connection
import java.sql.DriverManager

/**
 * Append-only event log in SQLite. Events are stored opaquely as JSON, keyed by their id;
 * `seq` is the server-side cursor clients page with.
 *
 * One JDBC connection, all access serialized through [lock]. Callers run on Dispatchers.IO.
 */
class EventStore(path: String) : AutoCloseable {
    private val lock = Any()
    private val conn: Connection = DriverManager.getConnection("jdbc:sqlite:$path")

    init {
        conn.createStatement().use { st ->
            st.execute("PRAGMA journal_mode=WAL")
            st.execute("PRAGMA synchronous=NORMAL")
            st.execute("PRAGMA busy_timeout=5000")
            st.execute(
                """
                CREATE TABLE IF NOT EXISTS events(
                    seq INTEGER PRIMARY KEY AUTOINCREMENT,
                    id TEXT UNIQUE NOT NULL,
                    json TEXT NOT NULL,
                    received_at INTEGER NOT NULL
                )
                """.trimIndent(),
            )
        }
        conn.autoCommit = false
        conn.commit()
    }

    /** Raw stored event: [json] is a complete JSON object. */
    class Row(val seq: Long, val json: String)

    class Page(val cursor: Long, val events: List<Row>, val inserted: Int)

    /**
     * Inserts [incoming] (id to json) ignoring ids already known, then returns events with
     * seq > [since] in seq order, at most [limit]. If the page is full the cursor is the last
     * returned seq (client must sync again to get the rest); otherwise it is the max seq.
     *
     * A `since` beyond the max seq means the client's cursor came from a different/reset
     * database; we then answer from 0 so it re-learns everything (clients dedupe by id).
     */
    fun sync(since: Long, incoming: List<Pair<String, String>>, limit: Int): Page = synchronized(lock) {
        require(limit > 0) { "limit must be positive" }
        try {
            // Remember the high-water mark before inserting: inserting this request's events can
            // push it past a cursor from an older/reset database, which must still be detected.
            val maxBefore = conn.createStatement().use { st ->
                st.executeQuery("SELECT COALESCE(MAX(seq), 0) FROM events").use { rs -> rs.next(); rs.getLong(1) }
            }
            var inserted = 0
            if (incoming.isNotEmpty()) {
                val now = System.currentTimeMillis()
                conn.prepareStatement("INSERT OR IGNORE INTO events(id, json, received_at) VALUES (?, ?, ?)").use { ps ->
                    for ((id, json) in incoming) {
                        ps.setString(1, id)
                        ps.setString(2, json)
                        ps.setLong(3, now)
                        inserted += ps.executeUpdate()
                    }
                }
            }
            val max = conn.createStatement().use { st ->
                st.executeQuery("SELECT COALESCE(MAX(seq), 0) FROM events").use { rs -> rs.next(); rs.getLong(1) }
            }
            val from = if (since > maxBefore) 0 else since
            val rows = ArrayList<Row>()
            conn.prepareStatement("SELECT seq, json FROM events WHERE seq > ? ORDER BY seq LIMIT ?").use { ps ->
                ps.setLong(1, from)
                ps.setInt(2, limit)
                ps.executeQuery().use { rs -> while (rs.next()) rows += Row(rs.getLong(1), rs.getString(2)) }
            }
            conn.commit()
            val cursor = if (rows.size >= limit) rows.last().seq else max
            Page(cursor, rows, inserted)
        } catch (t: Throwable) {
            runCatching { conn.rollback() }
            throw t
        }
    }

    fun count(): Long = synchronized(lock) {
        conn.createStatement().use { st ->
            st.executeQuery("SELECT COUNT(*) FROM events").use { rs -> rs.next(); rs.getLong(1) }
        }.also { conn.commit() }
    }

    override fun close() = synchronized(lock) { conn.close() }
}
