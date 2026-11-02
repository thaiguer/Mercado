package com.thaiguer.mercado

import android.util.Base64
import com.thaiguer.mercado.core.MercadoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class CloudConfig(
    val repositoryUrl: String,
    val token: String,
    val branch: String = "main",
    val filePath: String = "data/mercado.json"
)

data class SyncSnapshot(
    val items: List<MercadoItem> = emptyList(),
    val deleted: Map<String, Long> = emptyMap()
)

data class RemoteSnapshot(
    val snapshot: SyncSnapshot,
    val sha: String?
)

object CloudSync {

    private data class GitHubFile(
        val content: JSONObject,
        val sha: String?
    )

    private data class Repository(
        val owner: String,
        val name: String
    )

    private fun repository(config: CloudConfig): Repository {

        val url = config.repositoryUrl
            .trim()
            .trimEnd('/')

        val regex = Regex(
            """^https://github\.com/([^/]+)/([^/]+?)(?:\.git)?$"""
        )

        val match = regex.matchEntire(url)
            ?: throw IllegalArgumentException(
                "Invalid GitHub repository URL."
            )

        return Repository(
            owner = match.groupValues[1],
            name = match.groupValues[2]
        )
    }

    private fun endpoint(config: CloudConfig): URL {

        require(config.token.isNotBlank()) {
            "GitHub token is empty."
        }

        require(config.branch.isNotBlank()) {
            "Branch is empty."
        }

        require(config.filePath.isNotBlank()) {
            "File path is empty."
        }

        val repo = repository(config)

        val encodedPath = config.filePath
            .trim('/')
            .split("/")
            .joinToString("/") {
                URLEncoder.encode(it, "UTF-8")
                    .replace("+", "%20")
            }

        val branch = URLEncoder.encode(
            config.branch.trim(),
            "UTF-8"
        )

        return URL(
            "https://api.github.com/repos/" +
                    "${repo.owner}/${repo.name}/contents/" +
                    "$encodedPath?ref=$branch"
        )
    }

    private fun configureConnection(
        connection: HttpURLConnection,
        config: CloudConfig
    ) {

        connection.setRequestProperty(
            "Authorization",
            "Bearer ${config.token}"
        )

        connection.setRequestProperty(
            "Accept",
            "application/vnd.github+json"
        )

        connection.setRequestProperty(
            "X-GitHub-Api-Version",
            "2022-11-28"
        )

        connection.connectTimeout = 15000
        connection.readTimeout = 15000
    }

    private fun readResponse(
        connection: HttpURLConnection
    ): String {

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
                "GitHub request failed: HTTP $status. $response"
            )
        }

        return response
    }

    private suspend fun downloadGitHubFile(
        config: CloudConfig
    ): GitHubFile? {

        return withContext(Dispatchers.IO) {

            val connection = endpoint(config)
                .openConnection() as HttpURLConnection

            try {

                connection.requestMethod = "GET"

                configureConnection(
                    connection,
                    config
                )

                val status = connection.responseCode

                if (status == 404) {
                    return@withContext null
                }

                val response = readResponse(connection)

                val json = JSONObject(response)

                val encodedContent = json
                    .optString("content")
                    .replace("\n", "")
                    .replace("\r", "")

                val decodedBytes = Base64.decode(
                    encodedContent,
                    Base64.DEFAULT
                )

                val decodedText = String(
                    decodedBytes,
                    Charsets.UTF_8
                )

                GitHubFile(
                    content = JSONObject(decodedText),
                    sha = json.optString("sha")
                        .takeIf { it.isNotBlank() }
                )

            } finally {
                connection.disconnect()
            }
        }
    }

    suspend fun downloadWithMetadata(
        config: CloudConfig
    ): RemoteSnapshot {

        val file = downloadGitHubFile(config)

        return if (file == null) {

            RemoteSnapshot(
                snapshot = SyncSnapshot(),
                sha = null
            )

        } else {

            RemoteSnapshot(
                snapshot = parseSnapshot(file.content),
                sha = file.sha
            )
        }
    }

    suspend fun download(
        config: CloudConfig
    ): SyncSnapshot {

        return downloadWithMetadata(config).snapshot
    }

    suspend fun upload(
        snapshot: SyncSnapshot,
        config: CloudConfig,
        existingSha: String?
    ) {

        withContext(Dispatchers.IO) {

            val json = snapshotToJson(snapshot)

            val content = Base64.encodeToString(
                json.toString(2).toByteArray(Charsets.UTF_8),
                Base64.NO_WRAP
            )

            val body = JSONObject().apply {

                put(
                    "message",
                    "Update Mercado shopping list"
                )

                put("content", content)

                put("branch", config.branch)

                existingSha?.let {
                    put("sha", it)
                }
            }

            val connection = endpoint(config)
                .openConnection() as HttpURLConnection

            try {

                connection.requestMethod = "PUT"
                connection.doOutput = true

                configureConnection(
                    connection,
                    config
                )

                connection.setRequestProperty(
                    "Content-Type",
                    "application/json; charset=utf-8"
                )

                connection.outputStream
                    .bufferedWriter(Charsets.UTF_8)
                    .use {
                        it.write(body.toString())
                    }

                readResponse(connection)

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
                    modifiedAt = obj.optLong(
                        "modifiedAt",
                        0L
                    )
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