package dev.zbxmon.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.atomic.AtomicInteger

/** API-level failure. The UI maps [kind] to a localized message; [detail] is raw server/diagnostic text. */
class ZabbixException(val kind: Kind, val detail: String = "") : Exception(detail.ifBlank { kind.name }) {
    enum class Kind { NO_SERVER, NOT_JSONRPC, API, HTTP, BAD_URL }
}

private fun strs(vararg s: String) = JsonArray(s.map { JsonPrimitive(it) })
private fun ids(l: List<String>) = JsonArray(l.map { JsonPrimitive(it) })
private fun JsonObject.s(k: String): String = (this[k] as? JsonPrimitive)?.contentOrNull ?: ""
private fun JsonObject.list(k: String): JsonArray = this[k] as? JsonArray ?: JsonArray(emptyList())

/**
 * Minimal client for the Zabbix 7.x JSON-RPC API.
 * Authentication: API token sent as "Authorization: Bearer <token>".
 */
class ZabbixApi(private val p: Profile) {
    private val http = Tls.client(p)
    private val json = Json { ignoreUnknownKeys = true }
    private val seq = AtomicInteger(0)

    private suspend fun call(method: String, params: JsonElement, auth: Boolean = true): JsonElement =
        withContext(Dispatchers.IO) {
            val body = buildJsonObject {
                put("jsonrpc", "2.0")
                put("method", method)
                put("params", params)
                put("id", seq.incrementAndGet())
            }.toString()
            val rb = Request.Builder().url(p.apiUrl)
                .post(body.toRequestBody("application/json-rpc".toMediaType()))
            if (auth) rb.header("Authorization", "Bearer ${p.token}")
            http.newCall(rb.build()).execute().use { r ->
                val txt = r.body?.string().orEmpty()
                if (!r.isSuccessful) throw ZabbixException(ZabbixException.Kind.HTTP, r.code.toString())
                val o = try {
                    json.parseToJsonElement(txt).jsonObject
                } catch (e: Exception) {
                    throw ZabbixException(ZabbixException.Kind.NOT_JSONRPC)
                }
                (o["error"] as? JsonObject)?.let { e ->
                    throw ZabbixException(ZabbixException.Kind.API, e.s("data").ifBlank { e.s("message") })
                }
                o["result"] ?: JsonNull
            }
        }

    /** Verifies URL + TLS + token. Returns the Zabbix version. */
    suspend fun check(): String {
        // apiinfo.version must be called without authentication
        val v = call("apiinfo.version", JsonArray(emptyList()), auth = false).jsonPrimitive.content
        call("host.get", buildJsonObject { put("output", strs("hostid")); put("limit", 1) })
        return v
    }

    suspend fun hosts(): List<Host> =
        call("host.get", buildJsonObject {
            put("output", strs("hostid", "name", "status"))
            put("selectInterfaces", strs("ip", "available"))
            put("selectHostGroups", strs("name"))
            put("selectTags", strs("tag", "value"))
            put("sortfield", "name")
        }).jsonArray.map { e ->
            val o = e.jsonObject
            val ifs = o.list("interfaces").map { it.jsonObject }
            Host(
                id = o.s("hostid"),
                name = o.s("name"),
                enabled = o.s("status") == "0",
                ifaceDown = ifs.any { it.s("available") == "2" },
                ip = ifs.firstOrNull()?.s("ip").orEmpty(),
                groups = o.list("hostgroups").map { it.jsonObject.s("name") }.filter { it.isNotBlank() },
                tags = o.list("tags").map {
                    val t = it.jsonObject
                    if (t.s("value").isBlank()) t.s("tag") else "${t.s("tag")}=${t.s("value")}"
                }.filter { it.isNotBlank() }
            )
        }

    suspend fun problems(hostId: String? = null): List<Problem> {
        val ps = call("problem.get", buildJsonObject {
            put("output", "extend")
            put("selectAcknowledges", "extend")
            put("sortfield", strs("eventid"))
            put("sortorder", "DESC")
            put("limit", 300)
            if (hostId != null) put("hostids", hostId)
        }).jsonArray.map { it.jsonObject }
        if (ps.isEmpty()) return emptyList()

        // problem.get does not return hosts, so resolve them through the triggers.
        val tids = ps.map { it.s("objectid") }.distinct()
        val hostByTrigger = call("trigger.get", buildJsonObject {
            put("triggerids", ids(tids))
            put("output", strs("triggerid"))
            put("selectHosts", strs("hostid", "name"))
        }).jsonArray.associate { t ->
            val o = t.jsonObject
            o.s("triggerid") to o.list("hosts").firstOrNull()?.jsonObject
        }

        return ps.map { o ->
            val h = hostByTrigger[o.s("objectid")]
            Problem(
                eventId = o.s("eventid"),
                triggerId = o.s("objectid"),
                name = o.s("name"),
                severity = o.s("severity").toIntOrNull() ?: 0,
                clock = o.s("clock").toLongOrNull() ?: 0L,
                acked = o.s("acknowledged") == "1",
                suppressed = o.s("suppressed") == "1",
                hostId = h?.s("hostid").orEmpty(),
                hostName = h?.s("name") ?: "?",
                acks = o.list("acknowledges").map {
                    val a = it.jsonObject
                    Ack(
                        clock = a.s("clock").toLongOrNull() ?: 0L,
                        user = a.s("username").ifBlank { "${a.s("name")} ${a.s("surname")}".trim() }
                            .ifBlank { "user ${a.s("userid")}" },
                        message = a.s("message"),
                        action = a.s("action").toIntOrNull() ?: 0
                    )
                }
            )
        }
    }

