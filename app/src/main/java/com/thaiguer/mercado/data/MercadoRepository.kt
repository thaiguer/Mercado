package com.thaiguer.mercado.data

import android.content.Context
import com.thaiguer.mercado.FirebaseConfig
import com.thaiguer.mercado.SyncSnapshot

class MercadoRepository(context: Context) {

    private val localStorage = LocalStorage(context)

    // Local shopping list

    fun loadSnapshot(): SyncSnapshot {
        return localStorage.loadSnapshot()
    }

    fun saveSnapshot(snapshot: SyncSnapshot) {
        localStorage.saveSnapshot(snapshot)
    }

    // Firebase configuration

    fun loadFirebaseConfig(): FirebaseConfig {
        return localStorage.loadFirebaseConfig()
    }

    fun saveFirebaseConfig(config: FirebaseConfig) {
        localStorage.saveFirebaseConfig(config)
    }

    // Synchronization preference

    fun loadSyncEnabled(): Boolean {
        return localStorage.loadSyncEnabled()
    }

    fun saveSyncEnabled(enabled: Boolean) {
        localStorage.saveSyncEnabled(enabled)
    }

    // Remember the email, but never the password

    fun loadEmail(): String {
        return localStorage.loadEmail()
    }

    fun saveEmail(email: String) {
        localStorage.saveEmail(email)
    }
}