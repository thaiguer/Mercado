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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.thaiguer.mercado.core.MercadoItem
import com.thaiguer.mercado.ui.theme.MercadoTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
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

    val lifecycleOwner = LocalLifecycleOwner.current

    var currentDestination by rememberSaveable {
        mutableStateOf(AppDestinations.HOME)
    }

    var items by remember {
        mutableStateOf(initialSnapshot.items)
    }

    var deleted by remember {
        mutableStateOf(initialSnapshot.deleted)
    }

    // GitHub repository settings.

    var repositoryUrl by remember {
        mutableStateOf(
            preferences.getString(
                "github_repository_url",
                ""
            ) ?: ""
        )
    }

    var githubToken by remember {
        mutableStateOf(
            preferences.getString(
                "github_token",
                ""
            ) ?: ""
        )
    }

    var githubBranch by remember {
        mutableStateOf(
            preferences.getString(
                "github_branch",
                "main"
            ) ?: "main"
        )
    }

    var githubFilePath by remember {
        mutableStateOf(
            preferences.getString(
                "github_file_path",
                "data/mercado.json"
            ) ?: "data/mercado.json"
        )
    }

    var syncEnabled by remember {
        mutableStateOf(
            preferences.getBoolean(
                "sync_enabled",
                false
            )
        )
    }

    var syncMessage by remember {
        mutableStateOf(
            "GitHub sync is disabled."
        )
    }

    var syncBusy by remember {
        mutableStateOf(false)
    }

    // Synchronization control.

    var syncPending by remember {
        mutableStateOf(false)
    }

    var lastSyncAttemptAt by remember {
        mutableLongStateOf(0L)
    }

    var retryAttempt by remember {
        mutableIntStateOf(0)
    }

    var retryJob by remember {
        mutableStateOf<Job?>(null)
    }

    var initialListObserved by remember {
        mutableStateOf(false)
    }

    var suppressNextDebounce by remember {
        mutableStateOf(false)
    }

    val scope = rememberCoroutineScope()

    val syncMutex = remember {
        Mutex()
    }

    // Save shopping list locally.

    LaunchedEffect(items, deleted) {

        saveSnapshot(
            context,
            SyncSnapshot(
                items = items,
                deleted = deleted
            )
        )
    }

    // Save GitHub settings locally.

    LaunchedEffect(
        repositoryUrl,
        githubToken,
        githubBranch,
        githubFilePath
    ) {

        preferences.edit()
            .putString(
                "github_repository_url",
                repositoryUrl
            )
            .putString(
                "github_token",
                githubToken
            )
            .putString(
                "github_branch",
                githubBranch
            )
            .putString(
                "github_file_path",
                githubFilePath
            )
            .apply()
    }

    // Save synchronization preference.

    LaunchedEffect(syncEnabled) {

        preferences.edit()
            .putBoolean(
                "sync_enabled",
                syncEnabled
            )
            .apply()

        if (!syncEnabled) {

            syncMessage =
                "GitHub sync is disabled. Your list is saved locally."
        }
    }

    // Save GitHub configuration.

    fun saveCloudConfig(
        newRepositoryUrl: String,
        newToken: String,
        newBranch: String,
        newFilePath: String
    ) {

        repositoryUrl = newRepositoryUrl.trim()

        githubToken = newToken.trim()

        githubBranch = newBranch.trim()

        githubFilePath = newFilePath
            .trim()
            .trim('/')

        syncMessage = "GitHub settings saved."
    }

    // Synchronization function.

    fun requestSync(
        force: Boolean = false,
        checkResumeInterval: Boolean = false
    ) {

        if (!syncEnabled) return

        if (
            repositoryUrl.isBlank() ||
            githubToken.isBlank() ||
            githubBranch.isBlank() ||
            githubFilePath.isBlank()
        ) {

            syncMessage =
                "Enter the GitHub repository URL, token, branch and file path."

            return
        }

        val now = System.currentTimeMillis()

        // Avoid unnecessary synchronization when returning
        // to the app too frequently.

        if (checkResumeInterval) {

            if (lastSyncAttemptAt == 0L) {
                return
            }

            if (now - lastSyncAttemptAt < 60_000L) {
                return
            }
        }

        if (force) {

            retryJob?.cancel()
            retryJob = null

            retryAttempt = 0
        }

        // Prevent simultaneous synchronization.

        if (!syncMutex.tryLock()) {

            syncPending = true

            return
        }

        scope.launch {

            try {

                syncBusy = true

                syncPending = false

                lastSyncAttemptAt =
                    System.currentTimeMillis()

                syncMessage = "Synchronizing with GitHub..."

                try {

                    val config = CloudConfig(
                        repositoryUrl = repositoryUrl,
                        token = githubToken,
                        branch = githubBranch,
                        filePath = githubFilePath
                    )

                    // Download remote data and SHA.

                    val remote = CloudSync.downloadWithMetadata(
                        config
                    )

                    // Capture the latest local data.

                    val local = SyncSnapshot(
                        items = items,
                        deleted = deleted
                    )

                    // Merge local and remote data.

                    val merged = CloudSync.merge(
                        local,
                        remote.snapshot
                    )

                    // Update local state only when necessary.

                    if (merged != local) {

                        suppressNextDebounce = true

                        items = merged.items
                        deleted = merged.deleted
                    }

                    // Upload only if remote data is different.

                    if (merged != remote.snapshot) {

                        CloudSync.upload(
                            snapshot = merged,
                            config = config,
                            existingSha = remote.sha
                        )
                    }

                    syncMessage =
                        "GitHub synchronized successfully."

                    // Reset retry counter after success.

                    retryAttempt = 0

                    retryJob?.cancel()
                    retryJob = null

                } catch (e: Exception) {

                    syncMessage =
                        "GitHub sync failed: " +
                                "${e.message ?: "Unknown error"}. " +
                                "Your local list is still available."

                    // Progressive retry intervals.

                    val retryDelays = listOf(
                        5L,
                        15L,
                        30L,
                        60L,
                        120L
                    )

                    val index = minOf(
                        retryAttempt,
                        retryDelays.lastIndex
                    )

                    val delaySeconds = retryDelays[index]

                    retryAttempt++

                    retryJob?.cancel()

                    retryJob = scope.launch {

                        delay(delaySeconds * 1000L)

                        if (syncEnabled) {
                            requestSync()
                        }
                    }

                }

            } finally {

                syncBusy = false

                syncMutex.unlock()

                // Process requests that arrived while
                // another synchronization was running.

                if (syncPending && syncEnabled) {

                    syncPending = false

                    requestSync()
                }
            }
        }
    }

    // Synchronize when settings change or sync is enabled.

    LaunchedEffect(
        syncEnabled,
        repositoryUrl,
        githubToken,
        githubBranch,
        githubFilePath
    ) {

        if (
            syncEnabled &&
            repositoryUrl.isNotBlank() &&
            githubToken.isNotBlank() &&
            githubBranch.isNotBlank() &&
            githubFilePath.isNotBlank()
        ) {

            requestSync(force = true)
        }
    }

    // Cancel retry attempts when synchronization is disabled.

    LaunchedEffect(syncEnabled) {

        if (!syncEnabled) {

            retryJob?.cancel()
            retryJob = null

            syncMessage =
                "GitHub sync is disabled. Your list is saved locally."
        }
    }

    // Debounce: wait five seconds after list changes.

    LaunchedEffect(items, deleted) {

        // Ignore the initial list composition.

        if (!initialListObserved) {

            initialListObserved = true

            return@LaunchedEffect
        }

        // Ignore changes applied from GitHub.

        if (suppressNextDebounce) {

            suppressNextDebounce = false

            return@LaunchedEffect
        }

        if (syncEnabled) {

            delay(5000)

            requestSync()
        }
    }

    // Synchronize when returning to the foreground.
    // Only if at least one minute has passed since
    // the previous synchronization attempt.

    DisposableEffect(lifecycleOwner) {

        val observer = LifecycleEventObserver {
                _, event ->

            if (event == Lifecycle.Event.ON_RESUME) {

                requestSync(
                    checkResumeInterval = true
                )
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
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

                            modifiedAt =
                                System.currentTimeMillis()
                        )
                    },

                    onUpdate = { updated ->

                        items = items.map {

                            if (it.id == updated.id) {

                                updated.copy(
                                    modifiedAt =
                                        System.currentTimeMillis()
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

                        val now =
                            System.currentTimeMillis()

                        items = items.map { item ->

                            if (item.ok) {

                                item.copy(
                                    ok = false,
                                    modifiedAt = now
                                )

                            } else {

                                item
                            }
                        }
                    },

                    onClearQuantity = {

                        val now =
                            System.currentTimeMillis()

                        items = items.map {

                            it.copy(
                                count = 0,
                                modifiedAt = now
                            )
                        }
                    },

                    onClearPrices = {

                        val now =
                            System.currentTimeMillis()

                        items = items.map {

                            it.copy(
                                price = 0.0,
                                modifiedAt = now
                            )
                        }
                    },

                    onClearAll = {

                        val now =
                            System.currentTimeMillis()

                        deleted = deleted + items.associate {

                            it.id to now
                        }

                        items = emptyList()
                    },

                    // GitHub configuration.

                    repositoryUrl = repositoryUrl,

                    token = githubToken,

                    branch = githubBranch,

                    filePath = githubFilePath,

                    onSaveCloudConfig = {
                            url,
                            token,
                            branch,
                            filePath ->

                        saveCloudConfig(
                            url,
                            token,
                            branch,
                            filePath
                        )
                    },

                    // Synchronization controls.

                    syncEnabled = syncEnabled,

                    onSyncEnabledChange = {
                        syncEnabled = it
                    },

                    syncMessage = syncMessage,

                    syncBusy = syncBusy,

                    onSyncNow = {
                        requestSync(force = true)
                    }
                )
            }
        }
    }
}