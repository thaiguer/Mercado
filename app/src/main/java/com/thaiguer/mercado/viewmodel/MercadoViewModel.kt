package com.thaiguer.mercado.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.database.ValueEventListener
import com.thaiguer.mercado.CloudSync
import com.thaiguer.mercado.FirebaseConfig
import com.thaiguer.mercado.FirebaseService
import com.thaiguer.mercado.SyncSnapshot
import com.thaiguer.mercado.core.MercadoItem
import com.thaiguer.mercado.data.MercadoRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

data class MercadoUiState(
    val items: List<MercadoItem> = emptyList(),
    val deleted: Map<String, Long> = emptyMap(),

    val firebaseConfig: FirebaseConfig = FirebaseConfig(),

    val email: String = "",
    val firebaseUserEmail: String? = null,

    val syncEnabled: Boolean = false,
    val syncMessage: String = "Firebase synchronization is disabled.",
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

    private var firebaseService: FirebaseService? = null
    private var firebaseListener: ValueEventListener? = null

    private var debounceJob: Job? = null

    private var syncInProgress = false
    private var syncPending = false
    private var pendingOnSuccess: (() -> Unit)? = null

    private fun createInitialState(): MercadoUiState {
        val snapshot = repository.loadSnapshot()
        val config = repository.loadFirebaseConfig()
        val enabled = repository.loadSyncEnabled()

        return MercadoUiState(
            items = snapshot.items,
            deleted = snapshot.deleted,
            firebaseConfig = config,
            email = repository.loadEmail(),
            syncEnabled = enabled,
            syncMessage = if (enabled) {
                "Firebase synchronization is enabled."
            } else {
                "Firebase synchronization is disabled."
            }
        )
    }

    init {
        initializeFirebase()
    }

    // ---------------------------------------------------------
    // Firebase initialization
    // ---------------------------------------------------------

    private fun initializeFirebase() {
        val config = _uiState.value.firebaseConfig

        if (!config.isValid) {
            _uiState.value = _uiState.value.copy(
                syncMessage = "Enter and save your Firebase configuration."
            )
            return
        }

        try {
            firebaseService = FirebaseService(
                getApplication(),
                config
            )

            val user = firebaseService?.currentUser()

            _uiState.value = _uiState.value.copy(
                firebaseUserEmail = user?.email
            )

            if (user != null && _uiState.value.syncEnabled) {
                synchronizeAndListen()
            } else if (user == null && _uiState.value.syncEnabled) {
                _uiState.value = _uiState.value.copy(
                    syncMessage = "Sign in to enable synchronization."
                )
            }

        } catch (e: Exception) {
            firebaseService = null

            _uiState.value = _uiState.value.copy(
                syncMessage = "Firebase initialization failed: ${e.message}"
            )
        }
    }

    fun saveFirebaseConfig(config: FirebaseConfig) {
        val cleanedConfig = config.copy(
            apiKey = config.apiKey.trim(),
            projectId = config.projectId.trim(),
            applicationId = config.applicationId.trim(),
            databaseUrl = config.databaseUrl.trim(),
            listId = config.listId.trim()
        )

        stopFirebaseListener()

        firebaseService = null

        repository.saveFirebaseConfig(cleanedConfig)

        _uiState.value = _uiState.value.copy(
            firebaseConfig = cleanedConfig,
            firebaseUserEmail = null,
            syncMessage = "Firebase settings saved."
        )

        if (!cleanedConfig.isValid) {
            _uiState.value = _uiState.value.copy(
                syncMessage = "Firebase configuration is incomplete."
            )
            return
        }

        try {
            firebaseService = FirebaseService(
                getApplication(),
                cleanedConfig
            )

            val user = firebaseService?.currentUser()

            _uiState.value = _uiState.value.copy(
                firebaseUserEmail = user?.email,
                syncMessage = if (user != null) {
                    "Firebase settings saved. Ready to synchronize."
                } else {
                    "Firebase settings saved. Sign in to continue."
                }
            )

            if (user != null && _uiState.value.syncEnabled) {
                synchronizeAndListen()
            }

        } catch (e: Exception) {
            firebaseService = null

            _uiState.value = _uiState.value.copy(
                syncMessage = "Firebase initialization failed: ${e.message}"
            )
        }
    }

    // ---------------------------------------------------------
    // Authentication
    // ---------------------------------------------------------

    fun updateEmail(email: String) {
        repository.saveEmail(email)

        _uiState.value = _uiState.value.copy(
            email = email
        )
    }

    fun signIn(email: String, password: String) {
        val config = _uiState.value.firebaseConfig

        if (!config.isValid) {
            _uiState.value = _uiState.value.copy(
                syncMessage = "Save a valid Firebase configuration first."
            )
            return
        }

        if (email.isBlank() || password.isBlank()) {
            _uiState.value = _uiState.value.copy(
                syncMessage = "Enter your email and password."
            )
            return
        }

        val service = firebaseService ?: try {
            FirebaseService(
                getApplication(),
                config
            ).also {
                firebaseService = it
            }
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                syncMessage = "Firebase initialization failed: ${e.message}"
            )
            return
        }

        _uiState.value = _uiState.value.copy(
            syncBusy = true,
            syncMessage = "Signing in..."
        )

        service.signIn(email, password) { user, error ->

            if (error != null || user == null) {
                _uiState.value = _uiState.value.copy(
                    syncBusy = false,
                    syncMessage = "Sign-in failed: ${error?.message ?: "Unknown error"}"
                )
                return@signIn
            }

            repository.saveEmail(email.trim())

            _uiState.value = _uiState.value.copy(
                email = email.trim(),
                firebaseUserEmail = user.email,
                syncBusy = false,
                syncMessage = "Signed in successfully."
            )

            if (_uiState.value.syncEnabled) {
                synchronizeAndListen()
            }
        }
    }

    fun signOut() {
        stopFirebaseListener()

        debounceJob?.cancel()
        debounceJob = null

        firebaseService?.signOut()

        repository.saveSyncEnabled(false)

        _uiState.value = _uiState.value.copy(
            firebaseUserEmail = null,
            syncEnabled = false,
            syncBusy = false,
            syncMessage = "Signed out. Your list is saved locally."
        )
    }

    // ---------------------------------------------------------
    // Shopping list
    // ---------------------------------------------------------

    fun addItem(item: MercadoItem) {
        val newItem = item.copy(
            id = UUID.randomUUID().toString(),
            modifiedAt = System.currentTimeMillis()
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
                        updatedItem.copy(modifiedAt = now)
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
                items = current.items.filterNot { it.id == id },
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
                items = current.items.filterNot { it.ok },
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
                    current.items.associate { it.id to now }

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
    // Synchronization controls
    // ---------------------------------------------------------

    fun setSyncEnabled(enabled: Boolean) {
        if (enabled) {
            if (firebaseService?.currentUser() == null) {
                _uiState.value = _uiState.value.copy(
                    syncEnabled = false,
                    syncMessage = "Sign in before enabling synchronization."
                )
                return
            }

            repository.saveSyncEnabled(true)

            _uiState.value = _uiState.value.copy(
                syncEnabled = true,
                syncMessage = "Starting Firebase synchronization..."
            )

            synchronizeAndListen()

        } else {
            repository.saveSyncEnabled(false)

            debounceJob?.cancel()
            debounceJob = null

            stopFirebaseListener()

            _uiState.value = _uiState.value.copy(
                syncEnabled = false,
                syncBusy = false,
                syncMessage = "Synchronization disabled. Your list is saved locally."
            )
        }
    }

    fun syncNow() {
        if (!_uiState.value.syncEnabled) {
            _uiState.value = _uiState.value.copy(
                syncMessage = "Enable synchronization first."
            )
            return
        }

        synchronize()
    }

    fun onAppResumed() {
        if (!_uiState.value.syncEnabled) return

        if (firebaseListener == null) {
            synchronizeAndListen()
        } else {
            synchronize()
        }
    }

    // ---------------------------------------------------------
    // Firebase synchronization
    // ---------------------------------------------------------

    private fun scheduleDebouncedSync() {
        debounceJob?.cancel()

        if (!_uiState.value.syncEnabled) return
        if (firebaseService?.currentUser() == null) return

        debounceJob = viewModelScope.launch {
            delay(3000)
            synchronize()
        }
    }

    private fun synchronizeAndListen() {
        stopFirebaseListener()

        synchronize {
            attachFirebaseListener()
        }
    }

    private fun synchronize(onSuccess: (() -> Unit)? = null) {

        if (syncInProgress) {
            syncPending = true

            if (onSuccess != null) {
                pendingOnSuccess = onSuccess
            }

            return
        }

        val service = firebaseService

        if (service == null || service.currentUser() == null) {
            _uiState.value = _uiState.value.copy(
                syncMessage = "Sign in to Firebase before synchronizing."
            )
            return
        }

        syncInProgress = true

        val current = _uiState.value

        val localSnapshot = SyncSnapshot(
            items = current.items,
            deleted = current.deleted
        )

        _uiState.value = current.copy(
            syncBusy = true,
            syncMessage = "Synchronizing with Firebase..."
        )

        service.synchronize(localSnapshot) { merged, error ->

            syncInProgress = false

            if (error != null || merged == null) {
                _uiState.value = _uiState.value.copy(
                    syncBusy = false,
                    syncMessage = "Firebase sync failed: ${error?.message ?: "Unknown error"}. Your local list is still available."
                )

            } else {
                // Merge again with the latest local state.
                // This protects edits made while the transaction was running.

                val latestState = _uiState.value

                val latestLocalSnapshot = SyncSnapshot(
                    items = latestState.items,
                    deleted = latestState.deleted
                )

                val finalSnapshot = CloudSync.merge(
                    latestLocalSnapshot,
                    merged
                )

                _uiState.value = _uiState.value.copy(
                    items = finalSnapshot.items,
                    deleted = finalSnapshot.deleted,
                    syncBusy = false,
                    syncMessage = "Firebase synchronized successfully."
                )

                repository.saveSnapshot(finalSnapshot)

                onSuccess?.invoke()

                // If local edits occurred during the transaction,
                // synchronize those edits in another transaction.

                if (finalSnapshot != merged) {
                    syncPending = true
                }
            }

            val pendingCallback = pendingOnSuccess
            pendingOnSuccess = null

            if (pendingCallback != null && error == null) {
                pendingCallback()
            }

            if (syncPending) {
                syncPending = false

                if (_uiState.value.syncEnabled) {
                    synchronize()
                }
            }
        }
    }

    private fun attachFirebaseListener() {
        if (!_uiState.value.syncEnabled) return
        if (firebaseService?.currentUser() == null) return
        if (firebaseListener != null) return

        try {
            firebaseListener = firebaseService?.observeSnapshot(

                onChanged = { remoteSnapshot ->

                    val localSnapshot = SyncSnapshot(
                        items = _uiState.value.items,
                        deleted = _uiState.value.deleted
                    )

                    val merged = CloudSync.merge(
                        localSnapshot,
                        remoteSnapshot
                    )

                    _uiState.value = _uiState.value.copy(
                        items = merged.items,
                        deleted = merged.deleted,
                        syncMessage = "List updated from Firebase."
                    )

                    repository.saveSnapshot(merged)

                    // Upload only if local data contains newer changes.

                    if (merged != remoteSnapshot) {
                        scheduleDebouncedSync()
                    }
                },

                onError = { error ->
                    _uiState.value = _uiState.value.copy(
                        syncMessage = "Firebase listener error: ${error.message}"
                    )
                }
            )

        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                syncMessage = "Could not start Firebase listener: ${e.message}"
            )
        }
    }

    private fun stopFirebaseListener() {
        val listener = firebaseListener

        if (listener != null) {
            firebaseService?.removeListener(listener)
            firebaseListener = null
        }
    }

    override fun onCleared() {
        debounceJob?.cancel()
        stopFirebaseListener()
        super.onCleared()
    }
}