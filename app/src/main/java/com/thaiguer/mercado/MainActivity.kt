package com.thaiguer.mercado

import android.os.Bundle
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import com.thaiguer.mercado.core.MercadoItem
import com.thaiguer.mercado.ui.theme.MercadoTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {
            MercadoTheme {
                MercadoApp()
            }
        }
    }
}

enum class AppDestinations(
    val label: String,
    val icon: String
) {
    HOME("Shopping List", "🛒"),
    MANAGE("Management", "⚙️")
}

private fun saveSnapshot(
    context: Context,
    snapshot: SyncSnapshot
) {
    context.getSharedPreferences(
        "MercadoPreferences",
        Context.MODE_PRIVATE
    ).edit()
        .putString(
            "shopping_list",
            CloudSync.snapshotToJson(snapshot).toString()
        )
        .apply()
}

private fun loadSnapshot(
    context: Context
): SyncSnapshot {

    val preferences = context.getSharedPreferences(
        "MercadoPreferences",
        Context.MODE_PRIVATE
    )

    val savedData = preferences.getString(
        "shopping_list",
        null
    )

    if (savedData == null) {

        return SyncSnapshot(
            items = listOf(
                MercadoItem(
                    id = "sample-rice",
                    name = "Rice",
                    count = 2,
                    price = 10.50,
                    modifiedAt = 1
                ),
                MercadoItem(
                    id = "sample-beans",
                    name = "Beans",
                    count = 3,
                    price = 8.00,
                    modifiedAt = 1
                ),
                MercadoItem(
                    id = "sample-milk",
                    name = "Milk",
                    count = 1,
                    price = 5.50,
                    modifiedAt = 1
                ),
                MercadoItem(
                    id = "sample-bread",
                    name = "Bread",
                    count = 2,
                    price = 7.00,
                    modifiedAt = 1
                )
            )
        )
    }

    return try {

        if (savedData.trimStart().startsWith("{")) {

            CloudSync.parseSnapshot(
                JSONObject(savedData)
            )

        } else {

            // Compatibility with the previous JSON array format.

            val array = JSONArray(savedData)
            val items = mutableListOf<MercadoItem>()

            for (i in 0 until array.length()) {

                val obj = array.getJSONObject(i)

                items.add(
                    MercadoItem(
                        id = "legacy-${obj.getInt("id")}",
                        name = obj.getString("name"),
                        ok = obj.optBoolean("ok", false),
                        count = obj.optInt("count", 1),
                        price = obj.optDouble("price", 0.0),
                        modifiedAt = 1
                    )
                )
            }

            SyncSnapshot(items = items)
        }

    } catch (e: Exception) {
        SyncSnapshot()
    }
}

