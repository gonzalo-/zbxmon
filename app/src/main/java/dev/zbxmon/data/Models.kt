package dev.zbxmon.data

data class Host(
    val id: String,
    val name: String,
    val enabled: Boolean,
    val ifaceDown: Boolean,
    val ip: String,
    /** Host group names. */
    val groups: List<String> = emptyList(),
    /** Host tags as "tag" or "tag=value". */
    val tags: List<String> = emptyList()
)

data class Ack(val clock: Long, val user: String, val message: String, val action: Int)

data class Problem(
    val eventId: String,
    val triggerId: String,
    val name: String,
    val severity: Int,
    val clock: Long,
    val acked: Boolean,
    val suppressed: Boolean,
    val hostId: String,
    val hostName: String,
    val acks: List<Ack>
)

data class Item(
    val id: String,
    val name: String,
    val key: String,
    val lastValue: String,
    val units: String,
    val valueType: Int,
    val lastClock: Long
) {
    /** Zabbix value types 0 (float) and 3 (unsigned integer) can be graphed. */
    val numeric: Boolean get() = valueType == 0 || valueType == 3
}

data class Point(val t: Long, val v: Double)

/** A problem that has already been resolved (history). Times are epoch seconds. */
data class Resolved(
    val eventId: String,
    val name: String,
    val severity: Int,
    val hostName: String,
    val start: Long,
    val end: Long
)

enum class AckAction { ACK, UNACK, COMMENT }

/** Display state of a host; the Hosts tab lets the user choose which states are visible. */
enum class HostState(val emoji: String) {
    OK("🟢"),
    PROBLEM("🚨"),
    IFACE_DOWN("📵"),
    MAINTENANCE("🔧"),
    DISABLED("⏸️")
}