    /** Problems resolved during the last [days] days (problem events that have a recovery event). */
    suspend fun resolved(days: Int = 7): List<Resolved> {
        val from = System.currentTimeMillis() / 1000 - days * 86400L
        val problemEvents = call("event.get", buildJsonObject {
            put("source", 0)
            put("object", 0)
            put("value", 1)
            put("time_from", from)
            put("output", strs("eventid", "name", "severity", "clock", "r_eventid"))
            put("selectHosts", strs("hostid", "name"))
            put("sortfield", strs("eventid"))
            put("sortorder", "DESC")
            put("limit", 300)
        }).jsonArray.map { it.jsonObject }.filter { it.s("r_eventid").let { r -> r.isNotBlank() && r != "0" } }
        if (problemEvents.isEmpty()) return emptyList()

        val recoveryClock = call("event.get", buildJsonObject {
            put("eventids", ids(problemEvents.map { it.s("r_eventid") }))
            put("output", strs("eventid", "clock"))
        }).jsonArray.associate { it.jsonObject.s("eventid") to (it.jsonObject.s("clock").toLongOrNull() ?: 0L) }

        return problemEvents.map { o ->
            Resolved(
                eventId = o.s("eventid"),
                name = o.s("name"),
                severity = o.s("severity").toIntOrNull() ?: 0,
                hostName = o.list("hosts").firstOrNull()?.jsonObject?.s("name") ?: "?",
                start = o.s("clock").toLongOrNull() ?: 0L,
                end = recoveryClock[o.s("r_eventid")] ?: 0L
            )
        }.sortedByDescending { it.end }
    }

    /** event.acknowledge action bitmask: 2 = acknowledge, 4 = add message, 16 = unacknowledge. */
    suspend fun ack(eventId: String, a: AckAction, message: String) {
        var action = when (a) {
            AckAction.ACK -> 2
            AckAction.UNACK -> 16
            AckAction.COMMENT -> 4
        }
        if (message.isNotBlank() && a != AckAction.COMMENT) action = action or 4
        call("event.acknowledge", buildJsonObject {
            put("eventids", ids(listOf(eventId)))
            put("action", action)
            if (message.isNotBlank()) put("message", message)
        })
    }

    suspend fun items(hostId: String): List<Item> =
        call("item.get", buildJsonObject {
            put("hostids", hostId)
            put("output", strs("itemid", "name", "key_", "lastvalue", "units", "value_type", "lastclock"))
            put("monitored", true)
            put("sortfield", "name")
        }).jsonArray.map {
            val o = it.jsonObject
            Item(
                id = o.s("itemid"),
                name = o.s("name"),
                key = o.s("key_"),
                lastValue = o.s("lastvalue"),
                units = o.s("units"),
                valueType = o.s("value_type").toIntOrNull() ?: 0,
                lastClock = o.s("lastclock").toLongOrNull() ?: 0L
            )
        }

    /** Up to 48 hours uses history.get; longer ranges use trend.get (hourly averages). */
    suspend fun series(itemId: String, valueType: Int, hours: Int): List<Point> {
        val from = System.currentTimeMillis() / 1000 - hours * 3600L
        return if (hours > 48) {
            call("trend.get", buildJsonObject {
                put("itemids", itemId)
                put("output", strs("clock", "value_avg"))
                put("time_from", from)
                put("limit", 5000)
            }).jsonArray.mapNotNull {
                val o = it.jsonObject
                val v = o.s("value_avg").toDoubleOrNull() ?: return@mapNotNull null
                Point(o.s("clock").toLongOrNull() ?: return@mapNotNull null, v)
            }.sortedBy { it.t }
        } else {
            call("history.get", buildJsonObject {
                put("itemids", itemId)
                put("history", valueType)
                put("output", "extend")
                put("time_from", from)
                put("sortfield", "clock")
                put("sortorder", "DESC")
                put("limit", 3000)
            }).jsonArray.mapNotNull {
                val o = it.jsonObject
                val v = o.s("value").toDoubleOrNull() ?: return@mapNotNull null
                Point(o.s("clock").toLongOrNull() ?: return@mapNotNull null, v)
            }.reversed()
        }
    }
}