@Composable
fun MercadoApp() {

    val context = LocalContext.current

    val preferences = remember {
        context.getSharedPreferences(
            "MercadoPreferences",
            Context.MODE_PRIVATE
        )
    }

    val initialSnapshot = remember {
        loadSnapshot(context)
    }

    var currentDestination by rememberSaveable {
        mutableStateOf(AppDestinations.HOME)
    }

    var items by remember {
        mutableStateOf(initialSnapshot.items)
    }

    var deleted by remember {
        mutableStateOf(initialSnapshot.deleted)
    }

    var apiUrl by remember {
        mutableStateOf(
            preferences.getString(
                "api_url",
                "https://api.jsonbin.io/v3"
            ) ?: "https://api.jsonbin.io/v3"
        )
    }

    var binId by remember {
        mutableStateOf(
            preferences.getString("bin_id", "") ?: ""
        )
    }

    var accessKey by remember {
        mutableStateOf(
            preferences.getString(
                "jsonbin_access_key",
                ""
            ) ?: ""
        )
    }

    var syncEnabled by remember {
        mutableStateOf(
            preferences.getBoolean("sync_enabled", false)
        )
    }

    var syncMessage by remember {
        mutableStateOf("Cloud sync is disabled.")
    }

    var syncBusy by remember {
        mutableStateOf(false)
    }

    val scope = rememberCoroutineScope()
    val syncMutex = remember { Mutex() }

    // Save shopping list locally.

    LaunchedEffect(items, deleted) {

        saveSnapshot(
            context,
            SyncSnapshot(items, deleted)
        )
    }

    // Save cloud synchronization preference.

    LaunchedEffect(syncEnabled) {

        preferences.edit()
            .putBoolean("sync_enabled", syncEnabled)
            .apply()

        if (!syncEnabled) {
            syncMessage =
                "Cloud sync is disabled. Your list is saved locally."
        }
    }

    // Save API settings whenever they change.

    LaunchedEffect(apiUrl, binId, accessKey) {

        preferences.edit()
            .putString("api_url", apiUrl)
            .putString("bin_id", binId)
            .putString("jsonbin_access_key", accessKey)
            .apply()
    }

    fun saveCloudConfig(
        newApiUrl: String,
        newBinId: String,
        newAccessKey: String
    ) {
        apiUrl = newApiUrl.trim()
        binId = newBinId.trim()
        accessKey = newAccessKey.trim()

        syncMessage = "Cloud settings saved."
    }

    // Synchronization function.

    fun syncNow() {

        if (!syncEnabled) return

        if (
            apiUrl.isBlank() ||
            binId.isBlank() ||
            accessKey.isBlank()
        ) {
            syncMessage =
                "Enter the API URL, Bin ID and Access Key first."
            return
        }

        scope.launch {

            syncMutex.withLock {

                syncBusy = true
                syncMessage = "Synchronizing..."

                try {

                    val config = CloudConfig(
                        apiUrl = apiUrl,
                        binId = binId,
                        accessKey = accessKey
                    )

                    // Download remote list.

                    val remote = CloudSync.download(config)

                    // Merge local and remote data.

                    val local = SyncSnapshot(
                        items = items,
                        deleted = deleted
                    )

                    val merged = CloudSync.merge(
                        local,
                        remote
                    )

                    // Update local state.

                    items = merged.items
                    deleted = merged.deleted

                    // Upload only if necessary.

                    if (merged != remote) {
                        CloudSync.upload(merged, config)
                    }

                    syncMessage =
                        "Cloud synchronized successfully."

                } catch (e: Exception) {

                    syncMessage =
                        "Cloud sync failed: ${e.message ?: "Unknown error"}. " +
                                "Your local list is still available."

                } finally {

                    syncBusy = false
                }
            }
        }
    }

    // Sync when enabled or when connection settings change.

    LaunchedEffect(
        syncEnabled,
        apiUrl,
        binId,
        accessKey
    ) {
        if (
            syncEnabled &&
            apiUrl.isNotBlank() &&
            binId.isNotBlank() &&
            accessKey.isNotBlank()
        ) {
            syncNow()
        }
    }

    // Wait five seconds after list changes before synchronizing.

    LaunchedEffect(items, deleted) {

        if (syncEnabled) {
            delay(5000)
            syncNow()
        }
    }

    NavigationSuiteScaffold(
        navigationSuiteItems = {

            AppDestinations.entries.forEach { destination ->

                item(
                    icon = {
                        Text(destination.icon)
                    },

                    label = {
                        Text(destination.label)
                    },

                    selected = currentDestination == destination,

                    onClick = {
                        currentDestination = destination
                    }
                )
            }
        }

    ) {

        when (currentDestination) {

            AppDestinations.HOME -> {

                HomeScreen(
                    products = items,

                    onAdd = { item ->

                        items = items + item.copy(
                            id = UUID.randomUUID().toString(),
                            modifiedAt = System.currentTimeMillis()
                        )
                    },

                    onUpdate = { updated ->

                        items = items.map {

                            if (it.id == updated.id) {
                                updated.copy(
                                    modifiedAt = System.currentTimeMillis()
                                )
                            } else {
                                it
                            }
                        }
                    },

                    onRemove = { id ->

                        deleted = deleted + (
                                id to System.currentTimeMillis()
                                )

                        items = items.filterNot {
                            it.id == id
                        }
                    }
                )
            }

            AppDestinations.MANAGE -> {

                ManageScreen(
                    items = items,

                    onClearSelected = {

                        val now = System.currentTimeMillis()
                        val selected = items.filter { it.ok }

                        deleted = deleted + selected.associate {
                            it.id to now
                        }

                        items = items.filterNot { it.ok }
                    },

                    onClearQuantity = {

                        val now = System.currentTimeMillis()

                        items = items.map {
                            it.copy(
                                count = 0,
                                modifiedAt = now
                            )
                        }
                    },

                    onClearPrices = {

                        val now = System.currentTimeMillis()

                        items = items.map {
                            it.copy(
                                price = 0.0,
                                modifiedAt = now
                            )
                        }
                    },

                    onClearAll = {

                        val now = System.currentTimeMillis()

                        deleted = deleted + items.associate {
                            it.id to now
                        }

                        items = emptyList()
                    },

                    apiUrl = apiUrl,
                    binId = binId,
                    accessKey = accessKey,

                    onSaveCloudConfig = { url, id, key ->
                        saveCloudConfig(url, id, key)
                    },

                    syncEnabled = syncEnabled,

                    onSyncEnabledChange = {
                        syncEnabled = it
                    },

                    syncMessage = syncMessage,
                    syncBusy = syncBusy,

                    onSyncNow = {
                        syncNow()
                    }
                )
            }
        }
    }
}