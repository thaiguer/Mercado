package com.thaiguer.mercado

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction
import com.google.firebase.database.ValueEventListener
import com.thaiguer.mercado.core.MercadoItem

class FirebaseService(
    context: Context,
    private val config: FirebaseConfig
) {
    private val firebaseApp: FirebaseApp
    private val auth: FirebaseAuth
    private val database: FirebaseDatabase

    private val listReference
        get() = database.reference
            .child("lists")
            .child(config.listId)

    init {
        require(config.isValid) {
            "Firebase configuration is incomplete."
        }

        require(
            config.listId.none { it in ".$#[]/" }
        ) {
            "List ID contains invalid Firebase characters."
        }

        val options = FirebaseOptions.Builder()
            .setApiKey(config.apiKey)
            .setProjectId(config.projectId)
            .setApplicationId(config.applicationId)
            .setDatabaseUrl(config.databaseUrl)
            .build()

        val appName = "mercado_${config.projectId}"

        firebaseApp = FirebaseApp.getApps(context)
            .firstOrNull { it.name == appName }
            ?: FirebaseApp.initializeApp(
                context.applicationContext,
                options,
                appName
            )
                    ?: throw IllegalStateException(
                "Could not initialize Firebase."
            )

        auth = FirebaseAuth.getInstance(firebaseApp)

        database = FirebaseDatabase.getInstance(
            firebaseApp,
            config.databaseUrl
        )
    }

    // ---------------------------------------------------------
    // Authentication
    // ---------------------------------------------------------

    fun currentUser(): FirebaseUser? {
        return auth.currentUser
    }

    fun signIn(
        email: String,
        password: String,
        onResult: (FirebaseUser?, Exception?) -> Unit
    ) {
        auth.signInWithEmailAndPassword(
            email.trim(),
            password
        ).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                onResult(auth.currentUser, null)
            } else {
                onResult(
                    null,
                    task.exception
                        ?: Exception("Authentication failed.")
                )
            }
        }
    }

    fun signOut() {
        auth.signOut()
    }

    // ---------------------------------------------------------
    // Real-time database listener
    // ---------------------------------------------------------

    fun observeSnapshot(
        onChanged: (SyncSnapshot) -> Unit,
        onError: (Exception) -> Unit
    ): ValueEventListener {

        val listener = object : ValueEventListener {

            override fun onDataChange(snapshot: DataSnapshot) {
                onChanged(parseSnapshot(snapshot.value))
            }

            override fun onCancelled(error: DatabaseError) {
                onError(error.toException())
            }
        }

        listReference.addValueEventListener(listener)

        return listener
    }

    fun removeListener(listener: ValueEventListener) {
        listReference.removeEventListener(listener)
    }

    // ---------------------------------------------------------
    // Synchronization
    // ---------------------------------------------------------

    fun synchronize(
        localSnapshot: SyncSnapshot,
        onComplete: (SyncSnapshot?, Exception?) -> Unit
    ) {
        listReference.runTransaction(
            object : Transaction.Handler {

                override fun doTransaction(
                    currentData: MutableData
                ): Transaction.Result {

                    val remoteSnapshot = parseSnapshot(
                        currentData.value
                    )

                    val merged = CloudSync.merge(
                        localSnapshot,
                        remoteSnapshot
                    )

                    currentData.value = snapshotToMap(merged)

                    return Transaction.success(currentData)
                }

                override fun onComplete(
                    error: DatabaseError?,
                    committed: Boolean,
                    currentData: DataSnapshot?
                ) {
                    if (error != null) {
                        onComplete(null, error.toException())
                        return
                    }

                    if (!committed || currentData == null) {
                        onComplete(
                            null,
                            Exception("Firebase transaction was not committed.")
                        )
                        return
                    }

                    onComplete(
                        parseSnapshot(currentData.value),
                        null
                    )
                }
            },
            false
        )
    }

    // ---------------------------------------------------------
    // Firebase serialization
    // ---------------------------------------------------------

    private fun snapshotToMap(
        snapshot: SyncSnapshot
    ): Map<String, Any> {

        val items = snapshot.items.associate { item ->
            item.id to mapOf(
                "id" to item.id,
                "name" to item.name,
                "ok" to item.ok,
                "count" to item.count,
                "price" to item.price,
                "modifiedAt" to item.modifiedAt
            )
        }

        val deleted = snapshot.deleted.mapValues { (_, timestamp) ->
            mapOf(
                "modifiedAt" to timestamp
            )
        }

        return mapOf(
            "items" to items,
            "deleted" to deleted
        )
    }

    private fun parseSnapshot(value: Any?): SyncSnapshot {

        val root = value as? Map<*, *>
            ?: return SyncSnapshot()

        val itemsMap = root["items"] as? Map<*, *>
            ?: emptyMap<Any, Any>()

        val deletedMap = root["deleted"] as? Map<*, *>
            ?: emptyMap<Any, Any>()

        val items = mutableListOf<MercadoItem>()

        for ((key, valueItem) in itemsMap) {

            val itemMap = valueItem as? Map<*, *>
                ?: continue

            val id = itemMap["id"] as? String
                ?: key as? String
                ?: continue

            val name = itemMap["name"] as? String
                ?: continue

            items.add(
                MercadoItem(
                    id = id,
                    name = name,
                    ok = itemMap["ok"] as? Boolean ?: false,
                    count = (itemMap["count"] as? Number)?.toInt() ?: 1,
                    price = (itemMap["price"] as? Number)?.toDouble() ?: 0.0,
                    modifiedAt = (itemMap["modifiedAt"] as? Number)?.toLong() ?: 0L
                )
            )
        }

        val deleted = mutableMapOf<String, Long>()

        for ((key, valueDeleted) in deletedMap) {

            val id = key as? String
                ?: continue

            val deletedEntry = valueDeleted as? Map<*, *>
                ?: continue

            val timestamp = (
                    deletedEntry["modifiedAt"] as? Number
                    )?.toLong() ?: 0L

            deleted[id] = timestamp
        }

        return SyncSnapshot(
            items = items,
            deleted = deleted
        )
    }
}