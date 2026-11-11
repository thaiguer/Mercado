package com.thaiguer.mercado

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.thaiguer.mercado.core.MercadoItem

@Composable
fun ManageScreen(
    items: List<MercadoItem>,
    onClearSelected: () -> Unit,
    onRemoveSelected: () -> Unit,
    onClearQuantity: () -> Unit,
    onClearPrices: () -> Unit,
    onClearAll: () -> Unit,

    firebaseConfig: FirebaseConfig,
    onSaveFirebaseConfig: (FirebaseConfig) -> Unit,

    email: String,
    onEmailChange: (String) -> Unit,
    firebaseUserEmail: String?,
    onSignIn: (String, String) -> Unit,
    onSignOut: () -> Unit,

    syncEnabled: Boolean,
    onSyncEnabledChange: (Boolean) -> Unit,
    syncMessage: String,
    syncBusy: Boolean,
    onSyncNow: () -> Unit
) {
    var apiKeyInput by remember(firebaseConfig.apiKey) {
        mutableStateOf(firebaseConfig.apiKey)
    }

    var projectIdInput by remember(firebaseConfig.projectId) {
        mutableStateOf(firebaseConfig.projectId)
    }

    var appIdInput by remember(firebaseConfig.applicationId) {
        mutableStateOf(firebaseConfig.applicationId)
    }

    var databaseUrlInput by remember(firebaseConfig.databaseUrl) {
        mutableStateOf(firebaseConfig.databaseUrl)
    }

    var listIdInput by remember(firebaseConfig.listId) {
        mutableStateOf(firebaseConfig.listId)
    }

    var passwordInput by remember {
        mutableStateOf("")
    }

    var showDialog by remember {
        mutableStateOf(false)
    }

    var action by remember {
        mutableStateOf("")
    }

    fun requestConfirmation(type: String) {
        action = type
        showDialog = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Management",
            style = MaterialTheme.typography.headlineMedium
        )

        Text("Products: ${items.size}")

        HorizontalDivider()

        // -----------------------------------------------------
        // Firebase configuration
        // -----------------------------------------------------

        Text(
            "Firebase configuration",
            style = MaterialTheme.typography.titleMedium
        )

        OutlinedTextField(
            value = apiKeyInput,
            onValueChange = { apiKeyInput = it },
            label = { Text("API Key") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = projectIdInput,
            onValueChange = { projectIdInput = it },
            label = { Text("Project ID") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = appIdInput,
            onValueChange = { appIdInput = it },
            label = { Text("App ID") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = databaseUrlInput,
            onValueChange = { databaseUrlInput = it },
            label = { Text("Database URL") },
            placeholder = {
                Text("https://your-project-default-rtdb.firebaseio.com")
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri
            ),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = listIdInput,
            onValueChange = { listIdInput = it },
            label = { Text("List ID") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = {
                onSaveFirebaseConfig(
                    FirebaseConfig(
                        apiKey = apiKeyInput.trim(),
                        projectId = projectIdInput.trim(),
                        applicationId = appIdInput.trim(),
                        databaseUrl = databaseUrlInput.trim(),
                        listId = listIdInput.trim()
                    )
                )
            },
            enabled = apiKeyInput.isNotBlank() &&
                    projectIdInput.isNotBlank() &&
                    appIdInput.isNotBlank() &&
                    databaseUrlInput.isNotBlank() &&
                    listIdInput.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save Firebase settings")
        }

        HorizontalDivider()

        // -----------------------------------------------------
        // Firebase authentication
        // -----------------------------------------------------

        Text(
            "Firebase account",
            style = MaterialTheme.typography.titleMedium
        )

        if (firebaseUserEmail == null) {

            OutlinedTextField(
                value = email,
                onValueChange = onEmailChange,
                label = { Text("Email") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = passwordInput,
                onValueChange = { passwordInput = it },
                label = { Text("Password") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    onSignIn(email.trim(), passwordInput)
                    passwordInput = ""
                },
                enabled = email.isNotBlank() &&
                        passwordInput.isNotBlank() &&
                        firebaseConfig.isValid,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Sign in")
            }

        } else {

            Text("Signed in as: $firebaseUserEmail")

            OutlinedButton(
                onClick = onSignOut,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Sign out")
            }
        }

        HorizontalDivider()

        // -----------------------------------------------------
        // Synchronization
        // -----------------------------------------------------

        Text(
            "Synchronization",
            style = MaterialTheme.typography.titleMedium
        )

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = syncEnabled,
                onCheckedChange = onSyncEnabledChange,
                enabled = firebaseUserEmail != null
            )

            Text("Sync to Firebase")
        }

        Text(
            syncMessage,
            style = MaterialTheme.typography.bodySmall,
            color = if (
                syncMessage.contains("failed", ignoreCase = true) ||
                syncMessage.contains("error", ignoreCase = true)
            ) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )

        Button(
            onClick = onSyncNow,
            enabled = syncEnabled &&
                    firebaseUserEmail != null &&
                    !syncBusy,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                if (syncBusy) "Synchronizing..."
                else "Sync now"
            )
        }

        HorizontalDivider()

        // -----------------------------------------------------
        // Product management
        // -----------------------------------------------------

        Text(
            "Product management",
            style = MaterialTheme.typography.titleMedium
        )

        Button(
            onClick = {
                requestConfirmation("removeSelected")
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = items.any { it.ok },
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error
            )
        ) {
            Text("Remove selected items (${items.count { it.ok }})")
        }

        Button(
            onClick = onClearSelected,
            modifier = Modifier.fillMaxWidth(),
            enabled = items.any { it.ok }
        ) {
            Text("Uncheck selected items (${items.count { it.ok }})")
        }

        Button(
            onClick = {
                requestConfirmation("quantity")
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = items.any { it.count != 0 }
        ) {
            Text("Clear quantity")
        }

        Button(
            onClick = {
                requestConfirmation("prices")
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = items.any { it.price != 0.0 }
        ) {
            Text("Clear prices")
        }

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = {
                requestConfirmation("all")
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = items.isNotEmpty(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error
            )
        ) {
            Text("Clear ALL")
        }
    }

    // ---------------------------------------------------------
    // Confirmation dialog
    // ---------------------------------------------------------

    if (showDialog) {
        val message = when (action) {
            "removeSelected" ->
                "Remove all selected products permanently?"

            "quantity" ->
                "Reset the quantity of all products to zero?"

            "prices" ->
                "Reset all product prices to zero?"

            "all" ->
                "Are you sure you want to delete ALL products?"

            else -> ""
        }

        AlertDialog(
            onDismissRequest = {
                showDialog = false
            },
            title = {
                Text("Confirm action")
            },
            text = {
                Text(message)
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        when (action) {
                            "removeSelected" -> onRemoveSelected()
                            "quantity" -> onClearQuantity()
                            "prices" -> onClearPrices()
                            "all" -> onClearAll()
                        }

                        showDialog = false
                    }
                ) {
                    Text(
                        "Confirm",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDialog = false
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}