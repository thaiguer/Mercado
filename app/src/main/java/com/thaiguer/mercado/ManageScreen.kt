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
    onClearQuantity: () -> Unit,
    onClearPrices: () -> Unit,
    onClearAll: () -> Unit,

    apiUrl: String,
    binId: String,
    accessKey: String,
    onSaveCloudConfig: (String, String, String) -> Unit,

    syncEnabled: Boolean,
    onSyncEnabledChange: (Boolean) -> Unit,
    syncMessage: String,
    syncBusy: Boolean,
    onSyncNow: () -> Unit
) {

    var urlInput by remember(apiUrl) {
        mutableStateOf(apiUrl)
    }

    var binInput by remember(binId) {
        mutableStateOf(binId)
    }

    var keyInput by remember(accessKey) {
        mutableStateOf(accessKey)
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

        // Cloud synchronization.

        Text(
            "Cloud synchronization",
            style = MaterialTheme.typography.titleMedium
        )

        OutlinedTextField(
            value = urlInput,
            onValueChange = {
                urlInput = it
            },
            label = {
                Text("API URL")
            },
            placeholder = {
                Text("https://api.jsonbin.io/v3")
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri
            ),
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = binInput,
            onValueChange = {
                binInput = it
            },
            label = {
                Text("Bin ID")
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = keyInput,
            onValueChange = {
                keyInput = it
            },
            label = {
                Text("Access Key")
            },
            placeholder = {
                Text("Paste your Access Key")
            },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = {
                onSaveCloudConfig(
                    urlInput,
                    binInput,
                    keyInput
                )
            },
            enabled = urlInput.isNotBlank() &&
                    binInput.isNotBlank() &&
                    keyInput.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save API settings")
        }

        HorizontalDivider()

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {

            Checkbox(
                checked = syncEnabled,
                onCheckedChange = onSyncEnabledChange
            )

            Text("Sync to cloud")
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
                    !syncBusy &&
                    apiUrl.isNotBlank() &&
                    binId.isNotBlank() &&
                    accessKey.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {

            Text(
                if (syncBusy) {
                    "Synchronizing..."
                } else {
                    "Sync now"
                }
            )
        }

        HorizontalDivider()

        // Product management.

        Text(
            "Product management",
            style = MaterialTheme.typography.titleMedium
        )

        Button(
            onClick = {
                requestConfirmation("selected")
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = items.any { it.ok }
        ) {
            Text(
                "Clear selected items (${items.count { it.ok }})"
            )
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

    if (showDialog) {

        val message = when (action) {

            "selected" ->
                "Remove all purchased items from the list?"

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

                            "selected" ->
                                onClearSelected()

                            "quantity" ->
                                onClearQuantity()

                            "prices" ->
                                onClearPrices()

                            "all" ->
                                onClearAll()
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