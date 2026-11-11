package com.thaiguer.mercado

data class FirebaseConfig(
    val apiKey: String = "",
    val projectId: String = "",
    val applicationId: String = "",
    val databaseUrl: String = "",
    val listId: String = "mercado"
) {
    val isValid: Boolean
        get() = apiKey.isNotBlank() &&
                projectId.isNotBlank() &&
                applicationId.isNotBlank() &&
                databaseUrl.isNotBlank() &&
                listId.isNotBlank()
}