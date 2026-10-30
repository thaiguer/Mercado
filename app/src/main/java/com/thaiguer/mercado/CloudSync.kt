package com.thaiguer.mercado

import com.thaiguer.mercado.core.MercadoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class CloudConfig(
    val apiUrl: String,
    val binId: String,
    val accessKey: String
)

data class SyncSnapshot(
    val items: List<MercadoItem> = emptyList(),
    val deleted: Map<String, Long> = emptyMap()
)

object CloudSync {

    private fun endpoint(
        config: CloudConfig,
        latest: Boolean
    ): URL {
        require(config.apiUrl.startsWith("https://")) {
            "API URL must start with https://"
        }

        require(config.binId.isNotBlank()) {
            "Bin ID is empty."
        }

        val base = config.apiUrl.trim().trimEnd('/')
        val suffix = if (latest) "/latest" else ""

        return URL("$base/b/${config.binId.trim()}$suffix")
    }

    suspend fun download(
        config: CloudConfig
    ): SyncSnapshot {
        return withContext(Dispatchers.IO) {

            require(config.accessKey.isNotBlank()) {
                "Access Key is empty."
            }

            val connection =
                endpoint(config, true)
                    .openConnection() as HttpURLConnection

            try {
                connection.requestMethod = "GET"

                connection.setRequestProperty(
                    "X-Access-Key",
                    config.accessKey
                )

                connection.setRequestProperty(
                    "X-Bin-Meta",
                    "false"
                )

                connection.connectTimeout = 10000
                connection.readTimeout = 10000

                val status = connection.responseCode

                val stream = if (status in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }

                val response = stream
                    ?.bufferedReader()
                    ?.use { it.readText() }
                    ?: ""

                if (status !in 200..299) {
                    throw Exception(
                        "Download failed: HTTP $status. $response"
                    )
                }

                parseSnapshot(JSONObject(response))

            } finally {
                connection.disconnect()
            }
        }
    }

    suspend fun upload(
        snapshot: SyncSnapshot,
        config: CloudConfig
    ) {
        withContext(Dispatchers.IO) {

            require(config.accessKey.isNotBlank()) {
                "Access Key is empty."
            }

            val connection =
                endpoint(config, false)
                    .openConnection() as HttpURLConnection

            try {
                connection.requestMethod = "PUT"
                connection.doOutput = true

                connection.setRequestProperty(
                    "Content-Type",
                    "application/json"
                )

                connection.setRequestProperty(
                    "X-Access-Key",
                    config.accessKey
                )

                connection.connectTimeout = 10000
                connection.readTimeout = 10000

                connection.outputStream.bufferedWriter().use {
                    it.write(snapshotToJson(snapshot).toString())
                }

                val status = connection.responseCode

                if (status !in 200..299) {
                    val error = connection.errorStream
                        ?.bufferedReader()
                        ?.use { it.readText() }

                    throw Exception(
                        "Upload failed: HTTP $status. $error"
                    )
                }

            } finally {
                connection.disconnect()
            }
        }
    }

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

    fun parseSnapshot(
        json: JSONObject
    ): SyncSnapshot {

        val data = if (json.opt("record") is JSONObject) {
            json.getJSONObject("record")
        } else {
            json
        }

        val items = mutableListOf<MercadoItem>()
        val deleted = mutableMapOf<String, Long>()

        val itemsArray =
            data.optJSONArray("items") ?: JSONArray()

        for (i in 0 until itemsArray.length()) {

            val obj = itemsArray.getJSONObject(i)

            items.add(
                MercadoItem(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    ok = obj.optBoolean("ok", false),
                    count = obj.optInt("count", 1),
                    price = obj.optDouble("price", 0.0),
                    modifiedAt = obj.optLong("modifiedAt", 0L)
                )
            )
        }

        val deletedArray =
            data.optJSONArray("deleted") ?: JSONArray()

        for (i in 0 until deletedArray.length()) {

            val obj = deletedArray.getJSONObject(i)

            deleted[obj.getString("id")] =
                obj.optLong("modifiedAt", 0L)
        }

        return SyncSnapshot(
            items = items,
            deleted = deleted
        )
    }
}