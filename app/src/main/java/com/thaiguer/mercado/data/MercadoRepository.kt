package com.thaiguer.mercado.data

import android.content.Context
import com.thaiguer.mercado.CloudConfig
import com.thaiguer.mercado.CloudSync
import com.thaiguer.mercado.SyncSnapshot

class MercadoRepository(context: Context) {

    private val localStorage = LocalStorage(context)

    fun loadSnapshot(): SyncSnapshot {
        return localStorage.loadSnapshot()
    }

    fun saveSnapshot(snapshot: SyncSnapshot) {
        localStorage.saveSnapshot(snapshot)
    }

    fun loadCloudConfig(): CloudConfig {
        return localStorage.loadCloudConfig()
    }

    fun saveCloudConfig(config: CloudConfig) {
        localStorage.saveCloudConfig(config)
    }

    fun loadSyncEnabled(): Boolean {
        return localStorage.loadSyncEnabled()
    }

    fun saveSyncEnabled(enabled: Boolean) {
        localStorage.saveSyncEnabled(enabled)
    }

    suspend fun synchronize(
        localSnapshot: SyncSnapshot,
        config: CloudConfig
    ): SyncSnapshot {

        val remote = CloudSync.downloadWithMetadata(config)

        val merged = CloudSync.merge(
            localSnapshot,
            remote.snapshot
        )

        if (merged != remote.snapshot) {
            CloudSync.upload(
                snapshot = merged,
                config = config,
                existingSha = remote.sha
            )
        }

        return merged
    }
}