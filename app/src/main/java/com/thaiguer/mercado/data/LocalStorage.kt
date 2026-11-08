package com.thaiguer.mercado.data

import android.content.Context
import com.thaiguer.mercado.CloudConfig
import com.thaiguer.mercado.CloudSync
import com.thaiguer.mercado.SyncSnapshot
import com.thaiguer.mercado.core.MercadoItem as CoreMercadoItem
import org.json.JSONArray
import org.json.JSONObject

class LocalStorage(context: Context) {

    private val preferences = context.applicationContext
        .getSharedPreferences(
            "MercadoPreferences",
            Context.MODE_PRIVATE
        )

    fun loadSnapshot(): SyncSnapshot {
        val savedData = preferences.getString(
            "shopping_list",
            null
        )

        if (savedData == null) {
            return createInitialSnapshot()
        }

        return try {
            if (savedData.trimStart().startsWith("{")) {
                CloudSync.parseSnapshot(
                    JSONObject(savedData)
                )
            } else {
                // Compatibility with the previous JSON array format.
                val array = JSONArray(savedData)
                val items = mutableListOf<CoreMercadoItem>()

                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)

                    items.add(
                        CoreMercadoItem(
                            id = "legacy-${obj.getInt("id")}",
                            name = obj.getString("name"),
                            ok = obj.optBoolean("ok", false),
                            count = obj.optInt("count", 1),
                            price = obj.optDouble("price", 0.0),
                            modifiedAt = 1L
                        )
                    )
                }

                SyncSnapshot(items = items)
            }
        } catch (_: Exception) {
            SyncSnapshot()
        }
    }

    fun saveSnapshot(snapshot: SyncSnapshot) {
        preferences.edit()
            .putString(
                "shopping_list",
                CloudSync.snapshotToJson(snapshot).toString()
            )
            .apply()
    }

    fun loadCloudConfig(): CloudConfig {
        return CloudConfig(
            repositoryUrl = preferences.getString(
                "github_repository_url",
                ""
            ) ?: "",
            token = preferences.getString(
                "github_token",
                ""
            ) ?: "",
            branch = preferences.getString(
                "github_branch",
                "main"
            ) ?: "main",
            filePath = preferences.getString(
                "github_file_path",
                "data/mercado.json"
            ) ?: "data/mercado.json"
        )
    }

    fun saveCloudConfig(config: CloudConfig) {
        preferences.edit()
            .putString(
                "github_repository_url",
                config.repositoryUrl
            )
            .putString(
                "github_token",
                config.token
            )
            .putString(
                "github_branch",
                config.branch
            )
            .putString(
                "github_file_path",
                config.filePath
            )
            .apply()
    }

    fun loadSyncEnabled(): Boolean {
        return preferences.getBoolean(
            "sync_enabled",
            false
        )
    }

    fun saveSyncEnabled(enabled: Boolean) {
        preferences.edit()
            .putBoolean("sync_enabled", enabled)
            .apply()
    }

    private fun createInitialSnapshot(): SyncSnapshot {
        return SyncSnapshot(
            items = listOf(
                CoreMercadoItem(
                    id = "sample-rice",
                    name = "Rice",
                    count = 2,
                    price = 10.50,
                    modifiedAt = 1L
                ),
                CoreMercadoItem(
                    id = "sample-beans",
                    name = "Beans",
                    count = 3,
                    price = 8.00,
                    modifiedAt = 1L
                ),
                CoreMercadoItem(
                    id = "sample-milk",
                    name = "Milk",
                    count = 1,
                    price = 5.50,
                    modifiedAt = 1L
                ),
                CoreMercadoItem(
                    id = "sample-bread",
                    name = "Bread",
                    count = 2,
                    price = 7.00,
                    modifiedAt = 1L
                )
            )
        )
    }
}