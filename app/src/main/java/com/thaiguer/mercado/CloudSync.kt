package com.thaiguer.mercado

import com.thaiguer.mercado.core.MercadoItem
import org.json.JSONArray
import org.json.JSONObject

data class SyncSnapshot(
    val items: List<MercadoItem> = emptyList(),
    val deleted: Map<String, Long> = emptyMap()
)

object CloudSync {

    // ---------------------------------------------------------
    // Merge local and remote snapshots
    // ---------------------------------------------------------

    fun merge(
        local: SyncSnapshot,
        remote: SyncSnapshot
    ): SyncSnapshot {

        data class Entry(
            val item: MercadoItem?,
            val timestamp: Long,
            val deleted: Boolean
        )

        val entries = mutableMapOf<String, Entry>()

        fun addItem(item: MercadoItem) {
            val candidate = Entry(
                item = item,
                timestamp = item.modifiedAt,
                deleted = false
            )

            val current = entries[item.id]

            if (
                current == null ||
                candidate.timestamp > current.timestamp
            ) {
                entries[item.id] = candidate
            }
        }

        fun addDeletion(
            id: String,
            timestamp: Long
        ) {
            val candidate = Entry(
                item = null,
                timestamp = timestamp,
                deleted = true
            )

            val current = entries[id]

            if (
                current == null ||
                timestamp >= current.timestamp
            ) {
                entries[id] = candidate
            }
        }

        listOf(local, remote).forEach { snapshot ->

            snapshot.items.forEach(::addItem)

            snapshot.deleted.forEach { (id, timestamp) ->
                addDeletion(id, timestamp)
            }
        }

        val mergedItems = entries.values
            .filter { !it.deleted }
            .mapNotNull { it.item }
            .sortedBy { it.name.lowercase() }

        val mergedDeleted = entries
            .filterValues { it.deleted }
            .mapValues { it.value.timestamp }

        return SyncSnapshot(
            items = mergedItems,
            deleted = mergedDeleted
        )
    }

    // ---------------------------------------------------------
    // Convert snapshot to JSON
    // ---------------------------------------------------------

    fun snapshotToJson(
        snapshot: SyncSnapshot
    ): JSONObject {

        val itemsArray = JSONArray()

        snapshot.items.forEach { item ->

            itemsArray.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("name", item.name)
                    put("ok", item.ok)
                    put("count", item.count)
                    put("price", item.price)
                    put("modifiedAt", item.modifiedAt)
                }
            )
        }

        val deletedArray = JSONArray()

        snapshot.deleted.forEach { (id, timestamp) ->

            deletedArray.put(
                JSONObject().apply {
                    put("id", id)
                    put("modifiedAt", timestamp)
                }
            )
        }

        return JSONObject().apply {
            put("schemaVersion", 1)
            put("items", itemsArray)
            put("deleted", deletedArray)
        }
    }

    // ---------------------------------------------------------
    // Parse JSON into snapshot
    // ---------------------------------------------------------

    fun parseSnapshot(
        json: JSONObject
    ): SyncSnapshot {

        // Support the previous JSONBin "record" structure.
        val data = json.optJSONObject("record") ?: json

        val items = mutableListOf<MercadoItem>()
        val deleted = mutableMapOf<String, Long>()

        val itemsArray = data.optJSONArray("items") ?: JSONArray()

        for (i in 0 until itemsArray.length()) {

            val obj = itemsArray.optJSONObject(i) ?: continue

            val id = obj.optString("id", "")
            val name = obj.optString("name", "")

            if (id.isBlank() || name.isBlank()) {
                continue
            }

            items.add(
                MercadoItem(
                    id = id,
                    name = name,
                    ok = obj.optBoolean("ok", false),
                    count = obj.optInt("count", 1),
                    price = obj.optDouble("price", 0.0),
                    modifiedAt = obj.optLong("modifiedAt", 0L)
                )
            )
        }

        val deletedArray = data.optJSONArray("deleted") ?: JSONArray()

        for (i in 0 until deletedArray.length()) {

            val obj = deletedArray.optJSONObject(i) ?: continue
            val id = obj.optString("id", "")

            if (id.isNotBlank()) {
                deleted[id] = obj.optLong("modifiedAt", 0L)
            }
        }

        return SyncSnapshot(
            items = items,
            deleted = deleted
        )
    }
}