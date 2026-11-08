package com.thaiguer.mercado.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.thaiguer.mercado.CloudConfig
import com.thaiguer.mercado.SyncSnapshot
import com.thaiguer.mercado.core.MercadoItem
import com.thaiguer.mercado.data.MercadoRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import java.util.UUID

data class MercadoUiState(
    val items: List<MercadoItem> = emptyList(),
    val deleted: Map<String, Long> = emptyMap(),
    val cloudConfig: CloudConfig = CloudConfig(
        repositoryUrl = "",
        token = "",
        branch = "main",
        filePath = "data/mercado.json"
    ),
    val syncEnabled: Boolean = false,
    val syncMessage: String = "GitHub sync is disabled.",
    val syncBusy: Boolean = false
)

class MercadoViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val repository = MercadoRepository(application)

    private val _uiState = MutableStateFlow(
        createInitialState()
    )

    val uiState: StateFlow<MercadoUiState> =
        _uiState.asStateFlow()

    private val syncMutex = Mutex()

    private var debounceJob: Job? = null
    private var retryJob: Job? = null

    private var retryAttempt = 0
    private var syncPending = false
    private var lastSyncAttemptAt = 0L

    private val retryDelays = listOf(
        5L,
        15L,
        30L,
        60L,
        120L
    )

    private fun createInitialState(): MercadoUiState {
        val snapshot = repository.loadSnapshot()

        return MercadoUiState(
            items = snapshot.items,
            deleted = snapshot.deleted,
            cloudConfig = repository.loadCloudConfig(),
            syncEnabled = repository.loadSyncEnabled(),
            syncMessage = if (repository.loadSyncEnabled()) {
                "Ready to synchronize with GitHub."
            } else {
                "GitHub sync is disabled."
            }
        )
    }

    // ---------------------------------------------------------
    // Shopping list
    // ---------------------------------------------------------

    fun addItem(item: MercadoItem) {
        val now = System.currentTimeMillis()

        val newItem = item.copy(
            id = UUID.randomUUID().toString(),
            modifiedAt = now
        )

        updateList { current ->
            current.copy(
                items = current.items + newItem
            )
        }
    }

    fun updateItem(updatedItem: MercadoItem) {
        val now = System.currentTimeMillis()

        updateList { current ->
            current.copy(
                items = current.items.map { item ->
                    if (item.id == updatedItem.id) {
                        updatedItem.copy(
                            modifiedAt = now
                        )
                    } else {
                        item
                    }
                }
            )
        }
    }

    fun removeItem(id: String) {
        val now = System.currentTimeMillis()

        updateList { current ->
            current.copy(
                items = current.items.filterNot {
                    it.id == id
                },
                deleted = current.deleted + (id to now)
            )
        }
    }

    fun clearSelected() {
        val now = System.currentTimeMillis()

        updateList { current ->
            current.copy(
                items = current.items.map { item ->
                    if (item.ok) {
                        item.copy(
                            ok = false,
                            modifiedAt = now
                        )
                    } else {
                        item
                    }
                }
            )
        }
    }

    fun removeSelected() {
        val now = System.currentTimeMillis()

        updateList { current ->
            val selectedIds = current.items
                .filter { it.ok }
                .map { it.id }

            val newDeleted = current.deleted +
                    selectedIds.associateWith { now }

            current.copy(
                items = current.items.filterNot {
                    it.ok
                },
                deleted = newDeleted
            )
        }
    }

    fun clearQuantity() {
        val now = System.currentTimeMillis()

        updateList { current ->
            current.copy(
                items = current.items.map {
                    it.copy(
                        count = 0,
                        modifiedAt = now
                    )
                }
            )
        }
    }

    fun clearPrices() {
        val now = System.currentTimeMillis()

        updateList { current ->
            current.copy(
                items = current.items.map {
                    it.copy(
                        price = 0.0,
                        modifiedAt = now
                    )
                }
            )
        }
    }

    fun clearAll() {
        val now = System.currentTimeMillis()

        updateList { current ->
            val newDeleted = current.deleted +
                    current.items.associate {
                        it.id to now
                    }

            current.copy(
                items = emptyList(),
                deleted = newDeleted
            )
        }
    }

    private fun updateList(
        transform: (MercadoUiState) -> MercadoUiState
    ) {
        val newState = transform(_uiState.value)

        _uiState.value = newState

        repository.saveSnapshot(
            SyncSnapshot(
                items = newState.items,
                deleted = newState.deleted
            )
        )

        scheduleDebouncedSync()
    }

    // ---------------------------------------------------------
    // GitHub configuration
    // ---------------------------------------------------------

    fun saveCloudConfig(
        repositoryUrl: String,
        token: String,
        branch: String,
        filePath: String
    ) {
        val config = CloudConfig(
            repositoryUrl = repositoryUrl.trim(),
            token = token.trim(),
            branch = branch.trim(),
            filePath = filePath.trim().trim('/')
        )

        repository.saveCloudConfig(config)

        _uiState.value = _uiState.value.copy(
            cloudConfig = config,
            syncMessage = "GitHub settings saved."
        )

        if (_uiState.value.syncEnabled) {
            requestSync(force = true)
        }
    }

    fun setSyncEnabled(enabled: Boolean) {
        repository.saveSyncEnabled(enabled)

        _uiState.value = _uiState.value.copy(
            syncEnabled = enabled,
            syncMessage = if (enabled) {
                "GitHub synchronization enabled."
            } else {
                "GitHub sync is disabled. Your list is saved locally."
            }
        )

        if (!enabled) {
            retryJob?.cancel()
            retryJob = null
        } else {
            requestSync(force = true)
        }
    }

    // ---------------------------------------------------------
    // Synchronization
    // ---------------------------------------------------------

    fun syncNow() {
        requestSync(force = true)
    }

    fun onAppResumed() {
        requestSync(checkResumeInterval = true)
    }

    private fun scheduleDebouncedSync() {
        debounceJob?.cancel()

        if (!_uiState.value.syncEnabled) {
            return
        }

        debounceJob = viewModelScope.launch {
            delay(5000)
            requestSync()
        }
    }

    private fun requestSync(
        force: Boolean = false,
        checkResumeInterval: Boolean = false
    ) {
        val current = _uiState.value

        if (!current.syncEnabled) {
            return
        }

        val config = current.cloudConfig

        if (
            config.repositoryUrl.isBlank() ||
            config.token.isBlank() ||
            config.branch.isBlank() ||
            config.filePath.isBlank()
        ) {
            _uiState.value = current.copy(
                syncMessage = "Enter the GitHub repository URL, token, branch and file path."
            )
            return
        }

        val now = System.currentTimeMillis()

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

        if (!syncMutex.tryLock()) {
            syncPending = true
            return
        }

        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(
                    syncBusy = true,
                    syncMessage = "Synchronizing with GitHub..."
                )

                syncPending = false
                lastSyncAttemptAt = System.currentTimeMillis()

                val currentState = _uiState.value

                val localSnapshot = SyncSnapshot(
                    items = currentState.items,
                    deleted = currentState.deleted
                )

                try {
                    val merged = repository.synchronize(
                        localSnapshot = localSnapshot,
                        config = currentState.cloudConfig
                    )

                    val newState = _uiState.value.copy(
                        items = merged.items,
                        deleted = merged.deleted,
                        syncMessage = "GitHub synchronized successfully."
                    )

                    _uiState.value = newState

                    repository.saveSnapshot(merged)

                    retryAttempt = 0
                    retryJob?.cancel()
                    retryJob = null

                } catch (e: Exception) {
                    _uiState.value = _uiState.value.copy(
                        syncMessage =
                            "GitHub sync failed: ${e.message ?: "Unknown error"}. " +
                                    "Your local list is still available."
                    )

                    scheduleRetry()
                }

            } finally {
                _uiState.value = _uiState.value.copy(
                    syncBusy = false
                )

                syncMutex.unlock()

                if (syncPending && _uiState.value.syncEnabled) {
                    syncPending = false
                    requestSync()
                }
            }
        }
    }

    private fun scheduleRetry() {
        val index = minOf(
            retryAttempt,
            retryDelays.lastIndex
        )

        val delaySeconds = retryDelays[index]

        retryAttempt++

        retryJob?.cancel()

        retryJob = viewModelScope.launch {
            delay(delaySeconds * 1000L)

            if (_uiState.value.syncEnabled) {
                requestSync()
            }
        }
    }

    override fun onCleared() {
        debounceJob?.cancel()
        retryJob?.cancel()
        super.onCleared()
    }
}