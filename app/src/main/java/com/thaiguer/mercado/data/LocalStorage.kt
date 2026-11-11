package com.thaiguer.mercado.data

import android.content.Context
import com.thaiguer.mercado.FirebaseConfig
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

    // ---------------------------------------------------------
    // Shopping list
    // ---------------------------------------------------------

    fun loadSnapshot(): SyncSnapshot {
        val savedData = preferences.getString("shopping_list", null)
            ?: return createInitialSnapshot()

        return try {
            if (savedData.trimStart().startsWith("{")) {
                CloudSync.parseSnapshot(JSONObject(savedData))
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

    // ---------------------------------------------------------
    // Firebase configuration
    // ---------------------------------------------------------

    fun loadFirebaseConfig(): FirebaseConfig {
        return FirebaseConfig(
            apiKey = preferences.getString(
                "firebase_api_key", ""
            ) ?: "",

            projectId = preferences.getString(
                "firebase_project_id", ""
            ) ?: "",

            applicationId = preferences.getString(
                "firebase_application_id", ""
            ) ?: "",

            databaseUrl = preferences.getString(
                "firebase_database_url", ""
            ) ?: "",

            listId = preferences.getString(
                "firebase_list_id", "mercado"
            ) ?: "mercado"
        )
    }

    fun saveFirebaseConfig(config: FirebaseConfig) {
        preferences.edit()
            .putString("firebase_api_key", config.apiKey)
            .putString("firebase_project_id", config.projectId)
            .putString("firebase_application_id", config.applicationId)
            .putString("firebase_database_url", config.databaseUrl)
            .putString("firebase_list_id", config.listId)
            .apply()
    }

    // ---------------------------------------------------------
    // Authentication
    // ---------------------------------------------------------

    fun loadEmail(): String {
        return preferences.getString("firebase_email", "") ?: ""
    }

    fun saveEmail(email: String) {
        preferences.edit()
            .putString("firebase_email", email)
            .apply()
    }

    // ---------------------------------------------------------
    // Synchronization preference
    // ---------------------------------------------------------

    fun loadSyncEnabled(): Boolean {
        return preferences.getBoolean("sync_enabled", false)
    }

    fun saveSyncEnabled(enabled: Boolean) {
        preferences.edit()
            .putBoolean("sync_enabled", enabled)
            .apply()
    }

    // ---------------------------------------------------------
    // Initial shopping list
    // ---------------------------------------------------------

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